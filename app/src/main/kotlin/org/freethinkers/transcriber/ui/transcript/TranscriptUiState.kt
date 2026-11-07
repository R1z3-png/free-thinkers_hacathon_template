package org.freethinkers.transcriber.ui.transcript

import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance

data class TranscriptUiState(
    val audioFileName: String? = null,
    val transcript: Transcript? = null,
    val processingStatus: ProcessingStatus? = null,
    val warningMessage: String? = null,
    val canRetryPostProcessing: Boolean = false,
    val isRetryingPostProcessing: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val sortedUtterances by lazy(LazyThreadSafetyMode.NONE) {
        transcript?.utterances.orEmpty().sortedBy { it.startMs }
    }

    val currentPageIndex: Int
        get() = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    val isExportAvailable: Boolean
        get() = processingStatus == ProcessingStatus.COMPLETED && transcript != null

    val hasNoSpeech: Boolean
        get() = isExportAvailable && transcript?.utterances?.isEmpty() == true

    val orderedUtterances: List<Utterance>
        get() = sortedUtterances

    val pageCount: Int
        get() = if (orderedUtterances.isEmpty()) 0 else (orderedUtterances.size + pageSize - 1) / pageSize

    val currentPageUtterances: List<Utterance>
        get() = orderedUtterances.drop(currentPageIndex * pageSize)
            .take(pageSize)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        fun from(session: AudioSession?, currentPage: Int): TranscriptUiState = TranscriptUiState(
            audioFileName = session?.audioFile?.fileName,
            transcript = session?.transcript,
            processingStatus = session?.processingStatus,
            warningMessage = session?.warningMessage,
            canRetryPostProcessing = session?.canRetryPostProcessing == true,
            isRetryingPostProcessing = session?.processingStatus == ProcessingStatus.PROCESSING && session.rawTranscript != null,
            errorMessage = session?.errorMessage,
            currentPage = currentPage,
        )
    }
}
