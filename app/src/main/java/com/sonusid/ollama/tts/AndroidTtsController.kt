package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

private const val DEFAULT_TTS_SPEECH_RATE = 0.92f
private const val DEFAULT_TTS_PITCH = 1.18f
private const val TTS_REFERENCE_PHRASE = "こんにちは。ラミィです。今日はどんなお手伝いをしましょうか。"
private const val TTS_REFERENCE_PHRASE_2 = "はい、了解しました。少しお待ちくださいね。内容を確認します。"
private const val TTS_REFERENCE_PHRASE_3 = "それでは設定内容を順番に説明しますね。まず最初に、必要な項目を確認しましょう。"
private const val TTS_REFERENCE_PHRASE_4 = "大丈夫ですよ。落ち着いて進めれば、きっとうまくいきます。"

class AndroidTtsController(context: Context) {
    companion object {
        const val DEFAULT_SPEECH_RATE: Float = DEFAULT_TTS_SPEECH_RATE
        const val DEFAULT_PITCH: Float = DEFAULT_TTS_PITCH
        const val MIN_SPEECH_RATE: Float = 0.70f
        const val MAX_SPEECH_RATE: Float = 1.20f
        const val MIN_PITCH: Float = 0.80f
        const val MAX_PITCH: Float = 1.40f
        const val AUTO_SPEAK_COOLDOWN_MS: Long = 0L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeakText: String? = null
    private var currentSpeechRate: Float = DEFAULT_TTS_SPEECH_RATE
    private var currentPitch: Float = DEFAULT_TTS_PITCH
    private var lastPlaybackEndedAtMs: Long = 0L

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.language = Locale.JAPANESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        notifyPlaybackState(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        markPlaybackEnded()
                        notifyPlaybackState(false)
                    }

                    override fun onError(utteranceId: String?) {
                        markPlaybackEnded()
                        notifyPlaybackState(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        markPlaybackEnded()
                        notifyPlaybackState(false)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        markPlaybackEnded()
                        notifyPlaybackState(false)
                    }
                })

                val pendingText = pendingSpeakText
                pendingSpeakText = null
                if (!pendingText.isNullOrEmpty()) {
                    speakInternal(pendingText)
                }
            } else {
                isReady = false
                pendingSpeakText = null
                notifyPlaybackState(false)
            }
        }
    }

    fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    fun speak(text: String) {
        val cleanedSpeechText = SpeechTextBuilder.build(text)
        val finalSpeechText = TtsSummaryBuilder.build(
            rawDisplayText = text,
            speechText = cleanedSpeechText,
            isError = false
        ).trim()
        if (finalSpeechText.isEmpty()) {
            return
        }
        if (!isReady) {
            pendingSpeakText = finalSpeechText
            return
        }
        speakInternal(finalSpeechText)
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

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
    }

    fun setSpeechConfig(rate: Float, pitch: Float) {
        currentSpeechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        currentPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
    }

    private fun speakInternal(text: String) {
        runCatching {
            tts?.setSpeechRate(currentSpeechRate)
            tts?.setPitch(currentPitch)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, nextUtteranceId())
        }.onFailure {
            notifyPlaybackState(false)
        }
    }

    fun stop() {
        pendingSpeakText = null
        runCatching {
            tts?.stop()
        }
        markPlaybackEnded()
        notifyPlaybackState(false)
    }

    fun isInCooldown(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return nowMs - lastPlaybackEndedAtMs < AUTO_SPEAK_COOLDOWN_MS
    }

    fun clearCooldown() {
        lastPlaybackEndedAtMs = 0L
    }

    fun shutdown() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        pendingSpeakText = null
        tts = null
        isReady = false
    }

    private fun notifyPlaybackState(isPlaying: Boolean) {
        mainHandler.post {
            onPlaybackStateChanged?.invoke(isPlaying)
        }
    }

    private fun markPlaybackEnded(nowMs: Long = SystemClock.elapsedRealtime()) {
        lastPlaybackEndedAtMs = nowMs
    }

    private fun nextUtteranceId(): String = "lami-tts-${UUID.randomUUID()}"
}
