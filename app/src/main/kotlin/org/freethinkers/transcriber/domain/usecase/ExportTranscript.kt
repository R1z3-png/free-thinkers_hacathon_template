package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.data.export.TranscriptJsonSerializer

class ExportTranscript {
    fun execute(transcript: Transcript): String = TranscriptJsonSerializer.serialize(transcript)

    fun suggestedFileName(audioFileName: String): String =
        audioFileName.substringBeforeLast('.', audioFileName) + ".json"
}
