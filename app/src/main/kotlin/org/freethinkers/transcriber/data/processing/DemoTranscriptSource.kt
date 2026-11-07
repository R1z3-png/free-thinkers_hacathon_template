package org.freethinkers.transcriber.data.processing

import org.freethinkers.transcriber.domain.model.Transcript

interface DemoTranscriptSource {
    val isAvailable: Boolean

    fun load(): Transcript
}
