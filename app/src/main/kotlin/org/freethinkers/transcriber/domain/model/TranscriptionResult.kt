package org.freethinkers.transcriber.domain.model

data class TranscriptionResult(
    val transcript: Transcript,
    val warningMessage: String? = null,
)
