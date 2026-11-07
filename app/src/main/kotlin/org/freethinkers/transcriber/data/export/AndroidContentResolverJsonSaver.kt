package org.freethinkers.transcriber.data.export

import android.content.ContentResolver
import android.net.Uri

class AndroidContentResolverJsonSaver(
    private val contentResolver: ContentResolver,
) : AndroidJsonSaver {
    override suspend fun saveJson(destination: Uri, json: String): Boolean = try {
        val output = contentResolver.openOutputStream(destination) ?: return false
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
        }
        true
    } catch (_: Exception) {
        false
    }
}
