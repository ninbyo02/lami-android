package io.github.ninbyo02.lami.asr.foundation.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GainAudioCaptureInputTest {
    @Test
    fun `read applies bounded gain only to returned samples`() {
        val delegate = FakeInput(shortArrayOf(-10_000, -100, 100, 10_000))
        val input = GainAudioCaptureInput(delegate, gain = 4.0)
        val target = ShortArray(6) { 7 }

        val count = input.read(target, 1, 4)

        assertEquals(4, count)
        assertArrayEquals(
            shortArrayOf(7, Short.MIN_VALUE, -400, 400, Short.MAX_VALUE, 7),
            target,
        )
    }

    private class FakeInput(private val samples: ShortArray) : AudioCaptureInput {
        override val isInitialized = true
        override fun routedDeviceId(): Int = 1
        override fun start() = Unit
        override fun read(target: ShortArray, offset: Int, length: Int): Int {
            val count = minOf(samples.size, length)
            samples.copyInto(target, destinationOffset = offset, endIndex = count)
            return count
        }
        override fun stop() = Unit
        override fun release() = Unit
    }
}
