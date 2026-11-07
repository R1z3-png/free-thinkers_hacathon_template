package org.freethinkers.transcriber.data.session

import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.repository.AudioSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryAudioSessionRepository : AudioSessionRepository {
    private val mutableCurrentSession = MutableStateFlow<AudioSession?>(null)

    override val currentSession: StateFlow<AudioSession?> = mutableCurrentSession

    override fun replaceSession(session: AudioSession) {
        mutableCurrentSession.value = session
    }

    override fun updateSession(transform: (AudioSession) -> AudioSession) {
        val current = mutableCurrentSession.value ?: return
        mutableCurrentSession.value = transform(current)
    }

    override fun clearSession() {
        mutableCurrentSession.value = null
    }
}
