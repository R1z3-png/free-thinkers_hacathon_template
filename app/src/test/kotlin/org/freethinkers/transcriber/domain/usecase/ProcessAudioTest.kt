package org.freethinkers.transcriber.domain.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.TranscriptionResult
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine
import org.freethinkers.transcriber.domain.repository.ModelRepository

class ProcessAudioTest {
    private val repository = InMemoryAudioSessionRepository()

    @Test
    fun `successful engine result completes session and keeps its warning`() = runBlocking {
        val file = audioFile()
        repository.replaceSession(AudioSession(file, languageTag = "en"))
        val transcript = Transcript(listOf(Utterance("speaker_1", 0, 900, "Hello")))
        val engine = FakeEngine(repository) { TranscriptionResult(transcript, "LLM warning") }
        val useCase = ProcessAudio(repository, FakeModelRepository(installed = true), engine)

        val result = useCase.execute()

        assertEquals(ProcessingStatus.PROCESSING, engine.observedStatus)
        assertSame(file, engine.receivedAudioFile)
        assertEquals("en", engine.receivedLanguageTag)
        assertEquals(ProcessingStatus.COMPLETED, result?.processingStatus)
        assertEquals(transcript, result?.transcript)
        assertEquals("LLM warning", result?.warningMessage)
        assertNull(result?.errorMessage)
        assertEquals(result, repository.currentSession.value)
    }

    @Test
    fun `engine failure records error and removes previous transcript`() = runBlocking {
        repository.replaceSession(
            AudioSession(
                audioFile = audioFile(),
                processingStatus = ProcessingStatus.COMPLETED,
                transcript = Transcript(listOf(Utterance("speaker_1", 0, 900, "Old result"))),
                warningMessage = "Old warning",
            ),
        )
        val engine = FakeEngine(repository) { throw IllegalStateException("ASR failed") }
        val useCase = ProcessAudio(repository, FakeModelRepository(installed = true), engine)

        val result = useCase.execute()

        assertEquals(ProcessingStatus.ERROR, result?.processingStatus)
        assertEquals("ASR failed", result?.errorMessage)
        assertNull(result?.transcript)
        assertNull(result?.warningMessage)
        assertEquals(result, repository.currentSession.value)
    }

    @Test
    fun `missing model package fails before invoking engine`() = runBlocking {
        repository.replaceSession(AudioSession(audioFile()))
        val engine = FakeEngine(repository) { error("Engine must not run without models") }
        val useCase = ProcessAudio(repository, FakeModelRepository(installed = false), engine)

        val result = useCase.execute()

        assertEquals(ProcessingStatus.ERROR, result?.processingStatus)
        assertEquals("Локальные модели, включая LLM, не установлены.", result?.errorMessage)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `does not start a second operation while processing`() = runBlocking {
        repository.replaceSession(AudioSession(audioFile(), processingStatus = ProcessingStatus.PROCESSING))
        val engine = FakeEngine(repository) { error("Engine must not run a second time") }
        val useCase = ProcessAudio(repository, FakeModelRepository(installed = true), engine)

        val result = useCase.execute()

        assertEquals(ProcessingStatus.PROCESSING, result?.processingStatus)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `returns null when there is no current session`() = runBlocking {
        val engine = FakeEngine(repository) { error("Engine must not run without a session") }
        val useCase = ProcessAudio(repository, FakeModelRepository(installed = true), engine)

        assertNull(useCase.execute())
        assertEquals(0, engine.callCount)
    }

    private fun audioFile() = AudioFile(
        contentUri = "content://audio/meeting",
        fileName = "meeting.mp3",
        durationMs = 60_000,
        importedAtEpochMs = 1_700_000_000_000,
    )

    private class FakeModelRepository(private val installed: Boolean) : ModelRepository {
        override fun areRequiredModelsInstalled(): Boolean = installed
        override suspend fun installRequiredModels() = Unit
    }

    private class FakeEngine(
        private val sessionRepository: InMemoryAudioSessionRepository,
        private val response: () -> TranscriptionResult,
    ) : TranscriptionEngine {
        var callCount = 0
            private set
        var receivedAudioFile: AudioFile? = null
            private set
        var receivedLanguageTag: String? = null
            private set
        var observedStatus: ProcessingStatus? = null
            private set

        override suspend fun transcribe(audioFile: AudioFile, languageTag: String): TranscriptionResult {
            callCount++
            receivedAudioFile = audioFile
            receivedLanguageTag = languageTag
            observedStatus = sessionRepository.currentSession.value?.processingStatus
            return response()
        }
    }
}
