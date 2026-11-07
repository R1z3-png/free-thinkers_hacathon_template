package org.freethinkers.transcriber.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus

class SelectRecognitionLanguageTest {
    private val repository = InMemoryAudioSessionRepository()
    private val useCase = SelectRecognitionLanguage(repository)

    @Test
    fun `selects English and updates current session`() {
        repository.replaceSession(AudioSession(audioFile()))

        assertTrue(useCase.execute("en"))
        assertEquals("en", repository.currentSession.value?.languageTag)
    }

    @Test
    fun `selects Russian`() {
        repository.replaceSession(AudioSession(audioFile(), languageTag = "en"))

        assertTrue(useCase.execute("ru"))
        assertEquals("ru", repository.currentSession.value?.languageTag)
    }

    @Test
    fun `rejects unsupported language tags`() {
        repository.replaceSession(AudioSession(audioFile()))

        assertFalse(useCase.execute("fr"))
        assertEquals("ru", repository.currentSession.value?.languageTag)
    }

    @Test
    fun `cannot select a language without a current session`() {
        assertFalse(useCase.execute("en"))
    }

    @Test
    fun `cannot change language while processing`() {
        repository.replaceSession(AudioSession(audioFile(), processingStatus = ProcessingStatus.PROCESSING))

        assertFalse(useCase.execute("en"))
        assertEquals("ru", repository.currentSession.value?.languageTag)
    }

    private fun audioFile() = AudioFile(
        contentUri = "content://audio/meeting",
        fileName = "meeting.mp3",
        durationMs = 60_000,
        importedAtEpochMs = 1_700_000_000_000,
    )
}
