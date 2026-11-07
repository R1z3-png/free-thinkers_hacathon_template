package org.freethinkers.transcriber.data.processing

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.domain.processing.OfflineTextPostProcessor
import java.io.File

/** Runs the Qwen GGUF model through a llama.cpp JNI bridge and returns plain text for Kotlin validation. */
class QwenGgufTextPostProcessor(
    private val modelFile: File,
    private val contextTokens: Int = 1_024,
    private val threads: Int = 2,
    private val runtime: LlamaCppRuntime = JniLlamaCppRuntime,
) : OfflineTextPostProcessor {
    private var nativeHandle: Long = 0

    init {
        require(contextTokens > 0 && threads > 0)
    }

    override suspend fun punctuateAndRestoreCase(text: String, languageTag: String): String =
        withContext(Dispatchers.Default) {
            check(modelFile.isFile && modelFile.length() > 0L) { "Qwen GGUF-модель не найдена: ${modelFile.name}" }
            if (nativeHandle == 0L) {
                val loadStartedAt = SystemClock.elapsedRealtime()
                nativeHandle = runtime.create(modelFile.absolutePath, contextTokens, threads)
                Log.i("QwenBenchmark", "model_load elapsed_ms=${SystemClock.elapsedRealtime() - loadStartedAt} threads=$threads")
                check(nativeHandle != 0L) { "llama.cpp не смог загрузить Qwen GGUF-модель." }
            }
            val completionStartedAt = SystemClock.elapsedRealtime()
            val outputLimit = maxOutputTokensFor(text)
            val expectedWordCount = WORD_TOKEN.findAll(text).count()
            val result = runtime.complete(
                nativeHandle,
                buildPrompt(text, languageTag),
                outputLimit,
                expectedWordCount,
            ).trim()
            Log.i(
                "QwenBenchmark",
                "completion elapsed_ms=${SystemClock.elapsedRealtime() - completionStartedAt} threads=$threads max_output_tokens=$outputLimit",
            )
            result
        }

    override suspend fun release() = withContext(Dispatchers.Default) {
        if (nativeHandle != 0L) {
            runtime.release(nativeHandle)
            nativeHandle = 0
        }
    }

    private fun buildPrompt(text: String, languageTag: String): String {
        val languageName = if (languageTag == "ru") "Russian" else "English"
        return """<|im_start|>system
Format this $languageName transcript with punctuation and capitalization. Preserve every word and number exactly and in order. Output only the formatted transcript.
<|im_end|>
<|im_start|>user
$text
<|im_end|>
<|im_start|>assistant
""".trimIndent()
    }

    private fun maxOutputTokensFor(text: String): Int {
        val wordCount = WORD_TOKEN.findAll(text).count()
        return (wordCount * TOKENS_PER_WORD + OUTPUT_TOKEN_ALLOWANCE).coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)
    }

    private companion object {
        const val TOKENS_PER_WORD = 2
        const val OUTPUT_TOKEN_ALLOWANCE = 12
        const val MIN_OUTPUT_TOKENS = 24
        const val MAX_OUTPUT_TOKENS = 64
        val WORD_TOKEN = Regex("[\\p{L}\\p{N}]+")
    }
}

interface LlamaCppRuntime {
    fun create(modelPath: String, contextTokens: Int, threads: Int): Long
    fun complete(handle: Long, prompt: String, maxOutputTokens: Int, expectedWordCount: Int): String
    fun release(handle: Long)
}

/** Requires a project-owned llama.cpp JNI shim to be built as libllamacpp_jni.so. */
private object JniLlamaCppRuntime : LlamaCppRuntime {
    override fun create(modelPath: String, contextTokens: Int, threads: Int): Long =
        callNative { nativeCreate(modelPath, contextTokens, threads) }
    override fun complete(handle: Long, prompt: String, maxOutputTokens: Int, expectedWordCount: Int): String =
        callNative { nativeComplete(handle, prompt, maxOutputTokens, expectedWordCount) }
    override fun release(handle: Long) = callNative { nativeRelease(handle) }

    private inline fun <T> callNative(block: () -> T): T = try {
        loadLibraryIfNeeded()
        block()
    } catch (error: UnsatisfiedLinkError) {
        throw IllegalStateException("Нативная библиотека llama.cpp пока не собрана для этого ABI.", error)
    }

    private fun loadLibraryIfNeeded() {
        if (!loaded) {
            System.loadLibrary("llamacpp_jni")
            loaded = true
        }
    }

    private var loaded = false

    private external fun nativeCreate(modelPath: String, contextTokens: Int, threads: Int): Long
    private external fun nativeComplete(handle: Long, prompt: String, maxOutputTokens: Int, expectedWordCount: Int): String
    private external fun nativeRelease(handle: Long)
}
