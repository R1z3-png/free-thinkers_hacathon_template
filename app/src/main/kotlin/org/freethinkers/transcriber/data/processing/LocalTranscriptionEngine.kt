package org.freethinkers.transcriber.data.processing

import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.TranscriptionResult
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine
import org.freethinkers.transcriber.domain.processing.TranscriptionEngineException

class LocalTranscriptionEngine : TranscriptionEngine {
    override suspend fun transcribe(audioFile: AudioFile, languageTag: String): TranscriptionResult =
        throw TranscriptionEngineException("Локальный STT-пайплайн ещё не подключён.")
}
