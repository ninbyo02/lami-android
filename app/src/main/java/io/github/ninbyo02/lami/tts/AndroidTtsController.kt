package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.util.DebugTraceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

private const val DEFAULT_TTS_SPEECH_RATE = 0.92f
private const val DEFAULT_TTS_PITCH = 1.18f
private const val TTS_REFERENCE_PHRASE = "こんにちは。ラミィです。今日はどんなお手伝いをしましょうか。"
private const val TTS_REFERENCE_PHRASE_2 = "はい、了解しました。少しお待ちくださいね。内容を確認します。"
private const val TTS_REFERENCE_PHRASE_3 = "それでは設定内容を順番に説明しますね。まず最初に、必要な項目を確認しましょう。"
private const val TTS_REFERENCE_PHRASE_4 = "大丈夫ですよ。落ち着いて進めれば、きっとうまくいきます。"

class AndroidTtsController internal constructor(
    context: Context,
    playbackOverride: AndroidTtsPcmPlayback?,
) : LamiVoiceEngine {
    constructor(context: Context) : this(context, null)

    companion object {
        const val DEFAULT_SPEECH_RATE: Float = DEFAULT_TTS_SPEECH_RATE
        const val DEFAULT_PITCH: Float = DEFAULT_TTS_PITCH
        const val MIN_SPEECH_RATE: Float = 0.70f
        const val MAX_SPEECH_RATE: Float = 1.20f
        const val MIN_PITCH: Float = 0.80f
        const val MAX_PITCH: Float = 1.40f
        const val AUTO_SPEAK_COOLDOWN_MS: Long = 0L
        private const val LOG_TAG = "LamiTts"
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var onAudioStarted: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onStreamingProgress: ((TtsStreamingProgress) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val queuedSpeechTexts = ArrayDeque<String>()
    private var hasActiveUtterance = false
    private var currentSpeechRate: Float = DEFAULT_TTS_SPEECH_RATE
    private var currentPitch: Float = DEFAULT_TTS_PITCH
    private var lastPlaybackEndedAtMs: Long = 0L
    @Volatile private var playbackGeneration: Long = 0L
    private var activeUtteranceId: String? = null
    private var playbackStateRevision = 0L
    private val audio = appContext.getSystemService(android.media.AudioManager::class.java)
    private val privacyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL && !usbPlayback.isUsbActive()) stop()
        }
    }
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    @Volatile
    var lastPlaybackCompletedNormally = false
        private set
    private val usbPlayback: AndroidTtsPcmPlayback = (playbackOverride ?: UsbTtsPlayback(appContext, ::trace)).also {
        it.setListener(::onPcmCompleted, ::notifyAudioStarted, ::notifyStreamingProgress)
    }

    fun canPlayInCurrentMode(): Boolean =
        audio.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL || usbPlayback.target() != null


    init {
        appContext.registerReceiver(privacyReceiver, android.content.IntentFilter().apply {
            addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }, Context.RECEIVER_NOT_EXPORTED)
        tts = TextToSpeech(appContext) { status ->
            synchronized(this@AndroidTtsController) {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    tts?.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    val languageResult = tts?.setLanguage(Locale.JAPANESE)
                    Log.i(LOG_TAG, "initialized status=success language_result=$languageResult")
                    trace("initialized status=success language_result=$languageResult")
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            // This is file synthesis starting, not AudioTrack playback.
                            hasActiveUtterance = true
                            notifyPlaybackState(true)
                        }

                        override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            usbPlayback.beginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                        }

                        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            if (audio == null) usbPlayback.failed(utteranceId, "Android TTS supplied null PCM")
                            else usbPlayback.audioAvailable(utteranceId, audio)
                        }

                        override fun onDone(utteranceId: String?): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            // File synthesis is complete; keep ownership until streamed audio drains.
                            usbPlayback.synthesized(utteranceId)
                        }

                        @Deprecated("Deprecated by Android; kept for compatibility with older TTS callbacks.")
                        override fun onError(utteranceId: String?): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            usbPlayback.failed(utteranceId, "Android TTSの音声生成に失敗しました。")
                        }

                        override fun onError(utteranceId: String?, errorCode: Int): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            usbPlayback.failed(utteranceId, "Android TTSの音声生成に失敗しました（$errorCode）。")
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean): Unit = synchronized(this@AndroidTtsController) {
                            if (!isCurrentPlaybackGeneration(utteranceId)) return
                            if (interrupted) queuedSpeechTexts.clear()
                            usbPlayback.failed(utteranceId, "Android TTSの音声生成が中断されました。")
                        }
                    })

                    speakNextQueuedIfAvailable()
                } else {
                    Log.e(LOG_TAG, "initialized status=failure error_code=$status")
                    isReady = false
                    queuedSpeechTexts.clear()
                    notifyPlaybackState(false)
                    notifyError("Android TTSを初期化できませんでした（$status）。")
                }
            }
        }
    }

    @Synchronized
    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    @Synchronized
    override fun setOnAudioStarted(listener: () -> Unit) {
        onAudioStarted = listener
    }

    @Synchronized
    override fun setOnError(listener: (String) -> Unit) {
        onError = listener
    }

    @Synchronized
    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) {
        onStreamingProgress = listener
    }

    override fun speak(text: String) {
        speakWithQueueMode(text, TextToSpeech.QUEUE_FLUSH)
    }

    override fun speakQueued(text: String) {
        speakWithQueueMode(text, TextToSpeech.QUEUE_ADD)
    }

    fun speakReferencePhrase() {
        speak(TTS_REFERENCE_PHRASE)
    }

    fun speakReferencePhrase2() {
        speak(TTS_REFERENCE_PHRASE_2)
    }

    fun speakReferencePhrase3() {
        speak(TTS_REFERENCE_PHRASE_3)
    }

    fun speakReferencePhrase4() {
        speak(TTS_REFERENCE_PHRASE_4)
    }

    @Synchronized
    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
    }

    @Synchronized
    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
    }

    @Synchronized
    fun setSpeechConfig(rate: Float, pitch: Float) {
        currentSpeechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        currentPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
    }

    @Synchronized
    private fun speakWithQueueMode(text: String, queueMode: Int) {
        val cleanedSpeechText = SpeechTextBuilder.build(text)
        val finalSpeechText = TtsSummaryBuilder.build(
            rawDisplayText = text,
            speechText = cleanedSpeechText,
            isError = false
        ).trim()
        if (finalSpeechText.isEmpty()) {
            Log.w(LOG_TAG, "request_rejected reason=empty_after_sanitize")
            return
        }
        lastPlaybackCompletedNormally = false
        val requestTrace =
            "request code_points=${finalSpeechText.codePointCount(0, finalSpeechText.length)} ready=$isReady queue_mode=$queueMode"
        Log.i(LOG_TAG, requestTrace)
        trace(requestTrace)
        if (BuildConfig.DEBUG) {
            val debugText = finalSpeechText.replace("\n", "\\n").replace("\r", "\\r")
            trace("request_text=$debugText")
            Log.d(LOG_TAG, "request_text=$debugText")
        }
        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            playbackGeneration += 1
            activeUtteranceId = null
            usbPlayback.stop()
            runCatching { tts?.stop() }
            queuedSpeechTexts.clear()
            hasActiveUtterance = false
        }
        // Split after sanitizing, and retain these boundaries while initialization
        // or earlier PCM playback is pending. Joining pending sentences could put
        // an otherwise valid request back over Android's UTF-16 input limit.
        queuedSpeechTexts.addAll(splitAndroidTtsText(finalSpeechText, TextToSpeech.getMaxSpeechInputLength()))
        if (!isReady) {
            return
        }
        if (queueMode == TextToSpeech.QUEUE_ADD) {
            if (hasActiveUtterance || tts?.isSpeaking == true || _isSpeaking.value) {
                notifyPlaybackState(true)
                return
            }
        }
        speakNextQueuedIfAvailable()
    }

    private fun speakNextQueuedIfAvailable(): Boolean {
        val nextText = queuedSpeechTexts.pollFirst() ?: return false
        notifyPlaybackState(true)
        speakInternal(nextText)
        return true
    }

    @Synchronized
    private fun speakInternal(text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val generation = playbackGeneration
            mainHandler.post {
                synchronized(this@AndroidTtsController) {
                    if (generation == playbackGeneration) speakInternal(text)
                }
            }
            return
        }
        runCatching {
            val engine = checkNotNull(tts) { "TTS engine unavailable" }
            val usb = usbPlayback.target()
            // External TTS exposes no actual per-utterance route. Unknown output is never
            // inferred private merely from a connected Bluetooth device.
            check(usb != null || audio.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                "Unverifiable external TTS route is blocked in silent/vibrate mode"
            }
            val utteranceId = nextUtteranceId()
            activeUtteranceId = utteranceId
            lastPlaybackCompletedNormally = false
            engine.setSpeechRate(currentSpeechRate)
            engine.setPitch(currentPitch)
            val file = usbPlayback.begin(utteranceId, usb)
            // Capture only. The Android engine must never also play through its own speaker route.
            val result = engine.synthesizeToFile(text, null, file, utteranceId)
            check(result == TextToSpeech.SUCCESS) { "TTS speak rejected with status=$result" }
            Log.i(LOG_TAG, "speak_accepted utterance_id=$utteranceId")
            trace("speak_accepted utterance_id=$utteranceId")
            hasActiveUtterance = true
            notifyPlaybackState(true)
        }.onFailure { exception ->
            activeUtteranceId = null
            usbPlayback.stop()
            Log.e(LOG_TAG, "speak_failed", exception)
            notifyError("Android TTSの読み上げを開始できませんでした: ${exception.message ?: exception.javaClass.simpleName}")
            hasActiveUtterance = false
            if (queuedSpeechTexts.isEmpty()) {
                markPlaybackEnded()
                notifyPlaybackState(false)
            } else {
                // A rejected fragment must not strand the remaining queue. Post
                // rather than recurse when a disconnected engine rejects many.
                val generation = playbackGeneration
                mainHandler.post {
                    synchronized(this@AndroidTtsController) {
                        if (generation == playbackGeneration && !speakNextQueuedIfAvailable()) {
                            markPlaybackEnded()
                            notifyPlaybackState(false)
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        playbackGeneration += 1
        activeUtteranceId = null
        trace("stop generation=$playbackGeneration queued=${queuedSpeechTexts.size}")
        usbPlayback.stop()
        queuedSpeechTexts.clear()
        hasActiveUtterance = false
        runCatching {
            tts?.stop()
        }
        markPlaybackEnded()
        notifyPlaybackState(false)
    }

    override suspend fun stopForVoiceInput() {
        stop()
        usbPlayback.awaitCleanup()
    }

    @Synchronized
    private fun onPcmCompleted(utteranceId: String, succeeded: Boolean, reason: String?) {
        if (!isCurrentPlaybackGeneration(utteranceId)) return
        activeUtteranceId = null
        lastPlaybackCompletedNormally = succeeded
        hasActiveUtterance = false
        if (!succeeded) {
            runCatching { tts?.stop() }
            notifyError("Android TTSの音声生成・再生に失敗しました: ${reason.orEmpty()}")
        }
        trace("playback_drained utterance_id=$utteranceId succeeded=$succeeded")
        if (!speakNextQueuedIfAvailable()) {
            markPlaybackEnded()
            notifyPlaybackState(false)
        }
    }

    override fun isInCooldown(): Boolean = isInCooldown(SystemClock.elapsedRealtime())

    fun isInCooldown(nowMs: Long): Boolean {
        return nowMs - lastPlaybackEndedAtMs < AUTO_SPEAK_COOLDOWN_MS
    }

    @Synchronized
    fun clearCooldown() {
        lastPlaybackEndedAtMs = 0L
    }

    @Synchronized
    override fun shutdown() {
        runCatching { appContext.unregisterReceiver(privacyReceiver) }
        stop()
        usbPlayback.shutdown()
        runCatching {
            tts?.shutdown()
        }
        queuedSpeechTexts.clear()
        hasActiveUtterance = false
        tts = null
        isReady = false
    }

    private fun notifyPlaybackState(isPlaying: Boolean) {
        _isSpeaking.value = isPlaying
        val revision = ++playbackStateRevision
        mainHandler.post {
            synchronized(this@AndroidTtsController) {
                if (revision == playbackStateRevision) onPlaybackStateChanged?.invoke(isPlaying)
            }
        }
    }

    private fun notifyAudioStarted(utteranceId: String) {
        mainHandler.post {
            synchronized(this@AndroidTtsController) {
                if (isCurrentPlaybackGeneration(utteranceId)) runCatching { onAudioStarted?.invoke() }
            }
        }
    }

    private fun notifyStreamingProgress(utteranceId: String, event: TtsStreamingProgress) {
        val generation = playbackGeneration
        if (!utteranceId.startsWith("lami-tts-$generation-")) return
        mainHandler.post {
            synchronized(this@AndroidTtsController) {
                if (generation == playbackGeneration) runCatching { onStreamingProgress?.invoke(event) }
            }
        }
    }

    private fun notifyError(message: String) {
        val generation = playbackGeneration
        mainHandler.post {
            synchronized(this@AndroidTtsController) {
                if (generation == playbackGeneration) runCatching { onError?.invoke(message) }
            }
        }
    }

    private fun markPlaybackEnded(nowMs: Long = SystemClock.elapsedRealtime()) {
        lastPlaybackEndedAtMs = nowMs
    }

    private fun nextUtteranceId(): String = "lami-tts-$playbackGeneration-${UUID.randomUUID()}"

    private fun trace(message: String) {
        DebugTraceFile.append(appContext, "${System.currentTimeMillis()} [LAMI_TTS] $message")
    }

    private fun isCurrentPlaybackGeneration(utteranceId: String?): Boolean {
        return utteranceId != null && utteranceId == activeUtteranceId &&
            utteranceId.startsWith("lami-tts-$playbackGeneration-")
    }
}
