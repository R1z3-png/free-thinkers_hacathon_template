package org.freethinkers.transcriber.data.export

/** Adapter boundary for saving UTF-8 JSON through Android's system document picker. */
interface AndroidJsonSaver {
    suspend fun saveJson(suggestedFileName: String, json: String): Boolean
}
