package org.freethinkers.transcriber.data.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.domain.repository.ModelRepository
import java.io.File
import java.security.MessageDigest

class LocalModelRepository(context: Context) : ModelRepository {
    private val appContext = context.applicationContext
    private val modelFiles = LocalModelFiles.inAppStorage(context)

    override fun areRequiredModelsInstalled(): Boolean =
        modelFiles.arePresent() && nativeBridgesAreLoadable()

    private fun nativeBridgesAreLoadable(): Boolean = try {
        System.loadLibrary("whispercpp_jni")
        System.loadLibrary("llamacpp_jni")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    override suspend fun installRequiredModels() = withContext(Dispatchers.IO) {
        modelFiles.whisper.parentFile?.mkdirs()
        MODEL_ASSETS.forEach { model ->
            val destination = File(modelFiles.whisper.parentFile, model.fileName)
            if (destination.isFile && destination.length() == model.byteCount) return@forEach

            val temporary = File(destination.parentFile, "${model.fileName}.part")
            temporary.delete()
            try {
                appContext.assets.open("models/${model.fileName}").use { input ->
                    temporary.outputStream().buffered().use(input::copyTo)
                }
                check(temporary.length() == model.byteCount) { "Model size mismatch: ${model.fileName}" }
                check(sha256(temporary) == model.sha256) { "Model checksum mismatch: ${model.fileName}" }
                java.nio.file.Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (error: Exception) {
                temporary.delete()
                throw IllegalStateException("Could not install model ${model.fileName}: ${error.message}", error)
            }
        }
        File(modelFiles.whisper.parentFile, OBSOLETE_WHISPER_MODEL).delete()
        Unit
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class BundledModel(val fileName: String, val byteCount: Long, val sha256: String)

    private companion object {
        val MODEL_ASSETS = listOf(
            BundledModel("ggml-base-q5_1.bin", 59_707_625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
            BundledModel("segmentation.onnx", 5_992_913L, "220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079"),
            BundledModel("embedding.onnx", 39_593_761L, "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"),
            BundledModel("llm.gguf", 491_400_032L, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"),
        )
        const val OBSOLETE_WHISPER_MODEL = "ggml-small-q5_1.bin"
    }
}
