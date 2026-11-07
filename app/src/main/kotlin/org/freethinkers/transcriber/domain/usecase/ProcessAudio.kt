package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.processing.TranscriptionEngine
import org.freethinkers.transcriber.domain.repository.AudioSessionRepository
import org.freethinkers.transcriber.domain.repository.ModelRepository

class ProcessAudio(
    private val sessionRepository: AudioSessionRepository,
    private val modelRepository: ModelRepository,
    private val transcriptionEngine: TranscriptionEngine,
) {
    suspend fun execute(): AudioSession? {
        val currentSession = sessionRepository.currentSession.value ?: return null
        if (currentSession.processingStatus == ProcessingStatus.PROCESSING) return currentSession

        val processingSession = currentSession.copy(
            processingStatus = ProcessingStatus.PROCESSING,
            transcript = null,
            warningMessage = null,
            errorMessage = null,
        )
        sessionRepository.replaceSession(processingSession)

        if (!modelRepository.areRequiredModelsInstalled()) {
            return processingSession.copy(
                processingStatus = ProcessingStatus.ERROR,
                errorMessage = MODELS_NOT_INSTALLED_MESSAGE,
            ).also(sessionRepository::replaceSession)
        }

        return try {
            val result = transcriptionEngine.transcribe(
                audioFile = processingSession.audioFile,
                languageTag = processingSession.languageTag,
            )
            processingSession.copy(
                processingStatus = ProcessingStatus.COMPLETED,
                transcript = result.transcript,
                warningMessage = result.warningMessage,
            ).also(sessionRepository::replaceSession)
        } catch (exception: Exception) {
            processingSession.copy(
                processingStatus = ProcessingStatus.ERROR,
                errorMessage = exception.message?.takeIf(String::isNotBlank)
                    ?: GENERIC_PROCESSING_ERROR_MESSAGE,
            ).also(sessionRepository::replaceSession)
        }
    }

    private companion object {
        const val MODELS_NOT_INSTALLED_MESSAGE = "Локальные модели, включая LLM, не установлены."
        const val GENERIC_PROCESSING_ERROR_MESSAGE = "Не удалось обработать аудиозапись. Проверьте файл и попробуйте снова."
    }
}
