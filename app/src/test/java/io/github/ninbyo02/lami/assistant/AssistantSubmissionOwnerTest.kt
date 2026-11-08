package io.github.ninbyo02.lami.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantSubmissionOwnerTest {
    private val owner = AssistantSubmissionOwner()
    private val text = "今日の天気"
    private fun result(id: Long, automatic: Boolean = true) {
        assertTrue(owner.offer(id, text, automatic))
        assertTrue(owner.consume(id, text))
    }
    @Test fun firstAndRepeatedInvocationsEachSendNewSpeechOnce() {
        repeat(3) {
            val id = owner.begin(); result(id)
            assertTrue(owner.claim(id, text, true))
            assertFalse(owner.claim(id, text, true))
        }
    }
    @Test fun settingIsTakenForEachNewInvocationNotStickyFromLastTime() {
        val off = owner.begin(); result(off, false)
        assertFalse(owner.claim(off, text, false))
        val on = owner.begin(); result(on, true)
        assertTrue(owner.claim(on, text, true))
        val offAgain = owner.begin(); result(offAgain, false)
        assertFalse(owner.claim(offAgain, text, true))
    }
    @Test fun buttonAloneCannotResendPreviousText() {
        val previous = owner.begin(); result(previous)
        assertTrue(owner.claim(previous, text, true))
        val next = owner.begin()
        assertFalse(owner.claim(next, text, true))
    }
    @Test fun rerequestWhileListeningRejectsOldResult() {
        val old = owner.begin(); val fresh = owner.begin()
        assertFalse(owner.offer(old, text, true))
        result(fresh); assertTrue(owner.claim(fresh, text, true))
    }
    @Test fun rerequestWhileResponseOrUiIsBusyInvalidatesPendingSend() {
        val old = owner.begin(); result(old)
        val fresh = owner.begin()
        assertFalse(owner.claim(old, text, true))
        result(fresh); assertTrue(owner.claim(fresh, text, true))
    }
    @Test fun staleBufferedTranscriptCannotReplaceTheNewComposer() {
        val old = owner.begin(); assertTrue(owner.offer(old, text, true))
        owner.begin()
        assertFalse(owner.consume(old, text))
    }
    @Test fun duplicatedResultAndConsumerAreRejected() {
        val id = owner.begin(); result(id)
        assertFalse(owner.offer(id, text, true))
        assertFalse(owner.consume(id, text))
    }
    @Test fun noSpeechOrFailureHasNothingToSend() {
        val id = owner.begin()
        assertFalse(owner.offer(id, "  ", true))
        assertFalse(owner.claim(id, text, true))
        owner.cancel(id)
        assertFalse(owner.offer(id, text, true))
    }
    @Test fun manualStopBeforeResultKeepsTextButSuppressesSend() {
        val id = owner.begin(); owner.stop(); result(id)
        assertFalse(owner.claim(id, text, true))
    }
    @Test fun manualStopAfterResultWinsDelayedSendRace() {
        val id = owner.begin(); result(id); owner.stop()
        assertFalse(owner.claim(id, text, true))
    }
    @Test fun lifecycleCancellationPreventsDelayedSendAndDoesNotCancelNextOwner() {
        val old = owner.begin(); result(old); owner.cancel(old)
        assertFalse(owner.claim(old, text, true))
        val fresh = owner.begin(); owner.cancel(old); result(fresh)
        assertTrue(owner.claim(fresh, text, true))
    }
    @Test fun settingsDisabledBeforeClaimSuppressesSend() {
        val id = owner.begin(); result(id)
        assertFalse(owner.claim(id, text, false))
    }
    @Test fun editedComposerOrMissingConsumerCannotSend() {
        val id = owner.begin(); assertTrue(owner.offer(id, text, true))
        assertFalse(owner.claim(id, text, true))
        assertTrue(owner.consume(id, text))
        assertFalse(owner.claim(id, "別の文", true))
    }
    @Test fun manualStopDoesNotPoisonNextInvocation() {
        owner.begin(); owner.stop()
        val fresh = owner.begin(); result(fresh)
        assertTrue(owner.claim(fresh, text, true))
    }
}
