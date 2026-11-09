package io.github.ninbyo02.lami.tts

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class LamiPcmResamplerTest {
    @Test fun `preserves duration dc level and finite boundaries`() {
        val result = LamiPcmResampler.resample(FloatArray(44_100) { .25f }, 44_100, 24_000)
        assertEquals(24_000, result.samples.size)
        assertTrue(result.samples.all { it.isFinite() && kotlin.math.abs(it - .25f) < .00001f })
        assertEquals(0, result.clippedSamples)
    }

    @Test fun `rejects above target nyquist while preserving speech band`() {
        fun rms(frequency: Int): Double {
            val input = FloatArray(44_100) { (0.5 * sin(2 * PI * frequency * it / 44_100)).toFloat() }
            val result = LamiPcmResampler.resample(input, 44_100, 24_000).samples
            return sqrt(result.sliceArray(200 until result.size - 200).map { it.toDouble() * it }.average())
        }
        val speech = rms(4_000)
        val rejected = listOf(12_100, 12_500, 18_000).map(::rms)
        assertEquals(0.5 / sqrt(2.0), speech, .003)
        assertTrue("Alias energy must be suppressed", rejected.all { it < speech / 100 })
    }

    @Test fun `same rate copies and invalid source is rejected`() {
        val input = floatArrayOf(.1f, -.2f)
        val output = LamiPcmResampler.resample(input, 24_000, 24_000).samples
        assertArrayEquals(input, output, 0f)
        assertNotSame(input, output)
        assertThrows(IllegalArgumentException::class.java) {
            LamiPcmResampler.resample(floatArrayOf(Float.NaN), 44_100, 24_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiPcmResampler.resample(input, 0, 24_000)
        }
    }
}
