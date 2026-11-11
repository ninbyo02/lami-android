package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LamiPcmEosDrainClockTest {
    @Test
    fun roundsRemainingFrameDurationUpToTheNextNanosecond() {
        val clock = LamiPcmEosDrainClock(writtenFrames = 3, headFrames = 2, sampleRate = 3,
            stoppedAtNs = 10L, tailGuardNs = 0L)
        assertEquals(333_333_344L, clock.deadlineNs)
        assertEquals(333_333_334L, clock.remainingNs(10L))

        val at24k = LamiPcmEosDrainClock(1, 0, 24_000, 0L, 0L)
        assertEquals(41_667L, at24k.deadlineNs)
    }

    @Test
    fun preservesQueuedTailAfterStopWithoutReadingAResetPlaybackHead() {
        val clock = LamiPcmEosDrainClock(48_000, 12_000, 24_000, 5_000_000_000L, 20_000_000L)
        assertEquals(6_520_000_000L, clock.deadlineNs)
        assertEquals(1_520_000_000L, clock.remainingNs(5_000_000_000L))
        assertEquals(1L, clock.remainingNs(6_519_999_999L))
        assertEquals(0L, clock.remainingNs(6_520_000_000L))
        assertEquals(0L, clock.remainingNs(7_000_000_000L))
    }

    @Test
    fun delayedHardwareTimestampCanExtendTheHeadEstimate() {
        val clock = LamiPcmEosDrainClock(24_000, 18_000, 24_000, 5_000_000_000L, 10_000_000L,
            timestampFrame = 12_000L, timestampNs = 5_000_000_000L)
        assertEquals(5_510_000_000L, clock.deadlineNs)
    }

    @Test
    fun timestampCannotShortenTheHeadEstimate() {
        val clock = LamiPcmEosDrainClock(24_000, 18_000, 24_000, 5_000_000_000L, 10_000_000L,
            timestampFrame = 24_000L, timestampNs = 4_900_000_000L)
        assertEquals(5_260_000_000L, clock.deadlineNs)
    }

    @Test
    fun invalidOrIncompleteTimestampsUseTheHeadEstimate() {
        val stop = 5_000_000_000L
        val invalid = listOf(
            null to stop,
            0L to null,
            -1L to stop,
            24_001L to stop,
            0L to -1L,
            0L to stop + 100_000_001L,
        )
        for ((frame, time) in invalid) {
            val clock = LamiPcmEosDrainClock(24_000, 18_000, 24_000, stop, 10_000_000L,
                timestampFrame = frame, timestampNs = time)
            assertEquals("Invalid timestamp frame=$frame time=$time", 5_260_000_000L, clock.deadlineNs)
        }
    }

    @Test
    fun staleTimestampIsIgnoredButExactlyTwoSecondsOldRemainsValid() {
        val stop = 10_000_000_000L
        val valid = LamiPcmEosDrainClock(96_000, 96_000, 24_000, stop, 0L,
            timestampFrame = 0L, timestampNs = stop - 2_000_000_000L)
        val stale = LamiPcmEosDrainClock(96_000, 96_000, 24_000, stop, 0L,
            timestampFrame = 0L, timestampNs = stop - 2_000_000_001L)
        assertEquals(stop + 2_000_000_000L, valid.deadlineNs)
        assertEquals(stop, stale.deadlineNs)
    }

    @Test
    fun allowsTimestampUpToOneHundredMillisecondsInTheFuture() {
        val stop = 5_000_000_000L
        val clock = LamiPcmEosDrainClock(24_000, 24_000, 24_000, stop, 0L,
            timestampFrame = 0L, timestampNs = stop + 100_000_000L)
        assertEquals(stop + 1_100_000_000L, clock.deadlineNs)
    }

    @Test
    fun aFullyPlayedOrEmptyBufferStillHonorsTheTailGuard() {
        assertEquals(25L, LamiPcmEosDrainClock(24_000, 24_000, 24_000, 5L, 20L).deadlineNs)
        assertEquals(25L, LamiPcmEosDrainClock(0, 0, 24_000, 5L, 20L).deadlineNs)
        assertEquals(1_000_000_005L, LamiPcmEosDrainClock(0, 0, 1, 5L, 1_000_000_000L).deadlineNs)
    }

    @Test
    fun deadlineAndRemainingDurationSaturateInsteadOfWrapping() {
        val clock = LamiPcmEosDrainClock(1, 0, 1, Long.MAX_VALUE - 5L, 10L)
        assertEquals(Long.MAX_VALUE, clock.deadlineNs)
        assertEquals(5L, clock.remainingNs(Long.MAX_VALUE - 5L))
        assertEquals(Long.MAX_VALUE, clock.remainingNs(Long.MIN_VALUE))
        assertEquals(0L, clock.remainingNs(Long.MAX_VALUE))
    }

    @Test
    fun supportsSignedNanoTimeOriginsAndLargeValidFrameCounts() {
        val signed = LamiPcmEosDrainClock(1, 0, 1, Long.MIN_VALUE, 0L)
        assertEquals(Long.MIN_VALUE + 1_000_000_000L, signed.deadlineNs)
        assertEquals(1_000_000_000L, signed.remainingNs(Long.MIN_VALUE))
        assertEquals(1_000_000_000L,
            LamiPcmEosDrainClock(Int.MAX_VALUE, 0, Int.MAX_VALUE, 0L, 0L).deadlineNs)
        assertEquals(180_000_000_000L, LamiPcmEosDrainClock(180, 0, 1, 0L, 0L).deadlineNs)
    }

    @Test
    fun rejectsInvalidHeadRateDurationAndTailGuard() {
        val invalid = listOf(
            { LamiPcmEosDrainClock(-1, 0, 24_000, 0L, 0L) },
            { LamiPcmEosDrainClock(1, -1, 24_000, 0L, 0L) },
            { LamiPcmEosDrainClock(1, 2, 24_000, 0L, 0L) },
            { LamiPcmEosDrainClock(1, 0, 0, 0L, 0L) },
            { LamiPcmEosDrainClock(1, 0, -1, 0L, 0L) },
            { LamiPcmEosDrainClock(181, 0, 1, 0L, 0L) },
            { LamiPcmEosDrainClock(1, 0, 1, 0L, -1L) },
            { LamiPcmEosDrainClock(1, 0, 1, 0L, 1_000_000_001L) },
        )
        for (create in invalid) assertThrows(IllegalArgumentException::class.java) { create() }
    }
}
