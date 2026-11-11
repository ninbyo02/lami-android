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

    @Test fun stoppingSpeechKeepsInferencePartialsVisibleButCannotRestartAutomaticSpeech() {
        val speech = ResponseSpeechSession().apply { begin() }
        val gate = PendingStreamingPartialGate()
        val requestEpoch = 10L
        var overlay = "最初の説明。"
        assertEquals(overlay, speech.take(overlay, false))
        speech.stop() // The message's playback Stop does not cancel the inference request.
        val resumedText = "最初の説明。生成再開後の説明。"
        if (gate.acceptsUpdate(requestEpoch, 10L, 3, 3, stopRequested = false)) overlay = resumedText

        assertEquals(resumedText, overlay)
        assertNull(speech.take(resumedText, false))
        assertNull(speech.take(resumedText, true))
    }

    @Test fun delayedPartialRechecksInferenceOwnerAfterDispatchInsteadOfSpeechOwner() {
        val gate = PendingStreamingPartialGate()
        var currentEpoch = 20L
        var stopRequested = false
        var overlay: String? = null
        val posted = {
            if (gate.acceptsUpdate(20L, currentEpoch, 3, 3, stopRequested)) overlay = "古いpartial"
        }
        assertTrue(gate.acceptsUpdate(20L, currentEpoch, 3, 3, stopRequested))
        stopRequested = true
        posted()
        assertNull(overlay)

        // A fresh request clears the stop flag but cannot revive the old request's callback.
        currentEpoch = 21L
        stopRequested = false
        posted()
        assertNull(overlay)
        assertTrue(PendingStreamingPartialGate().acceptsUpdate(21L, currentEpoch, 3, 3, stopRequested))
    }

    @Test fun chatChangeImageCancellationAndProviderCompletionEachRejectOldPartials() {
        val gate = PendingStreamingPartialGate()
        assertFalse(gate.acceptsUpdate(30L, 30L, 3, 4, stopRequested = false))
        assertFalse(gate.acceptsUpdate(30L, 30L, 3, 3, stopRequested = false, imageCallbacksAllowed = false))
        assertTrue(gate.acceptsUpdate(30L, 30L, 3, 3, stopRequested = false))
        gate.close()
        assertFalse(gate.acceptsUpdate(30L, 30L, 3, 3, stopRequested = false))
    }

    @Test fun returningToTheSameChatCannotReviveItsPreviousRequestEpoch() {
        val old = PendingStreamingPartialGate()
        val originalEpoch = 40L
        var currentEpoch = originalEpoch
        var currentChatId = 3
        assertTrue(old.acceptsUpdate(originalEpoch, currentEpoch, 3, currentChatId, stopRequested = false))

        currentChatId = 4
        currentEpoch += 1
        assertFalse(old.acceptsUpdate(originalEpoch, currentEpoch, 3, currentChatId, stopRequested = false))
        currentChatId = 3
        currentEpoch += 1
        assertFalse(old.acceptsUpdate(originalEpoch, currentEpoch, 3, currentChatId, stopRequested = false))
        val next = PendingStreamingPartialGate()
        assertTrue(next.acceptsUpdate(currentEpoch, currentEpoch, 3, currentChatId, stopRequested = false))
    }
}
