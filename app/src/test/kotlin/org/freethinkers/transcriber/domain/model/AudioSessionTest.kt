package org.freethinkers.transcriber.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioSessionTest {
    @Test
    fun `new session has no transcript`() {
        val audioFile = AudioFile(
            contentUri = "content://audio/meeting",
            fileName = "meeting.mp3",
            durationMs = 60_000,
            importedAtEpochMs = 1_700_000_000_000,
        )
        val session = AudioSession(audioFile)

        assertEquals("meeting.mp3", session.audioFile.fileName)
        assertEquals(60_000, session.audioFile.durationMs)
        assertEquals("ru", session.languageTag)
        assertEquals(ProcessingStatus.READY, session.processingStatus)
        assertNull(session.transcript)
        assertNull(session.warningMessage)
        assertNull(session.errorMessage)
    }
}
