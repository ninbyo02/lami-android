package io.github.ninbyo02.lami.assistant

import io.github.ninbyo02.lami.tts.LamiVoiceEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** Owns a dedicated cue voice, so chat playback listeners are never replaced. */
internal class AssistantListeningCue(
    private val voice: LamiVoiceEngine,
    private val timeoutMillis: Long = 10_000L,
    private val completedNormally: () -> Boolean = { true },
) {
    suspend fun play() {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        voice.setOnPlaybackStateChanged { playing ->
            if (playing) started.complete(Unit)
            else if (started.isCompleted) finished.complete(Unit)
        }
        try {
            withTimeout(timeoutMillis) {
                voice.speak("どうしましたか。")
                started.await()
                finished.await()
                check(completedNormally()) { "listening cue playback failed" }
            }
        } finally {
            voice.setOnPlaybackStateChanged { }
            voice.stop()
        }
    }
}
