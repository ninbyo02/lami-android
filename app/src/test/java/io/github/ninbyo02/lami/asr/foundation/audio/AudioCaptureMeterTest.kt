package io.github.ninbyo02.lami.asr.foundation.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureMeterTest {
    @Test
    fun `silence maps to the bounded meter floor`() {
        val level = measureAudioLevel(ShortArray(320))

        assertEquals(MIN_AUDIO_DBFS, level.rmsDbFs)
        assertEquals(MIN_AUDIO_DBFS, level.peakDbFs)
    }

    @Test
    fun `half scale constant input is approximately minus six dBFS`() {
        val level = measureAudioLevel(ShortArray(320) { 16_384 })

        assertEquals(-6.0206f, level.rmsDbFs, 0.001f)
        assertEquals(-6.0206f, level.peakDbFs, 0.001f)
    }

    @Test
    fun `short minimum value is measured safely as full scale`() {
        val level = measureAudioLevel(shortArrayOf(Short.MIN_VALUE))

        assertEquals(0f, level.rmsDbFs, 0f)
        assertEquals(0f, level.peakDbFs, 0f)
    }
    @Test
    fun `metering does not mutate PCM samples`() {
        val samples = ShortArray(320) { index ->
            (index * 97 - 16_000).toShort()
        }
        val original = samples.copyOf()

        val level = measureAudioLevel(samples)

        assertTrue(samples.contentEquals(original))
        assertTrue(level.rmsDbFs <= level.peakDbFs)
        assertTrue(level.peakDbFs <= 0f)
    }
}
