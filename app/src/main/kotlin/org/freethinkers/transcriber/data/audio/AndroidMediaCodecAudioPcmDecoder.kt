package org.freethinkers.transcriber.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.domain.processing.AudioPcmDecoder
import org.freethinkers.transcriber.domain.processing.PcmAudio
import org.freethinkers.transcriber.domain.processing.PcmAudioPreprocessor
import java.nio.ByteOrder

/** Decodes audio selected through SAF using Android's installed codecs, then normalizes to mono 16 kHz. */
class AndroidMediaCodecAudioPcmDecoder(context: Context) : AudioPcmDecoder {
    private val appContext = context.applicationContext

    override suspend fun decodeToMono16k(contentUri: String): PcmAudio = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val uri = Uri.parse(contentUri)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioImportException("В выбранном файле не найдена аудиодорожка.")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw AudioImportException("Не удалось определить формат аудиодорожки.")
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val samples = FloatSampleAccumulator()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw AudioImportException("Не удалось получить буфер декодера.")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        outputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        outputEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            samples.add(outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN), outputEncoding)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (samples.size == 0) throw AudioImportException("В аудиофайле нет декодированных данных.")
            PcmAudioPreprocessor.toMono16k(samples.toFloatArray(), outputSampleRate, outputChannels).also { audio ->
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
                Log.i(LOG_TAG, "decode_done elapsed_ms=$elapsed audio_ms=${audio.durationMs} sample_rate=$outputSampleRate channels=$outputChannels")
            }
        } catch (error: AudioImportException) {
            throw error
        } catch (error: Exception) {
            throw AudioImportException("Не удалось декодировать аудиофайл: ${error.message ?: "неподдерживаемый формат"}")
        } finally {
            codec?.runCatching { stop() }
            codec?.release()
            extractor.release()
        }
    }

    private class FloatSampleAccumulator {
        private var values = FloatArray(INITIAL_CAPACITY)
        var size: Int = 0
            private set

        fun add(buffer: java.nio.ByteBuffer, encoding: Int) {
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT -> 2
                PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_FLOAT, PCM_32BIT -> 4
                else -> throw AudioImportException("Формат PCM декодера не поддерживается: $encoding")
            }
            val count = buffer.remaining() / bytesPerSample
            ensureCapacity(size + count)
            repeat(count) {
                val value = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                    AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32768f
                    PCM_24BIT_PACKED -> {
                        val low = buffer.get().toInt() and 0xff
                        val middle = buffer.get().toInt() and 0xff
                        val high = buffer.get().toInt()
                        val raw = (high shl 16) or (middle shl 8) or low
                        (if (raw and 0x800000 != 0) raw or -0x1000000 else raw) / 8_388_608f
                    }
                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
                    PCM_32BIT -> buffer.int / 2_147_483_648f
                    else -> error("Encoding is checked above")
                }
                values[size++] = value.coerceIn(-1f, 1f)
            }
        }

        fun toFloatArray(): FloatArray = values.copyOf(size)

        private fun ensureCapacity(minimum: Int) {
            if (minimum <= values.size) return
            var capacity = values.size
            while (capacity < minimum) capacity = (capacity * 1.5).toInt().coerceAtLeast(capacity + 1)
            values = values.copyOf(capacity)
        }
    }

    private companion object {
        const val LOG_TAG = "AudioBenchmark"
        const val CODEC_TIMEOUT_US = 10_000L
        const val INITIAL_CAPACITY = 16_384
        const val PCM_24BIT_PACKED = 21
        const val PCM_32BIT = 22
    }
}
