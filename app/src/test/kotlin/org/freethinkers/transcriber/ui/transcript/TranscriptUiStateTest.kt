package org.freethinkers.transcriber.ui.transcript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance

class TranscriptUiStateTest {
    @Test
    fun `offers export for completed transcript including empty transcript`() {
        val state = TranscriptUiState(
            transcript = Transcript(),
            processingStatus = ProcessingStatus.COMPLETED,
        )

        assertTrue(state.isExportAvailable)
        assertTrue(state.hasNoSpeech)
        assertEquals(0, state.pageCount)
    }

    @Test
    fun `does not offer export before processing completes`() {
        val state = TranscriptUiState(
            transcript = Transcript(listOf(Utterance("speaker_1", 0, 100, "hello"))),
            processingStatus = ProcessingStatus.READY,
        )

        assertFalse(state.isExportAvailable)
    }

    @Test
    fun `pages whole utterances and keeps chronological order`() {
        val utterances = (0 until 25).reversed().map { index ->
            Utterance("speaker_1", index.toLong(), index + 1L, "line $index")
        }
        val session = AudioSession(
            audioFile = AudioFile("content://audio", "meeting.mp3", 1000, 0),
            processingStatus = ProcessingStatus.COMPLETED,
            transcript = Transcript(utterances),
        )

        val firstPage = TranscriptUiState.from(session, currentPage = 0)
        val secondPage = TranscriptUiState.from(session, currentPage = 1)

        assertEquals(2, firstPage.pageCount)
        assertEquals(20, firstPage.currentPageUtterances.size)
        assertEquals(5, secondPage.currentPageUtterances.size)
        assertEquals(0L, firstPage.currentPageUtterances.first().startMs)
        assertEquals(20L, secondPage.currentPageUtterances.first().startMs)
    }
}
