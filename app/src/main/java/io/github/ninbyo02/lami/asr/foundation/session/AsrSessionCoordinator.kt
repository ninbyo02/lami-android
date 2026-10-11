package io.github.ninbyo02.lami.asr.foundation.session

import io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine
import io.github.ninbyo02.lami.asr.foundation.asr.AsrEvent
import io.github.ninbyo02.lami.asr.foundation.asr.AsrSession
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.audio.AudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.vad.EndpointingAudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.vad.VadEndpointPolicy
import io.github.ninbyo02.lami.asr.foundation.vad.VadEngine
import java.util.concurrent.CancellationException

class AsrSessionCoordinator(
    private val engine: AsrEngine,
    private val stateMachine: AsrStateMachine = AsrStateMachine(),
    private val blockedCancelWaitTimeoutMillis: Long = BLOCKED_CANCEL_WAIT_TIMEOUT_MILLIS,
) : AutoCloseable {
    @Volatile
    var state: AsrUiState = AsrUiState()
        private set

    private var session: AsrSession? = null
    private var source: AudioFrameSource? = null
    private var vad: VadEngine? = null
    private var preparedModel: VerifiedModel? = null
    private var closed = false
    private var closing = false
    private var operationInProgress = false
    private var activeOperation: String? = null
    private var cancellationRequested = false
    private var terminalCloseFailureMessage: String? = null
    private val closeMonitor = Object()
    internal var beforeConsumeGateReleaseForTest: (() -> Unit)? = null
    internal var afterConcurrentCancellationRequestForTest: (() -> Unit)? = null

    init {
        require(blockedCancelWaitTimeoutMillis > 0) {
            "blockedCancelWaitTimeoutMillis must be positive"
        }
    }

    suspend fun prepare(model: VerifiedModel) {
        beginOperation("prepare")
        try {
            state = stateMachine.reduce(state, AsrAction.RequestModelLoad)
            try {
                session = engine.open(model)
                preparedModel = model
                state = stateMachine.reduce(state, AsrAction.ModelReady)
            } catch (failure: Throwable) {
                handleOperationalFailure("prepare", failure)
            }
        } finally {
            endOperation()
        }
    }

    suspend fun start(
        source: AudioFrameSource,
        vad: VadEngine? = null,
        endpointPolicy: VadEndpointPolicy? = null,
    ) {
        beginOperation("start")
        try {
            check(state.phase == AsrPhase.READY) { "start requires READY" }
            check(this.source == null) { "start requires no active source" }
            require(endpointPolicy == null || vad != null) {
                "endpoint policy requires a VAD engine"
            }
            this.source = source
            this.vad = vad
            try {
                vad?.reset()
                if (endpointPolicy != null) {
                    this.source = EndpointingAudioFrameSource(
                        delegate = source,
                        vad = checkNotNull(vad),
                        policy = endpointPolicy,
                    )
                    this.vad = null
                }
                if (session == null) {
                    val model = checkNotNull(preparedModel) { "start requires a prepared model" }
                    session = engine.open(model)
                }
            } catch (failure: Throwable) {
                handleOperationalFailure("start", failure)
                return
            }
            state = stateMachine.reduce(state, AsrAction.StartListening)
        } finally {
            endOperation()
        }
    }

    suspend fun consumeNextFrame(): Boolean {
        beginOperation("consume")
        try {
            checkNotNull(source) { "consume requires an active source" }
            checkNotNull(session) { "consume requires a prepared session" }
            return try {
                val frame = checkNotNull(source).read()
                throwIfCancellationRequested()
                if (frame == null) {
                    finalizeSession()
                    false
                } else {
                    try {
                        vad?.accept(frame)
                        throwIfCancellationRequested()
                    } catch (failure: Throwable) {
                        handleOperationalFailure("vad.accept", failure)
                        return false
                    }
                    val events = try {
                        checkNotNull(session).accept(frame)
                            .also { throwIfCancellationRequested() }
                    } catch (failure: Throwable) {
                        handleOperationalFailure("session.accept", failure)
                        return false
                    }
                    var mayConsumeMore = true
                    for (event in events) {
                        throwIfCancellationRequested()
                        when (event) {
                            is AsrEvent.Partial ->
                                state = stateMachine.reduce(
                                    state,
                                    AsrAction.PartialTranscript(event.text),
                                )

                            is AsrEvent.Endpoint -> {
                                finalizeSession(endpointFinalText = event.finalText)
                                mayConsumeMore = false
                            }
                        }
                        if (!mayConsumeMore) break
                    }
                    throwIfCancellationRequested()
                    mayConsumeMore
                }
            } catch (failure: Throwable) {
                if (failure is SanitizedCancellationException) {
                    throw failure
                }
                handleOperationalFailure("consume", failure)
                false
            }
        } finally {
            endOperation()
        }
    }

    suspend fun stopAndFinalize() {
        beginOperation("stop")
        try {
            finalizeSession()
        } finally {
            endOperation()
        }
    }

    fun cancelAndDiscard() {
        var concurrentConsume = false
        val sourceToStop = synchronized(closeMonitor) {
            check(!closed) { "cancel rejected: coordinator closed" }
            check(!closing) { "cancel rejected: coordinator closing" }
            if (operationInProgress) {
                check(activeOperation == "consume") { "cancel rejected: operation in progress" }
                cancellationRequested = true
                concurrentConsume = true
                source
            } else {
                operationInProgress = true
                activeOperation = "cancel"
                cancellationRequested = false
                null
            }
        }
        if (concurrentConsume) {
            afterConcurrentCancellationRequestForTest?.invoke()
            if (sourceToStop != null) {
                try {
                    sourceToStop.stop()
                } catch (_: RuntimeException) {
                    // stop may report failure after unblocking; the consumer still owns all cleanup.
                }
            }
            synchronized(closeMonitor) {
                val deadlineNanos = System.nanoTime() +
                    blockedCancelWaitTimeoutMillis * NANOS_PER_MILLISECOND
                while (operationInProgress) {
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0) throw IllegalStateException("cancel timed out")
                    try {
                        val remainingMillis = remainingNanos / NANOS_PER_MILLISECOND
                        val remainingNanosPart =
                            (remainingNanos % NANOS_PER_MILLISECOND).toInt()
                        closeMonitor.wait(remainingMillis, remainingNanosPart)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("cancel interrupted")
                    }
                }
            }
            return
        }
        try {
            if (state.phase == AsrPhase.READY && source == null && session == null) return
            checkNotNull(source) { "cancel requires an active source" }
            checkNotNull(session) { "cancel requires a prepared session" }
            state = stateMachine.reduce(state, AsrAction.CancelAndDiscard)
            val cleanupFailure = discardOwnedResources()
            if (cleanupFailure != null) {
                preparedModel = null
                state = stateMachine.reduce(
                    state,
                    AsrAction.Failed(
                        safeFailureMessage(cleanupFailure.operation, cleanupFailure.failure),
                    ),
                )
                return
            }
            state = stateMachine.reduce(state, AsrAction.Cancelled)
        } finally {
            endOperation()
        }
    }

    fun discardReview() {
        beginOperation("discardReview")
        try {
            check(source == null && session == null) {
                "discardReview requires released resources"
            }
            state = stateMachine.reduce(state, AsrAction.DiscardReview)
        } finally {
            endOperation()
        }
    }

    fun resetAfterFailure() {
        beginOperation("reset")
        try {
            check(source == null && session == null && preparedModel == null) {
                "reset requires released resources and no prepared model"
            }
            state = stateMachine.reduce(state, AsrAction.Reset)
        } finally {
            endOperation()
        }
    }

    private suspend fun finalizeSession(endpointFinalText: String? = null) {
        throwIfCancellationRequested()
        val activeSource = checkNotNull(source) { "finalize requires an active source" }
        val activeVad = vad
        val activeSession = checkNotNull(session) { "finalize requires a prepared session" }
        state = stateMachine.reduce(state, AsrAction.StopAndFinalize)
        source = null
        vad = null
        val stopFailure = runCatching { activeSource.stop() }.exceptionOrNull()
        val closeFailure = runCatching { activeSource.close() }.exceptionOrNull()
        val vadCloseFailure = runCatching { activeVad?.close() }.exceptionOrNull()
        val sourceFailure = stopFailure ?: closeFailure ?: vadCloseFailure
        if (sourceFailure != null) {
            val operation = when (sourceFailure) {
                stopFailure -> "source.stop"
                closeFailure -> "source.close"
                else -> "vad.close"
            }
            handleOperationalFailure(operation, sourceFailure)
            return
        }
        throwIfCancellationRequested()
        val result = try {
            activeSession.finish()
        } catch (failure: Throwable) {
            handleOperationalFailure("session.finish", failure)
            return
        }
        throwIfCancellationRequested()
        session = null
        val sessionCloseFailure = runCatching { activeSession.close() }.exceptionOrNull()
        if (sessionCloseFailure != null) {
            handleOperationalFailure("session.close", sessionCloseFailure)
            return
        }
        throwIfCancellationRequested()
        val finalText = result.finalText.ifBlank { endpointFinalText.orEmpty() }
        val transcript = result.transcript?.takeIf { it.text == finalText }
        state = stateMachine.reduce(
            state,
            AsrAction.FinalTranscript(finalText, transcript),
        )
        throwIfCancellationRequested()
    }

    private fun throwIfCancellationRequested() {
        val requested = synchronized(closeMonitor) { cancellationRequested }
        if (requested) throw CancellationException(SANITIZED_CANCELLATION_MESSAGE)
    }

    private fun handleOperationalFailure(operation: String, failure: Throwable) {
        if (failure !is CancellationException) {
            failOperational(operation, failure)
            return
        }

        synchronized(closeMonitor) {
            cancellationRequested = false
        }
        if (state.phase in setOf(AsrPhase.LISTENING, AsrPhase.FINALIZING)) {
            state = stateMachine.reduce(state, AsrAction.CancelAndDiscard)
            val cleanupFailure = discardOwnedResources()
            if (cleanupFailure == null) {
                state = stateMachine.reduce(state, AsrAction.Cancelled)
            } else {
                preparedModel = null
                state = stateMachine.reduce(
                    state,
                    AsrAction.Failed(
                        safeFailureMessage(cleanupFailure.operation, cleanupFailure.failure),
                    ),
                )
            }
        } else if (state.phase == AsrPhase.REVIEWING) {
            state = stateMachine.reduce(state, AsrAction.DiscardReview)
        } else {
            failOperational(operation, failure)
        }
        throw SanitizedCancellationException()
    }

    private fun failOperational(operation: String, failure: Throwable) {
        discardOwnedResources()
        preparedModel = null
        state = stateMachine.reduce(
            state,
            AsrAction.Failed(safeFailureMessage(operation, failure)),
        )
    }

    private fun discardOwnedResources(): CleanupFailure? {
        val activeSource = source
        source = null
        val activeVad = vad
        vad = null
        val activeSession = session
        session = null
        var firstFailure: CleanupFailure? = null

        fun attempt(operation: String, release: () -> Unit) {
            try {
                release()
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = CleanupFailure(operation, failure)
                }
            }
        }

        if (activeSource != null) {
            attempt("source.stop") { activeSource.stop() }
            attempt("source.close") { activeSource.close() }
        }
        if (activeVad != null) {
            attempt("vad.close") { activeVad.close() }
        }
        if (activeSession != null) {
            attempt("session.cancel") { activeSession.cancel() }
            attempt("session.close") { activeSession.close() }
        }
        return firstFailure
    }

    override fun close() {
        synchronized(closeMonitor) {
            while (closing) {
                try {
                    closeMonitor.wait()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("close interrupted")
                }
            }
            if (closed) {
                terminalCloseFailureMessage?.let { throw IllegalStateException(it) }
                return
            }
            check(!operationInProgress) { "close rejected: operation in progress" }
            closing = true
            operationInProgress = true
            activeOperation = "close"
        }

        var failureMessage: String? = null
        try {
            val cleanupFailure = discardOwnedResources()
            preparedModel = null
            state = state.copy(partialText = "", finalText = "")
            val engineFailure = runCatching { engine.close() }.exceptionOrNull()
            val firstFailure = cleanupFailure ?: engineFailure?.let {
                CleanupFailure("engine.close", it)
            }
            failureMessage = firstFailure?.let {
                safeFailureMessage(it.operation, it.failure)
            }
        } catch (failure: Throwable) {
            failureMessage = safeFailureMessage("close", failure)
        } finally {
            synchronized(closeMonitor) {
                terminalCloseFailureMessage = failureMessage
                closed = true
                closing = false
                operationInProgress = false
                activeOperation = null
                closeMonitor.notifyAll()
            }
        }
        failureMessage?.let { throw IllegalStateException(it) }
    }

    private fun beginOperation(operation: String) {
        synchronized(closeMonitor) {
            check(!closed) { "$operation rejected: coordinator closed" }
            check(!closing) { "$operation rejected: coordinator closing" }
            check(!operationInProgress) { "$operation rejected: operation in progress" }
            operationInProgress = true
            activeOperation = operation
            cancellationRequested = false
        }
    }

    private fun endOperation() {
        val completingConsume = synchronized(closeMonitor) {
            check(operationInProgress) { "coordinator operation gate was not held" }
            activeOperation == "consume"
        }
        if (completingConsume) beforeConsumeGateReleaseForTest?.invoke()

        val lateConsumeCancellation = synchronized(closeMonitor) {
            val requested = activeOperation == "consume" && cancellationRequested
            if (!requested) {
                operationInProgress = false
                activeOperation = null
                cancellationRequested = false
                closeMonitor.notifyAll()
            }
            requested
        }
        if (!lateConsumeCancellation) return

        var lateFailure: Throwable? = null
        try {
            when (state.phase) {
                AsrPhase.LISTENING,
                AsrPhase.FINALIZING,
                -> handleOperationalFailure(
                    "consume",
                    CancellationException(SANITIZED_CANCELLATION_MESSAGE),
                )
                AsrPhase.REVIEWING -> {
                    synchronized(closeMonitor) { cancellationRequested = false }
                    state = stateMachine.reduce(state, AsrAction.DiscardReview)
                    throw SanitizedCancellationException()
                }
                else -> {
                    synchronized(closeMonitor) { cancellationRequested = false }
                    throw SanitizedCancellationException()
                }
            }
        } catch (failure: Throwable) {
            lateFailure = failure
        }
        synchronized(closeMonitor) {
            operationInProgress = false
            activeOperation = null
            cancellationRequested = false
            closeMonitor.notifyAll()
        }
        lateFailure?.let { throw it }
    }

    private fun safeFailureMessage(operation: String, failure: Throwable): String =
        "$operation failed: ${failure.javaClass.simpleName}"

    private data class CleanupFailure(
        val operation: String,
        val failure: Throwable,
    )

    private class SanitizedCancellationException :
        CancellationException(SANITIZED_CANCELLATION_MESSAGE)

    private companion object {
        const val SANITIZED_CANCELLATION_MESSAGE = "ASR operation cancelled"
        const val BLOCKED_CANCEL_WAIT_TIMEOUT_MILLIS = 6_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
