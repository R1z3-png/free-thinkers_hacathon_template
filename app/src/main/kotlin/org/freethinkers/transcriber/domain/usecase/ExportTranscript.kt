package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.Transcript

class ExportTranscript {
    fun execute(transcript: Transcript): String = TODO("Serialize the transcript using the contract schema")
}
