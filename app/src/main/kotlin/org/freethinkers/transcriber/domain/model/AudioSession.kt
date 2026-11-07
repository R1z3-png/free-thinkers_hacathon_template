package org.freethinkers.transcriber.domain.model

data class AudioSession(
    val audioFile: AudioFile,
    val languageTag: String = "ru",
    val processingStatus: ProcessingStatus = ProcessingStatus.READY,
    val transcript: Transcript? = null,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
)
