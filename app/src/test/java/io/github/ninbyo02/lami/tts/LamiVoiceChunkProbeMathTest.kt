package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class LamiVoiceChunkProbeMathTest {
    @Test fun channelMajorPrefixKeepsAllSixteenChannels() {
        val values = LongArray(16 * 5) { it.toLong() }
        val prefix = LamiVoiceChunkProbeMath.prefixCodes(values, 5, 3)
        for (channel in 0 until 16) {
            assertArrayEquals(longArrayOf(channel * 5L, channel * 5L + 1, channel * 5L + 2), prefix.copyOfRange(channel * 3, channel * 3 + 3))
        }
    }
    @Test fun tailDifferenceDisappearsOnlyWhenTailIsExcluded() {
        val full = floatArrayOf(1f, 2f, 3f, 4f)
        val prefix = floatArrayOf(1f, 2f, 3f, 2f)
        assertEquals(0.0, LamiVoiceChunkProbeMath.difference(full, prefix, 3).maxAbsolute, 0.0)
        val difference = LamiVoiceChunkProbeMath.difference(full, prefix, 4)
        assertEquals(2.0, difference.maxAbsolute, 0.0)
        assertEquals(1.0, difference.rms, 0.0)
    }
    @Test fun earlyDifferenceCannotBeHiddenByTrimmingTail() {
        assertEquals(1.0, LamiVoiceChunkProbeMath.difference(floatArrayOf(1f, 0f), floatArrayOf(0f, 0f), 1).maxAbsolute, 0.0)
    }
    @Test fun appendingPrefixesReconstructsPcmWithoutRepeatingSamples() {
        val first = floatArrayOf(0f, 0.1f)
        val next = floatArrayOf(0f, 0.1f, 0.2f, 0.3f)
        val last = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.4f)
        val assembled = LamiVoiceChunkProbeMath.appendedPcm(FloatArray(0), first) +
            LamiVoiceChunkProbeMath.appendedPcm(first, next) + LamiVoiceChunkProbeMath.appendedPcm(next, last)
        assertArrayEquals(last, assembled, 0f)
    }
    @Test(expected = IllegalArgumentException::class)
    fun changedPrefixCannotBeSubmittedAsNewAudio() {
        LamiVoiceChunkProbeMath.appendedPcm(floatArrayOf(0.1f), floatArrayOf(0.2f, 0.3f))
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateFinalPrefixCannotRepeatAudio() {
        LamiVoiceChunkProbeMath.appendedPcm(floatArrayOf(0.1f), floatArrayOf(0.1f))
    }
    @Test fun malformedInputAndEmptyComparisonAreRejected() {
        for (operation in listOf<() -> Unit>(
            { LamiVoiceChunkProbeMath.prefixCodes(LongArray(32), 3, 2) },
            { LamiVoiceChunkProbeMath.prefixCodes(LongArray(32), 2, 3) },
            { LamiVoiceChunkProbeMath.difference(floatArrayOf(0f), floatArrayOf(0f), 0) },
            { LamiVoiceChunkProbeMath.difference(floatArrayOf(Float.NaN), floatArrayOf(0f), 1) }
        )) {
            try { operation(); fail("Expected invalid input rejection") } catch (_: IllegalArgumentException) { }
        }
    }
}
