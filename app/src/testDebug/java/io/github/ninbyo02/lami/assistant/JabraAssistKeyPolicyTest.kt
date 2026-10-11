package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JabraAssistKeyPolicyTest {
    private val policy = JabraAssistKeyPolicy()

    private fun key(
        action: Int,
        eligible: Boolean = true,
        keyCode: Int = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        repeat: Int = 0,
        canceled: Boolean = false,
        deviceId: Int = 12,
        downTime: Long = 100L,
        eventTime: Long = 102L,
    ) = policy.handle(keyCode, action, repeat, deviceId, downTime, eventTime, canceled, eligible)

    @Test fun jabraPulse_launchesOnceOnMatchingUp() {
        assertEquals(JabraKeyResult(true), key(KeyEvent.ACTION_DOWN))
        assertEquals(JabraKeyResult(true, true), key(KeyEvent.ACTION_UP))
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP))
    }

    @Test fun shortVolumePress_isPassedThrough() {
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_DOWN, keyCode = KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP, keyCode = KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test fun mediaPlayingOrAssistantDisabled_passesWholePressThrough() {
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_DOWN, eligible = false))
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP, eligible = true))
    }

    @Test fun playbackStartsAfterDown_consumesMatchingUpWithoutLaunching() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(JabraKeyResult(true), key(KeyEvent.ACTION_UP, eligible = false))
    }

    @Test fun repeatDoesNotLaunchAndCancellationSuppressesLaunch() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(JabraKeyResult(true), key(KeyEvent.ACTION_DOWN, repeat = 1))
        assertEquals(JabraKeyResult(true), key(KeyEvent.ACTION_UP, canceled = true))
    }

    @Test fun otherDeviceAndUnmatchedUp_doNotLaunch() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP, deviceId = 1))
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP, downTime = 99L))
        assertEquals(JabraKeyResult(true, true), key(KeyEvent.ACTION_UP))
    }

    @Test fun staleUpAndServiceReset_doNotLaunch() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(JabraKeyResult(true), key(KeyEvent.ACTION_UP, eventTime = 10_101L))
        key(KeyEvent.ACTION_DOWN)
        policy.reset()
        assertEquals(JabraKeyResult(false), key(KeyEvent.ACTION_UP))
    }

    @Test fun onlyMeasuredJabraUsbIdsAreAccepted() {
        assertTrue(isJabraAssistDevice(0x0b0e, 0x2e41))
        assertFalse(isJabraAssistDevice(0, 0))
        assertFalse(isJabraAssistDevice(0x0b0e, 0x1234))
        assertFalse(isJabraAssistDevice(0x1234, 0x2e41))
    }
}
