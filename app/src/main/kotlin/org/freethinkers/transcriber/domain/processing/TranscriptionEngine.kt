package org.freethinkers.transcriber.domain.processing

import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.TranscriptionResult

/** Boundary for the future on-device ASR, diarization, and local post-processing pipeline. */
interface TranscriptionEngine {
    suspend fun transcribe(audioFile: AudioFile, languageTag: String): TranscriptionResult
}

class TranscriptionEngineException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
