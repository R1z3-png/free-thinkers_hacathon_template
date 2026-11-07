package org.freethinkers.transcriber.domain.model

data class Utterance(
    val speakerId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)