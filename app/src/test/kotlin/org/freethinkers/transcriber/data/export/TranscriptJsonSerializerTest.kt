package org.freethinkers.transcriber.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance

class TranscriptJsonSerializerTest {
    @Test
    fun `serializes empty transcript using the contract schema`() {
        assertEquals("{\n  \"transcript\": []\n}", TranscriptJsonSerializer.serialize(Transcript()))
    }

    @Test
    fun `serializes utterance fields and escapes JSON strings`() {
        val transcript = Transcript(
            listOf(Utterance("speaker_1", 1250, 4380, "Он сказал: \"Привет\"\\пока")),
        )

        assertEquals(
            "{\n" +
                "  \"transcript\": [\n" +
                "    {\n" +
                "      \"speaker_id\": \"speaker_1\",\n" +
                "      \"start_ms\": 1250,\n" +
                "      \"end_ms\": 4380,\n" +
                "      \"text\": \"Он сказал: \\\"Привет\\\"\\\\пока\"\n" +
                "    }\n" +
                "  ]\n" +
                "}",
            TranscriptJsonSerializer.serialize(transcript),
        )
    }

    @Test
    fun `serializes utterances in chronological order`() {
        val transcript = Transcript(
            listOf(
                Utterance("speaker_2", 2000, 3000, "second"),
                Utterance("speaker_1", 1000, 1800, "first"),
            ),
        )

        val json = TranscriptJsonSerializer.serialize(transcript)

        assertEquals(true, json.indexOf("\"text\": \"first\"") < json.indexOf("\"text\": \"second\""))
    }
}
