package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

const val EXTRA_ASSISTANT_WAKE_WORD = "io.github.ninbyo02.lami.extra.ASSISTANT_WAKE_WORD"

enum class AssistantVoicePhase(val promptPlaceholder: String?) {
    IDLE(null),
    PREPARING("音声認識を準備しています…"),
    LISTENING("聞いています…"),
    WAITING_FOR_WAKE_WORD("呼びかけを待っています…"),
}

sealed interface AssistantVoiceEvent {
    data object Listening : AssistantVoiceEvent
    data object WaitingForWakeWord : AssistantVoiceEvent

    data class Transcript(
        val text: String,
        val autoSubmit: Boolean = false,
        val invocationId: Long = 0L,
    ) : AssistantVoiceEvent

    data class Failure(val message: String) : AssistantVoiceEvent
}

/** Process-local bridge from the Activity-owned recognizer to the active chat composer. */
object AssistantVoiceEvents {
    internal val submissionOwner = AssistantSubmissionOwner()
    private val mutableInvocation = MutableStateFlow(0L)
    val invocation = mutableInvocation.asStateFlow()
    fun beginInvocation(): Long = submissionOwner.begin().also { mutableInvocation.value = it }
    fun isCurrentInvocation(id: Long) = submissionOwner.isCurrent(id)
    fun cancelInvocation(id: Long) = submissionOwner.cancel(id)
    fun consumeTranscript(event: AssistantVoiceEvent.Transcript) = submissionOwner.consume(event.invocationId, event.text)
    fun claimSubmission(id: Long, text: String, settingEnabled: Boolean) = submissionOwner.claim(id, text, settingEnabled)
    fun publishTranscript(id: Long, text: String, autoSend: Boolean) {
        if (submissionOwner.offer(id, text, autoSend)) {
            publish(AssistantVoiceEvent.Transcript(text.trim(), submissionOwner.automaticAllowed(id), id))
        }
    }
    private data class CapturePreparation(val owner: Any, val prepare: suspend () -> Unit)
    private val captureLock = Any()
    private var capturePreparation: CapturePreparation? = null
    private val channel = Channel<AssistantVoiceEvent>(capacity = Channel.BUFFERED)
    private val mutablePhase = MutableStateFlow(AssistantVoicePhase.IDLE)
    private val mutableWakeCandidate = MutableStateFlow<String?>(null)
    private val stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events = channel.receiveAsFlow()
    val phase = mutablePhase.asStateFlow()
    val wakeCandidate = mutableWakeCandidate.asStateFlow()
    val stopRecognitionRequests = stopRequests.asSharedFlow()

    fun registerCapturePreparation(prepare: suspend () -> Unit): AutoCloseable {
        val handler = CapturePreparation(Any(), prepare)
        synchronized(captureLock) { capturePreparation = handler }
        return AutoCloseable {
            synchronized(captureLock) { if (capturePreparation === handler) capturePreparation = null }
        }
    }

    suspend fun prepareCapture() {
        val handler = synchronized(captureLock) { checkNotNull(capturePreparation) { "Chat input is not ready" } }
        handler.prepare()
        check(synchronized(captureLock) { capturePreparation === handler }) { "Chat input was replaced" }
    }

    fun requestStopRecognition() {
        submissionOwner.stop()
        stopRequests.tryEmit(Unit)
    }

    fun prepare() {
        mutableWakeCandidate.value = null
        mutablePhase.value = AssistantVoicePhase.PREPARING
    }

    fun reset() {
        mutableWakeCandidate.value = null
        mutablePhase.value = AssistantVoicePhase.IDLE
    }

    fun showWakeCandidateForDebug(text: String?) {
        if (!io.github.ninbyo02.lami.BuildConfig.DEBUG) return
        if (mutablePhase.value != AssistantVoicePhase.WAITING_FOR_WAKE_WORD) return
        mutableWakeCandidate.value = text?.take(64)
    }

    fun publish(event: AssistantVoiceEvent) {
        if (event != AssistantVoiceEvent.WaitingForWakeWord) mutableWakeCandidate.value = null
        mutablePhase.value = when (event) {
            AssistantVoiceEvent.Listening -> AssistantVoicePhase.LISTENING
            AssistantVoiceEvent.WaitingForWakeWord -> AssistantVoicePhase.WAITING_FOR_WAKE_WORD
            else -> AssistantVoicePhase.IDLE
        }
        channel.trySend(event)
    }
}
