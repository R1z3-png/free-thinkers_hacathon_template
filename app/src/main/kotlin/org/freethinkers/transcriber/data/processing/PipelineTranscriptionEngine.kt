package org.freethinkers.transcriber.data.processing

import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.TranscriptionResult
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.processing.AudioProcessingPipeline
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine

/** Adapts the app's selected audio session to the reusable Kotlin pipeline. */
class PipelineTranscriptionEngine(
    private val pipeline: AudioProcessingPipeline,
) : TranscriptionEngine {
    override suspend fun transcribe(audioFile: AudioFile, languageTag: String): TranscriptionResult =
        pipeline.process(audioFile.contentUri, languageTag)

    override suspend fun retryPostProcessing(rawTranscript: Transcript, languageTag: String): TranscriptionResult =
        pipeline.retryPostProcessing(rawTranscript, languageTag)
}
