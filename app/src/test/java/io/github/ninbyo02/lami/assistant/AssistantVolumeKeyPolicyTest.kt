package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantVolumeKeyPolicyTest {
    @Test fun `held key only changes volume once even after speech ends`() {
        val policy = AssistantVolumeKeyPolicy()
        assertEquals(75, policy.press(25, false, -1, 90, 150, false))
        assertNull(policy.press(25, true, -1, 75, 150, true))
        assertNull(policy.press(25, true, -1, 75, 150, false))
        assertNull(policy.press(25, false, -1, 75, 150, false))
        policy.release(25)
        assertEquals(60, policy.press(25, false, -1, 75, 150, false))
    }

    @Test fun `press during conversation cannot become a new press when speech ends`() {
        val policy = AssistantVolumeKeyPolicy()
        assertNull(policy.press(24, false, 1, 90, 150, true))
        assertNull(policy.press(24, true, 1, 90, 150, false))
        policy.release(24)
        assertEquals(105, policy.press(24, false, 1, 90, 150, false))
    }

    @Test fun `repeat received without initial down is ignored until release`() {
        val policy = AssistantVolumeKeyPolicy()
        assertNull(policy.press(24, true, 1, 90, 150, false))
        assertNull(policy.press(24, false, 1, 90, 150, false))
        policy.release(24)
        assertEquals(105, policy.press(24, false, 1, 90, 150, false))
    }

    @Test fun `steps reach eighty percent and stop there until a released down press`() {
        val policy = AssistantVolumeKeyPolicy()
        assertEquals(120, policy.press(24, false, 1, 105, 150, false))
        assertNull(policy.press(24, true, 1, 120, 150, false))
        policy.release(24)
        assertEquals(120, policy.press(24, false, 1, 120, 150, false))
        policy.release(24)
        assertEquals(105, policy.press(24, false, -1, 120, 150, false))
        assertEquals(0, policy.press(25, false, -1, 0, 150, false))
        policy.reset()
        assertFalse(policy.isPressed(24))
        assertFalse(policy.isPressed(25))
        assertNull(policy.press(24, false, 1, 0, 0, false))
    }

    @Test fun `ten percent steps preserve the eighty percent cap across device ranges`() {
        for (max in listOf(10, 100, 150)) {
            assertEquals(8 * max / 10, nextAssistantMediaVolume(7 * max / 10, 1, max))
            assertEquals(8 * max / 10, nextAssistantMediaVolume(8 * max / 10, 1, max))
            assertEquals(7 * max / 10, nextAssistantMediaVolume(8 * max / 10, -1, max))
        }
    }
}
