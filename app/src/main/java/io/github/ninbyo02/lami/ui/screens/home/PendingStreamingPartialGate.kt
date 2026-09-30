package io.github.ninbyo02.lami.ui.screens.home

import java.util.concurrent.atomic.AtomicBoolean

/** One provider invocation. A posted UI callback must recheck after dispatch. */
internal class PendingStreamingPartialGate {
    private val open = AtomicBoolean(true)
    val acceptsUpdates: Boolean get() = open.get()
    fun close() { open.set(false) }
}
