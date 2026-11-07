package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.Transcript

class ProcessAudio {
    suspend fun execute(languageTag: String): Transcript = TODO("Run local ASR, diarization, and post-processing")
}
