package io.github.ninbyo02.lami.asr

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.github.ninbyo02.lami.asr.platform.audio.AudioInputSource
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureObserver
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvent
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvents
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import io.github.ninbyo02.lami.assistant.AssistantPreparedModelOwner
import io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.platform.model.AndroidLocalModelStore
import io.github.ninbyo02.lami.asr.platform.sherpa.SherpaOfflineAsrEngine
import java.util.concurrent.atomic.AtomicBoolean
import io.github.ninbyo02.lami.asr.foundation.wake.WakeWordMatcher
import io.github.ninbyo02.lami.asr.foundation.wake.WakeWordSession
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences

internal sealed interface AssistantRecognitionResult {
    data class Success(
        val transcript: String,
        val usedBluetoothMicrophone: Boolean,
        val automaticEligible: Boolean = true,
    ) : AssistantRecognitionResult

    data class ModelRequired(val stagingPath: String) : AssistantRecognitionResult

    data class Failure(val detail: String) : AssistantRecognitionResult
}

/** One-shot local recognizer used by Android assist launches inside the LAMI process. */
internal class LamiAssistantRecognizer(
    context: Context,
    private val beforeCapture: suspend () -> Unit = {},
) : Closeable {
    private val appContext = context.applicationContext
    private var activeController: PrivacyLocalAsrController? = null
    private val finishRequested = AtomicBoolean(false)
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val models = AssistantPreparedModelOwner(
        scope = modelScope,
        loadModel = {
            trace("model_verify_begin")
            AndroidLocalModelStore(appContext).loadOrInstall().also { trace("model_verify_complete") }
        },
        key = { model: VerifiedModel -> model.contentFingerprint },
        create = { model: VerifiedModel ->
            trace("model_native_begin")
            val engine = SherpaOfflineAsrEngine()
            try {
                // Initialize native weights without creating AudioRecord or changing audio routing.
                engine.open(model).close()
                trace("model_native_ready")
                engine
            } catch (failure: Throwable) { engine.close(); throw failure }
        },
        dispose = { engine: SherpaOfflineAsrEngine ->
            runCatching { engine.close() }.onFailure { trace("model_release_failed type=${it.javaClass.simpleName}") }
        },
    )

    fun prepareModel(): Deferred<AssistantPreparedModelOwner.Prepared<VerifiedModel, SherpaOfflineAsrEngine>> {
        trace("model_prepare_requested")
        return models.prepare()
    }

    fun releasePreparedModel() { models.invalidate() }

    suspend fun stopAndKeep() {
        finishRequested.set(true)
        activeController?.stopAndKeep()
    }

    suspend fun listenOnce(wakeWord: String? = null,
                           isCurrentSession: () -> Boolean = { true },
                           onCaptureEvent: (AudioCaptureEvent) -> Unit = {},
                           publishEvent: (AssistantVoiceEvent) -> Unit = AssistantVoiceEvents::publish,
                           onStage: (String) -> Unit = {},
                           preparation: Deferred<AssistantPreparedModelOwner.Prepared<VerifiedModel, SherpaOfflineAsrEngine>> = prepareModel()): AssistantRecognitionResult {
        check(activeController == null) { "assistant recognition is already active" }
        finishRequested.set(false)
        val matcher = try {
            wakeWord?.let(::WakeWordMatcher)
        } catch (_: IllegalArgumentException) {
            return AssistantRecognitionResult.Failure("呼びかけ語を確認してください。")
        }
        val audio = appContext.getSystemService(AudioManager::class.java)
        if (matcher != null && (audio.isMusicActive || audio.mode != AudioManager.MODE_NORMAL)) {
            return AssistantRecognitionResult.Failure("音楽や通話を止めてから待機を開始してください。")
        }
        val awaitingWakeWord = AtomicBoolean(matcher != null)
        val preferences = SettingsPreferences(appContext)
        val settings = preferences.asrListeningSettingsFlow.first().copy(
            endSilenceMillis = preferences.voiceInputSilenceSecondsFlow.first() * 1000)
        val bluetooth = canUseBluetoothConversationMicrophone()
        trace("listen_once bluetooth=$bluetooth")
        val route = if (bluetooth) BluetoothConversationAudio(appContext) else ConversationAudioRoute.NONE
        val ready = try { preparation.await() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            val store = AndroidLocalModelStore(appContext)
            return if (store.missingStagedFiles().isNotEmpty()) AssistantRecognitionResult.ModelRequired(store.stagingPath)
            else AssistantRecognitionResult.Failure("音声モデルを準備できませんでした (${failure.javaClass.simpleName})")
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrentSession()) return AssistantRecognitionResult.Failure("音声入力を終了しました。")
        val lease = models.acquire(ready)
        onStage("model_await_complete")
        val controller = try { PrivacyLocalAsrController(
            context = appContext,
            // Keep the communication route across wake candidates, without retaining raw PCM.
            conversationAudio = if (matcher == null) route else ConversationAudioRoute.NONE,
            audioInputSource = if (bluetooth) {
                AudioInputSource.VOICE_COMMUNICATION
            } else {
                AudioInputSource.MIC
            },
            audioInputGain = if (bluetooth) BLUETOOTH_INPUT_GAIN else 1.0,
            listeningSettings = settings,
            preparedModel = lease.model,
            engine = object : AsrEngine {
                override suspend fun open(model: VerifiedModel) = lease.resource.open(model)
                override fun close() { lease.close() }
            },
        ) } catch (failure: Throwable) { lease.close(); throw failure }
        var peakDbFs = -120f
        controller.setAudioCaptureObserver(AudioCaptureObserver { event ->
            if (!isCurrentSession()) return@AudioCaptureObserver
            if (!awaitingWakeWord.get()) onCaptureEvent(event)
            when (event) {
                AudioCaptureEvent.Started -> publishEvent(
                    if (awaitingWakeWord.get()) AssistantVoiceEvent.WaitingForWakeWord else AssistantVoiceEvent.Listening,
                )
                is AudioCaptureEvent.Level -> peakDbFs = maxOf(peakDbFs, event.sample.peakDbFs)
                is AudioCaptureEvent.InputInterrupted -> trace("capture_interrupted reason=${event.reason}")
                else -> Unit
            }
        })
        activeController = controller
        return try {
            when (val prepared = controller.prepare()) {
                is PrivacyAsrScreenState -> {
                    trace("prepared phase=${prepared.phase} detail=${prepared.detail}")
                    when (prepared.phase) {
                    PrivacyAsrPhase.READY -> {
                        if (finishRequested.get()) return AssistantRecognitionResult.Failure("音声入力を終了しました。")
                        withContext(Dispatchers.IO) { route.start() }
                        if (matcher != null) {
                            publishEvent(AssistantVoiceEvent.WaitingForWakeWord)
                            val detected = WakeWordSession(matcher).awaitDetection {
                                val candidate = controller.recordWakeWordUtterance()
                                currentCoroutineContext().ensureActive()
                                val candidateText = candidate.transcript?.text
                                trace("wake_candidate phase=${candidate.phase} textLength=${candidateText?.length ?: 0} matched=${candidateText?.let(matcher::matches) == true}")
                                AssistantVoiceEvents.showWakeCandidateForDebug(candidateText)
                                when (candidate.phase) {
                                    PrivacyAsrPhase.FINAL -> candidate.transcript?.text.also {
                                        controller.releaseReview()
                                    }
                                    PrivacyAsrPhase.READY -> null
                                    else -> error("wake capture failed")
                                }
                            }
                            if (!detected) return AssistantRecognitionResult.Failure("呼びかけ待機を終了しました。")
                            awaitingWakeWord.set(false)
                            AssistantVoiceEvents.prepare()
                        }
                        onStage("cue_begin")
                        beforeCapture()
                        onStage("cue_complete")
                        currentCoroutineContext().ensureActive()
                        recognize(controller, bluetooth) { models.invalidateIfCurrent(ready) }
                    }
                    PrivacyAsrPhase.MODEL_REQUIRED ->
                        AssistantRecognitionResult.ModelRequired(controller.stagingPath)
                    else -> {
                        models.invalidateIfCurrent(ready)
                        AssistantRecognitionResult.Failure(prepared.detail)
                    }
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            models.invalidateIfCurrent(ready)
            trace("recognition_failed type=${failure.javaClass.simpleName}")
            AssistantRecognitionResult.Failure(
                "音声認識に失敗しました (${failure.javaClass.simpleName})",
            )
        } finally {
            trace("capture_peak_dbfs=$peakDbFs")
            activeController = null
            try {
                controller.close()
            } finally {
                lease.close()
                route.stop()
            }
        }
    }

    private suspend fun recognize(
        controller: PrivacyLocalAsrController,
        bluetooth: Boolean,
        invalidateFailedModel: () -> Unit,
    ): AssistantRecognitionResult {
        val state = controller.recordUtterance()
        if (state.phase == PrivacyAsrPhase.ERROR) invalidateFailedModel()
        currentCoroutineContext().ensureActive()
        trace("record_ended phase=${state.phase} detail=${state.detail} textLength=${state.transcript?.text?.length ?: 0}")
        val transcript = state.transcript?.text?.trim().orEmpty()
        return if (state.phase == PrivacyAsrPhase.FINAL && transcript.isNotEmpty()) {
            AssistantRecognitionResult.Success(transcript, bluetooth,
                !finishRequested.get() && state.endReason == io.github.ninbyo02.lami.asr.foundation.vad.FinalizeReason.END_SILENCE)
        } else {
            AssistantRecognitionResult.Failure(state.detail)
        }
    }

    private fun canUseBluetoothConversationMicrophone(): Boolean {
        val audio = appContext.getSystemService(AudioManager::class.java)
        return audio.mode == AudioManager.MODE_NORMAL &&
            !audio.isMusicActive &&
            audio.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
    }

    override fun close() {
        // The cancelled capture job owns its coordinator cleanup and releases its lease last.
        // Never release native weights underneath an in-flight session or initializer.
        models.close()
    }

    private fun trace(message: String) {
        val runtime = Runtime.getRuntime()
        io.github.ninbyo02.lami.util.DebugTraceFile.append(appContext,
            "[LAMI_ASR] elapsed_ms=${android.os.SystemClock.elapsedRealtime()} " +
                "java_used_bytes=${runtime.totalMemory() - runtime.freeMemory()} " +
                "native_heap_bytes=${android.os.Debug.getNativeHeapAllocatedSize()} $message")
    }

    private companion object {
        // Real-device Pioneer tests showed useful speech headroom at +12 dB.
        const val BLUETOOTH_INPUT_GAIN = 4.0
    }
}
