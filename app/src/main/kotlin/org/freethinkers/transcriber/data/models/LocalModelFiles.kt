package org.freethinkers.transcriber.data.models

import android.content.Context
import java.io.File

/** Paths correspond to android-port/model-manifest.json in the Python reference repository. */
data class LocalModelFiles(
    val whisper: File,
    val diarizationSegmentation: File,
    val speakerEmbedding: File,
    val qwen: File,
) {
    fun arePresent(): Boolean = listOf(whisper, diarizationSegmentation, speakerEmbedding, qwen)
        .all { it.isFile && it.length() > 0L }

    companion object {
        fun inAppStorage(context: Context): LocalModelFiles {
            val directory = File(context.applicationContext.filesDir, "models")
            return LocalModelFiles(
                whisper = File(directory, "ggml-base-q5_1.bin"),
                diarizationSegmentation = File(directory, "segmentation.onnx"),
                speakerEmbedding = File(directory, "embedding.onnx"),
                qwen = File(directory, "llm.gguf"),
            )
        }
    }
}
