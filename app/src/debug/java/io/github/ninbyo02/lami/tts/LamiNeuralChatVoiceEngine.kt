package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Debug chat bridge for the validated local neural voice path. */
internal class LamiNeuralChatVoiceEngine(
    private val context: Context,
    private val root: File,
) : LamiVoiceEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    private var listener: (Boolean) -> Unit = {}
    private var job: Job? = null
    private var lastEndedAtMs = Long.MIN_VALUE

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    override fun speak(text: String) = start(text)
    override fun speakQueued(text: String) = start(text)

    private fun start(text: String) {
        if (text.isBlank()) return
        job?.cancel()
        job = scope.launch {
            setSpeaking(true)
            try {
                val pcm = LamiVoiceDiagnostic.synthesizeText(context, root, text)
                LamiPcmPlayer.play(pcm)
            } finally {
                lastEndedAtMs = SystemClock.elapsedRealtime()
                setSpeaking(false)
            }
        }
    }

    override fun isInCooldown(): Boolean =
        SystemClock.elapsedRealtime() - lastEndedAtMs < 500L

    override fun stop() {
        job?.cancel()
        job = null
        setSpeaking(false)
    }

    override fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun setSpeaking(value: Boolean) {
        if (speaking.value == value) return
        speaking.value = value
        listener(value)
    }
}
