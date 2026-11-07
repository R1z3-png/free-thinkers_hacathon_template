package org.freethinkers.transcriber.domain.repository

interface ModelRepository {
    fun areRequiredModelsInstalled(): Boolean

    suspend fun installRequiredModels()
}
