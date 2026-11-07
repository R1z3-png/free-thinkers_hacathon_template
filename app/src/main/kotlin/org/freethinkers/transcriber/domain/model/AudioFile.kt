package org.freethinkers.transcriber.domain.model

/** Reference to the selected original audio and the metadata shown in the current session. */
data class AudioFile(
    val contentUri: String,
    val fileName: String,
    val durationMs: Long?,
    val importedAtEpochMs: Long,
)
