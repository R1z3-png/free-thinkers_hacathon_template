package org.freethinkers.transcriber.data.processing

import android.content.Context
import org.freethinkers.transcriber.domain.model.Transcript

fun createDemoTranscriptSource(context: Context): DemoTranscriptSource = UnavailableDemoTranscriptSource

private object UnavailableDemoTranscriptSource : DemoTranscriptSource {
    override val isAvailable: Boolean = false

    override fun load(): Transcript = error("Demo transcript is available only in debug builds")
}
