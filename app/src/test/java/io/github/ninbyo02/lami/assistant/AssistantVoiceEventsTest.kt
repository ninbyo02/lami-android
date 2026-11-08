package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVoiceEventsTest {
    @Test
    fun wakeCandidateIsDebugOnlyAndSurvivesTheNextCaptureStart() {
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        AssistantVoiceEvents.showWakeCandidateForDebug("ラム")
        val expected = if (io.github.ninbyo02.lami.BuildConfig.DEBUG) "ラム" else null
        assertEquals(expected, AssistantVoiceEvents.wakeCandidate.value)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        assertEquals(expected, AssistantVoiceEvents.wakeCandidate.value)
        assertEquals(AssistantVoicePhase.WAITING_FOR_WAKE_WORD, AssistantVoiceEvents.phase.value)
    }

    @Test
    fun candidatePreviewCannotLeakIntoCommandCaptureOrAnotherSession() {
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        AssistantVoiceEvents.showWakeCandidateForDebug("ラム")
        AssistantVoiceEvents.prepare()
        assertEquals(null, AssistantVoiceEvents.wakeCandidate.value)
        AssistantVoiceEvents.showWakeCandidateForDebug("unexpected")
        assertEquals(null, AssistantVoiceEvents.wakeCandidate.value)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        AssistantVoiceEvents.showWakeCandidateForDebug("ラム")
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Failure("test stop"))
        assertEquals(null, AssistantVoiceEvents.wakeCandidate.value)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        AssistantVoiceEvents.showWakeCandidateForDebug("ラム")
        AssistantVoiceEvents.reset()
        assertEquals(null, AssistantVoiceEvents.wakeCandidate.value)
    }

    @Test
    fun wakeWaitingIsDistinctFromCommandListeningAndCanBeReset() {
        AssistantVoiceEvents.publish(AssistantVoiceEvent.WaitingForWakeWord)
        assertEquals(AssistantVoicePhase.WAITING_FOR_WAKE_WORD, AssistantVoiceEvents.phase.value)
        AssistantVoiceEvents.reset()
        assertEquals(AssistantVoicePhase.IDLE, AssistantVoiceEvents.phase.value)
    }

    @After
    fun reset() {
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Failure("test reset"))
    }

    @Test
    fun preparationDoesNotIndicateMicrophoneReadiness() {
        AssistantVoiceEvents.prepare()
        assertEquals(AssistantVoicePhase.PREPARING, AssistantVoiceEvents.phase.value)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Listening)
        assertEquals(AssistantVoicePhase.LISTENING, AssistantVoiceEvents.phase.value)
    }

    @Test
    fun replacementComposerReceivesCurrentListeningState() = runBlocking {
        AssistantVoiceEvents.prepare()
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Listening)
        assertEquals(AssistantVoicePhase.LISTENING, AssistantVoiceEvents.phase.first())
    }

    @Test
    fun recognitionResultClearsListeningState() {
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Listening)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Transcript("test", autoSubmit = true))
        assertEquals(AssistantVoicePhase.IDLE, AssistantVoiceEvents.phase.value)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Listening)
        AssistantVoiceEvents.publish(AssistantVoiceEvent.Failure("test"))
        assertEquals(AssistantVoicePhase.IDLE, AssistantVoiceEvents.phase.value)
    }
}
