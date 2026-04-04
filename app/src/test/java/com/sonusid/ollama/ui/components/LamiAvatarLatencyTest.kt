package io.github.ninbyo02.lami.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LamiAvatarLatencyTest {
    @Test
    fun latencyMsToQualityLevel_mapsThresholds() {
        assertEquals(0, latencyMsToQualityLevel(null))
        assertEquals(4, latencyMsToQualityLevel(0L))
        assertEquals(4, latencyMsToQualityLevel(120L))
        assertEquals(3, latencyMsToQualityLevel(121L))
        assertEquals(3, latencyMsToQualityLevel(250L))
        assertEquals(2, latencyMsToQualityLevel(251L))
        assertEquals(2, latencyMsToQualityLevel(500L))
        assertEquals(1, latencyMsToQualityLevel(501L))
    }

    @Test
    fun formatLatencyText_formatsNullAndMillis() {
        assertEquals("--ms", formatLatencyText(null))
        assertEquals("84ms", formatLatencyText(84L))
    }
}
