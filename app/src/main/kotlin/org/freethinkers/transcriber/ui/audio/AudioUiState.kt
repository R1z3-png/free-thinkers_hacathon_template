package org.freethinkers.transcriber.ui.audio

import org.freethinkers.transcriber.domain.model.AudioFile

data class AudioUiState(
    val audioFile: AudioFile? = null,
    val errorMessage: String? = null,
)
