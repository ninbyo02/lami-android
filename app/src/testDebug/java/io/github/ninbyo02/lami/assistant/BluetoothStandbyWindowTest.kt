package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothStandbyWindowTest {
    @Test fun defaultAndInvalidDuration_areBoundedToThirtyMinutes() {
        val window = BluetoothStandbyWindow(100, Int.MAX_VALUE)
        assertEquals(30, window.minutes)
        assertEquals(1_800_100L, window.deadline)
    }

    @Test fun eachSupportedDuration_expiresAndNeverAcceptsLateKeys() {
        for (minutes in BluetoothStandbyWindow.durations) {
            val window = BluetoothStandbyWindow(100, minutes)
            assertTrue(window.canLaunch(window.deadline - 1))
            assertFalse(window.canLaunch(window.deadline))
            assertFalse(window.canLaunch(window.deadline + 999))
            assertEquals(0L, window.remaining(window.deadline + 999))
        }
    }

    @Test fun repeatedLaunches_areDebouncedWithoutExtendingTheDeadline() {
        val window = BluetoothStandbyWindow(100, 5)
        val deadline = window.deadline
        window.launched(200)
        assertFalse(window.canLaunch(1699))
        assertTrue(window.canLaunch(1700))
        window.launched(1700)
        assertEquals(deadline, window.deadline)
        assertFalse(window.canLaunch(deadline))
    }
}
