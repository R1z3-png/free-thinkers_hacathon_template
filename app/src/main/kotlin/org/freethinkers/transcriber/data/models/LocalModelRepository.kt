package org.freethinkers.transcriber.data.models

import org.freethinkers.transcriber.domain.repository.ModelRepository

class LocalModelRepository : ModelRepository {
    // Model locations and manifests depend on the Python/ML runtime choice, which is not finalized yet.
    override fun areRequiredModelsInstalled(): Boolean = false

    override suspend fun installRequiredModels(): Unit = TODO("Download models during first-time setup")
}
