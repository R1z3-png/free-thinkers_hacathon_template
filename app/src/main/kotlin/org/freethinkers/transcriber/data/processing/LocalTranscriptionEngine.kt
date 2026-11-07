package org.freethinkers.transcriber.data.processing

import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine

class LocalTranscriptionEngine : TranscriptionEngine {
    override suspend fun transcribe(audioPath: String, languageTag: String): Transcript =
        TODO("Connect the selected local ASR, diarization, and LLM runtime")
}
