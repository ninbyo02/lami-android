package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LamiPcmGenerationPaceTest {
    @Test fun `cold startup latency is excluded from the production rate`() {
        val pace = LamiPcmGenerationPace()
        pace.prefixProduced(3_840, 60_000)
        assertEquals(640, pace.minimumBufferMs)
        pace.prefixProduced(15_360, 60_400)
        assertEquals(640, pace.minimumBufferMs)
    }

    @Test fun `slow codec generation raises reserve despite a decoder catchup burst`() {
        val pace = LamiPcmGenerationPace()
        pace.prefixProduced(3_840, 10_000)
        pace.prefixProduced(15_360, 11_500)
        assertEquals(2_400, pace.minimumBufferMs)
        pace.prefixProduced(30_720, 11_501)
        assertEquals(2_400, pace.minimumBufferMs)
        assertEquals(640, LamiPcmGenerationPace().minimumBufferMs)
    }

    @Test fun `small scheduling jitter leaves the lower latency reserve`() {
        val pace = LamiPcmGenerationPace()
        pace.prefixProduced(3_840, 0)
        pace.prefixProduced(15_360, 550) // 480ms of audio, below 15% slack.
        assertEquals(640, pace.minimumBufferMs)
        pace.prefixProduced(30_720, 1_287) // 640ms of audio, above 15% slack.
        assertEquals(2_400, pace.minimumBufferMs)
    }

    @Test fun `invalid or decreasing generation evidence does not overwrite prior evidence`() {
        val pace = LamiPcmGenerationPace()
        pace.prefixProduced(3_840, 100)
        assertThrows(IllegalArgumentException::class.java) { pace.prefixProduced(3_840, 101) }
        assertThrows(IllegalArgumentException::class.java) { pace.prefixProduced(15_360, 99) }
        pace.prefixProduced(15_360, 200)
        assertEquals(640, pace.minimumBufferMs)
    }
}
