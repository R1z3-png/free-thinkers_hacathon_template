package org.freethinkers.transcriber.data.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.freethinkers.transcriber.domain.model.AudioFile

interface AudioMetadataReader {
    fun readAndValidate(contentUri: String): AudioFile
}

class AndroidAudioMetadataReader(context: Context) : AudioMetadataReader {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val applicationContext = context.applicationContext

    override fun readAndValidate(contentUri: String): AudioFile {
        val uri = Uri.parse(contentUri)
        val fileName = readDisplayName(uri)
        if (!fileName.endsWith(".mp3", ignoreCase = true)) {
            throw AudioImportException("Выберите файл с расширением .mp3.")
        }

        val durationMs = readDuration(uri)
        return AudioFile(
            contentUri = contentUri,
            fileName = fileName,
            durationMs = durationMs,
            importedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun readDisplayName(uri: Uri): String {
        val name = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name?.takeIf(String::isNotBlank)
            ?: throw AudioImportException("Не удалось получить имя выбранного файла.")
    }

    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: throw AudioImportException("Файл повреждён или не содержит читаемую аудиозапись.")
        } catch (exception: AudioImportException) {
            throw exception
        } catch (_: Exception) {
            throw AudioImportException("Не удалось прочитать MP3. Файл повреждён или не поддерживается.")
        } finally {
            retriever.release()
        }
    }
}

class AudioImportException(message: String) : IllegalArgumentException(message)
