package io.github.ninbyo02.lami.assistant

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Shared model initialization only. Capture/session ownership remains exclusive. */
internal class AssistantPreparedModelOwner<M, R>(
    private val scope: CoroutineScope,
    private val loadModel: suspend () -> M,
    private val key: (M) -> String,
    private val create: suspend (M) -> R,
    private val dispose: suspend (R) -> Unit,
) : Closeable {
    internal class Slot<R>(val key: String, val value: R) {
        var valid = true
        var leased = false
        var disposalScheduled = false
    }
    internal class Prepared<M, R>(val model: M, internal val slot: Slot<R>)
    internal class Lease<M, R>(val model: M, val resource: R, private val release: () -> Unit) : Closeable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }

    private val lock = Any()
    private var generation = 0L
    private var closed = false
    private var current: Slot<R>? = null
    private var activeLease: Slot<R>? = null
    private var leaseReturned: CompletableDeferred<Unit>? = null
    private var pending: Deferred<Prepared<M, R>>? = null
    private var pendingGeneration = -1L
    private var disposalTail: Job? = null

    fun prepare(): Deferred<Prepared<M, R>> = synchronized(lock) {
        check(!closed) { "model owner is closed" }
        pending?.takeIf { it.isActive && pendingGeneration == generation }?.let { return it }
        val previous = pending
        val epoch = generation
        val next = scope.async(start = CoroutineStart.LAZY) {
            // Invalidating an in-flight native initializer never starts a second one concurrently.
            previous?.join()
            requireCurrent(epoch)
            try {
                val model = loadModel() // Preserve full file verification on every new preparation.
                requireCurrent(epoch)
                val identity = key(model)
                val reused = synchronized(lock) { current?.takeIf { it.valid && it.key == identity } }
                if (reused != null) return@async Prepared(model, reused)
                val captureFinished = synchronized(lock) {
                    retireLocked(current)
                    current = null
                    leaseReturned
                }
                captureFinished?.await()
                val disposal = synchronized(lock) { disposalTail }
                disposal?.join()
                requireCurrent(epoch)
                val resource = create(model)
                synchronized(lock) {
                    if (closed || epoch != generation) {
                        retireLocked(Slot(identity, resource))
                        throw CancellationException("model preparation superseded")
                    }
                    val slot = Slot(identity, resource)
                    current = slot
                    Prepared(model, slot)
                }
            } catch (failure: Throwable) {
                synchronized(lock) {
                    if (epoch == generation) { retireLocked(current); current = null }
                }
                throw failure
            }
        }
        pending = next
        pendingGeneration = epoch
        next.invokeOnCompletion { synchronized(lock) { if (pending === next) pending = null } }
        next.start()
        next
    }

    fun acquire(prepared: Prepared<M, R>): Lease<M, R> = synchronized(lock) {
        val slot = prepared.slot
        check(!closed && current === slot && slot.valid) { "model preparation is no longer current" }
        check(activeLease == null) { "model already has an active capture owner" }
        activeLease = slot
        leaseReturned = CompletableDeferred()
        slot.leased = true
        Lease(prepared.model, slot.value) {
            synchronized(lock) {
                slot.leased = false
                if (!slot.valid || closed) retireLocked(slot)
                if (activeLease === slot) {
                    activeLease = null
                    leaseReturned?.complete(Unit)
                    leaseReturned = null
                }
            }
        }
    }

    fun invalidate() = synchronized(lock) {
        generation++
        retireLocked(current)
        current = null
    }

    fun invalidateIfCurrent(prepared: Prepared<M, R>) = synchronized(lock) {
        if (current === prepared.slot) invalidate()
    }

    override fun close() = synchronized(lock) {
        if (!closed) { closed = true; invalidate() }
    }

    private fun requireCurrent(epoch: Long) = synchronized(lock) {
        if (closed || epoch != generation) throw CancellationException("model preparation superseded")
    }

    private fun retireLocked(slot: Slot<R>?) {
        if (slot == null) return
        slot.valid = false
        if (!slot.leased && !slot.disposalScheduled) {
            slot.disposalScheduled = true
            val previous = disposalTail
            disposalTail = scope.launch { previous?.join(); dispose(slot.value) }
        }
    }
}
