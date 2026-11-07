package org.freethinkers.transcriber.domain.model

data class AudioSession(
    val audioFile: AudioFile,
    val languageTag: String = "ru",
    val processingStatus: ProcessingStatus = ProcessingStatus.READY,
    val transcript: Transcript? = null,
    val rawTranscript: Transcript? = null,
    val warningMessage: String? = null,
    val nonPostProcessingWarningMessage: String? = null,
    val postProcessingWarningMessage: String? = null,
    val errorMessage: String? = null,
) {
    val canRetryPostProcessing: Boolean
        get() = processingStatus == ProcessingStatus.COMPLETED &&
            rawTranscript != null && postProcessingWarningMessage != null
}
