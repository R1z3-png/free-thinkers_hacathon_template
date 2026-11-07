package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.RecognitionLanguage
import org.freethinkers.transcriber.domain.repository.AudioSessionRepository

class SelectRecognitionLanguage(private val sessionRepository: AudioSessionRepository) {
    fun execute(languageTag: String): Boolean {
        val language = RecognitionLanguage.fromTag(languageTag) ?: return false
        val session = sessionRepository.currentSession.value ?: return false
        if (session.processingStatus == ProcessingStatus.PROCESSING) return false

        sessionRepository.replaceSession(session.copy(languageTag = language.tag))
        return true
    }
}
