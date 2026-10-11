package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdleHeadsetVolumePolicyTest {
    private val baseline = IdleHeadsetVolumeSnapshot(181, true, 7, 90)

    @Test fun `one voice burst changes media once without restoring later changes`() {
        val policy = IdleHeadsetVolumePolicy()
        assertNull(policy.update(baseline, 0, 150))
        assertEquals(105, policy.update(baseline.copy(voiceVolume = 8), 1_200, 150))
        policy.acknowledgeMediaAdjustment(105)
        assertNull(policy.update(baseline.copy(voiceVolume = 8, mediaVolume = 105), 1_400, 150))
        assertNull(policy.update(baseline.copy(voiceVolume = 9, mediaVolume = 115), 1_600, 150))
        assertNull(policy.update(baseline.copy(voiceVolume = 9, mediaVolume = 125),
            3_000, 150, conversationActive = true))
    }

    @Test fun `separate press after quiet period changes only one more tier`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline.copy(mediaVolume = 60), 0, 150)
        assertEquals(75, policy.update(baseline.copy(mediaVolume = 60, voiceVolume = 8), 1_200, 150))
        policy.acknowledgeMediaAdjustment(75)
        assertEquals(90, policy.update(baseline.copy(mediaVolume = 75, voiceVolume = 9), 2_600, 150))
    }

    @Test fun `media only press triggers one step and no continuous restoration`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(105, policy.update(baseline.copy(mediaVolume = 100), 1_200, 150))
        policy.acknowledgeMediaAdjustment(105)
        assertNull(policy.update(baseline.copy(mediaVolume = 120), 1_700, 150))
        assertNull(policy.update(baseline.copy(mediaVolume = 120), 4_000, 150))
    }

    @Test fun `music recording and route transition cannot start idle burst`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertNull(policy.update(baseline.copy(eligible = false, voiceVolume = 8), 1_200, 150))
        assertNull(policy.update(baseline.copy(voiceVolume = 9), 1_400, 150))
        assertNull(policy.update(baseline.copy(voiceVolume = 10), 1_500, 150))
        assertNull(policy.update(baseline.copy(routeId = 200), 2_000, 150))
        assertNull(policy.update(baseline.copy(routeId = 200, voiceVolume = 8), 2_500, 150))
    }

    @Test fun `down from above cap lands on one safe tier`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline.copy(mediaVolume = 135), 0, 150)
        assertEquals(105, policy.update(baseline.copy(mediaVolume = 125, voiceVolume = 6), 1_200, 150))
    }

    @Test fun `repeat latch survives speech and blocks changes during conversation`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(75, policy.update(baseline.copy(voiceVolume = 6), 1_200, 150))
        assertNull(policy.update(baseline.copy(eligible = false, voiceVolume = 5, mediaVolume = 75),
            1_800, 150, conversationActive = true))
        assertNull(policy.update(baseline.copy(voiceVolume = 4, mediaVolume = 75), 2_400, 150))
        assertNull(policy.update(baseline.copy(voiceVolume = 3, mediaVolume = 75),
            4_000, 150, conversationActive = true))
        assertNull(policy.update(baseline.copy(voiceVolume = 2, mediaVolume = 75), 4_600, 150))
        assertEquals(60, policy.update(baseline.copy(voiceVolume = 1, mediaVolume = 75), 6_000, 150))
    }

    @Test fun `headset voice direction wins when stale media echo disagrees`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(75, policy.update(baseline.copy(voiceVolume = 6, mediaVolume = 100), 1_200, 150))
    }

    @Test fun `upper cap is applied once without repeated writes`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline.copy(mediaVolume = 120), 0, 150)
        assertEquals(120, policy.update(baseline.copy(mediaVolume = 120, voiceVolume = 8), 1_200, 150))
        assertNull(policy.update(baseline.copy(mediaVolume = 135, voiceVolume = 9), 1_800, 150))
        assertNull(policy.update(baseline.copy(mediaVolume = 135, voiceVolume = 9), 4_000, 150))
    }

    @Test fun `headset drift cannot skip the next logical tier`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(75, policy.update(baseline.copy(voiceVolume = 6), 1_200, 150))
        policy.acknowledgeMediaAdjustment(75)
        assertNull(policy.update(baseline.copy(mediaVolume = 65, voiceVolume = 6),
            1_800, 150, conversationActive = true))
        assertEquals(60, policy.update(baseline.copy(mediaVolume = 65, voiceVolume = 5), 3_500, 150))
        policy.acknowledgeMediaAdjustment(60)
        assertNull(policy.update(baseline.copy(mediaVolume = 50, voiceVolume = 5),
            4_000, 150, conversationActive = true))
        assertEquals(45, policy.update(baseline.copy(mediaVolume = 50, voiceVolume = 4), 6_000, 150))
    }

    @Test fun `late rounded AVRCP echo is not mistaken for a press`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(75, policy.update(baseline.copy(voiceVolume = 6), 1_200, 150))
        policy.acknowledgeMediaAdjustment(75)
        assertNull(policy.update(baseline.copy(mediaVolume = 70, voiceVolume = 6), 4_500, 150))
        assertEquals(60, policy.update(baseline.copy(mediaVolume = 60, voiceVolume = 6), 4_700, 150))
    }

    @Test fun `HFP and AVRCP halves of one press cannot double step`() {
        val policy = IdleHeadsetVolumePolicy()
        policy.update(baseline, 0, 150)
        assertEquals(75, policy.update(baseline.copy(mediaVolume = 80), 1_200, 150))
        policy.acknowledgeMediaAdjustment(75)
        assertNull(policy.update(baseline.copy(mediaVolume = 75, voiceVolume = 6), 1_800, 150))
        assertEquals(75, policy.commandBaseline(65))
    }
}
