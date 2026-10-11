package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbSpeechPcmTest {
    @Test
    fun nativeStereoIsUnchanged() {
        val samples = shortArrayOf(-32768, 32767, -200, 100)
        assertArrayEquals(samples, UsbSpeechPcm.stereo48k(samples, 48000, 2))
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

    @Test(expected = IllegalArgumentException::class)
    fun oversizedSpeechIsRejected() {
        UsbSpeechPcm.stereo48k(ShortArray(480001), 8000, 1)
    }
}
