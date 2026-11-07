package org.freethinkers.transcriber.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import org.freethinkers.transcriber.domain.model.Transcript

class ExportTranscriptTest {
    @Test
    fun `suggested filename replaces mp3 extension regardless of case`() {
        val exportTranscript = ExportTranscript()

        assertEquals("meeting.json", exportTranscript.suggestedFileName("meeting.MP3"))
    }

    @Test
    fun `serializes an empty transcript`() {
        assertEquals("{\n  \"transcript\": []\n}", ExportTranscript().execute(Transcript()))
    }
}
