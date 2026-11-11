package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Debug Qwen bridge; explicit engine selection streams newly generated PCM. */
internal class LamiNeuralChatVoiceEngine(
    private val context: Context,
    private val root: File,
    private val streamingEnabled: Boolean = false,
    private val preferEarlyStart: Boolean = true,
    private val traceFactory: ((Long, String) -> LamiSpeechTraceCapture)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val streamSpeech: (suspend (String, (String) -> Unit, () -> Unit) -> FloatArray)? = null,
    private val streamSpeechWithProgress: suspend (String, (String) -> Unit, () -> Unit, (TtsStreamingProgress) -> Unit) -> FloatArray = { text, progress, started, streamingProgress ->
        LamiVoiceStreamingProbe.runSpeech(context, root, text, progress, started,
            onStreamingProgress = streamingProgress, preferEarlyStart = preferEarlyStart)
    },
    private val planText: suspend (String, (String) -> Unit) -> List<String> = { text, progress ->
        LamiPreparedVoiceSynthesizer.planText(root, text, progress)
    },
    private val synthesizeSpeech: suspend (String, (String) -> Unit) -> FloatArray = { text, progress ->
        LamiVoiceStreamingProbe.prepareSpeech(context, root, text, progress)
    },
    private val playBufferedSpeech: suspend (FloatArray, () -> Unit) -> Unit = { pcm, started ->
        LamiPcmPlayer.play(context, pcm, started)
    },
    private val playPreparedSpeech: suspend (FloatArray, () -> Unit, (TtsStreamingProgress) -> Unit) -> Unit = { pcm, started, progress ->
        LamiVoiceStreamingProbe.playPreparedSpeech(context, pcm, started, progress)
    },
) : LamiVoiceEngine {
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    @Volatile private var listener: (Boolean) -> Unit = {}
    @Volatile private var audioStartedListener: () -> Unit = {}
    @Volatile private var errorListener: (String) -> Unit = {}
    @Volatile private var streamingListener: (TtsStreamingProgress) -> Unit = {}
    private val requestIds = AtomicLong()
    @Volatile private var lastEndedAtMs = Long.MIN_VALUE

    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    override fun setOnAudioStarted(listener: () -> Unit) {
        audioStartedListener = listener
    }

    override fun setOnError(listener: (String) -> Unit) {
        errorListener = listener
    }

    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) {
        streamingListener = listener
    }

    private fun reportStreamingProgress(progress: TtsStreamingProgress) {
        traceProgress(progress)
        runCatching { streamingListener(progress) }
    }

    private fun traceProgress(progress: TtsStreamingProgress) {
        Log.i("LamiNeuralChatTts", "stream_event=${progress.event} source_ns=${progress.timestampNs} samples=${progress.sampleCount} underruns=${progress.underruns}")
    }

    private val queue = LamiQwenSpeechQueue<PreparedClip>(scope, { busy ->
        if (!busy && speaking.value) {
            lastEndedAtMs = SystemClock.elapsedRealtime()
        }
        setSpeaking(busy)
    }, prepareRequest = { request ->
        val clip = checkNotNull(prepareUtterance(request.text,
            deferPcm = request.mode == LamiQwenRequestMode.QUEUED_PREPARED, reportFailure = false))
        val earlyPrefix = streamingEnabled && preferEarlyStart && request.startsBurst
        clip.copy(preparedPlayback = !earlyPrefix &&
            (request.mode == LamiQwenRequestMode.QUEUED_PREPARED || clip.remaining.isNotEmpty()))
    }, remaining = { it.remaining }, prepareText = { text, _ ->
        checkNotNull(prepareUtterance(text, deferPcm = true, reportFailure = false))
            .copy(preparedPlayback = true)
    }, makeReady = { clip -> if (clip.preparedPlayback) prepareLongSegment(clip) else clip },
        play = ::playClip, onFailure = { failure ->
            Log.e("LamiNeuralChatTts", "queued segment preparation failed", failure)
            runCatching { errorListener("Qwen3-TTSの音声生成に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
        })

    override fun speak(text: String) = queue.replace(text)
    override fun speakQueued(text: String) = queue.enqueue(text)

    private data class PreparedClip(val pcm: FloatArray?, val firstAudio: LamiFirstAudioTiming.Request,
        val capture: LamiSpeechTraceGuard?, val text: String, val remaining: List<String>, val preparedPlayback: Boolean = false)

    private fun trace(firstAudio: LamiFirstAudioTiming.Request, event: String) {
        val elapsedMs = LamiFirstAudioTiming.elapsedMsSince(
            firstAudio,
            SystemClock.elapsedRealtimeNanos(),
        )
        Log.i("LamiNeuralChatTts", "request=${firstAudio.requestId} $event elapsed_ms=$elapsedMs")
    }

    private suspend fun prepareUtterance(text: String, deferPcm: Boolean = false, reportFailure: Boolean = true): PreparedClip? {
        // Include optional diagnostic setup in the preparation-to-playback interval.
        val started = SystemClock.elapsedRealtimeNanos()
        val requestId = requestIds.incrementAndGet()
        var capture: LamiSpeechTraceGuard? = null
        val requestOrdinal = requestId - 1L
        val firstAudio = LamiFirstAudioTiming.start(
            requestId = requestId,
            requestOrdinal = requestOrdinal,
            startedAtNs = started,
            startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(requestOrdinal),
        )
        trace(firstAudio, LamiFirstAudioTiming.requestStartedEvent(firstAudio))
        trace(firstAudio, "status=started text_chars=${text.length}")
        return try {
            val chunks = planText(text) { trace(firstAudio, it) }.filter { it.isNotBlank() }
            currentCoroutineContext().ensureActive()
            require(chunks.isNotEmpty()) { "No Qwen speech text remained after planning" }
            val chunk = chunks.first()
            capture = traceFactory?.let { factory ->
                LamiSpeechTraceGuard.create { factory(requestId, chunk) }
            }
            if (streamingEnabled || deferPcm || chunks.size > 1) return PreparedClip(null, firstAudio, capture, chunk, chunks.drop(1))
            capture?.event("synthesis_started")
            val pcm = synthesizeSpeech(chunk) { trace(firstAudio, it) }
            trace(firstAudio, LamiFirstAudioTiming.preparedEvent(firstAudio, SystemClock.elapsedRealtimeNanos()))
            currentCoroutineContext().ensureActive()
            trace(firstAudio, "synthesis=complete samples=${pcm.size}")
            capture?.event("pcm_ready")
            PreparedClip(pcm, firstAudio, capture, chunk, chunks.drop(1))
        } catch (cancelled: CancellationException) {
            capture?.failure("cancelled")
            trace(firstAudio, "status=cancelled phase=synthesis")
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            capture?.failure("synthesis")
            trace(firstAudio, "status=failure class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=$requestId synthesis failed", failure)
            if (!reportFailure) throw failure
            runCatching { errorListener("Qwen3-TTSの音声生成に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
            null
        }
    }

    private suspend fun playClip(clip: PreparedClip, onStarted: () -> Unit): Boolean = playSegment(clip, onStarted)

    private suspend fun prepareLongSegment(clip: PreparedClip): PreparedClip {
        if (clip.pcm != null) return clip.copy(preparedPlayback = true)
        try {
            currentCoroutineContext().ensureActive()
            trace(clip.firstAudio, "mode=bounded_segment_prefetch ready_ahead_limit=1")
            reportStreamingProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_STARTED,
                timestampNs = SystemClock.elapsedRealtimeNanos()))
            clip.capture?.event("synthesis_started")
            val pcm = synthesizeSpeech(clip.text) { trace(clip.firstAudio, it) }
            val readyAtNs = SystemClock.elapsedRealtimeNanos()
            currentCoroutineContext().ensureActive()
            LamiPcmContract.validate(pcm)
            trace(clip.firstAudio, LamiFirstAudioTiming.preparedEvent(clip.firstAudio, readyAtNs))
            trace(clip.firstAudio, "segment_prefetch=ready samples=${pcm.size}")
            clip.capture?.event("pcm_ready")
            reportStreamingProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED,
                sampleCount = pcm.size, timestampNs = readyAtNs))
            return clip.copy(pcm = pcm, preparedPlayback = true)
        } catch (failure: Exception) {
            clip.capture?.failure(if (failure is CancellationException) "cancelled" else "synthesis")
            trace(clip.firstAudio, "segment_prefetch=failure class=${failure.javaClass.simpleName}")
            throw failure
        }
    }

    private suspend fun playSegment(clip: PreparedClip, onPlaybackStarted: () -> Unit = {}): Boolean {
        val playbackContext = currentCoroutineContext()
        val callbacksActive = AtomicBoolean(true)
        try {
            val onStarted: () -> Unit = {
                val playbackStartedAtNs = SystemClock.elapsedRealtimeNanos()
                if (callbacksActive.get() && playbackContext.isActive) {
                    trace(
                        clip.firstAudio,
                        LamiFirstAudioTiming.playbackStartedEvent(
                            clip.firstAudio,
                            playbackStartedAtNs,
                        ),
                    )
                    clip.capture?.event("playback_started")
                    trace(clip.firstAudio, "playback=started")
                    onPlaybackStarted()
                    runCatching { audioStartedListener() }
                }
            }
            val onProgress: (TtsStreamingProgress) -> Unit = { progress ->
                if (callbacksActive.get() && playbackContext.isActive) reportStreamingProgress(progress)
            }
            val pcm = if (clip.pcm == null) {
                streamSpeech?.invoke(clip.text, { trace(clip.firstAudio, it) }, onStarted)
                    ?: streamSpeechWithProgress(clip.text, { trace(clip.firstAudio, it) }, onStarted, onProgress)
            } else {
                if (clip.preparedPlayback) playPreparedSpeech(clip.pcm, onStarted, onProgress)
                else playBufferedSpeech(clip.pcm, onStarted)
                clip.pcm
            }
            playbackContext.ensureActive()
            clip.capture?.success(pcm)
            trace(clip.firstAudio, "status=complete")
            return true
        } catch (cancelled: CancellationException) {
            clip.capture?.failure("cancelled")
            trace(clip.firstAudio, "status=cancelled phase=playback")
            throw cancelled
        } catch (failure: Exception) {
            playbackContext.ensureActive()
            clip.capture?.failure("playback")
            trace(clip.firstAudio, "status=failure phase=playback class=${failure.javaClass.simpleName}")
            Log.e("LamiNeuralChatTts", "request=${clip.firstAudio.requestId} playback failed", failure)
            runCatching { errorListener("Qwen3-TTSの音声再生に失敗しました: ${failure.message ?: failure.javaClass.simpleName}") }
            return false
        } finally {
            callbacksActive.set(false)
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
