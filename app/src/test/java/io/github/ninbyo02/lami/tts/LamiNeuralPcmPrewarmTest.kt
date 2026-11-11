package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiNeuralPcmPrewarmTest {
    private val pcm = floatArrayOf(0.1f, -0.2f)

    private fun TestScope.engineScope() = CoroutineScope(
        backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])
    )

    @Test fun `owned native load overlaps text generation and reduces first PCM wait even after FLUSH`() = runTest {
        class NativeLoadSession : LamiResidentPcmSession {
            var loaded = false
            var loads = 0
            override suspend fun prewarm() {
                if (loaded) return
                loads++
                // ORT loading cannot be interrupted by coroutine cancellation.
                withContext(NonCancellable) { delay(1_000); loaded = true }
            }
            override suspend fun synthesize(text: String): FloatArray {
                if (!loaded) prewarm()
                return pcm
            }
            override suspend fun close() = Unit
        }
        val warmSession = NativeLoadSession()
        var warmStartedAt: Long? = null
        val warmBusy = mutableListOf<Boolean>()
        val warm = LamiNeuralPcmVoiceEngine(engineScope(), warmSession, { _, started -> started() })
        warm.setOnPlaybackStateChanged(warmBusy::add)
        warm.setOnAudioStarted { warmStartedAt = currentTime }
        warm.prewarm()
        runCurrent()
        assertFalse(warm.isSpeaking.value)
        assertTrue(warmBusy.isEmpty())
        advanceTimeBy(750)
        val warmTextReady = currentTime
        warm.speak("こんにちは。")
        runCurrent()
        assertNull(warmStartedAt)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(250L, checkNotNull(warmStartedAt) - warmTextReady)
        assertEquals(1, warmSession.loads)
        assertFalse(warm.isSpeaking.value)

        val coldSession = NativeLoadSession()
        var coldStartedAt: Long? = null
        val cold = LamiNeuralPcmVoiceEngine(engineScope(), coldSession, { _, started -> started() })
        cold.setOnAudioStarted { coldStartedAt = currentTime }
        val coldTextReady = currentTime
        cold.speak("こんにちは。")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, checkNotNull(coldStartedAt) - coldTextReady)
        warm.shutdown()
        cold.shutdown()
        runCurrent()
    }

    @Test fun `queued speech awaits useful warmup and successful warmup stays memoized after stop`() = runTest {
        val loaded = CompletableDeferred<Unit>()
        var warmCalls = 0
        var warmDone = false
        var cancellations = 0
        var played = 0
        val engine = LamiNeuralPcmVoiceEngine(engineScope(), object : LamiResidentPcmSession {
            override suspend fun prewarm() { warmCalls++; loaded.await(); warmDone = true }
            override suspend fun synthesize(text: String): FloatArray {
                assertTrue("Synthesis must await the owned load", warmDone)
                return pcm
            }
            override fun cancel() { cancellations++ }
            override suspend fun close() = Unit
        }, { _, _ -> played++ })
        engine.prewarm()
        engine.prewarm()
        runCurrent()
        assertEquals(1, warmCalls)
        assertFalse(engine.isSpeaking.value)
        engine.speakQueued("最初の文です。")
        runCurrent()
        assertTrue(engine.isSpeaking.value)
        assertEquals(0, played)
        assertEquals(0, cancellations)
        loaded.complete(Unit)
        runCurrent()
        assertEquals(1, played)
        assertFalse(engine.isSpeaking.value)
        engine.stop()
        engine.prewarm()
        runCurrent()
        assertEquals("A completed resident load survives stop", 1, warmCalls)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `microphone barrier joins cancelled native load and replacement warmup before retry`() = runTest {
        val nativeCleanup = CompletableDeferred<Unit>()
        var nativeReleased = false
        var warmCalls = 0
        val engine = LamiNeuralPcmVoiceEngine(engineScope(), object : LamiResidentPcmSession {
            override suspend fun prewarm() {
                if (++warmCalls == 1) try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { nativeCleanup.await(); nativeReleased = true }
                }
            }
            override suspend fun synthesize(text: String) = pcm
            override suspend fun close() { assertTrue(nativeReleased) }
        }, { _, _ -> fail("Prewarm must never start playback") })
        engine.prewarm()
        runCurrent()
        engine.stop()
        engine.prewarm()
        runCurrent()
        assertEquals("A replacement load waits for previous native ownership", 1, warmCalls)
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertFalse(engine.isSpeaking.value)
        nativeCleanup.complete(Unit)
        barrier.await()
        assertTrue(nativeReleased)
        engine.prewarm()
        runCurrent()
        assertEquals("Cancelled warmups can be retried after cleanup", 2, warmCalls)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `repeated FLUSH cannot synthesize stale text or overlap cancelled native loading`() = runTest {
        val nativeCleanup = CompletableDeferred<Unit>()
        var nativeReleased = false
        val synthesized = mutableListOf<String>()
        var played = 0
        val engine = LamiNeuralPcmVoiceEngine(engineScope(), object : LamiResidentPcmSession {
            override suspend fun prewarm() {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { nativeCleanup.await(); nativeReleased = true }
                }
            }
            override suspend fun synthesize(text: String): FloatArray {
                assertTrue("New JNI work must follow cancelled native load cleanup", nativeReleased)
                synthesized += text
                return pcm
            }
            override suspend fun close() = Unit
        }, { _, _ -> played++ })
        engine.prewarm()
        runCurrent()
        engine.speak("古い文章です。")
        runCurrent()
        engine.speak("置き換えた文章です。")
        runCurrent()
        assertTrue(synthesized.isEmpty())
        nativeCleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("置き換えた文章です。"), synthesized)
        assertEquals(1, played)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `shutdown joins optional native load before releasing resident session`() = runTest {
        val nativeCleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var warmCalls = 0
        val engine = LamiNeuralPcmVoiceEngine(engineScope(), object : LamiResidentPcmSession {
            override suspend fun prewarm() {
                warmCalls++
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { nativeCleanup.await(); events += "native_released" }
                }
            }
            override suspend fun synthesize(text: String) = pcm
            override suspend fun close() { events += "session_closed" }
        }, { _, _ -> fail("Prewarm must never start playback") })
        engine.prewarm()
        runCurrent()
        engine.shutdown()
        engine.prewarm()
        runCurrent()
        assertTrue(events.isEmpty())
        assertEquals(1, warmCalls)
        nativeCleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("native_released", "session_closed"), events)
    }

    @Test fun `optional warmup failure is silent and actual speech can retry loading`() = runTest {
        var warmCalls = 0
        var loaded = false
        var played = 0
        val errors = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val events = mutableListOf<String>()
        val engine = LamiNeuralPcmVoiceEngine(engineScope(), object : LamiResidentPcmSession {
            override suspend fun prewarm() {
                if (++warmCalls == 1) error("Optional load failed")
                loaded = true
            }
            override suspend fun synthesize(text: String): FloatArray {
                if (!loaded) prewarm()
                return pcm
            }
            override suspend fun close() = Unit
        }, { _, _ -> played++ }, onEvent = { _, event -> events += event })
        engine.setOnError(errors::add)
        engine.setOnPlaybackStateChanged(busy::add)
        engine.prewarm()
        runCurrent()
        assertEquals(1, warmCalls)
        assertTrue(errors.isEmpty())
        assertTrue(busy.isEmpty())
        assertTrue(events.any { it.startsWith("failure_prewarm_") })
        engine.speak("読み上げを再試行します。")
        runCurrent()
        assertEquals(2, warmCalls)
        assertTrue(loaded)
        assertEquals(1, played)
        assertTrue(errors.isEmpty())
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }
}
