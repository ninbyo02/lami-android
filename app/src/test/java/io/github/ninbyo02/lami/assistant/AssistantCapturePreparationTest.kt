package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantCapturePreparationTest {
    @Test fun capturePreparationWaitsForPlaybackAndManualCaptureCleanup() = runTest {
        val released = CompletableDeferred<Unit>()
        val registration = AssistantVoiceEvents.registerCapturePreparation { released.await() }
        try {
            val waiting = async { AssistantVoiceEvents.prepareCapture() }; runCurrent()
            assertFalse(waiting.isCompleted); released.complete(Unit); runCurrent(); waiting.await()
        } finally { registration.close() }
    }
    @Test fun removingOldComposerDoesNotRemoveItsReplacement() = runTest {
        var called = false
        val old = AssistantVoiceEvents.registerCapturePreparation { error("old handler") }
        val fresh = AssistantVoiceEvents.registerCapturePreparation { called = true }
        old.close()
        try { AssistantVoiceEvents.prepareCapture(); assertTrue(called) } finally { fresh.close() }
    }
    @Test fun missingOrReplacedComposerAbortsBeforeCapture() = runTest {
        assertTrue(runCatching { AssistantVoiceEvents.prepareCapture() }.isFailure)
        val released = CompletableDeferred<Unit>()
        val old = AssistantVoiceEvents.registerCapturePreparation { released.await() }
        val waiting = async { runCatching { AssistantVoiceEvents.prepareCapture() } }; runCurrent()
        val fresh = AssistantVoiceEvents.registerCapturePreparation { }
        try { released.complete(Unit); runCurrent(); assertTrue(waiting.await().isFailure) }
        finally { old.close(); fresh.close() }
    }
}
