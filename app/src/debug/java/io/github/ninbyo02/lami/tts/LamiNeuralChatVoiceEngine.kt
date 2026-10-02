package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val requestIds = AtomicLong()
    private var lastEndedAtMs = Long.MIN_VALUE

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    private val queue = LamiSpeechQueue(scope, { busy ->
        if (!busy && speaking.value) lastEndedAtMs = SystemClock.elapsedRealtime()
        setSpeaking(busy)
    }, ::playUtterance)

    override fun speak(text: String) = queue.replace(text)
    override fun speakQueued(text: String) = queue.enqueue(text)

    private suspend fun playUtterance(text: String) {
        val requestId = requestIds.incrementAndGet()
        val started = SystemClock.elapsedRealtime()
        fun trace(event: String) {
            Log.i("LamiNeuralChatTts", "request=$requestId $event elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        }
        trace("status=started text_chars=${text.length}")
        try {
            val pcm = LamiVoiceDiagnostic.synthesizeText(context, root, text, ::trace)
            trace("synthesis=complete samples=${pcm.size}")
            LamiPcmPlayer.play(pcm) { trace("playback=started") }
            trace("status=complete")
        } catch (cancelled: CancellationException) {
            trace("status=cancelled")
            throw cancelled
        } catch (failure: Exception) {
            trace("status=failure class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=$requestId synthesis or playback failed", failure)
        }
    }

    override fun isInCooldown(): Boolean =
        SystemClock.elapsedRealtime() - lastEndedAtMs < 500L

    override fun stop() {
        queue.stop()
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
