package org.freethinkers.transcriber.domain.usecase

import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.repository.AudioSessionRepository

class ImportAudio(private val sessionRepository: AudioSessionRepository) {
    /** Call only after the selected URI has been validated and its metadata read successfully. */
    fun execute(audioFile: AudioFile): AudioSession {
        val newSession = AudioSession(audioFile = audioFile)
        sessionRepository.replaceSession(newSession)
        return newSession
    }
}
