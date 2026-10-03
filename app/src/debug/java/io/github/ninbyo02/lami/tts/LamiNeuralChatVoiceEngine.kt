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

    private val queue = LamiSpeechPipeline<PreparedClip?>(scope, { busy ->
        if (!busy && speaking.value) lastEndedAtMs = SystemClock.elapsedRealtime()
        setSpeaking(busy)
    }, ::prepareUtterance, ::playClip)

    override fun speak(text: String) = queue.replace(text)
    override fun speakQueued(text: String) = queue.enqueue(text)

    private data class PreparedClip(val pcm: FloatArray, val requestId: Long, val started: Long)

    private fun trace(requestId: Long, started: Long, event: String) {
        Log.i("LamiNeuralChatTts", "request=$requestId $event elapsed_ms=${SystemClock.elapsedRealtime() - started}")
    }

    private suspend fun prepareUtterance(text: String): PreparedClip? {
        val requestId = requestIds.incrementAndGet()
        val started = SystemClock.elapsedRealtime()
        trace(requestId, started, "status=started text_chars=${text.length}")
        return try {
            val pcm = LamiVoiceDiagnostic.synthesizeText(context, root, text) { trace(requestId, started, it) }
            trace(requestId, started, "synthesis=complete samples=${pcm.size}")
            PreparedClip(pcm, requestId, started)
        } catch (cancelled: CancellationException) {
            trace(requestId, started, "status=cancelled phase=synthesis")
            throw cancelled
        } catch (failure: Exception) {
            trace(requestId, started, "status=failure class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=$requestId synthesis failed", failure)
            null
        }
    }

    private suspend fun playClip(clip: PreparedClip?) {
        if (clip == null) return
        try {
            LamiPcmPlayer.play(clip.pcm) { trace(clip.requestId, clip.started, "playback=started") }
            trace(clip.requestId, clip.started, "status=complete")
        } catch (cancelled: CancellationException) {
            trace(clip.requestId, clip.started, "status=cancelled phase=playback")
            throw cancelled
        } catch (failure: Exception) {
            trace(clip.requestId, clip.started, "status=failure phase=playback class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=${clip.requestId} playback failed", failure)
        }
    }

    override fun isInCooldown(): Boolean =
        lastEndedAtMs != Long.MIN_VALUE && SystemClock.elapsedRealtime() - lastEndedAtMs < 500L

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
