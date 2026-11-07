package org.freethinkers.transcriber.domain.model

data class TranscriptionResult(
    val transcript: Transcript,
    val warningMessage: String? = null,
    /** ASR-aligned text before punctuation/case restoration; used for text-only retries. */
    val rawTranscript: Transcript = transcript,
    val nonPostProcessingWarningMessage: String? = null,
    val postProcessingWarningMessage: String? = null,
)
