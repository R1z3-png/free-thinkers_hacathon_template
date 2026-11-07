package org.freethinkers.transcriber.data.processing

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.domain.processing.OfflineSpeakerDiarizer
import org.freethinkers.transcriber.domain.processing.PcmAudio
import org.freethinkers.transcriber.domain.processing.SpeakerTurn

/** Uses sherpa-onnx's pyannote segmentation + 3D-Speaker embedding + clustering pipeline. */
class SherpaOnnxSpeakerDiarizer(
    private val segmentationModelPath: String,
    private val embeddingModelPath: String,
    private val threads: Int = 2,
    private val clusteringThreshold: Float = 0.8f,
) : OfflineSpeakerDiarizer {
    init {
        require(segmentationModelPath.isNotBlank())
        require(embeddingModelPath.isNotBlank())
        require(threads > 0)
        require(clusteringThreshold in 0f..2f)
    }

    override suspend fun diarize(audio: PcmAudio): List<SpeakerTurn> = withContext(Dispatchers.Default) {
        val pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(segmentationModelPath, 0.1f)
        val segmentation = OfflineSpeakerSegmentationModelConfig(pyannote, threads, false, "cpu")
        val embedding = SpeakerEmbeddingExtractorConfig(embeddingModelPath, threads, false, "cpu")
        val clustering = FastClusteringConfig(-1, clusteringThreshold, true)
        val config = OfflineSpeakerDiarizationConfig(segmentation, embedding, clustering, 0.2f, 0.3f)

        // Both models are loaded by absolute app-private paths. Passing an AssetManager
        // as well makes sherpa-onnx treat those paths as APK assets and abort natively.
        val diarizer = OfflineSpeakerDiarization(null, config)
        try {
            require(diarizer.sampleRate() == audio.sampleRateHz) {
                "sherpa-onnx requires ${diarizer.sampleRate()} Hz input, got ${audio.sampleRateHz} Hz."
            }
            diarizer.process(audio.samples).map { segment ->
                SpeakerTurn(
                    startMs = (segment.start * 1_000).toLong(),
                    endMs = (segment.end * 1_000).toLong(),
                    speakerId = "sherpa_${segment.speaker}",
                )
            }
        } finally {
            diarizer.release()
        }
    }
}
