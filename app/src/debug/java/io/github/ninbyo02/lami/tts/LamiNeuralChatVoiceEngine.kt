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
        if (!busy && speaking.value) {
            lastEndedAtMs = SystemClock.elapsedRealtime()
        }
        setSpeaking(busy)
    }, ::prepareUtterance, ::playClip)

    override fun speak(text: String) = queue.replace(text)
    override fun speakQueued(text: String) = queue.enqueue(text)

    private data class PreparedClip(val pcm: FloatArray, val firstAudio: LamiFirstAudioTiming.Request)

    private fun trace(firstAudio: LamiFirstAudioTiming.Request, event: String) {
        val elapsedMs = LamiFirstAudioTiming.elapsedMsSince(
            firstAudio,
            SystemClock.elapsedRealtimeNanos(),
        )
        Log.i("LamiNeuralChatTts", "request=${firstAudio.requestId} $event elapsed_ms=$elapsedMs")
    }

    private suspend fun prepareUtterance(text: String): PreparedClip? {
        val requestId = requestIds.incrementAndGet()
        val requestOrdinal = requestId - 1L
        val started = SystemClock.elapsedRealtimeNanos()
        val firstAudio = LamiFirstAudioTiming.start(
            requestId = requestId,
            requestOrdinal = requestOrdinal,
            startedAtNs = started,
            startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(requestOrdinal),
        )
        trace(firstAudio, LamiFirstAudioTiming.requestStartedEvent(firstAudio))
        trace(firstAudio, "status=started text_chars=${text.length}")
        return try {
            val pcm = LamiVoiceDiagnostic.synthesizeText(context, root, text) { trace(firstAudio, it) }
            trace(firstAudio, "synthesis=complete samples=${pcm.size}")
            PreparedClip(pcm, firstAudio)
        } catch (cancelled: CancellationException) {
            trace(firstAudio, "status=cancelled phase=synthesis")
            throw cancelled
        } catch (failure: Exception) {
            trace(firstAudio, "status=failure class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=$requestId synthesis failed", failure)
            null
        }
    }

    private suspend fun playClip(clip: PreparedClip?) {
        if (clip == null) return
        try {
            LamiPcmPlayer.play(clip.pcm) {
                trace(
                    clip.firstAudio,
                    LamiFirstAudioTiming.playbackStartedEvent(
                        clip.firstAudio,
                        SystemClock.elapsedRealtimeNanos(),
                    ),
                )
                trace(clip.firstAudio, "playback=started")
            }
            trace(clip.firstAudio, "status=complete")
        } catch (cancelled: CancellationException) {
            trace(clip.firstAudio, "status=cancelled phase=playback")
            throw cancelled
        } catch (failure: Exception) {
            trace(clip.firstAudio, "status=failure phase=playback class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=${clip.firstAudio.requestId} playback failed", failure)
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
