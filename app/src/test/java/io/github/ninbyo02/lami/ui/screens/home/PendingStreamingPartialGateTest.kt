package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class PendingStreamingPartialGateTest {
    @Test fun delayedPartialCannotRecreateOverlayAfterFinalization() {
        val gate = PendingStreamingPartialGate()
        var overlay: String? = null
        val posted = { if (gate.acceptsUpdates) overlay = "old transient reply" }
        assertTrue(gate.acceptsUpdates)
        gate.close()
        overlay = null // DB-backed reply has replaced the transient display.
        posted()
        assertNull(overlay)
    }
    @Test fun completedInvocationCannotOverwriteNextInvocation() {
        val old = PendingStreamingPartialGate()
        old.close()
        val next = PendingStreamingPartialGate()
        assertFalse(old.acceptsUpdates)
        assertTrue(next.acceptsUpdates)
        old.close()
        assertTrue(next.acceptsUpdates)
    }
}
