package org.freethinkers.transcriber.ui.processing

import org.freethinkers.transcriber.domain.model.ProcessingStatus

data class ProcessingUiState(
    val audioFileName: String? = null,
    val languageTag: String = "ru",
    val modelFilesInstalled: Boolean = false,
    val processingStatus: ProcessingStatus? = null,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
) {
    val hasAudioFile: Boolean get() = audioFileName != null
    val isBusy: Boolean get() = processingStatus == ProcessingStatus.PROCESSING
}
