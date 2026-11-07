package org.freethinkers.transcriber.domain.usecase

import kotlin.test.Test
import kotlin.test.assertNull
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession

class ClearAudioSessionTest {
    private val repository = InMemoryAudioSessionRepository()
    private val clearAudioSession = ClearAudioSession(repository)

    @Test
    fun `clears the current session`() {
        repository.replaceSession(
            AudioSession(
                AudioFile("content://audio/meeting", "meeting.mp3", 60_000, 1_700_000_000_000),
            ),
        )

        clearAudioSession.execute()

        assertNull(repository.currentSession.value)
    }

    @Test
    fun `clearing an empty repository is safe`() {
        clearAudioSession.execute()

        assertNull(repository.currentSession.value)
    }
}
