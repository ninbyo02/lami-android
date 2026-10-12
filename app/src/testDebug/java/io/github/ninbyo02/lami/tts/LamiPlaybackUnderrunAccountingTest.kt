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

    @Test fun observedDeltaRejectsInvalidPlatformCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            LamiPlaybackUnderrunAccounting.observedDelta(initialCount = -1, finalCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiPlaybackUnderrunAccounting.observedDelta(initialCount = 0, finalCount = -1)
        }
    }
}
