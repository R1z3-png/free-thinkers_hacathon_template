package org.freethinkers.transcriber.data.processing

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.domain.processing.OfflineSpeechRecognizer
import org.freethinkers.transcriber.domain.processing.PcmAudio
import org.freethinkers.transcriber.domain.processing.RecognizedWord
import java.io.File

/** Kotlin adapter for the app's whisper.cpp JNI bridge. One native context is reused across ASR windows. */
class WhisperCppSpeechRecognizer(
    private val modelFile: File,
    private val threads: Int = java.lang.Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
    private val runtime: WhisperCppRuntime = JniWhisperCppRuntime,
) : OfflineSpeechRecognizer {
    private var nativeHandle: Long = 0

    init {
        require(threads > 0)
    }

    override suspend fun recognize(audio: PcmAudio, languageTag: String): List<RecognizedWord> =
        withContext(Dispatchers.Default) {
            checkModelFile()
            if (nativeHandle == 0L) {
                val modelLoadStartedAt = System.nanoTime()
                nativeHandle = runtime.create(modelFile.absolutePath, threads)
                val modelLoadElapsed = (System.nanoTime() - modelLoadStartedAt) / 1_000_000L
                Log.i(LOG_TAG, "model_load model=${modelFile.name} elapsed_ms=$modelLoadElapsed threads=$threads")
                check(nativeHandle != 0L) { "whisper.cpp не смог загрузить модель." }
            }
            val startedAt = System.nanoTime()
            Log.i(LOG_TAG, "transcription_start model=${modelFile.name} audio_ms=${audio.durationMs} threads=$threads")
            runtime.transcribe(nativeHandle, audio.samples, languageTag).also { words ->
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
                val rtf = "%.2f".format(java.util.Locale.ROOT, elapsed.toDouble() / audio.durationMs.coerceAtLeast(1L))
                Log.i(LOG_TAG, "transcription_done model=${modelFile.name} elapsed_ms=$elapsed audio_ms=${audio.durationMs} rtf=$rtf words=${words.size}")
            }
        }

    override suspend fun release() = withContext(Dispatchers.Default) {
        if (nativeHandle != 0L) {
            runtime.release(nativeHandle)
            nativeHandle = 0
        }
    }

    private fun checkModelFile() {
        check(modelFile.isFile && modelFile.length() > 0L) { "Whisper-модель не найдена: ${modelFile.name}" }
    }
    private companion object {
        const val LOG_TAG = "WhisperBenchmark"
    }
}

interface WhisperCppRuntime {
    fun create(modelPath: String, threads: Int): Long
    fun transcribe(handle: Long, samples: FloatArray, languageTag: String): List<RecognizedWord>
    fun release(handle: Long)
}

/** Requires the project-owned JNI shim to be built as libwhispercpp_jni.so. */
private object JniWhisperCppRuntime : WhisperCppRuntime {
    override fun create(modelPath: String, threads: Int): Long = callNative { nativeCreate(modelPath, threads) }
    override fun transcribe(handle: Long, samples: FloatArray, languageTag: String): List<RecognizedWord> =
        callNative { nativeTranscribe(handle, samples, languageTag).toList() }
    override fun release(handle: Long) = callNative { nativeRelease(handle) }

    private inline fun <T> callNative(block: () -> T): T = try {
        loadLibraryIfNeeded()
        block()
    } catch (error: UnsatisfiedLinkError) {
        throw IllegalStateException("Нативная библиотека whisper.cpp пока не собрана для этого ABI.", error)
    }

    private fun loadLibraryIfNeeded() {
        if (!loaded) {
            System.loadLibrary("whispercpp_jni")
            loaded = true
        }
    }

    private var loaded = false

    private external fun nativeCreate(modelPath: String, threads: Int): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, languageTag: String): Array<RecognizedWord>
    private external fun nativeRelease(handle: Long)
}
