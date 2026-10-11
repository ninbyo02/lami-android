package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVoiceEventsTest {
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
