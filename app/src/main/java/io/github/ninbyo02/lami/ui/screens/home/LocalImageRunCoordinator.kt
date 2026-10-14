package io.github.ninbyo02.lami.ui.screens.home

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class LocalImageRunPhase { IDLE, RUNNING, FINALIZING, CANCELLING, RELEASING, RELEASE_FAILED }
private enum class LocalImageTerminalClaim { ACTIVE, CANCELLED, FINALIZING }

internal data class LocalImageRunSnapshot(
    val generation: Long = 0,
    val phase: LocalImageRunPhase = LocalImageRunPhase.IDLE,
    val reason: String? = null,
) {
    val busy: Boolean get() = phase != LocalImageRunPhase.IDLE
    val cleanupPending: Boolean get() = busy && phase != LocalImageRunPhase.RUNNING
}

/** Process-wide fence: a new Activity must also wait for the old native owner. */
internal class LocalImageRunCoordinator {
    private val mutableState = MutableStateFlow(LocalImageRunSnapshot())
    val state: StateFlow<LocalImageRunSnapshot> = mutableState
    private var current: LocalImageRunLease? = null
    private var quarantinedResources: List<Any> = emptyList()

    @Synchronized fun begin(owner: Any, chatId: Int?, modelPath: String?, backend: Any): LocalImageRunLease? {
        if (mutableState.value.busy) return null
        val generation = mutableState.value.generation + 1
        return LocalImageRunLease(this, generation, owner, chatId, modelPath, backend).also {
            current = it
            mutableState.value = LocalImageRunSnapshot(generation, LocalImageRunPhase.RUNNING)
        }
    }

    @Synchronized internal fun accepts(lease: LocalImageRunLease): Boolean =
        current === lease && mutableState.value.phase == LocalImageRunPhase.RUNNING && !lease.cancelled

    @Synchronized internal fun owns(lease: LocalImageRunLease): Boolean = current === lease

    @Synchronized internal fun claimTerminal(lease: LocalImageRunLease, allowReleaseFailed: Boolean): Boolean {
        if (current !== lease || lease.cancelled ||
            (!allowReleaseFailed && mutableState.value.phase != LocalImageRunPhase.RUNNING) ||
            !lease.claimTerminalAtomically()) return false
        if (mutableState.value.phase != LocalImageRunPhase.RELEASE_FAILED) {
            mutableState.value = LocalImageRunSnapshot(lease.generation, LocalImageRunPhase.FINALIZING)
        }
        return true
    }

    @Synchronized internal fun phase(lease: LocalImageRunLease, phase: LocalImageRunPhase, reason: String?) {
        if (current === lease && mutableState.value.phase != LocalImageRunPhase.RELEASE_FAILED) {
            mutableState.value = LocalImageRunSnapshot(lease.generation, phase, reason)
        }
    }

    @Synchronized internal fun complete(lease: LocalImageRunLease) {
        if (current !== lease || mutableState.value.phase == LocalImageRunPhase.RELEASE_FAILED) return
        current = null
        mutableState.value = LocalImageRunSnapshot(lease.generation)
    }

    @Synchronized fun quarantine(reason: String, resources: List<Any>) {
        // Retain handles of uncertain ownership; never retry close or reuse them blindly.
        quarantinedResources = quarantinedResources + resources
        mutableState.value = mutableState.value.copy(phase = LocalImageRunPhase.RELEASE_FAILED, reason = reason)
    }
}

internal class LocalImageRunLease internal constructor(
    private val coordinator: LocalImageRunCoordinator,
    val generation: Long,
    val owner: Any,
    chatId: Int?,
    val modelPath: String?,
    val backend: Any,
) {
    private val terminalClaim = AtomicReference(LocalImageTerminalClaim.ACTIVE)
    private val job = AtomicReference<Job?>(null)
    private val message = AtomicReference<Int?>(null)
    @Volatile var chatId: Int? = chatId
        private set
    val cancelled: Boolean get() = terminalClaim.get() == LocalImageTerminalClaim.CANCELLED || job.get()?.isCancelled == true
    val acceptsCallbacks: Boolean get() = coordinator.accepts(this)
    val ownsRun: Boolean get() = coordinator.owns(this)
    val assistantMessageId: Int? get() = message.get()

    fun bindJob(value: Job) {
        check(job.compareAndSet(null, value)) { "Image dispatch job already bound" }
        value.invokeOnCompletion { coordinator.complete(this) }
        if (terminalClaim.get() == LocalImageTerminalClaim.CANCELLED) value.cancel()
    }

    fun bindAssistantMessage(id: Int?) { message.set(id) }
    fun bindChat(id: Int) { chatId = id }

    fun requestCancellation(reason: String): Boolean {
        if (!ownsRun || !terminalClaim.compareAndSet(LocalImageTerminalClaim.ACTIVE, LocalImageTerminalClaim.CANCELLED)) return false
        coordinator.phase(this, LocalImageRunPhase.CANCELLING, reason)
        job.get()?.cancel()
        return true
    }

    fun releasing() { coordinator.phase(this, LocalImageRunPhase.RELEASING, "native-and-history-cleanup") }

    internal fun claimTerminalAtomically(): Boolean =
        terminalClaim.compareAndSet(LocalImageTerminalClaim.ACTIVE, LocalImageTerminalClaim.FINALIZING)

    fun claimCompletion(): Boolean = coordinator.claimTerminal(this, allowReleaseFailed = false)
    fun claimFailure(): Boolean = coordinator.claimTerminal(this, allowReleaseFailed = true)

    suspend fun <T> persistTerminal(block: suspend () -> T): T {
        check(terminalClaim.get() == LocalImageTerminalClaim.FINALIZING)
        return try {
            block()
        } catch (failure: Throwable) {
            coordinator.quarantine("terminal-persistence:${failure.javaClass.simpleName}", emptyList())
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw LocalImagePersistenceException(failure)
        }
    }

    suspend fun finishHistoryCleanup(cancelHistory: suspend (Int?) -> Unit): Boolean {
        if (!cancelled) return true
        return try {
            cancelHistory(assistantMessageId)
            true
        } catch (failure: Throwable) {
            coordinator.quarantine("history-cleanup:${failure.javaClass.simpleName}", emptyList())
            false
        }
    }
}

internal val localImageRunCoordinator = LocalImageRunCoordinator()

internal class LocalImageReleaseException(stage: String, cause: Throwable) :
    IllegalStateException("Image resource release could not be confirmed: $stage", cause)

internal class LocalImagePersistenceException(cause: Throwable) :
    IllegalStateException("Image terminal persistence could not be confirmed", cause)

/** The provider has already quarantined these handles; lifecycle jobs must not crash the app. */
internal fun runLocalImageLifecycleCleanup(block: () -> Unit): Boolean = try {
    block()
    true
} catch (_: LocalImageReleaseException) {
    false
}

/** Called after the send has ended; cancellation has already been requested when applicable. */
internal fun releaseLocalImageResources(
    conversation: Any?,
    engine: Any?,
    closeConversation: () -> Unit,
    closeEngine: () -> Unit,
    quarantine: (String, List<Any>) -> Unit = localImageRunCoordinator::quarantine,
) {
    try {
        closeConversation()
    } catch (failure: Throwable) {
        quarantine("conversation-close:${failure.javaClass.simpleName}", listOfNotNull(conversation, engine))
        throw LocalImageReleaseException("conversation", failure)
    }
    try {
        closeEngine()
    } catch (failure: Throwable) {
        quarantine("engine-close:${failure.javaClass.simpleName}", listOfNotNull(engine))
        throw LocalImageReleaseException("engine", failure)
    }
}
