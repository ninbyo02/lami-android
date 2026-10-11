package io.github.ninbyo02.lami.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdleVolumeAnnouncementPolicyTest {
    private val baseline = IdleHeadsetVolumeSnapshot(181, true, 5, 90)

    @Test fun `one idle change announces actual percent only once after settling`() {
        val policy = IdleVolumeAnnouncementPolicy()
        assertNull(policy.update(baseline, 0, 150, 0))
        val changed = baseline.copy(mediaVolume = 100)
        assertNull(policy.update(changed, 0, 150, 200))
        assertNull(policy.update(changed, 0, 150, 800))
        assertEquals(HeadsetVolumeAnnouncement(70, 1_600), policy.update(changed, 0, 150, 1_600))
        assertNull(policy.update(changed, 0, 150, 1_800))
    }

    @Test fun `held button coalesces to last number`() {
        val policy = IdleVolumeAnnouncementPolicy()
        policy.update(baseline, 0, 150, 0)
        policy.update(baseline.copy(mediaVolume = 100), 0, 150, 200)
        policy.update(baseline.copy(mediaVolume = 110), 0, 150, 600)
        val final = baseline.copy(mediaVolume = 120)
        assertNull(policy.update(final, 0, 150, 1_000))
        assertNull(policy.update(final, 0, 150, 1_400))
        assertEquals(80, policy.update(final, 0, 150, 2_400)?.percent)
    }

    @Test fun `speech music or recording cancels pending announcement`() {
        val policy = IdleVolumeAnnouncementPolicy()
        policy.update(baseline, 0, 150, 0)
        val changed = baseline.copy(mediaVolume = 100)
        policy.update(changed, 0, 150, 200)
        assertNull(policy.update(changed.copy(eligible = false), 0, 150, 800))
        assertNull(policy.update(changed, 0, 150, 2_000))
        assertNull(policy.update(changed, 0, 150, 3_000))
    }

    @Test fun `disconnect route switch and pause cancel pending announcement`() {
        for (route in listOf<Int?>(null, 200)) {
            val policy = IdleVolumeAnnouncementPolicy()
            policy.update(baseline, 0, 150, 0)
            val changed = baseline.copy(mediaVolume = 100)
            policy.update(changed, 0, 150, 200)
            assertNull(policy.update(changed.copy(routeId = route), 0, 150, 1_000))
        }
        val policy = IdleVolumeAnnouncementPolicy()
        policy.update(baseline, 0, 150, 0)
        policy.update(baseline.copy(mediaVolume = 100), 0, 150, 200)
        policy.reset()
        assertNull(policy.update(baseline, 0, 150, 1_000))
    }

    @Test fun `percent uses device range and includes silent and maximum endpoints`() {
        for ((volume, expected) in listOf(2 to 0, 6 to 50, 10 to 80)) {
            val policy = IdleVolumeAnnouncementPolicy()
            policy.update(baseline, 2, 10, 0)
            val changed = baseline.copy(mediaVolume = volume)
            policy.update(changed, 2, 10, 200)
            assertEquals(expected, policy.update(changed, 2, 10, 1_600)?.percent)
        }
        assertEquals("音量、60パーセント。", HeadsetVolumeAnnouncement(60, 0).text)
    }
}
