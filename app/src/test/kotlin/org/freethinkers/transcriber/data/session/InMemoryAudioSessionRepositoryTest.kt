package org.freethinkers.transcriber.data.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus

class InMemoryAudioSessionRepositoryTest {
    private val repository = InMemoryAudioSessionRepository()

    @Test
    fun `starts without a current session`() {
        assertNull(repository.currentSession.value)
    }

    @Test
    fun `replace publishes the new current session`() {
        val session = AudioSession(audioFile("meeting.mp3"))

        repository.replaceSession(session)

        assertEquals(session, repository.currentSession.value)
    }

    @Test
    fun `update changes current session while preserving other fields`() {
        val session = AudioSession(audioFile("meeting.mp3"))
        repository.replaceSession(session)

        repository.updateSession { it.copy(processingStatus = ProcessingStatus.PROCESSING) }

        assertEquals(ProcessingStatus.PROCESSING, repository.currentSession.value?.processingStatus)
        assertEquals(audioFile("meeting.mp3"), repository.currentSession.value?.audioFile)
    }

    @Test
    fun `update is ignored when there is no current session`() {
        repository.updateSession { it.copy(processingStatus = ProcessingStatus.ERROR) }

        assertNull(repository.currentSession.value)
    }

    @Test
    fun `clear removes the current session`() {
        repository.replaceSession(AudioSession(audioFile("meeting.mp3")))

        repository.clearSession()

        assertNull(repository.currentSession.value)
    }

    private fun audioFile(name: String) = AudioFile(
        contentUri = "content://audio/$name",
        fileName = name,
        durationMs = 60_000,
        importedAtEpochMs = 1_700_000_000_000,
    )
}
