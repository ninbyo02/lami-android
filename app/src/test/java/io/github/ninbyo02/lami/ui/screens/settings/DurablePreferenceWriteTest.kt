package io.github.ninbyo02.lami.ui.screens.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DurablePreferenceWriteTest {
    @Test fun leavingScreenDoesNotCancelAnExplicitOffWrite() = runTest {
        val screen = CoroutineScope(coroutineContext + Job())
        val release = CompletableDeferred<Unit>()
        var stored = true
        var complete = false
        val write = screen.writePreferenceDurably(
            { release.await(); stored = false }, { fail("write failed") }, { complete = true })
        screen.cancel()
        release.complete(Unit)
        write.join()
        assertFalse(stored)
        assertTrue(complete)
    }

    @Test fun failedWriteIsReportedAndDoesNotPretendToPersist() = runTest {
        var stored = true
        var failed = false
        var complete = false
        writePreferenceDurably({ throw java.io.IOException("test") },
            { failed = true }, { complete = true }).join()
        assertTrue(stored)
        assertTrue(failed)
        assertTrue(complete)
    }

    @Test fun writeStartsBeforeNavigationCanDisposeTheScreen() = runTest {
        val screen = CoroutineScope(coroutineContext + Job())
        var started = false
        val write = screen.writePreferenceDurably({ started = true }, {}, {})
        assertTrue(started)
        screen.cancel()
        write.join()
    }
}
