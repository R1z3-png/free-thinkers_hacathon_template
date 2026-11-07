package org.freethinkers.transcriber.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance

class ImportAudioTest {
    private val repository = InMemoryAudioSessionRepository()
    private val importAudio = ImportAudio(repository)

    @Test
    fun `valid imported audio creates a ready Russian session`() {
        val file = audioFile("first.mp3")

        val session = importAudio.execute(file)

        assertEquals(file, session.audioFile)
        assertEquals("ru", session.languageTag)
        assertEquals(ProcessingStatus.READY, session.processingStatus)
        assertNull(session.transcript)
        assertEquals(session, repository.currentSession.value)
    }

    @Test
    fun `importing another file replaces session and clears previous transcript and status`() {
        repository.replaceSession(
            AudioSession(
                audioFile = audioFile("first.mp3"),
                languageTag = "en",
                processingStatus = ProcessingStatus.COMPLETED,
                transcript = Transcript(listOf(Utterance("speaker_1", 0, 1_000, "Hello"))),
                warningMessage = "Earlier warning",
            ),
        )
        val replacement = audioFile("second.mp3")

        val newSession = importAudio.execute(replacement)

        assertEquals(replacement, newSession.audioFile)
        assertEquals("ru", newSession.languageTag)
        assertEquals(ProcessingStatus.READY, newSession.processingStatus)
        assertNull(newSession.transcript)
        assertNull(newSession.warningMessage)
        assertNull(newSession.errorMessage)
        assertEquals(newSession, repository.currentSession.value)
    }

    private fun audioFile(name: String) = AudioFile(
        contentUri = "content://audio/$name",
        fileName = name,
        durationMs = 60_000,
        importedAtEpochMs = 1_700_000_000_000,
    )
}
