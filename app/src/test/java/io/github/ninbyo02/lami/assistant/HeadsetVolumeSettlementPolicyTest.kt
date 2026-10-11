package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetVolumeSettlementPolicyTest {
    private val baseline = IdleHeadsetVolumeSnapshot(181, true, 7, 70)

    @Test fun `release cue absorbs native repeats before one setting and confirmed number`() {
        val policy = HeadsetVolumeSettlementPolicy()
        assertTrue(policy.begin(baseline, 60, 0))
        assertFalse(policy.begin(baseline, 45, 100))
        val held = baseline.copy(voiceVolume = 6, mediaVolume = 50)
        assertNull(policy.update(held, true, false, 150, 600))
        assertNull(policy.update(held, false, false, 150, 1_400))
        assertEquals(HeadsetVolumeSettlementAction.Apply(60, false),
            policy.update(held, false, false, 150, 1_600))
        policy.applied(60, 1_600, false)
        val corrected = held.copy(mediaVolume = 60)
        assertNull(policy.update(corrected, false, false, 150, 1_900))
        assertEquals(HeadsetVolumeSettlementAction.Confirm(60, 60),
            policy.update(corrected, false, false, 150, 2_000))
        assertFalse(policy.isActive)
        assertNull(policy.update(corrected, false, false, 150, 3_000))
    }

    @Test fun `no setting during cue playback even if volume has stopped changing`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 60, 0)
        assertNull(policy.update(baseline, true, false, 150, 2_000))
        assertNull(policy.update(baseline.copy(eligible = false), false, false, 150, 2_200))
        assertEquals(HeadsetVolumeSettlementAction.Apply(60, false),
            policy.update(baseline, false, false, 150, 2_400))
    }

    @Test fun `late native step requires quiet then at most one repair`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 45, 0)
        policy.update(baseline, false, false, 150, 1_200)
        policy.applied(45, 1_200, false)
        val drift = baseline.copy(mediaVolume = 30)
        assertNull(policy.update(drift, false, false, 150, 1_600))
        assertNull(policy.update(drift, false, false, 150, 2_200))
        assertEquals(HeadsetVolumeSettlementAction.Apply(45, true),
            policy.update(drift, false, false, 150, 2_800))
        policy.applied(45, 2_800, true)
        val corrected = drift.copy(mediaVolume = 45)
        assertEquals(HeadsetVolumeSettlementAction.Confirm(45, 45),
            policy.update(corrected, false, false, 150, 3_400))
    }

    @Test fun `failed repair cancels rather than oscillating forever`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 45, 0)
        policy.update(baseline, false, false, 150, 1_200)
        policy.applied(45, 1_200, false)
        val drift = baseline.copy(mediaVolume = 30)
        policy.update(drift, false, false, 150, 1_600)
        policy.update(drift, false, false, 150, 2_800)
        policy.applied(45, 2_800, true)
        policy.update(drift, false, false, 150, 3_000)
        assertEquals(HeadsetVolumeSettlementAction.Cancel("volume_did_not_settle"),
            policy.update(drift, false, false, 150, 4_200))
        assertNull(policy.update(drift, false, false, 150, 5_000))
    }

    @Test fun `route loss assistant interruption timeout and pause cancel without applying`() {
        for ((snapshot, interrupted, time, reason) in listOf(
            Case(baseline.copy(routeId = null), false, 1_200, "route_changed"),
            Case(baseline, true, 1_200, "assistant_or_call_started"),
            Case(baseline, false, 8_000, "timeout"),
        )) {
            val policy = HeadsetVolumeSettlementPolicy()
            policy.begin(baseline, 60, 0)
            assertEquals(HeadsetVolumeSettlementAction.Cancel(reason),
                policy.update(snapshot, false, interrupted, 150, time))
        }
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 60, 0)
        policy.reset()
        assertFalse(policy.isActive)
    }

    @Test fun `small device rounding is confirmed without another write`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 45, 0)
        policy.update(baseline, false, false, 150, 1_200)
        policy.applied(45, 1_200, false)
        val rounded = baseline.copy(mediaVolume = 40)
        assertNull(policy.update(rounded, false, false, 150, 1_400))
        assertEquals(HeadsetVolumeSettlementAction.Confirm(45, 40),
            policy.update(rounded, false, false, 150, 2_000))
        assertEquals("音量。", HeadsetVolumeAnnouncement(30, 0,
            HeadsetVolumeFeedbackPhase.RELEASE_CUE).text)
        assertEquals("30パーセント。", HeadsetVolumeAnnouncement(30, 0,
            HeadsetVolumeFeedbackPhase.SETTLED).text)
    }

    @Test fun `quick follow up omits cue but still waits for release and stable volume`() {
        val policy = confirmedPolicy()
        assertTrue(policy.begin(baseline.copy(mediaVolume = 60), 75, 2_500))
        assertFalse(policy.releaseCueRequired)
        val pressed = baseline.copy(voiceVolume = 8, mediaVolume = 70)
        assertNull(policy.update(pressed, false, false, 150, 3_000))
        assertNull(policy.update(pressed, false, false, 150, 3_800))
        assertEquals(HeadsetVolumeSettlementAction.Apply(75, false),
            policy.update(pressed, false, false, 150, 4_000))
        policy.applied(75, 4_000, false)
        assertNull(policy.update(pressed.copy(mediaVolume = 75), false, false, 150, 4_200))
        assertEquals(HeadsetVolumeSettlementAction.Confirm(75, 75),
            policy.update(pressed.copy(mediaVolume = 75), false, false, 150, 4_400))
    }

    @Test fun `volume speech tail accepts input briefly without accepting ongoing music`() {
        assertTrue(acceptsIdleHeadsetInput(true, false, 1_000, 1_130))
        assertFalse(acceptsIdleHeadsetInput(true, true, 1_000, 1_130))
        assertFalse(acceptsIdleHeadsetInput(true, false, 1_000, 1_601))
        assertFalse(acceptsIdleHeadsetInput(true, false, -1, 1_130))
        assertTrue(acceptsIdleHeadsetInput(false, false, -1, 1_130))
    }

    @Test fun `another native repeat still postpones the shortened quiet window`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 60, 0)
        assertNull(policy.update(baseline.copy(voiceVolume = 6), false, false, 150, 600))
        assertNull(policy.update(baseline.copy(voiceVolume = 5), false, false, 150, 1_200))
        assertNull(policy.update(baseline.copy(voiceVolume = 5), false, false, 150, 2_000))
        assertEquals(HeadsetVolumeSettlementAction.Apply(60, false),
            policy.update(baseline.copy(voiceVolume = 5), false, false, 150, 2_100))
    }

    @Test fun `first operation and expired follow up window use the cue`() {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 60, 0)
        assertTrue(policy.releaseCueRequired)
        val confirmed = confirmedPolicy()
        confirmed.begin(baseline.copy(mediaVolume = 60), 75, 6_800)
        assertTrue(confirmed.releaseCueRequired)
        val withinWindow = confirmedPolicy()
        withinWindow.begin(baseline.copy(mediaVolume = 60), 75, 6_799)
        assertFalse(withinWindow.releaseCueRequired)
    }

    @Test fun `route change pause and cancellation restore the first operation cue`() {
        val differentRoute = confirmedPolicy()
        differentRoute.begin(baseline.copy(routeId = 200), 75, 2_000)
        assertTrue(differentRoute.releaseCueRequired)
        val paused = confirmedPolicy()
        paused.reset()
        paused.begin(baseline, 75, 2_000)
        assertTrue(paused.releaseCueRequired)
        val interrupted = confirmedPolicy()
        interrupted.begin(baseline, 75, 2_000)
        assertFalse(interrupted.releaseCueRequired)
        interrupted.update(baseline, false, true, 150, 2_100)
        interrupted.begin(baseline, 75, 2_200)
        assertTrue(interrupted.releaseCueRequired)
    }

    private fun confirmedPolicy(): HeadsetVolumeSettlementPolicy {
        val policy = HeadsetVolumeSettlementPolicy()
        policy.begin(baseline, 60, 0)
        policy.update(baseline, false, false, 150, 1_200)
        policy.applied(60, 1_200, false)
        assertEquals(HeadsetVolumeSettlementAction.Confirm(60, 60),
            policy.update(baseline.copy(mediaVolume = 60), false, false, 150, 1_800))
        return policy
    }

    private data class Case(
        val snapshot: IdleHeadsetVolumeSnapshot,
        val interrupted: Boolean,
        val time: Long,
        val reason: String,
    )
}
