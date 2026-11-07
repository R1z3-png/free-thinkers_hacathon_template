package org.freethinkers.transcriber.data.models

import org.freethinkers.transcriber.domain.repository.ModelRepository

class LocalModelRepository : ModelRepository {
    override fun areRequiredModelsInstalled(): Boolean = TODO("Check model files in private app storage")

    override suspend fun installRequiredModels(): Unit = TODO("Download models during first-time setup")
}
