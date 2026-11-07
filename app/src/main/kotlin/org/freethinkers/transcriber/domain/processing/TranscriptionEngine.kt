package org.freethinkers.transcriber.domain.processing

import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.TranscriptionResult

/** Boundary for the future on-device ASR, diarization, and local post-processing pipeline. */
interface TranscriptionEngine {
    suspend fun transcribe(audioFile: AudioFile, languageTag: String): TranscriptionResult

    suspend fun retryPostProcessing(rawTranscript: org.freethinkers.transcriber.domain.model.Transcript, languageTag: String): TranscriptionResult =
        TranscriptionResult(
            transcript = rawTranscript,
            warningMessage = "Повторная обработка текста недоступна.",
            rawTranscript = rawTranscript,
            postProcessingWarningMessage = "Повторная обработка текста недоступна.",
        )
}
