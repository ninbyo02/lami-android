package io.github.ninbyo02.lami.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LamiPlaybackUnderrunAccountingTest {
    @Test fun observedDeltaReportsOnlyNewUnderruns() {
        assertEquals(0, LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 0, finalCount = 0))
        assertEquals(3, LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 0, finalCount = 3))
        assertEquals(2, LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 5, finalCount = 7))
    }

    @Test fun observedDeltaDoesNotReportCounterResetAsUnderrun() {
        assertEquals(0, LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 4, finalCount = 1))
    }

    @Test fun sessionAccumulatesOnlyForwardUnderrunCounts() {
        val session = LamiPlaybackUnderrunAccounting.session(initialCount = 2)

        assertEquals(0, session.observe(2))
        assertEquals(3, session.observe(5))
        assertEquals(3, session.observe(1))
        assertEquals(5, session.observe(3))
        assertEquals(5, session.observedUnderruns)
    }

    @Test fun repeatedCounterResetsRebaseWithoutRecountingBaseline() {
        val session = LamiPlaybackUnderrunAccounting.session(initialCount = 10)
        // Each decrease starts a new counter epoch; only subsequent increases count.
        val readingsAndTotals = listOf(
            14 to 4,
            6 to 4,
            6 to 4,
            9 to 7,
            2 to 7,
            5 to 10,
            0 to 10,
            0 to 10,
            1 to 11,
        )

        for ((reading, expectedTotal) in readingsAndTotals) {
            assertEquals("Counter reading $reading", expectedTotal, session.observe(reading))
            assertEquals(expectedTotal, session.observedUnderruns)
        }
    }

    @Test fun sessionSaturatesAccumulatedUnderrunsInsteadOfWrapping() {
        val session = LamiPlaybackUnderrunAccounting.session(initialCount = 0)

        assertEquals(Int.MAX_VALUE, session.observe(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, session.observe(0))
        assertEquals(Int.MAX_VALUE, session.observe(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, session.observedUnderruns)
    }

    @Test fun monotonicCounterTotalIsIndependentOfObservationCadence() {
        val initialCount = 17
        val finalCount = Int.MAX_VALUE
        val finalOnly = LamiPlaybackUnderrunAccounting.session(initialCount)
        val frequentlyPolled = LamiPlaybackUnderrunAccounting.session(initialCount)

        // Repeated polls and intermediate readings must not inflate or lose events.
        val readings = listOf(initialCount, 18, 1_000_000_000, finalCount - 1, finalCount)
        for (reading in readings) {
            assertEquals(reading - initialCount, frequentlyPolled.observe(reading))
            assertEquals(reading - initialCount, frequentlyPolled.observe(reading))
        }

        val expected = finalCount - initialCount
        assertEquals(expected, finalOnly.observe(finalCount))
        assertEquals(expected, frequentlyPolled.observedUnderruns)
        assertEquals(finalOnly.observedUnderruns, frequentlyPolled.observedUnderruns)
    }

    @Test fun playbackSessionsKeepIndependentBaselinesAndTotals() {
        val firstPlayback = LamiPlaybackUnderrunAccounting.session(initialCount = 4)
        assertEquals(3, firstPlayback.observe(7))

        // A later playback starts with the existing cumulative platform count.
        val laterPlayback = LamiPlaybackUnderrunAccounting.session(initialCount = 7)
        assertEquals(0, laterPlayback.observe(7))
        assertEquals(2, laterPlayback.observe(9))
        assertEquals(3, firstPlayback.observedUnderruns)

        // Resetting one track's counter must not rebase another session.
        assertEquals(3, firstPlayback.observe(0))
        assertEquals(4, firstPlayback.observe(1))
        assertEquals(3, laterPlayback.observe(10))
        assertEquals(4, firstPlayback.observedUnderruns)
        assertEquals(3, laterPlayback.observedUnderruns)
    }

    @Test fun sessionRejectsInvalidObservationWithoutCorruptingAccounting() {
        val session = LamiPlaybackUnderrunAccounting.session(initialCount = 2)

        assertEquals(3, session.observe(5))
        assertThrows(IllegalArgumentException::class.java) {
            session.observe(-1)
        }
        assertEquals(3, session.observedUnderruns)
        assertEquals(5, session.observe(7))
    }

    @Test fun observedDeltaRejectsInvalidPlatformCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            LamiPlaybackUnderrunAccounting.observedDelta(initialCount = -1, finalCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 0, finalCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiPlaybackUnderrunAccounting.session(initialCount = -1)
        }
    }

    @Test fun streamPlayerStatsComeFromPostPlaybackUnderrunObservation() {
        val source = File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiPcmStreamPlayer.kt",
        ).readText()

        val sessionStart = source.indexOf(
            "val underrunSession = LamiPlaybackUnderrunAccounting.session(track.underrunCount)",
        )
        val playbackDrain = source.indexOf("while (track.playbackHeadPosition < submitted)")
        val finalObservation = source.indexOf(
            "val observedUnderruns = underrunSession.observe(track.underrunCount)",
        )
        val statsReturn = source.indexOf("return Stats(submitted, observedUnderruns)")

        assertTrue(sessionStart >= 0)
        assertTrue(playbackDrain > sessionStart)
        assertTrue(finalObservation > playbackDrain)
        assertTrue(statsReturn > finalObservation)
    }

    @Test fun streamPlayerStatsDoNotExposeRawCumulativeTrackCounter() {
        val source = File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiPcmStreamPlayer.kt",
        ).readText()

        val statsUnderrunArgument = Regex("""return\s+Stats\(\s*submitted\s*,\s*([^)]+)\)""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?.trim()

        assertEquals("observedUnderruns", statsUnderrunArgument)
        assertTrue(source.contains("val observedUnderruns = underrunSession.observe(track.underrunCount)"))
        assertFalse(Regex("""return\s+Stats\([^)]*track\.underrunCount""").containsMatchIn(source))
    }
}
