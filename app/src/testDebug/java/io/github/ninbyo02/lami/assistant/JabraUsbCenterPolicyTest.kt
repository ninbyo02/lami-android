package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JabraUsbCenterPolicyTest {
    private val policy = JabraUsbCenterPolicy()
    private fun key(action: Int, eligible: Boolean = true, music: Boolean = false,
        code: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, at: Long = 120L, repeat: Int = 0) =
        policy.handle(KeyEvent(100L, at, action, code, repeat), eligible, music)

    @Test fun idleShortPressLaunchesOnMatchingReleaseOnly() {
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_DOWN))
        assertEquals(BluetoothKeyResult(true, true), key(KeyEvent.ACTION_UP))
        assertFalse(key(KeyEvent.ACTION_UP).consume)
    }

    @Test fun musicPauseAndSubsequentResumeBothPassThrough() {
        assertFalse(key(KeyEvent.ACTION_DOWN, eligible = false, music = true).consume)
        assertFalse(key(KeyEvent.ACTION_UP, music = false).consume)
        assertTrue(policy.musicReserved)
        assertFalse(key(KeyEvent.ACTION_DOWN).consume)
        assertFalse(key(KeyEvent.ACTION_UP).consume)
    }

    @Test fun explicitStandbyRestartRestoresIdleLaunch() {
        key(KeyEvent.ACTION_DOWN, eligible = false, music = true)
        policy.reset()
        assertFalse(policy.musicReserved)
        key(KeyEvent.ACTION_DOWN)
        assertTrue(key(KeyEvent.ACTION_UP).launch)
    }

    @Test fun musicStartingAfterOwnedDownConsumesUpWithoutLaunching() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP, eligible = false, music = true))
    }

    @Test fun longHoldAndRepeatedKeyDoNotLaunch() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP, at = 1800L))
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_DOWN, repeat = 1)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP))
    }

    @Test fun disabledAssistantAndOtherButtonsPassThrough() {
        assertFalse(key(KeyEvent.ACTION_DOWN, eligible = false).consume)
        assertFalse(key(KeyEvent.ACTION_UP).launch)
        assertFalse(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_VOLUME_DOWN).consume)
        assertFalse(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_MEDIA_PREVIOUS).consume)
    }

    @Test fun busyPressIsConsumedAndFreshPressAfterSpeechLaunchesNextTurn() {
        val down = KeyEvent(100L, 100L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        val up = KeyEvent(100L, 120L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        assertTrue(policy.handle(down, true, false, canLaunch = false).consume)
        assertEquals(BluetoothKeyResult(true), policy.handle(up, true, false, canLaunch = false))
        assertFalse(policy.musicReserved)
        val nextDown = KeyEvent(2000L, 2000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        val nextUp = KeyEvent(2000L, 2020L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        policy.handle(nextDown, true, false)
        assertTrue(policy.handle(nextUp, true, false).launch)
    }
}
