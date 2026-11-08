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

/** Debug chat bridge; streaming requires the isolated trial opt-in. */
internal class LamiNeuralChatVoiceEngine(
    private val context: Context,
    private val root: File,
    private val streamingEnabled: Boolean = false,
    private val traceFactory: ((Long, String) -> LamiSpeechTraceCapture)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val streamSpeech: suspend (String, (String) -> Unit, () -> Unit) -> FloatArray = { text, progress, started ->
        LamiVoiceStreamingProbe.runSpeech(context, root, text, progress, started)
    },
) : LamiVoiceEngine {
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

    private data class PreparedClip(val pcm: FloatArray?, val firstAudio: LamiFirstAudioTiming.Request, val capture: LamiSpeechTraceGuard?, val text: String)

    private fun trace(firstAudio: LamiFirstAudioTiming.Request, event: String) {
        val elapsedMs = LamiFirstAudioTiming.elapsedMsSince(
            firstAudio,
            SystemClock.elapsedRealtimeNanos(),
        )
        Log.i("LamiNeuralChatTts", "request=${firstAudio.requestId} $event elapsed_ms=$elapsedMs")
    }

    private suspend fun prepareUtterance(text: String): PreparedClip? {
        // Include optional diagnostic setup in the preparation-to-playback interval.
        val started = SystemClock.elapsedRealtimeNanos()
        val requestId = requestIds.incrementAndGet()
        val capture = traceFactory?.let { factory ->
            LamiSpeechTraceGuard.create { factory(requestId, text) }
        }
        val requestOrdinal = requestId - 1L
        val firstAudio = LamiFirstAudioTiming.start(
            requestId = requestId,
            requestOrdinal = requestOrdinal,
            startedAtNs = started,
            startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(requestOrdinal),
        )
        trace(firstAudio, LamiFirstAudioTiming.requestStartedEvent(firstAudio))
        trace(firstAudio, "status=started text_chars=${text.length}")
        if (streamingEnabled) return PreparedClip(null, firstAudio, capture, text)
        return try {
            capture?.event("synthesis_started")
            val pcm = LamiVoiceDiagnostic.synthesizeText(context, root, text) { trace(firstAudio, it) }
            trace(firstAudio, LamiFirstAudioTiming.preparedEvent(firstAudio, SystemClock.elapsedRealtimeNanos()))
            trace(firstAudio, "synthesis=complete samples=${pcm.size}")
            capture?.event("pcm_ready")
            PreparedClip(pcm, firstAudio, capture, text)
        } catch (cancelled: CancellationException) {
            capture?.failure("cancelled")
            trace(firstAudio, "status=cancelled phase=synthesis")
            throw cancelled
        } catch (failure: Exception) {
            capture?.failure("synthesis")
            trace(firstAudio, "status=failure class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=$requestId synthesis failed", failure)
            null
        }
    }

    private suspend fun playClip(clip: PreparedClip?) {
        if (clip == null) return
        try {
            val onStarted = {
                val playbackStartedAtNs = SystemClock.elapsedRealtimeNanos()
                trace(
                    clip.firstAudio,
                    LamiFirstAudioTiming.playbackStartedEvent(
                        clip.firstAudio,
                        playbackStartedAtNs,
                    ),
                )
                clip.capture?.event("playback_started")
                trace(clip.firstAudio, "playback=started")
            }
            val pcm = if (clip.pcm == null) {
                streamSpeech(clip.text, { trace(clip.firstAudio, it) }, onStarted)
            } else {
                LamiPcmPlayer.play(clip.pcm, onStarted)
                clip.pcm
            }
            clip.capture?.success(pcm)
            trace(clip.firstAudio, "status=complete")
        } catch (cancelled: CancellationException) {
            clip.capture?.failure("cancelled")
            trace(clip.firstAudio, "status=cancelled phase=playback")
            throw cancelled
        } catch (failure: Exception) {
            clip.capture?.failure("playback")
            trace(clip.firstAudio, "status=failure phase=playback class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=${clip.firstAudio.requestId} playback failed", failure)
        }
    }

    override fun isInCooldown(): Boolean =
        lastEndedAtMs != Long.MIN_VALUE && SystemClock.elapsedRealtime() - lastEndedAtMs < 500L

    override fun stop() {
        queue.stop()
    }

    override suspend fun stopForVoiceInput() {
        stop()
        queue.awaitCleanup()
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
