package io.github.ninbyo02.lami.ui.screens.spriteeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftSkinOverlayTest {
    @Test
    fun minecraftSkinRegions_returnsOnlyForSupportedSquareSizes() {
        assertTrue(minecraftSkinRegions(63, 64).isEmpty())
        assertTrue(minecraftSkinRegions(64, 63).isEmpty())
        assertTrue(minecraftSkinRegions(64, 32).isEmpty())
        assertTrue(minecraftSkinRegions(128, 64).isEmpty())
        assertTrue(minecraftSkinRegions(256, 256).isEmpty())
        assertTrue(minecraftSkinRegions(64, 64).isNotEmpty())
        assertTrue(minecraftSkinRegions(128, 128).isNotEmpty())
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
    fun minecraftSkinRegions_128x128ExactlyScale64x64RegionsByTwo() {
        val regions64 = minecraftSkinRegions(64, 64)
        val regions128 = minecraftSkinRegions(128, 128)

        assertEquals(regions64.size, regions128.size)
        assertEquals(
            regions64.map { region ->
                MinecraftSkinRegion(
                    label = region.label,
                    x = region.x * 2,
                    y = region.y * 2,
                    w = region.w * 2,
                    h = region.h * 2,
                )
            },
            regions128,
        )
        regions128.forEach { region ->
            assertTrue(region.x + region.w <= 128)
            assertTrue(region.y + region.h <= 128)
        }
    }

    @Test
    fun minecraftSkinOverlayStrokeWidth_matchesAtEquivalent64And128PreviewSizes() {
        assertEquals(
            minecraftSkinOverlayStrokeWidth(bitmapWidth = 64, renderScale = 4f),
            minecraftSkinOverlayStrokeWidth(bitmapWidth = 128, renderScale = 2f),
            0f,
        )
        assertEquals(1f, minecraftSkinOverlayStrokeWidth(bitmapWidth = 64, renderScale = 1f), 0f)
        assertEquals(2.5f, minecraftSkinOverlayStrokeWidth(bitmapWidth = 128, renderScale = 8f), 0f)
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
    fun minecraftSkinPartLabelAt_128x128ReturnsScaledMainParts() {
        assertEquals("Head Front", minecraftSkinPartLabelAt(128, 128, 16, 16))
        assertEquals("Body Front", minecraftSkinPartLabelAt(128, 128, 40, 40))
        assertEquals("Right Arm Front", minecraftSkinPartLabelAt(128, 128, 88, 40))
        assertEquals("Left Arm Front", minecraftSkinPartLabelAt(128, 128, 72, 104))
        assertEquals("Left Arm Overlay Back", minecraftSkinPartLabelAt(128, 128, 120, 104))
    }

    @Test
    fun minecraftSkinPartLabelAt_usesHalfOpenRegionBounds() {
        assertEquals("Head Front", minecraftSkinPartLabelAt(64, 64, 15, 15))
        assertEquals("Head Left", minecraftSkinPartLabelAt(64, 64, 16, 15))
        assertEquals("Head Front", minecraftSkinPartLabelAt(128, 128, 31, 31))
        assertEquals("Head Left", minecraftSkinPartLabelAt(128, 128, 32, 31))
    }

    @Test
    fun minecraftSkinPartLabelAt_returnsNullForUnsupportedSizeOrOutsideRegions() {
        assertNull(minecraftSkinPartLabelAt(64, 32, 8, 8))
        assertNull(minecraftSkinPartLabelAt(128, 64, 16, 16))
        assertNull(minecraftSkinPartLabelAt(256, 256, 32, 32))
        assertNull(minecraftSkinPartLabelAt(64, 64, 63, 31))
        assertNull(minecraftSkinPartLabelAt(128, 128, 126, 62))
        assertNull(minecraftSkinPartLabelAt(64, 64, -1, 8))
    }
}
