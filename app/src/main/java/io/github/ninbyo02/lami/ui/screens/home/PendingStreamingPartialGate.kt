package io.github.ninbyo02.lami.ui.screens.home

import java.util.concurrent.atomic.AtomicBoolean

/** One provider invocation. A posted UI callback must recheck after dispatch. */
internal class PendingStreamingPartialGate {
    private val open = AtomicBoolean(true)
    val acceptsUpdates: Boolean get() = open.get()

    fun acceptsUpdate(
        requestEpoch: Long,
        currentEpoch: Long,
        requestChatId: Int,
        currentChatId: Int?,
        stopRequested: Boolean,
        imageCallbacksAllowed: Boolean = true,
    ): Boolean = acceptsUpdates && requestEpoch == currentEpoch &&
        requestChatId == currentChatId && !stopRequested && imageCallbacksAllowed

    fun close() { open.set(false) }
}
