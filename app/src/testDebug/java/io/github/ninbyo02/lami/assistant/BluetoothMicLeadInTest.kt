package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMicLeadInTest {
    @Test
    fun milliseconds_mapToExactSixteenKhzSampleCounts() {
        assertEquals(0, BluetoothMicLeadIn.sampleCount(0, 16_000))
        assertEquals(16, BluetoothMicLeadIn.sampleCount(1, 16_000))
        assertEquals(48000, BluetoothMicLeadIn.sampleCount(3000, 16_000))
    }

    @Test
    fun invalidInput_isRejectedRatherThanClamped() {
        listOf("", " ", "-1", "3001", "1.5", "no", "999999999999").forEach {
            assertNull(BluetoothMicLeadIn.parse(it))
        }
        assertEquals(0, BluetoothMicLeadIn.parse("0"))
        assertEquals(3000, BluetoothMicLeadIn.parse(" 3000 "))
    }

    @Test
    fun zeroCut_preservesEverySample() {
        val destination = ShortArray(3)
        val leadIn = BluetoothMicLeadIn(0, 16_000)
        assertEquals(3, leadIn.append(shortArrayOf(10, 20, 30), 3, destination, 0))
        assertArrayEquals(shortArrayOf(10, 20, 30), destination)
    }

    @Test
    fun cutAcrossPartialReads_preservesSuffixInTheBoundaryBlock() {
        val leadIn = BluetoothMicLeadIn(1, 16_000)
        val destination = ShortArray(5)
        assertEquals(0, leadIn.append(ShortArray(10) { 99 }, 10, destination, 0))
        assertEquals(6, leadIn.remainingSamples)
        assertEquals(2, leadIn.append(shortArrayOf(99, 99, 99, 99, 99, 99, 10, 20, 99), 8, destination, 0))
        assertEquals(0, leadIn.remainingSamples)
        assertEquals(3, leadIn.append(shortArrayOf(30, 40, 50), 3, destination, 2))
        assertArrayEquals(shortArrayOf(10, 20, 30, 40, 50), destination)
    }

    @Test
    fun longestCut_stillRetainsFiveSecondsWithoutPadding() {
        val leadIn = BluetoothMicLeadIn(3000, 16_000)
        val destination = ShortArray(80000)
        val block = ShortArray(512)
        var offset = 0
        var readSamples = 0
        while (offset < destination.size) {
            val count = minOf(block.size, leadIn.remainingSamples + destination.size - offset)
            for (index in 0 until count) block[index] = if (readSamples + index < 48000) 99 else 42
            offset += leadIn.append(block, count, destination, offset)
            readSamples += count
        }
        assertEquals(128000, readSamples)
        assertArrayEquals(ShortArray(80000) { 42 }, destination)
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeConstruction_isRejected() {
        BluetoothMicLeadIn(3001, 16_000)
    }
}
