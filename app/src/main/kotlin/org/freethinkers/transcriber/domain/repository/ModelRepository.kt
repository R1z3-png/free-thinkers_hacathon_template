package org.freethinkers.transcriber.domain.repository

interface ModelRepository {
    /** True only when every required model file, including the LLM, is installed in app-private storage. */
    fun areRequiredModelsInstalled(): Boolean

    suspend fun installRequiredModels()
}
