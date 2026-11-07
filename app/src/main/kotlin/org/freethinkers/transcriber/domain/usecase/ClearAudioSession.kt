package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.repository.AudioSessionRepository

class ClearAudioSession(private val sessionRepository: AudioSessionRepository) {
    fun execute() = sessionRepository.clearSession()
}
