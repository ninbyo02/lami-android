package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BluetoothMicSamplesTest {
    @Test fun emptyAndSilence_haveZeroLevel() {
        assertEquals(BluetoothMicSamples(0, 0, 0, 0.0), BluetoothMicSamples.measure(shortArrayOf()))
        assertEquals(BluetoothMicSamples(3, 0, 0, 0.0), BluetoothMicSamples.measure(shortArrayOf(0, 0, 0)))
    }

    @Test fun fullScaleNegativeSample_doesNotOverflow() {
        val measured = BluetoothMicSamples.measure(shortArrayOf(Short.MIN_VALUE))
        assertEquals(32768, measured.peak)
        assertEquals(32768.0, measured.rms, 0.001)
    }

    @Test fun levels_includeSilenceWithoutPretendingToRecognizeSpeech() {
        val measured = BluetoothMicSamples.measure(shortArrayOf(1000, -1000, 0, 0))
        assertEquals(2, measured.nonZero)
        assertEquals(1000, measured.peak)
        assertEquals(707.1068, measured.rms, 0.001)
    }

    @Test fun playbackGain_isBoundedAndDoesNotChangeCapturedSamples() {
        val original = shortArrayOf(100, -100, 0)
        val gain = BluetoothMicSamples.playbackGain(original)
        assertEquals(16.0, gain, 0.0)
        assertArrayEquals(shortArrayOf(1600, -1600, 0), BluetoothMicSamples.amplifiedCopy(original, gain))
        assertArrayEquals(shortArrayOf(100, -100, 0), original)
        assertEquals(1.0, BluetoothMicSamples.playbackGain(shortArrayOf(0)), 0.0)
    }

    @Test fun playbackGain_doesNotRaiseAlreadyLoudSamplesOrWrapClipping() {
        assertEquals(1.0, BluetoothMicSamples.playbackGain(shortArrayOf(Short.MIN_VALUE)), 0.0)
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE),
            BluetoothMicSamples.amplifiedCopy(shortArrayOf(10000, -10000), 8.0))
    }

    @Test fun playbackGain_targetsAverageLevelWithPeakHeadroom() {
        assertEquals(3.0, BluetoothMicSamples.playbackGain(shortArrayOf(1000, -1000)), 0.0)
        val sparse = ShortArray(1000).apply { this[0] = 2000 }
        assertEquals(14.336, BluetoothMicSamples.playbackGain(sparse), 0.001)
        assertEquals(28672, BluetoothMicSamples.measure(BluetoothMicSamples.amplifiedCopy(sparse, 14.336)).peak)
    }
}
