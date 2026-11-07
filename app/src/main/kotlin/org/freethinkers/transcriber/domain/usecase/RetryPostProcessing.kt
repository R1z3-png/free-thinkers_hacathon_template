package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine
import org.freethinkers.transcriber.domain.repository.AudioSessionRepository
import kotlinx.coroutines.CancellationException

class RetryPostProcessing(
    private val sessionRepository: AudioSessionRepository,
    private val transcriptionEngine: TranscriptionEngine,
) {
    suspend fun execute() {
        val current = sessionRepository.currentSession.value ?: return
        val raw = current.rawTranscript ?: return
        if (!current.canRetryPostProcessing) return
        sessionRepository.replaceSession(current.copy(processingStatus = ProcessingStatus.PROCESSING, errorMessage = null))
        val updated = try {
            val result = transcriptionEngine.retryPostProcessing(raw, current.languageTag)
            current.copy(
                processingStatus = ProcessingStatus.COMPLETED,
                transcript = result.transcript,
                warningMessage = listOfNotNull(current.nonPostProcessingWarningMessage, result.postProcessingWarningMessage).joinToString(" ").ifEmpty { null },
                postProcessingWarningMessage = result.postProcessingWarningMessage,
                errorMessage = null,
            )
        } catch (cancelled: CancellationException) {
            sessionRepository.replaceSession(current)
            throw cancelled
        } catch (error: Exception) {
            current.copy(
                processingStatus = ProcessingStatus.COMPLETED,
                transcript = raw,
                warningMessage = listOfNotNull(current.nonPostProcessingWarningMessage, error.message ?: "Не удалось повторно обработать текст.").joinToString(" "),
                postProcessingWarningMessage = error.message ?: "Не удалось повторно обработать текст.",
            )
        }
        sessionRepository.replaceSession(updated)
    }
}
