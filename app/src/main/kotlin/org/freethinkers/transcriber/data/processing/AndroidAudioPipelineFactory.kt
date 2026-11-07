package org.freethinkers.transcriber.data.processing

import android.content.Context
import android.util.Log
import org.freethinkers.transcriber.data.audio.AndroidMediaCodecAudioPcmDecoder
import org.freethinkers.transcriber.data.models.LocalModelFiles
import org.freethinkers.transcriber.domain.processing.AudioPipelineComponents
import org.freethinkers.transcriber.domain.processing.AudioProcessingPipeline

/** Builds the production Android adapters around the shared Kotlin orchestration. */
fun createAndroidAudioProcessingPipeline(context: Context): AudioProcessingPipeline {
    val appContext = context.applicationContext
    val files = LocalModelFiles.inAppStorage(appContext)
    return AudioProcessingPipeline(
        AudioPipelineComponents(
            decoder = AndroidMediaCodecAudioPcmDecoder(appContext),
            speechRecognizer = WhisperCppSpeechRecognizer(files.whisper),
            diarizer = SherpaOnnxSpeakerDiarizer(
                segmentationModelPath = files.diarizationSegmentation.absolutePath,
                embeddingModelPath = files.speakerEmbedding.absolutePath,
            ),
            textPostProcessor = QwenGgufTextPostProcessor(
                modelFile = files.qwen,
                contextTokens = 1_024,
                threads = 4,
            ),
        ),
        onDiarizationFailure = { error -> Log.e("AudioPipeline", "Speaker diarization failed; continuing without speakers", error) },
    )
}
