package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationVolumeHoldPolicyTest {
    private val baseline = IdleHeadsetVolumeSnapshot(181, true, 6, 90)

    @Test fun `speech holds volume despite headset repeats and releases after quiet`() {
        val hold = ConversationVolumeHoldPolicy()
        assertNull(hold.target(baseline, false, 105, 0))
        assertEquals(90, hold.target(baseline, true, 105, 100))
        assertEquals(90, hold.target(baseline.copy(mediaVolume = 80, voiceVolume = 5), true, 105, 300))
        hold.acknowledgeMediaAdjustment(90)
        assertEquals(90, hold.target(baseline.copy(voiceVolume = 5), false, 105, 500))
        assertEquals(90, hold.target(baseline.copy(mediaVolume = 80, voiceVolume = 4), false, 105, 1_500))
        hold.acknowledgeMediaAdjustment(90)
        assertEquals(90, hold.target(baseline.copy(voiceVolume = 4), false, 105, 2_200))
        assertNull(hold.target(baseline.copy(voiceVolume = 4), false, 105, 2_400))
        assertFalse(hold.isHolding)
        assertTrue(hold.releasedOnLastUpdate)
    }

    @Test fun `volume can rearm when speech ends after input has been quiet`() {
        val hold = ConversationVolumeHoldPolicy()
        hold.target(baseline, false, 105, 0)
        assertEquals(90, hold.target(baseline, true, 105, 100))
        hold.noteKeyInput(400)
        assertEquals(90, hold.target(baseline, true, 105, 1_000))
        assertNull(hold.target(baseline, false, 105, 1_350))
        assertTrue(hold.releasedOnLastUpdate)
    }

    @Test fun `new conversation starts from last controlled level and route loss clears hold`() {
        val hold = ConversationVolumeHoldPolicy()
        hold.target(baseline, false, 105, 0)
        hold.acknowledgeMediaAdjustment(105)
        assertEquals(105, hold.target(baseline.copy(mediaVolume = 120), true, 105, 100))
        assertTrue(hold.isHolding)
        assertNull(hold.target(baseline.copy(routeId = null), true, 105, 200))
        assertFalse(hold.isHolding)
    }
}
