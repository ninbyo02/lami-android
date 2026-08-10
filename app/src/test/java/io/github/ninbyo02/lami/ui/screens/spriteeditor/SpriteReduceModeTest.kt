package io.github.ninbyo02.lami.ui.screens.spriteeditor

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteReduceModeTest {

    @Test
    fun reduceChoices_keepApprovedOrderAndHideLegacyMode() {
        assertEquals(
            listOf(
                SpriteReduceChoice.ImageAdaptive,
                SpriteReduceChoice.FixedPalette,
                SpriteReduceChoice.Cancel,
            ),
            SPRITE_REDUCE_CHOICES,
        )
        assertEquals("Image Adaptive", SpriteReduceChoice.ImageAdaptive.label)
        assertEquals("Fixed Palette", SpriteReduceChoice.FixedPalette.label)
        assertEquals("Cancel", SpriteReduceChoice.Cancel.label)
        assertFalse(SPRITE_REDUCE_CHOICES.any { it.mode == SpriteReduceMode.LegacyFixedPaletteV1 })
    }

    @Test
    fun oldReduceToken_restoresLegacyV1ForCompatibility() {
        assertEquals(
            SpriteReduceRepeat(SpriteReduceMode.LegacyFixedPaletteV1),
            restoreSpriteReduceRepeat(listOf("ReduceTo256Colors")),
        )
    }

    @Test
    fun adaptiveRepeat_roundTripsOpaqueUniqueAnchorsDeterministically() {
        val anchors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val repeat = SpriteReduceRepeat(SpriteReduceMode.ImageAdaptive, anchors)

        val saved = saveSpriteReduceRepeat(repeat)
        val restored = restoreSpriteReduceRepeat(saved)

        assertEquals(repeat, restored)
        assertEquals(listOf("ReduceTo256ColorsV2", "ImageAdaptive", "FFFF0000", "FF00FF00", "FF0000FF"), saved)
        assertTrue(restored!!.rgbAnchors.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun fixedAndLegacyRepeat_neverCarryAdaptiveAnchors() {
        val anchors = listOf(Color.RED)

        assertEquals(
            SpriteReduceRepeat(SpriteReduceMode.FixedPaletteV2),
            SpriteReduceRepeat.create(SpriteReduceMode.FixedPaletteV2, anchors),
        )
        assertEquals(
            SpriteReduceRepeat(SpriteReduceMode.LegacyFixedPaletteV1),
            SpriteReduceRepeat.create(SpriteReduceMode.LegacyFixedPaletteV1, anchors),
        )
    }

    @Test
    fun malformedAdaptiveAnchors_failClosedWithoutReusePalette() {
        val transparent = 0x010A141E
        val malformed = listOf(
            "ReduceTo256ColorsV2",
            "ImageAdaptive",
            "%08X".format(transparent),
        )

        assertEquals(
            SpriteReduceRepeat(SpriteReduceMode.ImageAdaptive),
            restoreSpriteReduceRepeat(malformed),
        )
    }
}
