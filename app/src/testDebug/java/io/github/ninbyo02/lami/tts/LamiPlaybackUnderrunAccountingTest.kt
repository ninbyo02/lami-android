package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun sessionSaturatesAccumulatedUnderrunsInsteadOfWrapping() {
        val session = LamiPlaybackUnderrunAccounting.session(initialCount = 0)

        assertEquals(Int.MAX_VALUE, session.observe(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, session.observe(0))
        assertEquals(Int.MAX_VALUE, session.observe(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, session.observedUnderruns)
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
}
