package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JabraUsbVolumePolicyTest {
    private val up = KeyEvent.KEYCODE_VOLUME_UP
    private val down = KeyEvent.KEYCODE_VOLUME_DOWN

    @Test fun `one step until release even if repeated reports arrive`() {
        val policy = JabraUsbVolumePolicy()
        assertEquals(JabraUsbVolumeResult(true, 90), policy.handle(up, true, true, false, 75, 150))
        assertEquals(JabraUsbVolumeResult(true), policy.handle(up, true, true, false, 90, 150))
        assertEquals(JabraUsbVolumeResult(true), policy.handle(up, false, true, false, 90, 150))
        assertEquals(JabraUsbVolumeResult(true, 105), policy.handle(up, true, true, false, 90, 150))
    }

    @Test fun `speech ending cannot activate a button pressed during speech`() {
        val policy = JabraUsbVolumePolicy()
        assertEquals(JabraUsbVolumeResult(true), policy.handle(down, true, true, true, 90, 150))
        assertEquals(JabraUsbVolumeResult(true), policy.handle(down, true, true, false, 90, 150))
        policy.handle(down, false, true, false, 90, 150)
        assertEquals(JabraUsbVolumeResult(true, 75), policy.handle(down, true, true, false, 90, 150))
    }

    @Test fun `owned release is consumed after leaving chat`() {
        val policy = JabraUsbVolumePolicy()
        policy.handle(up, true, true, false, 60, 150)
        assertEquals(JabraUsbVolumeResult(true), policy.handle(up, false, false, false, 75, 150))
        assertFalse(policy.handle(up, true, false, false, 75, 150).consume)
    }

    @Test fun `music outside scope and unmatched release pass through`() {
        val policy = JabraUsbVolumePolicy()
        assertFalse(policy.handle(down, true, false, false, 90, 150).consume)
        assertFalse(policy.handle(down, false, true, false, 90, 150).consume)
        assertFalse(policy.handle(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true, true, false, 90, 150).consume)
    }

    @Test fun `upper and lower boundaries never overshoot`() {
        val policy = JabraUsbVolumePolicy()
        assertEquals(120, policy.handle(up, true, true, false, 105, 150).target)
        assertEquals(JabraUsbVolumeResult(true), policy.handle(up, true, true, false, 120, 150))
        policy.handle(up, false, true, false, 120, 150)
        assertEquals(120, policy.handle(up, true, true, false, 120, 150).target)
        policy.handle(up, false, true, false, 120, 150)
        assertEquals(105, policy.handle(down, true, true, false, 120, 150).target)
        policy.reset()
        assertEquals(0, policy.handle(down, true, true, false, 0, 150).target)
    }
}
