package org.freethinkers.transcriber.domain.processing

import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.TranscriptionResult
import org.freethinkers.transcriber.domain.model.Utterance
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min

/** A mono, 16 kHz floating-point audio buffer. The decoder owns its memory. */
data class PcmAudio(val samples: FloatArray, val sampleRateHz: Int = TARGET_SAMPLE_RATE_HZ) {
    init {
        require(sampleRateHz == TARGET_SAMPLE_RATE_HZ) { "Pipeline input must be 16 kHz PCM." }
        require(samples.isNotEmpty()) { "Audio contains no samples." }
        require(samples.all(Float::isFinite)) { "Audio contains invalid samples." }
    }

    val durationMs: Long
        get() = (samples.size.toLong() * 1_000L + sampleRateHz - 1) / sampleRateHz

    companion object {
        const val TARGET_SAMPLE_RATE_HZ = 16_000
    }
}

/** Converts decoded interleaved PCM to normalized mono float PCM at the pipeline rate. */
object PcmAudioPreprocessor {
    fun toMono16k(interleavedSamples: FloatArray, sourceSampleRateHz: Int, channelCount: Int): PcmAudio {
        require(sourceSampleRateHz > 0) { "Source sample rate must be positive." }
        require(channelCount > 0) { "Channel count must be positive." }
        require(interleavedSamples.isNotEmpty() && interleavedSamples.size % channelCount == 0) {
            "Decoded PCM does not contain complete audio frames."
        }
        val frameCount = interleavedSamples.size / channelCount
        if (sourceSampleRateHz == PcmAudio.TARGET_SAMPLE_RATE_HZ && channelCount == 1) {
            return PcmAudio(interleavedSamples)
        }

        val outputSize = max(1, (frameCount.toLong() * PcmAudio.TARGET_SAMPLE_RATE_HZ / sourceSampleRateHz).toInt())
        val resampled = FloatArray(outputSize) { outputIndex ->
            val sourcePosition = outputIndex.toDouble() * sourceSampleRateHz / PcmAudio.TARGET_SAMPLE_RATE_HZ
            val left = min(sourcePosition.toInt(), frameCount - 1)
            val right = min(left + 1, frameCount - 1)
            val fraction = (sourcePosition - left).toFloat()
            var leftSum = 0.0
            var rightSum = 0.0
            for (channel in 0 until channelCount) {
                leftSum += interleavedSamples[left * channelCount + channel]
                rightSum += interleavedSamples[right * channelCount + channel]
            }
            val leftMono = leftSum / channelCount
            val rightMono = rightSum / channelCount
            (leftMono * (1.0 - fraction) + rightMono * fraction).coerceIn(-1.0, 1.0).toFloat()
        }
        return PcmAudio(resampled)
    }
}

data class RecognizedWord(val startMs: Long, val endMs: Long, val text: String)
data class SpeakerTurn(val startMs: Long, val endMs: Long, val speakerId: String)

/** Platform adapter: decode the selected audio file and normalize it to mono float 16 kHz. */
fun interface AudioPcmDecoder {
    suspend fun decodeToMono16k(contentUri: String): PcmAudio
}

/** Model adapter boundaries. Implementations can later wrap ONNX/JNI runtimes without changing orchestration. */
fun interface OfflineSpeechRecognizer {
    suspend fun recognize(audio: PcmAudio, languageTag: String): List<RecognizedWord>
    suspend fun release() = Unit
}

fun interface OfflineSpeakerDiarizer {
    suspend fun diarize(audio: PcmAudio): List<SpeakerTurn>
}

fun interface OfflineTextPostProcessor {
    suspend fun punctuateAndRestoreCase(text: String, languageTag: String): String
    suspend fun release() = Unit
}

data class AudioPipelineComponents(
    val decoder: AudioPcmDecoder,
    val speechRecognizer: OfflineSpeechRecognizer,
    val diarizer: OfflineSpeakerDiarizer,
    val textPostProcessor: OfflineTextPostProcessor?,
)

/** Kotlin orchestration for the local pipeline; all model implementations are injected. */
class AudioProcessingPipeline(
    private val components: AudioPipelineComponents,
    private val onDiarizationFailure: (Exception) -> Unit = {},
) {
    suspend fun process(contentUri: String, languageTag: String): TranscriptionResult {
        require(languageTag == "ru" || languageTag == "en") { "Unsupported recognition language: $languageTag" }
        val audio = components.decoder.decodeToMono16k(contentUri)
        val durationMs = audio.durationMs
        val warnings = mutableListOf<String>()
        val speakerTurns = try {
            canonicalizeTurns(components.diarizer.diarize(audio), durationMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onDiarizationFailure(error)
            warnings += DIARIZATION_FALLBACK_WARNING
            emptyList()
        }

        // ASR errors intentionally propagate: without recognized words there is no usable transcript.
        val windows = createAsrWindows(speakerTurns, durationMs)
        val words = try {
            windows.flatMap { window ->
                val startSample = window.first * PcmAudio.TARGET_SAMPLE_RATE_HZ / 1_000
                val endSample = min(audio.samples.size.toLong(), window.second * PcmAudio.TARGET_SAMPLE_RATE_HZ / 1_000)
                val windowAudio = PcmAudio(audio.samples.copyOfRange(startSample.toInt(), endSample.toInt()))
                components.speechRecognizer.recognize(windowAudio, languageTag).mapNotNull { word ->
                    require(word.startMs >= 0 && word.startMs <= word.endMs) { "ASR returned invalid timestamps." }
                    normalizeWord(word.copy(startMs = word.startMs + window.first, endMs = word.endMs + window.first), durationMs)
                }
            }.sortedWith(compareBy<RecognizedWord> { it.startMs }.thenBy { it.endMs })
        } finally {
            components.speechRecognizer.release()
        }
        val raw = Transcript(renumberObservedSpeakers(alignWords(words, speakerTurns)))
        if (raw.utterances.isNotEmpty() && speakerTurns.isEmpty() && warnings.isEmpty()) {
            warnings += DIARIZATION_EMPTY_WARNING
        }
        validateTranscript(raw, durationMs)
        val nonPostWarning = warnings.distinct().takeIf { it.isNotEmpty() }?.joinToString(" ")
        if (raw.utterances.isEmpty()) return TranscriptionResult(raw, nonPostWarning, raw, nonPostWarning)

        val processor = components.textPostProcessor
        if (processor == null) {
            warnings += TEXT_MODEL_UNAVAILABLE_WARNING
            return TranscriptionResult(raw, listOfNotNull(nonPostWarning, TEXT_MODEL_UNAVAILABLE_WARNING).joinToString(" "), raw, nonPostWarning, TEXT_MODEL_UNAVAILABLE_WARNING)
        }
        val finalized = try {
            raw.utterances.map { utterance ->
                if (utterance.text == UNINTELLIGIBLE_TEXT) return@map utterance
                try {
                    val candidate = processor.punctuateAndRestoreCase(utterance.text, languageTag)
                    utterance.copy(text = acceptSafeText(utterance.text, candidate))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings += TEXT_POSTPROCESSING_WARNING
                    utterance
                }
            }
        } finally {
            processor.release()
        }
        val transcript = Transcript(finalized)
        validateTranscript(transcript, durationMs)
        val postWarning = TEXT_POSTPROCESSING_WARNING.takeIf { TEXT_POSTPROCESSING_WARNING in warnings }
        return TranscriptionResult(
            transcript,
            listOfNotNull(nonPostWarning, postWarning).joinToString(" ").ifEmpty { null },
            raw,
            nonPostWarning,
            postWarning,
        )
    }

    suspend fun retryPostProcessing(rawTranscript: Transcript, languageTag: String): TranscriptionResult {
        require(languageTag == "ru" || languageTag == "en") { "Unsupported recognition language: $languageTag" }
        val processor = components.textPostProcessor
            ?: return TranscriptionResult(rawTranscript, TEXT_MODEL_UNAVAILABLE_WARNING, rawTranscript, postProcessingWarningMessage = TEXT_MODEL_UNAVAILABLE_WARNING)
        val warnings = mutableSetOf<String>()
        val finalized = try {
            rawTranscript.utterances.map { utterance ->
                if (utterance.text == UNINTELLIGIBLE_TEXT) return@map utterance
                try {
                    utterance.copy(text = acceptSafeText(utterance.text, processor.punctuateAndRestoreCase(utterance.text, languageTag)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings += TEXT_POSTPROCESSING_WARNING
                    utterance
                }
            }
        } finally {
            processor.release()
        }
        val postWarning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")
        return TranscriptionResult(Transcript(finalized), postWarning, rawTranscript, postProcessingWarningMessage = postWarning)
    }

    private fun canonicalizeTurns(turns: List<SpeakerTurn>, durationMs: Long): List<SpeakerTurn> {
        val labels = linkedMapOf<String, String>()
        return turns.sortedWith(compareBy<SpeakerTurn> { it.startMs }.thenBy { it.endMs }.thenBy { it.speakerId })
            .mapNotNull { turn ->
                if (turn.startMs > turn.endMs) return@mapNotNull null
                val startMs = turn.startMs.coerceIn(0L, durationMs)
                val endMs = turn.endMs.coerceIn(startMs, durationMs)
                if (startMs == endMs) null else turn.copy(
                    startMs = startMs,
                    endMs = endMs,
                    speakerId = labels.getOrPut(turn.speakerId) { "speaker_${labels.size + 1}" },
                )
            }
    }

    private fun normalizeWord(word: RecognizedWord, durationMs: Long): RecognizedWord? {
        require(word.startMs >= 0 && word.startMs <= word.endMs) { "ASR returned invalid timestamps." }
        val start = word.startMs.coerceAtMost(durationMs)
        val end = word.endMs.coerceAtMost(durationMs).coerceAtLeast(start)
        val text = word.text.trim()
        return if (text.isEmpty() || end == start) null else word.copy(startMs = start, endMs = end, text = text)
    }

    private fun createAsrWindows(turns: List<SpeakerTurn>, durationMs: Long): List<Pair<Long, Long>> {
        if (turns.isEmpty()) return listOf(0L to durationMs)
        val regions = mutableListOf<Pair<Long, Long>>()
        for (turn in turns.sortedBy { it.startMs }) {
            val previous = regions.lastOrNull()
            if (previous != null && (turn.startMs <= previous.second || turn.startMs - previous.second <= ASR_MERGE_GAP_MS)) {
                regions[regions.lastIndex] = previous.first to max(previous.second, turn.endMs)
            } else {
                regions += turn.startMs to turn.endMs
            }
        }
        return regions.mapIndexed { index, region ->
            val previousEnd = regions.getOrNull(index - 1)?.second
            val nextStart = regions.getOrNull(index + 1)?.first
            val leftBoundary = if (previousEnd == null) 0 else previousEnd + (region.first - previousEnd) / 2
            val rightBoundary = if (nextStart == null) durationMs else region.second + (nextStart - region.second) / 2
            max(leftBoundary, region.first - ASR_WINDOW_PADDING_MS) to
                min(rightBoundary, region.second + ASR_WINDOW_PADDING_MS)
        }
    }

    private fun alignWords(words: List<RecognizedWord>, turns: List<SpeakerTurn>): List<Utterance> {
        val speechRegions = turns.sortedBy { it.startMs }.fold(mutableListOf<Pair<Long, Long>>()) { regions, turn ->
            val previous = regions.lastOrNull()
            if (previous != null && turn.startMs <= previous.second) {
                regions[regions.lastIndex] = previous.first to max(previous.second, turn.endMs)
            } else regions += turn.startMs to turn.endMs
            regions
        }
        val wordsWithGaps = words.toMutableList()
        speechRegions.filter { (start, end) -> end - start >= 200 && words.none { it.startMs < end && it.endMs > start } }
            .forEach { (start, end) -> wordsWithGaps += RecognizedWord(start, end, UNINTELLIGIBLE_TEXT) }

        val result = mutableListOf<Utterance>()
        for (word in wordsWithGaps.sortedWith(compareBy<RecognizedWord> { it.startMs }.thenBy { it.endMs })) {
            val overlapping = turns.filter { it.startMs < max(word.endMs, word.startMs + 1) && it.endMs > word.startMs }
            val speaker = when {
                overlapping.map { it.speakerId }.distinct().size > 1 -> "overlap"
                overlapping.isEmpty() -> "unknown"
                else -> overlapping.groupBy { it.speakerId }.maxBy { (_, items) ->
                    items.sumOf { min(word.endMs, it.endMs) - max(word.startMs, it.startMs) }
                }.key
            }
            val previous = result.lastOrNull()
            if (previous != null && previous.speakerId == speaker && word.text != UNINTELLIGIBLE_TEXT &&
                previous.text != UNINTELLIGIBLE_TEXT && word.startMs - previous.endMs <= MAX_WORD_GAP_MS &&
                word.endMs - previous.startMs <= MAX_UTTERANCE_MS
            ) {
                result[result.lastIndex] = previous.copy(endMs = max(previous.endMs, word.endMs), text = "${previous.text} ${word.text}")
            } else result += Utterance(speaker, word.startMs, word.endMs, word.text)
        }
        return result
    }

    /** Number only speakers that actually appear in utterances; overlap is a label, not a speaker. */
    private fun renumberObservedSpeakers(utterances: List<Utterance>): List<Utterance> {
        val labels = linkedMapOf<String, String>()
        return utterances.map { utterance ->
            if (!utterance.speakerId.startsWith("speaker_")) return@map utterance
            utterance.copy(
                speakerId = labels.getOrPut(utterance.speakerId) { "speaker_${labels.size + 1}" },
            )
        }
    }

    private fun acceptSafeText(original: String, candidate: String): String {
        val cleaned = candidate.trim().replace(WHITESPACE, " ")
        require(cleaned.isNotEmpty()) { "Text post-processor returned empty text." }
        require(normalizedWords(original) == normalizedWords(cleaned)) { "Text post-processor changed recognized words." }
        require(cleaned.codePointCount(0, cleaned.length) <= maxOf(64, original.codePointCount(0, original.length) * 3)) {
            "Text post-processor returned unexpectedly long text."
        }
        require(cleaned.codePoints().allMatch {
            Character.isLetterOrDigit(it) || Character.isWhitespace(it) ||
                (it <= Char.MAX_VALUE.code && it.toChar() in ALLOWED_PUNCTUATION)
        }) {
            "Text post-processor added unsupported characters."
        }
        return cleaned
    }

    private fun normalizedWords(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

    private fun validateTranscript(transcript: Transcript, durationMs: Long) {
        var previousStart = -1L
        transcript.utterances.forEach { utterance ->
            require(SPEAKER_ID.matches(utterance.speakerId)) { "Invalid speaker identifier." }
            require(utterance.text.isNotBlank()) { "Transcript contains empty text." }
            require(utterance.startMs >= 0 && utterance.startMs <= utterance.endMs && utterance.endMs <= durationMs) {
                "Transcript timestamps exceed audio duration."
            }
            require(utterance.startMs >= previousStart) { "Transcript rows are not ordered by start time." }
            previousStart = utterance.startMs
        }
    }

    private companion object {
        const val UNINTELLIGIBLE_TEXT = "[Неразборчиво]"
        const val MAX_WORD_GAP_MS = 700L
        const val MAX_UTTERANCE_MS = 15_000L
        const val ASR_WINDOW_PADDING_MS = 500L
        const val ASR_MERGE_GAP_MS = 1_500L
        const val DIARIZATION_FALLBACK_WARNING = "Не удалось определить говорящих; для реплик указан unknown."
        const val DIARIZATION_EMPTY_WARNING = "Диаризация не нашла говорящих; для реплик указан unknown."
        const val TEXT_MODEL_UNAVAILABLE_WARNING = "Локальная языковая модель пока не подключена; сохранён результат ASR."
        const val TEXT_POSTPROCESSING_WARNING = "Не удалось обработать часть текста; для этих реплик сохранён результат ASR."
        val SPEAKER_ID = Regex("speaker_[1-9][0-9]*|unknown|overlap")
        val WORD = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")
        val WHITESPACE = Regex("\\s+")
        val ALLOWED_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '…', '—', '–', '-', '(', ')', '[', ']', '«', '»', '"', '\'', '’')
    }
}
