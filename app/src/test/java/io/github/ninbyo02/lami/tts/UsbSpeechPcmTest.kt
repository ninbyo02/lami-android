package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.roundToInt

class UsbSpeechPcmTest {
    @Test
    fun nativeStereoIsUnchanged() {
        val samples = shortArrayOf(-32768, 32767, -200, 100)
        val converted = UsbSpeechPcm.stereo48k(samples, 48000, 2)
        assertSame(samples, converted)
        assertArrayEquals(shortArrayOf(-32768, 32767, -200, 100), converted)
    }

    @Test
    fun mono24kDuplicatesChannelsAndInterpolatesWithoutAmplification() {
        assertArrayEquals(shortArrayOf(0, 0, 500, 500, 1000, 1000, 1000, 1000),
            UsbSpeechPcm.stereo48k(shortArrayOf(0, 1000), 24000, 1))
    }

    @Test
    fun durationIsPreservedFor16k() {
        assertEquals(96000, UsbSpeechPcm.stereo48k(ShortArray(16000), 16000, 1).size)
    }

    @Test
    fun resamplingMatchesLegacyIndexwiseFormula() {
        val cases = listOf(
            ResampleCase(shortArrayOf(-1200, 0, 1500), 16_000, 1),
            ResampleCase(shortArrayOf(-1000, 1000, 0, 500, 2000, -2000), 24_000, 2),
            ResampleCase(shortArrayOf(-3000, 3000, -1000, 1000, 0, 0, 1000, -1000), 44_100, 2),
        )

        cases.forEach { case ->
            assertArrayEquals(
                legacyStereo48k(case.samples, case.rate, case.channels),
                UsbSpeechPcm.stereo48k(case.samples, case.rate, case.channels),
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedSpeechIsRejected() {
        UsbSpeechPcm.stereo48k(ShortArray(480001), 8000, 1)
    }

    private data class ResampleCase(val samples: ShortArray, val rate: Int, val channels: Int)

    private fun legacyStereo48k(samples: ShortArray, rate: Int, channels: Int): ShortArray {
        val frames = samples.size / channels
        val outputFrames = (frames * 48_000L / rate).toInt()
        return ShortArray(outputFrames * 2) { index ->
            val position = (index / 2) * rate.toDouble() / 48_000
            val first = position.toInt().coerceAtMost(frames - 1)
            val next = (first + 1).coerceAtMost(frames - 1)
            val channel = if (channels == 1) 0 else index % 2
            val a = samples[first * channels + channel].toDouble()
            val b = samples[next * channels + channel].toDouble()
            (a + (b - a) * (position - first)).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
