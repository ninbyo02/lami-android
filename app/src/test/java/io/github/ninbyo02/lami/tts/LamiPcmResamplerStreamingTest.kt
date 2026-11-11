package io.github.ninbyo02.lami.tts

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class LamiPcmResamplerStreamingTest {
    @Test fun `44100 streaming preserves global phase across arbitrary tiny chunks`() {
        val input = signal(44_117, 44_100)
        assertMatchesBatch(input, 44_100, 24_000, intArrayOf(1, 2, 7, 63, 3, 147, 64, 5))
    }

    @Test fun `48000 streaming and upsampling match batch across chunk boundaries`() {
        assertMatchesBatch(signal(8_193, 48_000), 48_000, 24_000, intArrayOf(1, 5, 128, 2, 31))
        assertMatchesBatch(signal(2_049, 16_000), 16_000, 24_000, intArrayOf(3, 1, 63, 127, 2))
    }

    @Test fun `finish preserves FIR tails and rounded lengths around phase boundaries`() {
        for (length in listOf(1, 2, 3, 63, 64, 65, 127, 128, 129, 146, 147, 148, 160, 319, 320, 321, 441)) {
            assertMatchesBatch(signal(length, 44_100), 44_100, 24_000, intArrayOf(1))
        }
    }

    @Test fun `identity rate emits independent copies immediately without a tail`() {
        val stream = LamiPcmResampler.Stream(24_000, 24_000)
        val first = floatArrayOf(0.1f, -0.2f, -0f)
        val emitted = stream.append(first)
        assertNotSame(first, emitted.samples)
        assertArrayEquals(first.map(Float::toRawBits).toIntArray(), emitted.samples.map(Float::toRawBits).toIntArray())
        first[0] = 1f
        assertEquals(0.1f, emitted.samples[0], 0f)
        assertTrue(stream.append(floatArrayOf()).samples.isEmpty())
        assertArrayEquals(floatArrayOf(0.3f), stream.append(floatArrayOf(0.3f)).samples, 0f)
        assertTrue(stream.finish().samples.isEmpty())
    }

    @Test fun `stream clipping count and waveform equal batch across a sharp transition`() {
        val input = FloatArray(2_048) { if (it < 1_024) -1f else 1f }
        val expected = LamiPcmResampler.resample(input, 44_100, 24_000)
        assertTrue("The test signal must exercise FIR overshoot clipping", expected.clippedSamples > 0)
        assertMatchesBatch(input, 44_100, 24_000, intArrayOf(1, 64, 2, 17, 255))
    }

    @Test fun `finite native overshoot reaches FIR output clipping with exact batch stream parity`() {
        val input = FloatArray(2_048) { when {
            it < 700 -> 1.2111009f
            it < 1_400 -> -1.3f
            else -> 0.2f
        } }
        val expected = LamiPcmResampler.resample(input, 44_100, 24_000)
        assertTrue(expected.clippedSamples > 0)
        assertTrue(expected.samples.all { it.isFinite() && it in -1f..1f })
        assertMatchesBatch(input, 44_100, 24_000, intArrayOf(1, 64, 2, 17, 255))
    }

    @Test fun `same-rate stream clips and counts finite overshoot without changing other samples`() {
        val input = floatArrayOf(-1.3f, -1f, -0f, 0.2f, 1f, 1.2111009f)
        val stream = LamiPcmResampler.Stream(24_000, 24_000)
        val first = stream.append(input.copyOfRange(0, 3))
        val second = stream.append(input.copyOfRange(3, input.size))
        assertEquals(2, first.clippedSamples + second.clippedSamples)
        assertArrayEquals(floatArrayOf(-1f, -1f, -0f, 0.2f, 1f, 1f), first.samples + second.samples, 0f)
        assertTrue(stream.finish().samples.isEmpty())
        assertMatchesBatch(input, 24_000, 24_000, intArrayOf(1, 2, 1))
    }

    @Test fun `empty stream finishes empty and finish is exact once`() {
        val empty = LamiPcmResampler.Stream(44_100, 24_000)
        assertTrue(empty.append(floatArrayOf()).samples.isEmpty())
        assertTrue(empty.finish().samples.isEmpty())
        assertThrows(IllegalStateException::class.java) { empty.finish() }
        assertThrows(IllegalStateException::class.java) { empty.append(floatArrayOf(0.1f)) }

        val short = LamiPcmResampler.Stream(44_100, 24_000)
        assertTrue(short.append(floatArrayOf(0.2f)).samples.isEmpty())
        assertArrayEquals(floatArrayOf(0.2f), short.finish().samples, 0f)
        assertThrows(IllegalStateException::class.java) { short.finish() }
    }

    @Test fun `invalid samples and rates are rejected without contaminating later valid PCM`() {
        for (rates in listOf(0 to 24_000, 44_100 to 0, 7_999 to 24_000, 44_100 to 192_001)) {
            assertThrows(IllegalArgumentException::class.java) { LamiPcmResampler.Stream(rates.first, rates.second) }
        }
        val stream = LamiPcmResampler.Stream(44_100, 24_000)
        for (sample in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { stream.append(floatArrayOf(sample)) }
        }
        val input = signal(256, 44_100)
        val actual = stream.append(input).samples + stream.finish().samples
        assertArrayEquals(LamiPcmResampler.resample(input, 44_100, 24_000).samples, actual, 0f)
    }

    @Test fun `same-rate stream also rejects nonfinite input before emitting it`() {
        val stream = LamiPcmResampler.Stream(24_000, 24_000)
        for (sample in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { stream.append(floatArrayOf(sample)) }
        }
        assertArrayEquals(floatArrayOf(0.2f), stream.append(floatArrayOf(0.2f)).samples, 0f)
    }

    @Test fun `duration limit is cumulative across input chunks`() {
        val stream = LamiPcmResampler.Stream(8_000, 8_000)
        assertEquals(8_000 * 15, stream.append(FloatArray(8_000 * 15)).samples.size)
        assertEquals(8_000 * 15, stream.append(FloatArray(8_000 * 15)).samples.size)
        assertThrows(IllegalArgumentException::class.java) { stream.append(floatArrayOf(0f)) }
        assertTrue(stream.finish().samples.isEmpty())
    }

    private fun assertMatchesBatch(input: FloatArray, inputRate: Int, outputRate: Int, chunkSizes: IntArray) {
        val expected = LamiPcmResampler.resample(input, inputRate, outputRate)
        val stream = LamiPcmResampler.Stream(inputRate, outputRate)
        val chunks = mutableListOf<FloatArray>()
        var offset = 0
        var turn = 0
        var clipped = 0
        while (offset < input.size) {
            val end = minOf(input.size, offset + chunkSizes[turn++ % chunkSizes.size])
            val result = stream.append(input.copyOfRange(offset, end))
            chunks += result.samples
            clipped += result.clippedSamples
            offset = end
            assertTrue(stream.append(floatArrayOf()).samples.isEmpty())
        }
        val beforeTail = chunks.sumOf { it.size }
        if (input.size > 256) assertTrue("Long input must produce PCM before finish", beforeTail > 0)
        val tail = stream.finish()
        chunks += tail.samples
        clipped += tail.clippedSamples
        val actual = FloatArray(chunks.sumOf { it.size })
        var outputOffset = 0
        for (chunk in chunks) {
            chunk.copyInto(actual, outputOffset)
            outputOffset += chunk.size
        }
        assertEquals(expected.clippedSamples, clipped)
        assertEquals(expected.samples.size, actual.size)
        assertArrayEquals("Stream phase/history/tail must equal batch for $inputRate->$outputRate length=${input.size}",
            expected.samples.map(Float::toRawBits).toIntArray(), actual.map(Float::toRawBits).toIntArray())
    }

    private fun signal(length: Int, rate: Int) = FloatArray(length) {
        (0.45 * sin(2 * PI * 1_123 * it / rate) + 0.15 * sin(2 * PI * 173 * it / rate)).toFloat()
    }
}
