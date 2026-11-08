package io.github.ninbyo02.lami.asr

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureObserver
import io.github.ninbyo02.lami.asr.foundation.audio.AudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.AudioRecordFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.ContinuousAudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.GainAudioCaptureInput
import io.github.ninbyo02.lami.asr.foundation.model.ModelInstallationException
import io.github.ninbyo02.lami.asr.foundation.session.AsrPhase
import io.github.ninbyo02.lami.asr.foundation.session.AsrSessionCoordinator
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.vad.AsrListeningSettings
import io.github.ninbyo02.lami.asr.foundation.vad.VadEndpointPolicy
import io.github.ninbyo02.lami.asr.foundation.vad.VadEndpointTerminal
import io.github.ninbyo02.lami.asr.foundation.vad.DiscardReason
import io.github.ninbyo02.lami.asr.platform.audio.AndroidAudioRecordInput
import io.github.ninbyo02.lami.asr.platform.audio.AndroidMicrophonePermissionGate
import io.github.ninbyo02.lami.asr.platform.audio.AudioInputSource
import io.github.ninbyo02.lami.asr.platform.model.AndroidLocalModelStore
import io.github.ninbyo02.lami.asr.platform.sherpa.SherpaOfflineAsrEngine
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class PrivacyAsrPhase {
    PREPARING,
    MODEL_REQUIRED,
    READY,
    LISTENING,
    FINAL,
    COMMITTED,
    MEETING_LISTENING,
    MEETING_REVIEW,
    MEETING_COMMITTED,
    ERROR,
}

internal data class PrivacyAsrScreenState(
    val phase: PrivacyAsrPhase = PrivacyAsrPhase.PREPARING,
    val detail: String = "モデルを検証しています。",
    val transcript: TranscriptRevision? = null,
    val endReason: io.github.ninbyo02.lami.asr.foundation.vad.FinalizeReason? = null,
)

internal interface PrivacyAsrSession : AutoCloseable {
    val stagingPath: String

    suspend fun prepare(): PrivacyAsrScreenState
    suspend fun importModel(uris: List<Uri>): PrivacyAsrScreenState
    suspend fun recordUtterance(): PrivacyAsrScreenState
    suspend fun recordMeetingUtterance(): PrivacyAsrScreenState = recordUtterance()
    suspend fun endMeetingCapture() = Unit
    fun setAudioCaptureObserver(observer: AudioCaptureObserver) = Unit
    suspend fun cancel(): PrivacyAsrScreenState
    fun releaseReview(): PrivacyAsrScreenState
    fun commit(editedText: String): PrivacyAsrScreenState
    fun discard(): PrivacyAsrScreenState
}

internal class PrivacyLocalAsrController(
    context: Context,
    private val conversationAudio: ConversationAudioRoute = ConversationAudioRoute.NONE,
    private val audioInputSource: AudioInputSource = AudioInputSource.MIC,
    private val audioInputGain: Double = 1.0,
    private val listeningSettings: AsrListeningSettings = AsrListeningSettings(),
    private val engine: io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine = SherpaOfflineAsrEngine(),
    private val preparedModel: io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel? = null,
) : PrivacyAsrSession {
    private val appContext = context.applicationContext
    private val modelStore = AndroidLocalModelStore(appContext)
    private val coordinator = AsrSessionCoordinator(engine)
    @Volatile
    private var captureObserver = AudioCaptureObserver.NONE
    private val meetingCaptureMonitor = Any()
    private var meetingCapture: ContinuousAudioFrameSource? = null
    @Volatile
    private var closed = false
    private var lastEndpointTerminal: VadEndpointTerminal? = null
    private val finishRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var singleCapture: AudioRecordFrameSource? = null

    suspend fun stopAndKeep() = withContext(Dispatchers.IO) {
        finishRequested.set(true)
        runCatching { singleCapture?.finish() }
        Unit
    }

    override val stagingPath: String
        get() = modelStore.stagingPath

    override fun setAudioCaptureObserver(observer: AudioCaptureObserver) {
        captureObserver = observer
    }

    override suspend fun prepare(): PrivacyAsrScreenState = withContext(Dispatchers.IO) {
        check(!closed)
        tracePrepare("begin")
        try {
            resetCoordinatorAfterFailure()
            tracePrepare("model_load_begin")
            val model = preparedModel ?: modelStore.loadOrInstall()
            tracePrepare("model_verified")
            coordinator.prepare(model)
            tracePrepare("engine_${coordinator.state.phase}_${coordinator.state.errorMessage.orEmpty()}")
            stateAfterPrepare()
        } catch (failure: Throwable) {
            tracePrepare("failed_${failure.javaClass.simpleName}")
            val missing = runCatching { modelStore.missingStagedFiles() }.getOrDefault(emptyList())
            if (missing.isNotEmpty()) {
                PrivacyAsrScreenState(
                    phase = PrivacyAsrPhase.MODEL_REQUIRED,
                    detail = "固定モデルが不足しています: ${missing.joinToString()}",
                )
            } else {
                val safeDetail = if (failure is ModelInstallationException) {
                    failure.message
                } else {
                    "prepare failed: ${failure.javaClass.simpleName}"
                }
                errorState(safeDetail)
            }
        }
    }

    private fun tracePrepare(stage: String) {
        if (io.github.ninbyo02.lami.BuildConfig.DEBUG) android.util.Log.i("LamiAsr", "prepare stage=$stage")
    }

    override suspend fun importModel(uris: List<Uri>): PrivacyAsrScreenState =
        withContext(Dispatchers.IO) {
            check(!closed)
            try {
                resetCoordinatorAfterFailure()
                val model = modelStore.importSelected(uris)
                coordinator.prepare(model)
                stateAfterPrepare("モデルを検証して端末内へ導入しました。")
            } catch (failure: Throwable) {
                val safeDetail = if (failure is ModelInstallationException) {
                    failure.message
                } else {
                    "model import failed: ${failure.javaClass.simpleName}"
                }
                errorState(safeDetail)
            }
        }

    override suspend fun recordUtterance(): PrivacyAsrScreenState = recordSingleUtterance(wakeCandidate = false)

    suspend fun recordWakeWordUtterance(): PrivacyAsrScreenState = recordSingleUtterance(wakeCandidate = true)

    private suspend fun recordSingleUtterance(wakeCandidate: Boolean): PrivacyAsrScreenState = withContext(Dispatchers.IO) {
        check(!closed)
        try {
            if (finishRequested.get()) return@withContext readyState("音声入力を停止しました。")
            conversationAudio.start()
            val source = createCaptureSource(DEFAULT_CAPTURE_BUFFER_FRAMES)
            singleCapture = source
            if (finishRequested.get()) source.finish()
            recognize(source, wakeCandidate)
        } catch (cancelled: CancellationException) {
            io.github.ninbyo02.lami.util.DebugTraceFile.append(appContext,
                "[LAMI_ASR] capture_cancelled type=${cancelled.javaClass.simpleName} endpoint=$lastEndpointTerminal")
            val noSpeech = (lastEndpointTerminal as? VadEndpointTerminal.Discard)?.reason == DiscardReason.NO_SPEECH
            readyState(if (noSpeech) "声を確認できませんでした。合図の後にもう一度話してください。" else "認識をキャンセルしました。")
        } catch (failure: Throwable) {
            errorState("record failed: ${failure.javaClass.simpleName}")
        } finally {
            singleCapture = null
            safeNotifyCaptureEvent(AudioCaptureEvent.Inactive)
            conversationAudio.stop()
        }
    }

    override suspend fun recordMeetingUtterance(): PrivacyAsrScreenState =
        withContext(Dispatchers.IO) {
            check(!closed)
            try {
                conversationAudio.start()
                recognize(openMeetingSegmentSource())
            } catch (_: CancellationException) {
                readyState("認識をキャンセルしました。")
            } catch (failure: Throwable) {
                closeMeetingCapture()
                conversationAudio.stop()
                errorState("meeting record failed: ${failure.javaClass.simpleName}")
            }
        }

    override suspend fun endMeetingCapture() = withContext(Dispatchers.IO) {
        try {
            closeMeetingCapture()
        } finally {
            conversationAudio.stop()
        }
    }

    override suspend fun cancel(): PrivacyAsrScreenState = withContext(Dispatchers.IO) {
        check(!closed)
        val capture = detachMeetingCapture()
        val stopFailure = runCatching { capture?.stop() }.exceptionOrNull()
        val coordinatorFailure = runCatching {
            when (coordinator.state.phase) {
                AsrPhase.LISTENING, AsrPhase.FINALIZING -> coordinator.cancelAndDiscard()
                AsrPhase.REVIEWING -> coordinator.discardReview()
                AsrPhase.READY -> Unit
                else -> error("cancel requires an active or reviewable session")
            }
        }.exceptionOrNull()
        val closeFailure = runCatching {
            if (capture != null) closeDetachedMeetingCapture(capture)
        }.exceptionOrNull()
        conversationAudio.stop()
        val failure = stopFailure ?: coordinatorFailure ?: closeFailure
        if (failure == null) {
            readyState("認識をキャンセルしました。")
        } else {
            errorState("cancel failed: ${failure.javaClass.simpleName}")
        }
    }

    override fun releaseReview(): PrivacyAsrScreenState {
        check(!closed)
        coordinator.discardReview()
        return readyState()
    }

    override fun commit(editedText: String): PrivacyAsrScreenState {
        check(!closed)
        val final = checkNotNull(coordinator.state.transcript)
        val normalized = editedText.trim()
        check(normalized.isNotEmpty()) { "committed transcript is blank" }
        val reviewed = if (normalized == final.text) final else final.revise(normalized)
        val committed = reviewed.commit()
        coordinator.discardReview()
        return PrivacyAsrScreenState(
            phase = PrivacyAsrPhase.COMMITTED,
            detail = "文字起こしを確定しました。",
            transcript = committed,
        )
    }

    override fun discard(): PrivacyAsrScreenState {
        check(!closed)
        coordinator.discardReview()
        return readyState("文字起こしを破棄しました。")
    }

    private suspend fun recognize(source: AudioFrameSource, wakeCandidate: Boolean = false): PrivacyAsrScreenState {
        lastEndpointTerminal = null
        coordinator.start(
            source = source,
            vad = listeningSettings.createVad(),
            endpointPolicy = VadEndpointPolicy(config = if (wakeCandidate) {
                listeningSettings.wakeEndpointConfig()
            } else {
                listeningSettings.endpointConfig()
            }, onTerminal = { terminal ->
                lastEndpointTerminal = terminal
                io.github.ninbyo02.lami.util.DebugTraceFile.append(appContext, "[LAMI_ASR] endpoint=$terminal")
            }),
        )
        while (coordinator.consumeNextFrame()) {
            currentCoroutineContext().ensureActive()
        }
        currentCoroutineContext().ensureActive()
        val result = coordinator.state
        return if (result.phase == AsrPhase.REVIEWING) {
            PrivacyAsrScreenState(
                phase = PrivacyAsrPhase.FINAL,
                detail = "端末内認識が完了しました。",
                transcript = result.transcript,
                endReason = (lastEndpointTerminal as? VadEndpointTerminal.Finalize)?.reason,
            )
        } else {
            errorState(result.errorMessage)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createCaptureSource(capacityFrames: Int): AudioRecordFrameSource {
        val permissionGate = AndroidMicrophonePermissionGate(appContext)
        check(permissionGate.isGranted()) { "microphone permission is not granted" }
        val platformInput = AndroidAudioRecordInput.create(source = audioInputSource)
        return AudioRecordFrameSource(
            input = if (audioInputGain == 1.0) {
                platformInput
            } else {
                GainAudioCaptureInput(platformInput, audioInputGain)
            },
            permissionGate = permissionGate,
            observer = AudioCaptureObserver { event ->
                if (event == AudioCaptureEvent.Started) {
                    val id = platformInput.routedDeviceId()
                    val device = appContext.getSystemService(android.media.AudioManager::class.java)
                        .getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
                    io.github.ninbyo02.lami.util.DebugTraceFile.append(appContext,
                        "[LAMI_ASR] capture_started route=$id type=${device?.type} source=$audioInputSource")
                }
                safeNotifyCaptureEvent(event)
            },
            capacityFrames = capacityFrames,
        )
    }

    private fun openMeetingSegmentSource(): AudioFrameSource =
        synchronized(meetingCaptureMonitor) {
            check(!closed) { "controller is closed" }
            val capture = meetingCapture ?: ContinuousAudioFrameSource(
                createCaptureSource(MEETING_CAPTURE_BUFFER_FRAMES),
            ).also { meetingCapture = it }
            capture.openSegment()
        }

    private fun detachMeetingCapture(): ContinuousAudioFrameSource? =
        synchronized(meetingCaptureMonitor) {
            meetingCapture.also { meetingCapture = null }
        }

    private fun closeMeetingCapture() {
        val capture = detachMeetingCapture() ?: return
        closeDetachedMeetingCapture(capture)
    }

    private fun closeDetachedMeetingCapture(capture: ContinuousAudioFrameSource) {
        try {
            capture.close()
        } finally {
            safeNotifyCaptureEvent(AudioCaptureEvent.Inactive)
        }
    }

    private fun safeNotifyCaptureEvent(event: AudioCaptureEvent) {
        try {
            captureObserver.onEvent(event)
        } catch (_: RuntimeException) {
            // UI metering is observational and must never alter recognition.
        }
    }

    private suspend fun resetCoordinatorAfterFailure() {
        if (coordinator.state.phase == AsrPhase.ERROR) {
            coordinator.resetAfterFailure()
        }
    }

    private fun stateAfterPrepare(
        detail: String = "端末内モデルの準備が完了しました。",
    ): PrivacyAsrScreenState = when (coordinator.state.phase) {
        AsrPhase.READY -> readyState(detail)
        else -> errorState(coordinator.state.errorMessage)
    }

    private fun readyState(
        detail: String = "端末内モデルの準備が完了しました。",
    ) = PrivacyAsrScreenState(
        phase = PrivacyAsrPhase.READY,
        detail = detail,
    )

    private fun errorState(message: String?) = PrivacyAsrScreenState(
        phase = PrivacyAsrPhase.ERROR,
        detail = message ?: "ASR処理に失敗しました。",
    )

    override fun close() {
        val capture = synchronized(meetingCaptureMonitor) {
            if (closed) return
            closed = true
            meetingCapture.also { meetingCapture = null }
        }
        runCatching { capture?.stop() }
        runCatching {
            if (coordinator.state.phase in setOf(AsrPhase.LISTENING, AsrPhase.FINALIZING)) {
                coordinator.cancelAndDiscard()
            }
        }
        runCatching {
            if (capture != null) closeDetachedMeetingCapture(capture)
        }
        runCatching { conversationAudio.stop() }
        coordinator.close()
    }

    private companion object {
        const val DEFAULT_CAPTURE_BUFFER_FRAMES = 25
        const val MEETING_CAPTURE_BUFFER_FRAMES = 250
    }
}
