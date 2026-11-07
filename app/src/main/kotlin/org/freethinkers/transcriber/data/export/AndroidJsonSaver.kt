package org.freethinkers.transcriber.data.export

import android.net.Uri

/** Adapter boundary for saving UTF-8 JSON through Android's system document picker. */
interface AndroidJsonSaver {
    suspend fun saveJson(destination: Uri, json: String): Boolean
}
