package org.freethinkers.transcriber.domain.processing

import org.freethinkers.transcriber.domain.model.Transcript

/** Contract for the future on-device ASR, diarization, and local post-processing pipeline. */
interface TranscriptionEngine {
    suspend fun transcribe(audioPath: String, languageTag: String): Transcript
}