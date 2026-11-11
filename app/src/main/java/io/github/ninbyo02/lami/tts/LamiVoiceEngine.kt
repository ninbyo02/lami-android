package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.flow.StateFlow

/** Playback boundary for LAMI speech.
 *
 * AndroidTtsController remains the production fallback. Neural/offline engines
 * can implement this contract without changing chat/inference orchestration.
 */
interface LamiVoiceEngine {
    val isSpeaking: StateFlow<Boolean>

    /** Optional owned model warm-up; it must participate in stop/voice-input cleanup. */
    fun prewarm() {}

    fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit)
    /** Engine/player start notification; this does not measure acoustic onset. */
    fun setOnAudioStarted(listener: () -> Unit) {}
    fun setOnError(listener: (String) -> Unit) {}
    fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) {}
    fun speak(text: String)
    fun speakQueued(text: String)
    fun isInCooldown(): Boolean = false
    fun stop()
    /** Await engine-owned playback cleanup before opening voice input. */
    suspend fun stopForVoiceInput() { stop() }
    fun shutdown()
    /** Dispose the engine, awaiting release when it owns a resident session. */
    suspend fun shutdownAndAwait() { shutdown() }
}
