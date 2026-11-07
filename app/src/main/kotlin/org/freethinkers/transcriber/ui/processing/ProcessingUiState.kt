package org.freethinkers.transcriber.ui.processing

data class ProcessingUiState(
    val languageTag: String = "ru",
    val isModelInstalled: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)
