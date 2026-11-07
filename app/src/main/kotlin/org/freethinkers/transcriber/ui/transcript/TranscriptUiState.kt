package org.freethinkers.transcriber.ui.transcript

import org.freethinkers.transcriber.domain.model.Transcript

data class TranscriptUiState(
    val transcript: Transcript? = null,
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val isExportAvailable: Boolean = false,
    val message: String? = null,
)
