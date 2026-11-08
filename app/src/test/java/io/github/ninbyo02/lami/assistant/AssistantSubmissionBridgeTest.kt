package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Production event bridge, without recognizer, microphone, UI dispatch or network. */
class AssistantSubmissionBridgeTest {
    @Test fun successiveInvocationsDeliverOnlyFreshUtterancesAndEachClaimsOnce() = runTest {
        repeat(3) {
            val id = AssistantVoiceEvents.beginInvocation()
            AssistantVoiceEvents.prepare()
            AssistantVoiceEvents.publishTranscript(id, "天気を教えて", true)
            val result = AssistantVoiceEvents.events.filterIsInstance<AssistantVoiceEvent.Transcript>()
                .first { it.invocationId == id }
            assertTrue(AssistantVoiceEvents.consumeTranscript(result))
            assertTrue(AssistantVoiceEvents.claimSubmission(id, result.text, true))
            assertFalse(AssistantVoiceEvents.claimSubmission(id, result.text, true))
            AssistantVoiceEvents.cancelInvocation(id)
        }
    }
    @Test fun stopStillDeliversManualTextWithoutAutomaticSending() = runTest {
        val id = AssistantVoiceEvents.beginInvocation()
        AssistantVoiceEvents.requestStopRecognition()
        AssistantVoiceEvents.publishTranscript(id, "次の予定", true)
        val result = AssistantVoiceEvents.events.filterIsInstance<AssistantVoiceEvent.Transcript>()
            .first { it.invocationId == id }
        assertFalse(result.autoSubmit)
        assertTrue(AssistantVoiceEvents.consumeTranscript(result))
        assertFalse(AssistantVoiceEvents.claimSubmission(id, result.text, true))
        AssistantVoiceEvents.cancelInvocation(id)
    }
    @Test fun bufferedTranscriptFromOldInvocationIsRejectedByTheRealConsumerGate() = runTest {
        val old = AssistantVoiceEvents.beginInvocation()
        AssistantVoiceEvents.publishTranscript(old, "古い入力", true)
        val fresh = AssistantVoiceEvents.beginInvocation()
        val buffered = AssistantVoiceEvents.events.filterIsInstance<AssistantVoiceEvent.Transcript>()
            .first { it.invocationId == old }
        assertFalse(AssistantVoiceEvents.consumeTranscript(buffered))
        assertFalse(AssistantVoiceEvents.claimSubmission(old, buffered.text, true))
        assertFalse(AssistantVoiceEvents.claimSubmission(fresh, buffered.text, true))
        AssistantVoiceEvents.cancelInvocation(fresh)
    }
}
