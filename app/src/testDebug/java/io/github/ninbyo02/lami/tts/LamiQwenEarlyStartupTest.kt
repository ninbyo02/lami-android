package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiQwenEarlyStartupTest {
    @Test fun earlyPolicyReleases640msWithoutCodecEosEvenWhenGenerationIsSlow() = runTest {
        val pace = LamiPcmGenerationPace().apply {
            prefixProduced(3_840, 0)
            prefixProduced(15_360, 5_000)
        }
        val budget = LamiQwenStartupBudget { true }
        val config = checkNotNull(LamiVoiceStreamingProbe.startupPrebuffer(true, false, pace, budget))
        assertEquals(2_400, pace.minimumBufferMs)
        assertEquals(640, config.minimumMs)
        assertEquals(640, config.maximumMs)
        assertNull(config.startupReady)
        assertFalse(budget.canStart(15_360, 5_000))
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(FloatArray(3_840) { 0.1f })
        chunks.send(FloatArray(11_520) { 0.1f })
        val collected = async { LamiPcmPrebuffer.collect(chunks, config, { 5_000L }, {}) }
        runCurrent()
        assertTrue(collected.isCompleted)
        assertEquals(15_360, collected.await().size)
        chunks.cancel()
    }

    @Test fun balancedAndFullParityDiagnosticsRetainTheirPreviousStartupPolicies() {
        val pace = LamiPcmGenerationPace().apply {
            prefixProduced(3_840, 0)
            prefixProduced(15_360, 5_000)
        }
        val budget = LamiQwenStartupBudget { true }
        val balanced = checkNotNull(LamiVoiceStreamingProbe.startupPrebuffer(false, false, pace, budget))
        assertEquals(2_400, balanced.sourceMinimumMs())
        assertEquals(false, balanced.startupReady?.invoke(57_600, 5_000))
        assertNull(LamiVoiceStreamingProbe.startupPrebuffer(true, true, pace, budget))
    }
}
