package io.github.ninbyo02.lami.assistant

import io.github.ninbyo02.lami.asr.foundation.audio.*
import org.junit.Assert.*
import org.junit.Test

class AssistantCaptureRequestGateTest {
    private val frame = AudioCaptureEvent.Level(AudioLevelSample(-120f, -120f))
    private fun ready(gate: AssistantCaptureRequestGate, id: Long) {
        gate.observe(id, AudioCaptureEvent.Started)
        gate.observe(id, AudioCaptureEvent.InputResumed)
        gate.observe(id, frame)
    }
    @Test fun launchOrModelPreparationAloneNeverPulses() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin()
        assertFalse(gate.claimPulse(id, true))
    }
    @Test fun nativeStartAndFramesWithoutVerifiedActiveInputNeverPulse() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin()
        gate.observe(id, AudioCaptureEvent.Started); gate.observe(id, frame)
        assertFalse(gate.claimPulse(id, true))
    }
    @Test fun activeConfigurationAloneNeedsAReceivedFrame() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin()
        gate.observe(id, AudioCaptureEvent.Started); gate.observe(id, AudioCaptureEvent.InputResumed)
        assertFalse(gate.claimPulse(id, true)); gate.observe(id, frame)
        assertTrue(gate.claimPulse(id, true))
    }
    @Test fun silentButValidMicrophoneFramesAreReadyToo() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); ready(gate, id)
        assertTrue(gate.claimPulse(id, true))
    }
    @Test fun duplicateCallbacksAndFocusChangesCannotDoublePulse() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); ready(gate, id)
        assertTrue(gate.claimPulse(id, true)); ready(gate, id)
        assertFalse(gate.claimPulse(id, true)); assertFalse(gate.claimPulse(id, false))
    }
    @Test fun focusArrivalCanClaimAnAlreadyReadyLiveInputOnce() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); ready(gate, id)
        assertFalse(gate.claimPulse(id, false)); assertTrue(gate.claimPulse(id, true)); assertFalse(gate.claimPulse(id, true))
    }
    @Test fun interruptionRequiresNewActiveStateAndNewFrame() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); ready(gate, id)
        gate.observe(id, AudioCaptureEvent.InputInterrupted(AudioInputInterruptionReason.CLIENT_SILENCED))
        assertFalse(gate.claimPulse(id, true)); gate.observe(id, frame); assertFalse(gate.claimPulse(id, true))
        gate.observe(id, AudioCaptureEvent.InputResumed); assertFalse(gate.claimPulse(id, true))
        gate.observe(id, frame); assertTrue(gate.claimPulse(id, true))
    }
    @Test fun cancelledDeniedOrFailedRequestIgnoresLateCallbacks() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); gate.finish(id); ready(gate, id)
        assertFalse(gate.claimPulse(id, true)); assertFalse(gate.isCurrent(id))
    }
    @Test fun repeatedInvocationReplacesTheOldReadinessAndPulsesForNewRequest() {
        val gate = AssistantCaptureRequestGate(); val old = gate.begin(); ready(gate, old)
        val fresh = gate.begin(); assertFalse(gate.claimPulse(old, true)); assertFalse(gate.claimPulse(fresh, true))
        gate.finish(old); ready(gate, old); assertTrue(gate.isCurrent(fresh))
        ready(gate, fresh); assertTrue(gate.claimPulse(fresh, true))
    }
    @Test fun lateStartAfterStoppedCaptureCannotResurrectReadiness() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin()
        gate.observe(id, AudioCaptureEvent.Stopped); ready(gate, id)
        assertFalse(gate.claimPulse(id, true)); assertTrue(gate.isCurrent(id))
    }
    @Test fun inactiveCapturePreservesResultOwnershipButNeverPulses() {
        val gate = AssistantCaptureRequestGate(); val id = gate.begin(); ready(gate, id)
        gate.observe(id, AudioCaptureEvent.Inactive)
        assertTrue(gate.isCurrent(id)); assertFalse(gate.isCaptureLive(id)); assertFalse(gate.claimPulse(id, true))
    }
    @Test fun newRequestAfterFinishedCaptureHasNoLeakedState() {
        val gate = AssistantCaptureRequestGate(); val old = gate.begin(); ready(gate, old); gate.claimPulse(old, true); gate.finish(old)
        val fresh = gate.begin(); assertFalse(gate.claimPulse(fresh, true)); ready(gate, fresh); assertTrue(gate.claimPulse(fresh, true))
    }
}
