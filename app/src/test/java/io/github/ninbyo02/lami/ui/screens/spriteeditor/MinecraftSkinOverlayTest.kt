package io.github.ninbyo02.lami.ui.screens.spriteeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftSkinOverlayTest {
    @Test
    fun minecraftSkinRegions_returnsOnlyFor64x64() {
        assertTrue(minecraftSkinRegions(63, 64).isEmpty())
        assertTrue(minecraftSkinRegions(64, 63).isEmpty())
        assertTrue(minecraftSkinRegions(64, 32).isEmpty())
        assertTrue(minecraftSkinRegions(64, 64).isNotEmpty())
    }

    @Test
    fun minecraftSkinRegions_stayInside64x64Bounds() {
        val regions = minecraftSkinRegions(64, 64)

        assertTrue(regions.isNotEmpty())
        regions.forEach { region ->
            assertTrue(region.x >= 0)
            assertTrue(region.y >= 0)
            assertTrue(region.w > 0)
            assertTrue(region.h > 0)
            assertTrue(region.x + region.w <= 64)
            assertTrue(region.y + region.h <= 64)
        }
    }

    @Test
    fun minecraftSkinPartLabelAt_returnsExpectedMainParts() {
        assertEquals("Head Front", minecraftSkinPartLabelAt(64, 64, 8, 8))
        assertEquals("Body Front", minecraftSkinPartLabelAt(64, 64, 20, 20))
        assertEquals("Right Arm Front", minecraftSkinPartLabelAt(64, 64, 44, 20))
        assertEquals("Left Arm Front", minecraftSkinPartLabelAt(64, 64, 36, 52))
        assertEquals("Left Arm Overlay Back", minecraftSkinPartLabelAt(64, 64, 60, 52))
    }

    @Test
    fun minecraftSkinPartLabelAt_usesHalfOpenRegionBounds() {
        assertEquals("Head Front", minecraftSkinPartLabelAt(64, 64, 15, 15))
        assertEquals("Head Left", minecraftSkinPartLabelAt(64, 64, 16, 15))
    }

    @Test
    fun minecraftSkinPartLabelAt_returnsNullForUnsupportedSizeOrOutsideRegions() {
        assertNull(minecraftSkinPartLabelAt(64, 32, 8, 8))
        assertNull(minecraftSkinPartLabelAt(64, 64, 63, 31))
        assertNull(minecraftSkinPartLabelAt(64, 64, -1, 8))
    }
}
