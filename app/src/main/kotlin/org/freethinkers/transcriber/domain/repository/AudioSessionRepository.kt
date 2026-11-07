package org.freethinkers.transcriber.domain.repository

import org.freethinkers.transcriber.domain.model.AudioSession
import kotlinx.coroutines.flow.StateFlow

/** Holds at most one in-memory session; state is intentionally not restored after process death. */
interface AudioSessionRepository {
    val currentSession: StateFlow<AudioSession?>

    fun replaceSession(session: AudioSession)

    fun updateSession(transform: (AudioSession) -> AudioSession)

    fun clearSession()
}
