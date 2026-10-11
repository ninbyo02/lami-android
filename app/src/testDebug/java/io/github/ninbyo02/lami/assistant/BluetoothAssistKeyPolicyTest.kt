package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BluetoothAssistKeyPolicyTest {
    private val policy = BluetoothAssistKeyPolicy()

    private fun key(action: Int, code: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        eligible: Boolean = true, time: Long = 120, repeat: Int = 0,
        flags: Int = 0, device: Int = 0, downTime: Long = 100): BluetoothKeyResult =
        policy.handle(KeyEvent(downTime, time, action, code, repeat, 0, device, 0, flags), eligible)

    @Test fun shortPress_launchesOnceOnMatchingRelease() {
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_DOWN, time = 100))
        assertEquals(BluetoothKeyResult(true, true), key(KeyEvent.ACTION_UP))
        assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_UP))
    }

    @Test fun inactiveTrialMusicOrCall_doesNotCaptureThePress() {
        assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_DOWN, eligible = false))
        assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_UP))
    }

    @Test fun losingEligibilityAfterDown_neverLaunches() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP, eligible = false))
    }

    @Test fun volumePreviousAndPause_areNotTriggers() {
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_DOWN, code))
            assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_UP, code))
        }
    }

    @Test fun longHeldRepeatedAndCanceledKeys_doNotLaunch() {
        key(KeyEvent.ACTION_DOWN, time = 100)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP, time = 1700))
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_DOWN, repeat = 1)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP))
        key(KeyEvent.ACTION_DOWN)
        assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
    }

    @Test fun unrelatedDeviceOrDownTime_cannotCompleteThePress() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_UP, device = 2))
        assertEquals(BluetoothKeyResult(false), key(KeyEvent.ACTION_UP, downTime = 99))
        assertEquals(BluetoothKeyResult(true, true), key(KeyEvent.ACTION_UP))
    }

    @Test fun playAndHeadsetHook_acceptShortPairs() {
        for (code in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK)) {
            assertEquals(BluetoothKeyResult(true), key(KeyEvent.ACTION_DOWN, code))
            assertEquals(BluetoothKeyResult(true, true), key(KeyEvent.ACTION_UP, code))
        }
    }
}
