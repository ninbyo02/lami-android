package io.github.ninbyo02.lami.asr.foundation.wake

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordSessionTest {
    @Test
    fun silenceAndNonmatchingSpeechAreIgnoredUntilExactDetection() = runBlocking {
        val inputs = listOf(null, "関係のない話", "ラミネート", "ラミ。").iterator()
        var reads = 0
        assertTrue(WakeWordSession(WakeWordMatcher("ラミ")).awaitDetection {
            reads++
            inputs.next()
        })
        assertEquals(4, reads)
    }

    @Test
    fun utteranceLimitDoesNotAccidentallyTrigger() = runBlocking {
        var reads = 0
        assertFalse(WakeWordSession(WakeWordMatcher("ラミ"), maximumUtterances = 3).awaitDetection {
            reads++
            "こんにちは"
        })
        assertEquals(3, reads)
    }

    @Test
    fun timeoutReleasesCooperativeCaptureAndDoesNotDetect() = runBlocking {
        var released = false
        assertFalse(WakeWordSession(WakeWordMatcher("ラミ"), timeoutMillis = 50).awaitDetection {
            try {
                delay(1_000)
                "ラミ"
            } finally {
                released = true
            }
        })
        assertTrue(released)
    }

    @Test
    fun explicitCancellationCannotBecomeDetectionOrRestart() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var reads = 0
        var detected = false
        var released = false
        val job = launch {
            detected = WakeWordSession(WakeWordMatcher("ラミ")).awaitDetection {
                reads++
                started.complete(Unit)
                try {
                    delay(10_000)
                    "ラミ"
                } finally {
                    released = true
                }
            }
        }
        started.await()
        job.cancelAndJoin()
        assertFalse(detected)
        assertTrue(released)
        assertEquals(1, reads)
    }

    @Test
    fun captureFailureStopsWithoutRetrying() = runBlocking {
        var reads = 0
        var failed = false
        try {
            WakeWordSession(WakeWordMatcher("ラミ")).awaitDetection {
                reads++
                error("input disconnected")
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(1, reads)
    }
}
