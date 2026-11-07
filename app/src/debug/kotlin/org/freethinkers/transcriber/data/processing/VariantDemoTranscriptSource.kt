package org.freethinkers.transcriber.data.processing

import android.content.Context
import org.freethinkers.transcriber.domain.model.Transcript
import org.freethinkers.transcriber.domain.model.Utterance
import org.json.JSONObject
import java.nio.charset.StandardCharsets

fun createDemoTranscriptSource(context: Context): DemoTranscriptSource = AssetDemoTranscriptSource(context)

private class AssetDemoTranscriptSource(private val context: Context) : DemoTranscriptSource {
    override val isAvailable: Boolean = true

    override fun load(): Transcript {
        val jsonText = context.assets.open(ASSET_NAME).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonText)
        val transcriptArray = root.getJSONArray("transcript")
        val utterances = (0 until transcriptArray.length()).map { index ->
            val item = transcriptArray.getJSONObject(index)
            val startMs = item.getLong("start_ms")
            val endMs = item.getLong("end_ms")
            require(startMs >= 0 && endMs >= startMs) { "Invalid time range in demo transcript at index $index" }
            Utterance(
                speakerId = item.getString("speaker_id"),
                startMs = startMs,
                endMs = endMs,
                text = item.getString("text"),
            )
        }
        return Transcript(utterances.sortedBy { it.startMs })
    }

    private companion object {
        const val ASSET_NAME = "demo_transcript.json"
    }
}
