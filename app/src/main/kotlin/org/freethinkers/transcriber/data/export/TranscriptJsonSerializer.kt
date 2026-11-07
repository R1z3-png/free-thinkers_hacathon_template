package org.freethinkers.transcriber.data.export

import org.freethinkers.transcriber.domain.model.Transcript

/** Serializes only the required transcript schema, using UTF-8-compatible JSON text and LF lines. */
object TranscriptJsonSerializer {
    fun serialize(transcript: Transcript): String = buildString {
        append("{\n  \"transcript\": ")
        if (transcript.utterances.isEmpty()) {
            append("[]")
        } else {
            append("[\n")
            transcript.utterances.forEachIndexed { index, utterance ->
                append("    {\n")
                append("      \"speaker_id\": \"").append(escape(utterance.speakerId)).append("\",\n")
                append("      \"start_ms\": ").append(utterance.startMs).append(",\n")
                append("      \"end_ms\": ").append(utterance.endMs).append(",\n")
                append("      \"text\": \"").append(escape(utterance.text)).append("\"\n")
                append("    }")
                if (index != transcript.utterances.lastIndex) append(',')
                append('\n')
            }
            append("  ]")
        }
        append("\n}")
    }

    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
    }
}
