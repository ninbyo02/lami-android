package io.github.ninbyo02.lami.asr

import io.github.ninbyo02.lami.asr.foundation.vad.FinalizeReason
import org.junit.Assert.*
import org.junit.Test

class VoiceInputSubmissionGateTest {
    @Test fun defaultOffKeepsTextWithoutSend() {
        val gate = VoiceInputSubmissionGate(false)
        assertFalse(gate.complete("hello", FinalizeReason.END_SILENCE))
        assertFalse(gate.claim("hello"))
    }
    @Test fun timeoutCanBeClaimedExactlyOnce() {
        val gate = VoiceInputSubmissionGate(true)
        assertTrue(gate.complete("hello", FinalizeReason.END_SILENCE))
        assertFalse(gate.complete("hello", FinalizeReason.END_SILENCE))
        assertTrue(gate.claim("hello")); assertFalse(gate.claim("hello"))
    }
    @Test fun stopBeforeTimeoutNeverSends() {
        val gate = VoiceInputSubmissionGate(true); gate.stop()
        assertTrue(gate.wasStopped())
        assertFalse(gate.complete("hello", FinalizeReason.END_SILENCE))
        assertFalse(gate.claim("hello"))
    }
    @Test fun stopAfterTimeoutBeforeQueuedClaimWins() {
        val gate = VoiceInputSubmissionGate(true)
        gate.complete("hello", FinalizeReason.END_SILENCE); gate.stop()
        assertFalse(gate.claim("hello"))
    }
    @Test fun lifecycleAndEditsInvalidateSubmission() {
        val gate = VoiceInputSubmissionGate(true)
        gate.complete("hello", FinalizeReason.END_SILENCE)
        assertFalse(gate.claim("edited")); gate.cancel()
        assertFalse(gate.claim("hello"))
    }
    @Test fun noSpeechExplicitStopAndHardLimitNeverSend() {
        for (reason in listOf(null, FinalizeReason.USER_STOP, FinalizeReason.HARD_LIMIT)) {
            val gate = VoiceInputSubmissionGate(true)
            assertFalse(gate.complete("hello", reason)); assertFalse(gate.claim("hello"))
        }
        val gate = VoiceInputSubmissionGate(true)
        assertFalse(gate.complete("   ", FinalizeReason.END_SILENCE))
    }
}
