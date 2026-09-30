package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.flow.StateFlow

/** Playback boundary for LAMI speech.
 *
 * AndroidTtsController remains the production fallback. Neural/offline engines
 * can implement this contract without changing chat/inference orchestration.
 */
interface LamiVoiceEngine {
    val isSpeaking: StateFlow<Boolean>

    fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit)
    fun speak(text: String)
    fun speakQueued(text: String)
    fun isInCooldown(): Boolean = false
    fun stop()
    fun shutdown()
}
