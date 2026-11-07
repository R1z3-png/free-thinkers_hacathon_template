package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.Transcript

class RetryPostProcessing {
    suspend fun execute(): Transcript = TODO("Retry local post-processing on the existing ASR result")
}
