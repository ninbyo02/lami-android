package io.github.ninbyo02.lami.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SpriteBitmapOpsTest {
    @Test
    fun legacyFixedSpritePaletteV1_preservesExactCanonicalOrderAndValues() {
        val palette = LEGACY_FIXED_SPRITE_PALETTE_V1

        assertEquals(256, palette.size)
        assertEquals(256, palette.toSet().size)
        palette.forEach { color ->
            assertEquals(255, Color.alpha(color))
        }
        assertEquals(Color.rgb(0, 0, 0), palette.first())
        assertEquals(Color.rgb(255, 255, 255), palette[215])

        val cubeLevels = setOf(0, 51, 102, 153, 204, 255)
        val expectedCube = buildList {
            for (red in cubeLevels) {
                for (green in cubeLevels) {
                    for (blue in cubeLevels) {
                        add(Color.rgb(red, green, blue))
                    }
                }
            }
        }
        assertEquals(expectedCube, palette.take(216))

        val cubeGrays = cubeLevels.map { Color.rgb(it, it, it) }.toSet()
        val extraGrays = palette.drop(216)
        assertEquals(40, extraGrays.size)
        extraGrays.forEach { color ->
            assertEquals(Color.red(color), Color.green(color))
            assertEquals(Color.green(color), Color.blue(color))
            assertFalse(cubeGrays.contains(color))
        }
    }

    @Test
    fun fixedSpritePaletteV2_hasDeterministic256UniqueOpaquePerceptualRamps() {
        val palette = FIXED_SPRITE_PALETTE
        val sections = fixedSpritePaletteDisplaySections()

        assertEquals(256, palette.size)
        assertEquals(256, palette.toSet().size)
        assertTrue(palette.all { Color.alpha(it) == 255 })
        assertEquals(
            listOf("Grayscale", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Magenta"),
            sections.map { it.label },
        )
        assertEquals(listOf(32, 28, 28, 28, 28, 28, 28, 28, 28), sections.map { it.colors.size })
        assertEquals(palette, sections.flatMap { it.colors })
        assertEquals(Color.BLACK, sections.first().colors.first())
        assertEquals(Color.WHITE, sections.first().colors.last())
        assertTrue(sections.first().colors.zipWithNext().all { (left, right) -> Color.red(left) < Color.red(right) })
    }

    @Test
    fun fixedSpritePaletteV2_keepsBasicEightHueAnchorsInTheirSections() {
        val sections = fixedSpritePaletteDisplaySections().associate { it.label to it.colors }

        assertTrue(sections.getValue("Red").contains(Color.RED))
        assertTrue(sections.getValue("Orange").contains(Color.rgb(255, 128, 0)))
        assertTrue(sections.getValue("Yellow").contains(Color.YELLOW))
        assertTrue(sections.getValue("Green").contains(Color.GREEN))
        assertTrue(sections.getValue("Cyan").contains(Color.CYAN))
        assertTrue(sections.getValue("Blue").contains(Color.BLUE))
        assertTrue(sections.getValue("Purple").contains(Color.rgb(128, 0, 255)))
        assertTrue(sections.getValue("Magenta").contains(Color.MAGENTA))
    }

    @Test
    fun fixedSpritePaletteV2_mapsRepresentativeSkinColorsToChromaticColors() {
        listOf(
            Color.rgb(234, 182, 161),
            Color.rgb(235, 181, 169),
            Color.rgb(255, 226, 189),
            Color.rgb(198, 134, 66),
            Color.rgb(141, 85, 36),
        ).forEach { skin ->
            val mapped = nearestFixedPaletteColor(skin)
            assertFalse("skin=${spriteEditorPaletteHexColor(skin)} mapped=${spriteEditorPaletteHexColor(mapped)}", Color.red(mapped) == Color.green(mapped) && Color.green(mapped) == Color.blue(mapped))
        }
    }

    @Test
    fun fixedSpritePalette_identityIsSharedAcrossCalls() {
        assertTrue(FIXED_SPRITE_PALETTE === fixedSpritePalette())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun fixedSpritePalette_isUnmodifiable() {
        (FIXED_SPRITE_PALETTE as MutableList<Int>).add(Color.RED)
    }

    @Test
    fun fixedSpritePaletteDisplaySections_preserveEveryCanonicalColorExactlyOnce() {
        val sections = fixedSpritePaletteDisplaySections()
        val displayed = sections.flatMap { it.colors }

        assertEquals(
            listOf("Grayscale", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Magenta"),
            sections.map { it.label },
        )
        assertEquals(256, displayed.size)
        assertEquals(256, displayed.distinct().size)
        assertEquals(FIXED_SPRITE_PALETTE.toSet(), displayed.toSet())
    }

    @Test
    fun fixedSpritePaletteDisplaySections_putExactGraysFirstFromBlackToWhite() {
        val grayscale = fixedSpritePaletteDisplaySections().first()

        assertTrue(grayscale.colors.all { color ->
            Color.red(color) == Color.green(color) && Color.green(color) == Color.blue(color)
        })
        assertEquals(Color.BLACK, grayscale.colors.first())
        assertEquals(Color.WHITE, grayscale.colors.last())
        assertEquals(grayscale.colors.map { Color.red(it) }.sorted(), grayscale.colors.map { Color.red(it) })
    }

    @Test
    fun fixedSpritePaletteDisplaySections_groupRepresentativeColorsByPerceptualHue() {
        val sections = fixedSpritePaletteDisplaySections().associate { it.label to it.colors }

        assertTrue(sections.getValue("Red").contains(Color.RED))
        assertTrue(sections.getValue("Orange").contains(Color.rgb(255, 128, 0)))
        assertTrue(sections.getValue("Yellow").contains(Color.YELLOW))
        assertTrue(sections.getValue("Green").contains(Color.GREEN))
        assertTrue(sections.getValue("Cyan").contains(Color.CYAN))
        assertTrue(sections.getValue("Blue").contains(Color.BLUE))
        assertTrue(sections.getValue("Purple").contains(Color.rgb(128, 0, 255)))
        assertTrue(sections.getValue("Magenta").contains(Color.MAGENTA))
    }

    @Test
    fun fixedSpritePaletteDisplaySections_preserveCanonicalContiguousSectionOrder() {
        val sections = fixedSpritePaletteDisplaySections()

        assertEquals(FIXED_SPRITE_PALETTE, sections.flatMap { it.colors })
        assertEquals(FIXED_SPRITE_PALETTE.subList(0, 32), sections[0].colors)
        sections.drop(1).forEachIndexed { index, section ->
            val start = 32 + index * 28
            assertEquals(FIXED_SPRITE_PALETTE.subList(start, start + 28), section.colors)
        }
    }

    @Test
    fun nearestFixedPaletteColor_mapsExactAndNearColorsDeterministically() {
        assertEquals(Color.RED, nearestFixedPaletteColor(Color.RED))
        assertEquals(Color.rgb(255, 128, 0), nearestFixedPaletteColor(Color.rgb(255, 128, 0)))

        val first = nearestFixedPaletteColor(Color.rgb(17, 18, 19))
        repeat(10) {
            assertEquals(first, nearestFixedPaletteColor(Color.rgb(17, 18, 19)))
        }
        assertTrue(FIXED_SPRITE_PALETTE.contains(first))
    }

    @Test
    fun legacyAndV2NearestIndexes_areDeterministicAndCacheIsolated() {
        val sampled = Color.rgb(52, 100, 151)
        val legacy = Color.rgb(51, 102, 153)
        val v2 = nearestFixedPaletteColor(sampled)

        assertNotEquals(legacy, v2)
        repeat(4) {
            assertEquals(legacy, nearestLegacyFixedPaletteColor(sampled))
            assertEquals(v2, nearestFixedPaletteColor(sampled))
            assertEquals(legacy, nearestLegacyFixedPaletteColor(sampled))
        }
        assertTrue(LEGACY_FIXED_SPRITE_PALETTE_V1.contains(legacy))
        assertTrue(FIXED_SPRITE_PALETTE.contains(v2))
    }

    @Test
    fun reduceToLegacyFixedPalette_preservesOldMappingAndSourceAlpha() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(128, 52, 100, 151))

        val result = reduceToLegacyFixedPalette(bitmap)

        assertTrue(result.changed)
        assertEquals(Color.argb(128, 51, 102, 153), result.bitmap.getPixel(0, 0))
    }

    @Test
    fun reduceToFixedPalette_preservesDimensionsAlphaAndTransparency() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(255, 52, 100, 151))
        bitmap.setPixel(1, 0, Color.argb(128, 52, 100, 151))
        bitmap.setPixel(0, 1, Color.TRANSPARENT)
        bitmap.setPixel(1, 1, 0x00112233)

        val result = reduceToFixedPalette(bitmap)

        assertTrue(result.changed)
        assertEquals(2, result.bitmap.width)
        assertEquals(2, result.bitmap.height)
        val expected = nearestFixedPaletteColor(Color.rgb(52, 100, 151))
        assertEquals(Color.argb(255, Color.red(expected), Color.green(expected), Color.blue(expected)), result.bitmap.getPixel(0, 0))
        assertEquals(Color.argb(128, Color.red(expected), Color.green(expected), Color.blue(expected)), result.bitmap.getPixel(1, 0))
        assertEquals(Color.TRANSPARENT, result.bitmap.getPixel(0, 1))
        assertEquals(0, Color.alpha(result.bitmap.getPixel(1, 1)))
    }

    @Test
    fun reduceToFixedPalette_isIdempotentForLowAndMidAlphaPremultipliedPixels() {
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(1, 52, 100, 151))
        bitmap.setPixel(1, 0, Color.argb(17, 52, 100, 151))
        bitmap.setPixel(2, 0, Color.argb(128, 52, 100, 151))

        val first = reduceToFixedPalette(bitmap)
        val second = reduceToFixedPalette(first.bitmap)

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertEquals(pixelsOf(first.bitmap).toList(), pixelsOf(second.bitmap).toList())
        for (x in 0 until 3) {
            assertEquals(Color.alpha(first.bitmap.getPixel(x, 0)), Color.alpha(second.bitmap.getPixel(x, 0)))
        }
    }

    @Test
    fun reduceToFixedPalette_alpha4RegressionPreservesPaletteRepresentablePhysicalGreen() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(4, 0, 255, 0))
        val before = bitmap.getPixel(0, 0)

        val first = reduceToFixedPalette(bitmap)
        val second = reduceToFixedPalette(first.bitmap)

        assertFalse(first.changed)
        assertSame(bitmap, first.bitmap)
        assertEquals(before, first.bitmap.getPixel(0, 0))
        assertFalse(second.changed)
        assertSame(first.bitmap, second.bitmap)
        assertEquals(before, second.bitmap.getPixel(0, 0))
    }

    @Test
    fun reduceToFixedPalette_isIdempotentForEveryAlphaAndFixedPalettePhysicalState() {
        for (alpha in 1..255) {
            val bitmap = Bitmap.createBitmap(FIXED_SPRITE_PALETTE.size, 1, Bitmap.Config.ARGB_8888)
            FIXED_SPRITE_PALETTE.forEachIndexed { index, color ->
                bitmap.setPixel(
                    index,
                    0,
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                )
            }
            val before = pixelsOf(bitmap).toList()

            val first = reduceToFixedPalette(bitmap)
            val second = reduceToFixedPalette(first.bitmap)

            assertFalse("alpha=$alpha should be a physical no-op", first.changed)
            assertSame(bitmap, first.bitmap)
            assertEquals("alpha=$alpha first pixels", before, pixelsOf(first.bitmap).toList())
            assertFalse("alpha=$alpha second pass changed", second.changed)
            assertSame(first.bitmap, second.bitmap)
            assertEquals("alpha=$alpha second pixels", before, pixelsOf(second.bitmap).toList())
        }
    }

    @Test
    fun reduceToFixedPalette_nonPremultipliedBitmapUsesRawRgbNotPhysicalEquivalence() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.setPixel(0, 0, Color.argb(4, 0, 128, 0))
        bitmap.setPixel(1, 0, Color.argb(4, 0, 255, 0))

        val result = reduceToFixedPalette(bitmap)

        assertTrue(result.changed)
        assertNotEquals(bitmap.getPixel(0, 0), result.bitmap.getPixel(0, 0))
        assertEquals(Color.argb(4, 0, 255, 0), result.bitmap.getPixel(1, 0))
        assertFalse(result.bitmap.isPremultiplied)
        val second = reduceToFixedPalette(result.bitmap)
        assertFalse(second.changed)
        assertSame(result.bitmap, second.bitmap)
    }

    @Test
    fun reduceToFixedPalette_rejectsRecycledBitmapWithoutCrash() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val result = reduceToFixedPalette(bitmap)

        assertTrue(result.rejected)
        assertEquals(PaletteBitmapRejectionReason.RECYCLED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun reduceToFixedPalette_handlesHardwareBitmapWithoutCrash() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.HARDWARE)
        assertEquals(Bitmap.Config.HARDWARE, bitmap.config)

        val result = reduceToFixedPalette(bitmap)

        assertEquals(PaletteBitmapRejectionReason.NONE, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun reduceToLegacyFixedPalette_handlesHardwareBitmapWithoutCrash() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.HARDWARE)
        assertEquals(Bitmap.Config.HARDWARE, bitmap.config)

        val result = reduceToLegacyFixedPalette(bitmap)

        assertEquals(PaletteBitmapRejectionReason.NONE, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun reduceToFixedPalette_rowBufferOomFailsClosed() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val before = pixelsOf(bitmap)

        val result = reduceToFixedPalette(
            bitmap,
            rowBufferAllocator = { throw OutOfMemoryError("test row buffer") },
        )

        assertEquals(PaletteBitmapRejectionReason.COPY_FAILED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertFalse(bitmap.isRecycled)
        assertArrayEquals(before, pixelsOf(bitmap))
    }

    @Test
    fun reduceToLegacyFixedPalette_rowBufferOomFailsClosed() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val before = pixelsOf(bitmap)

        val result = reduceToLegacyFixedPalette(
            bitmap,
            rowBufferAllocator = { throw OutOfMemoryError("test row buffer") },
        )

        assertEquals(PaletteBitmapRejectionReason.COPY_FAILED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertFalse(bitmap.isRecycled)
        assertArrayEquals(before, pixelsOf(bitmap))
    }

    @Test
    fun reduceToFixedPalette_cancelsBeforeApplyingRowAndRecyclesNewOutput() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(52, 100, 151))

        val result = reduceToFixedPalette(bitmap) { row -> row >= 1 }

        assertTrue(result.cancelled)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun reduceToFixedPalette_noOpDetectionAndSourceImmutability() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.argb(128, 204, 204, 204))
        val before = pixelsOf(bitmap)

        val result = reduceToFixedPalette(bitmap)

        assertFalse(result.changed)
        assertEquals(before.toList(), pixelsOf(result.bitmap).toList())
        assertEquals(before.toList(), pixelsOf(bitmap).toList())
    }

    @Test
    fun reduceToFixedPalette_rejectsImagesAboveSpriteEditingGuard() {
        val bitmap = Bitmap.createBitmap(2049, 2048, Bitmap.Config.ARGB_8888)

        val result = reduceToFixedPalette(bitmap)

        assertTrue(result.rejected)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun reduceToFixedPalette_sameInputProducesSameResult() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(
            Color.rgb(1, 2, 3),
            Color.rgb(10, 200, 40),
            Color.rgb(230, 20, 120),
            Color.argb(128, 18, 52, 241),
            Color.TRANSPARENT,
            Color.rgb(250, 250, 250),
        )
        bitmap.setPixels(colors, 0, 3, 0, 0, 3, 2)

        val first = reduceToFixedPalette(bitmap)
        val second = reduceToFixedPalette(bitmap)

        assertEquals(pixelsOf(first.bitmap).toList(), pixelsOf(second.bitmap).toList())
        assertEquals(first.changed, second.changed)
    }

    @Test
    fun fillSelectionWithColor_fillsOnlySelectionAndPreservesSelectionContract() {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.RED)
        val before = pixelsOf(bitmap)
        val selection = RectPx.of(1, 1, 2, 1)

        val result = fillSelectionWithColor(bitmap, selection, Color.rgb(51, 102, 153))

        assertTrue(result.changed)
        assertEquals(4, result.bitmap.width)
        assertEquals(3, result.bitmap.height)
        assertEquals(Color.RED, result.bitmap.getPixel(0, 0))
        assertEquals(Color.rgb(51, 102, 153), result.bitmap.getPixel(1, 1))
        assertEquals(Color.rgb(51, 102, 153), result.bitmap.getPixel(2, 1))
        assertEquals(Color.TRANSPARENT, result.bitmap.getPixel(3, 1))
        assertEquals(before.toList(), pixelsOf(bitmap).toList())
    }

    @Test
    fun fillSelectionWithColor_rowBufferOomFailsClosed() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val before = pixelsOf(bitmap)

        val result = fillSelectionWithColor(
            bitmap,
            RectPx.of(0, 0, 2, 1),
            Color.BLUE,
            rowBufferAllocator = { throw OutOfMemoryError("test row buffer") },
        )

        assertEquals(PaletteBitmapRejectionReason.COPY_FAILED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertFalse(bitmap.isRecycled)
        assertArrayEquals(before, pixelsOf(bitmap))
    }

    @Test
    fun fillSelectionWithColor_noOpWhenPixelsAlreadyMatch() {
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(1, 0, Color.rgb(51, 102, 153))
        bitmap.setPixel(2, 0, Color.BLACK)

        val result = fillSelectionWithColor(
            bitmap,
            RectPx.of(1, 0, 1, 1),
            Color.rgb(51, 102, 153),
        )

        assertFalse(result.changed)
        assertEquals(pixelsOf(bitmap).toList(), pixelsOf(result.bitmap).toList())
    }

    @Test
    fun fillSelectionWithColor_preservesSelectedRgbaAlpha() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val sampled = Color.argb(96, 23, 45, 67)

        val result = fillSelectionWithColor(bitmap, RectPx.of(0, 0, 1, 1), sampled)

        assertTrue(result.changed)
        assertEquals(sampled, result.bitmap.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, result.bitmap.getPixel(1, 0))
    }

    @Test
    fun fillSelectionWithColor_rejectsImagesAboveSpriteEditingGuard() {
        val bitmap = Bitmap.createBitmap(2049, 2048, Bitmap.Config.ARGB_8888)

        val result = fillSelectionWithColor(bitmap, RectPx.of(0, 0, 1, 1), Color.WHITE)

        assertTrue(result.rejected)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun previewOffsetToBitmapPixel_handlesNonSquareFractionalPanZoomEdgesAndOutside() {
        val viewSize = IntSize(width = 101, height = 101)
        val bitmapWidth = 10
        val bitmapHeight = 5
        val displayScale = 0.5f
        val panOffset = Offset(0.75f, -0.25f)

        // fitScale=10.1, renderScale=5.05, renderLeft=26.0, renderTop=37.625.
        assertEquals(
            androidx.compose.ui.unit.IntOffset(0, 0),
            previewOffsetToBitmapPixel(
                position = Offset(26.01f, 37.635f),
                viewSize = viewSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                displayScale = displayScale,
                panOffset = panOffset,
            ),
        )
        assertEquals(
            androidx.compose.ui.unit.IntOffset(9, 4),
            previewOffsetToBitmapPixel(
                position = Offset(76.49f, 62.865f),
                viewSize = viewSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                displayScale = displayScale,
                panOffset = panOffset,
            ),
        )
        assertEquals(
            androidx.compose.ui.unit.IntOffset(5, 2),
            previewOffsetToBitmapPixel(
                position = Offset(51.35f, 47.825f),
                viewSize = viewSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                displayScale = displayScale,
                panOffset = panOffset,
            ),
        )
        assertEquals(
            null,
            previewOffsetToBitmapPixel(
                position = Offset(25.99f, 37.635f),
                viewSize = viewSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                displayScale = displayScale,
                panOffset = panOffset,
            ),
        )
        assertEquals(
            null,
            previewOffsetToBitmapPixel(
                position = Offset(76.50f, 62.865f),
                viewSize = viewSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                displayScale = displayScale,
                panOffset = panOffset,
            ),
        )
    }

    @Test
    fun spriteEditorToolsSheetOrder_preservesApprovedFillOrdering() {
        val labels = spriteEditorToolsSheetItems().map { it.label }

        assertTrue(labels.indexOf("Fill Connected") < labels.indexOf("Fill Selection"))
        assertTrue(labels.indexOf("Fill Selection") < labels.indexOf("Clear Region"))
    }

    @Test
    fun selectSpriteEditorCurrentColor_keepsCurrentSeparateFromEightRecentColors() {
        val initial = SpriteEditorColorHistory(
            currentColor = Color.BLACK,
            recentColors = (0 until 8).map { Color.rgb(it, it, it) },
        )

        val updated = initial.select(Color.RED)

        assertEquals(Color.RED, updated.currentColor)
        assertEquals(8, updated.recentColors.size)
        assertEquals(Color.BLACK, updated.recentColors.first())
        assertFalse(updated.recentColors.contains(updated.currentColor))
    }

    @Test
    fun selectSpriteEditorCurrentColor_reselectingCurrentColorDoesNotChangeHistory() {
        val initial = SpriteEditorColorHistory(
            currentColor = Color.RED,
            recentColors = listOf(Color.BLUE, Color.GREEN),
        )

        assertEquals(initial, initial.select(Color.RED))
    }

    @Test
    fun selectSpriteEditorCurrentColor_ordersDeduplicatesAndCapsPreviousColors() {
        var history = SpriteEditorColorHistory(Color.BLACK, emptyList())
        val colors = (1..10).map { Color.rgb(it, it * 2, it * 3) }
        colors.forEach { color -> history = history.select(color) }
        history = history.select(colors[5])

        assertEquals(colors[5], history.currentColor)
        assertEquals(8, history.recentColors.size)
        assertEquals(colors.last(), history.recentColors.first())
        assertFalse(history.recentColors.contains(history.currentColor))
        assertEquals(history.recentColors.size, history.recentColors.distinct().size)
    }

    @Test
    fun selectSpriteEditorCurrentColor_preservesRgbaAndTreatsDifferentAlphaAsDifferentColors() {
        val opaque = Color.argb(255, 20, 40, 60)
        val translucent = Color.argb(96, 20, 40, 60)
        val initial = SpriteEditorColorHistory(currentColor = opaque, recentColors = emptyList())

        val updated = initial.select(translucent)

        assertEquals(translucent, updated.currentColor)
        assertEquals(listOf(opaque), updated.recentColors)
        assertEquals(updated, updated.select(translucent))
    }

    @Test
    fun colorPaletteSelection_keepsSheetOpenAndShowsVisibleSelectionRing() {
        assertFalse(DISMISS_COLOR_PALETTE_AFTER_SELECTION)
        assertEquals(3, spriteEditorPaletteSelectionRingWidthDp(selected = true))
        assertEquals(null, spriteEditorPaletteSelectionRingWidthDp(selected = false))
    }

    @Test
    fun eyedropperPointSample_rejectsTransparentPixelsIncludingHiddenRgb() {
        assertEquals(null, eyedropperPaletteColorForSample(Color.TRANSPARENT))
        assertEquals(null, eyedropperPaletteColorForSample(0x00112233))
    }

    @Test
    fun eyedropperPointSample_preservesExactNonTransparentRgba() {
        val sampled = Color.argb(1, 52, 100, 151)
        assertEquals(sampled, eyedropperPaletteColorForSample(sampled))
    }

    @Test
    fun eyedropperSelectionDecision_uniformSelectsColorWithoutTapFallback() {
        val sampled = Color.argb(128, 52, 100, 151)
        val decision = decideEyedropperSelectionResult(
            UniformSelectionColorResult(UniformSelectionColorStatus.UNIFORM, sampled),
        )

        assertEquals(sampled, decision.selectedColor)
        assertFalse(decision.activateTapFallback)
        assertEquals("Color selected from box", decision.message)
    }

    @Test
    fun eyedropperSelectionDecision_mixedUsesTapAndTransparentDoesNotSelect() {
        val mixed = decideEyedropperSelectionResult(
            UniformSelectionColorResult(UniformSelectionColorStatus.MIXED),
        )
        val transparent = decideEyedropperSelectionResult(
            UniformSelectionColorResult(UniformSelectionColorStatus.TRANSPARENT),
        )

        assertEquals(null, mixed.selectedColor)
        assertTrue(mixed.activateTapFallback)
        assertEquals("Selection contains multiple colors. Tap a pixel.", mixed.message)
        assertEquals(null, transparent.selectedColor)
        assertFalse(transparent.activateTapFallback)
        assertEquals("Selection is transparent", transparent.message)
    }

    @Test
    fun findUniformSelectionColor_returnsExactRgbaInsideBoxAndIgnoresOutside() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val selected = Color.argb(128, 20, 40, 60)
        bitmap.eraseColor(Color.YELLOW)
        bitmap.setPixel(1, 0, selected)
        bitmap.setPixel(2, 0, selected)

        val result = findUniformSelectionColor(bitmap, RectPx.of(1, 0, 2, 1))

        assertEquals(UniformSelectionColorStatus.UNIFORM, result.status)
        assertEquals(selected, result.color)
    }

    @Test
    fun findUniformSelectionColor_reportsMixedWithoutChoosingMajorityColor() {
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.RED)
        bitmap.setPixel(2, 0, Color.BLUE)

        val result = findUniformSelectionColor(bitmap, RectPx.of(0, 0, 3, 1))

        assertEquals(UniformSelectionColorStatus.MIXED, result.status)
        assertEquals(null, result.color)
    }

    @Test
    fun findUniformSelectionColor_reportsFullyTransparentSelection() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val result = findUniformSelectionColor(bitmap, RectPx.of(0, 0, 2, 2))

        assertEquals(UniformSelectionColorStatus.TRANSPARENT, result.status)
        assertEquals(null, result.color)
    }

    @Test
    fun findUniformSelectionColor_treatsDifferentHiddenRgbAsFullyTransparent() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.setPixel(0, 0, 0x00112233)
        bitmap.setPixel(1, 0, 0x00445566)

        val result = findUniformSelectionColor(bitmap, RectPx.of(0, 0, 2, 1))

        assertEquals(UniformSelectionColorStatus.TRANSPARENT, result.status)
        assertEquals(null, result.color)
    }

    @Test
    fun findUniformSelectionColor_rowBufferOomFailsClosed() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val before = pixelsOf(bitmap)

        val result = findUniformSelectionColor(
            bitmap,
            RectPx.of(0, 0, 2, 1),
            rowBufferAllocator = { throw OutOfMemoryError("test row buffer") },
        )

        assertEquals(UniformSelectionColorStatus.READ_FAILED, result.status)
        assertEquals(null, result.color)
        assertFalse(bitmap.isRecycled)
        assertArrayEquals(before, pixelsOf(bitmap))
    }

    @Test
    fun findUniformSelectionColor_honorsRowCancellation() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val result = findUniformSelectionColor(
            bitmap,
            RectPx.of(0, 0, 2, 3),
            shouldCancel = { row -> row >= 1 },
        )

        assertEquals(UniformSelectionColorStatus.CANCELLED, result.status)
        assertEquals(null, result.color)
    }

    @Test
    fun paletteSwatchSemantics_keepsContentDescriptionSeparateFromStableTestTag() {
        val semantics = spriteEditorPaletteSwatchSemantics(
            label = "Palette Color 7",
            color = Color.rgb(1, 10, 255),
            currentColor = Color.BLACK,
            testTag = "spriteEditorPaletteColor7",
        )

        assertEquals("Palette Color 7 #010AFF", semantics.contentDescription)
        assertEquals("spriteEditorPaletteColor7", semantics.testTag)
        assertFalse(semantics.selected)
    }

    @Test
    fun paletteSwatchSemantics_requiresExactRgbaMatchForSelection() {
        val semantics = spriteEditorPaletteSwatchSemantics(
            label = "Recent Color 1",
            color = Color.argb(128, 51, 102, 153),
            currentColor = Color.rgb(51, 102, 153),
            testTag = "spriteEditorRecentColor0",
        )

        assertEquals("Recent Color 1 #80336699", semantics.contentDescription)
        assertFalse(semantics.selected)
    }

    @Test
    fun paletteSwatchSemantics_formatsAndSelectsExactTranslucentRgba() {
        val sampled = Color.argb(128, 1, 10, 255)
        val semantics = spriteEditorPaletteSwatchSemantics(
            label = "Current Color",
            color = sampled,
            currentColor = sampled,
            testTag = "spriteEditorCurrentColor",
        )

        assertEquals("Current Color #80010AFF", semantics.contentDescription)
        assertTrue(semantics.selected)
    }

    @Test
    fun paletteBitmapResultOwner_publishNewThenCloseRecyclesNewBitmap() {
        val source = testBitmap()
        val created = testBitmap()
        val owner = PaletteBitmapResultOwner(source)

        owner.publish(PaletteBitmapResult(created, changed = true))
        owner.close()

        assertFalse(source.isRecycled)
        assertTrue(created.isRecycled)
    }

    @Test
    fun paletteBitmapResultOwner_publishSourceThenClosePreservesSourceBitmap() {
        val source = testBitmap()
        val owner = PaletteBitmapResultOwner(source)

        owner.publish(PaletteBitmapResult(source, changed = false))
        owner.close()

        assertFalse(source.isRecycled)
    }

    @Test
    fun paletteBitmapResultOwner_takeAdoptThenClosePreservesAdoptedBitmap() {
        val source = testBitmap()
        val adopted = testBitmap()
        val owner = PaletteBitmapResultOwner(source)

        owner.publish(PaletteBitmapResult(adopted, changed = true))
        val taken = owner.take()
        owner.close()

        assertSame(adopted, taken?.bitmap)
        assertFalse(source.isRecycled)
        assertFalse(adopted.isRecycled)
    }

    @Test
    fun paletteBitmapResultOwner_closeRecyclesRejectedNoOpNewBitmap() {
        val source = testBitmap()
        val rejectedNewBitmap = testBitmap()
        val owner = PaletteBitmapResultOwner(source)

        owner.publish(
            PaletteBitmapResult(
                rejectedNewBitmap,
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.CANCELLED,
            ),
        )
        owner.close()

        assertFalse(source.isRecycled)
        assertTrue(rejectedNewBitmap.isRecycled)
    }

    @Test
    fun paletteBitmapApplicationDecision_adoptsOnlyCurrentChangedAcceptedResult() {
        val decision = decidePaletteBitmapApplication(
            currentUnchanged = true,
            result = PaletteBitmapResult(testBitmap(), changed = true),
            appliedMessage = "Reduced to 256 colors",
        )

        assertTrue(decision.adopted)
        assertEquals("Reduced to 256 colors", decision.message)
    }

    @Test
    fun paletteBitmapApplicationDecision_rejectsStaleResultWithUnchangedMessage() {
        val decision = decidePaletteBitmapApplication(
            currentUnchanged = false,
            result = PaletteBitmapResult(testBitmap(), changed = true),
            appliedMessage = "Reduced to 256 colors",
        )

        assertFalse(decision.adopted)
        assertEquals("Sprite changed; operation skipped", decision.message)
    }

    @Test
    fun paletteBitmapApplicationDecision_rejectsNoOpWithCallerMessage() {
        val decision = decidePaletteBitmapApplication(
            currentUnchanged = true,
            result = PaletteBitmapResult(testBitmap(), changed = false),
            unchangedMessage = "No pixels changed",
            appliedMessage = "Selection filled",
        )

        assertFalse(decision.adopted)
        assertEquals("No pixels changed", decision.message)
    }

    @Test
    fun paletteBitmapApplicationDecision_rejectsRejectedResultWithExistingUiMessage() {
        val decision = decidePaletteBitmapApplication(
            currentUnchanged = true,
            result = PaletteBitmapResult(
                testBitmap(),
                changed = false,
                rejectionReason = PaletteBitmapRejectionReason.TOO_LARGE,
            ),
            appliedMessage = "Selection filled",
        )

        assertFalse(decision.adopted)
        assertEquals("Image too large for sprite operation (max 4,194,304 pixels)", decision.message)
    }

    @Test
    fun noOpHistoryDecision_usesChangedAndRejectedResultContract() {
        assertTrue(shouldPushHistoryForPaletteBitmapResult(PaletteBitmapResult(testBitmap(), changed = true)))
        assertFalse(shouldPushHistoryForPaletteBitmapResult(PaletteBitmapResult(testBitmap(), changed = false)))
        assertFalse(
            shouldPushHistoryForPaletteBitmapResult(
                PaletteBitmapResult(
                    testBitmap(),
                    changed = false,
                    rejectionReason = PaletteBitmapRejectionReason.TOO_LARGE,
                ),
            ),
        )
    }

    @Test
    fun rectNormalizeClamp_clampsToImageBounds() {
        val rect = RectPx.of(x = -4, y = 10, w = 40, h = 40)
        val normalized = rectNormalizeClamp(rect, imageW = 16, imageH = 12)

        assertEquals(0, normalized.x)
        assertEquals(0, normalized.y)
        assertEquals(16, normalized.w)
        assertEquals(12, normalized.h)
    }

    @Test
    fun clearTransparent_clearsOnlyTargetArea() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val cleared = clearTransparent(bitmap, RectPx.of(0, 0, 1, 1))

        val clearedPixel = cleared.getPixel(0, 0)
        val untouchedPixel = cleared.getPixel(1, 1)

        assertEquals(0, Color.alpha(clearedPixel))
        assertTrue(Color.alpha(untouchedPixel) > 0)
    }

    @Test
    fun resizeSelectionToMax64And128_haveDedicatedPublicOperations() {
        val methodNames = Class.forName(
            "io.github.ninbyo02.lami.ui.screens.spriteeditor.SpriteBitmapOpsKt",
        ).declaredMethods.map { it.name }.toSet()

        assertTrue("resizeSelectionToMax64 operation is missing", "resizeSelectionToMax64" in methodNames)
        assertTrue("resizeSelectionToMax128 operation is missing", "resizeSelectionToMax128" in methodNames)
    }

    @Test
    fun resizeSelectionToMax64And128_atOrBelowThresholdAreNoOpAndKeepCanvas() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val cases = listOf(
            64 to RectPx.of(10, 20, 64, 40),
            128 to RectPx.of(30, 40, 100, 128),
        )

        for ((target, selection) in cases) {
            val result = when (target) {
                64 -> resizeSelectionToMax64(bitmap, selection)
                else -> resizeSelectionToMax128(bitmap, selection)
            }

            assertEquals("target=$target", false, result.applied)
            assertEquals("target=$target", selection, result.selection)
            assertEquals("target=$target canvas width", 320, result.bitmap.width)
            assertEquals("target=$target canvas height", 240, result.bitmap.height)
        }
    }

    @Test
    fun resizeSelectionToMax64And128_wideSelectionPreservesAspectRatioAndCanvas() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val cases = listOf(
            Triple(64, RectPx.of(10, 20, 160, 80), RectPx.of(10, 20, 64, 32)),
            Triple(128, RectPx.of(10, 20, 320, 160), RectPx.of(10, 20, 128, 64)),
        )

        for ((target, selection, expected) in cases) {
            val result = when (target) {
                64 -> resizeSelectionToMax64(bitmap, selection)
                else -> resizeSelectionToMax128(bitmap, selection)
            }

            assertEquals("target=$target", true, result.applied)
            assertEquals("target=$target", expected, result.selection)
            assertEquals("target=$target canvas width", 400, result.bitmap.width)
            assertEquals("target=$target canvas height", 400, result.bitmap.height)
        }
    }

    @Test
    fun resizeSelectionToMax64And128_tallSelectionPreservesAspectRatioAndCanvas() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val cases = listOf(
            Triple(64, RectPx.of(10, 20, 80, 160), RectPx.of(10, 20, 32, 64)),
            Triple(128, RectPx.of(10, 20, 160, 320), RectPx.of(10, 20, 64, 128)),
        )

        for ((target, selection, expected) in cases) {
            val result = when (target) {
                64 -> resizeSelectionToMax64(bitmap, selection)
                else -> resizeSelectionToMax128(bitmap, selection)
            }

            assertEquals("target=$target", true, result.applied)
            assertEquals("target=$target", expected, result.selection)
            assertEquals("target=$target canvas width", 400, result.bitmap.width)
            assertEquals("target=$target canvas height", 400, result.bitmap.height)
        }
    }

    @Test
    fun resizeSelectionToMax64And128_topLeftAndCenterAnchorsMatchSharedContract() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val selection = RectPx.of(20, 30, 256, 128)
        val cases = listOf(
            Triple(64, RectPx.of(20, 30, 64, 32), RectPx.of(116, 78, 64, 32)),
            Triple(128, RectPx.of(20, 30, 128, 64), RectPx.of(84, 62, 128, 64)),
        )

        for ((target, expectedTopLeft, expectedCenter) in cases) {
            val topLeft = when (target) {
                64 -> resizeSelectionToMax64(bitmap, selection, anchor = ResizeAnchor.TopLeft)
                else -> resizeSelectionToMax128(bitmap, selection, anchor = ResizeAnchor.TopLeft)
            }
            val center = when (target) {
                64 -> resizeSelectionToMax64(bitmap, selection, anchor = ResizeAnchor.Center)
                else -> resizeSelectionToMax128(bitmap, selection, anchor = ResizeAnchor.Center)
            }

            assertEquals("target=$target top-left", expectedTopLeft, topLeft.selection)
            assertEquals("target=$target center", expectedCenter, center.selection)
        }
    }

    @Test
    fun resizeSelectionToMax288_smallerSelectionIsNotApplied() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val result = resizeSelectionToMax288(bitmap, RectPx.of(10, 20, 200, 100))

        assertEquals(false, result.applied)
        assertEquals(RectPx.of(10, 20, 200, 100), result.selection)
        assertEquals(300, result.bitmap.width)
        assertEquals(300, result.bitmap.height)
    }

    @Test
    fun resizeSelectionToMax288_576x288Becomes288x144WithoutChangingCanvas() {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 40 until 328) for (x in 12 until 588) bitmap.setPixel(x, y, Color.BLACK)

        val result = resizeSelectionToMax288(
            bitmap,
            RectPx.of(12, 40, 576, 288),
            downscaleMode = ResizeDownscaleMode.PixelArtStable,
        )

        assertEquals(true, result.applied)
        assertEquals(RectPx.of(12, 40, 288, 144), result.selection)
        assertEquals(600, result.bitmap.width)
        assertEquals(400, result.bitmap.height)
    }

    @Test
    fun resizeSelectionToMax288_anchorsMatchExisting96Behavior() {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 40 until 328) for (x in 12 until 588) bitmap.setPixel(x, y, Color.BLACK)

        val topLeft = resizeSelectionToMax288(
            bitmap,
            RectPx.of(12, 40, 576, 288),
            anchor = ResizeAnchor.TopLeft,
            downscaleMode = ResizeDownscaleMode.PixelArtStable,
        )
        val center = resizeSelectionToMax288(
            bitmap,
            RectPx.of(12, 40, 576, 288),
            anchor = ResizeAnchor.Center,
            downscaleMode = ResizeDownscaleMode.PixelArtStable,
        )

        assertEquals(RectPx.of(12, 40, 288, 144), topLeft.selection)
        assertEquals(RectPx.of(156, 112, 288, 144), center.selection)
    }

    @Test
    fun resizeSelectionToMax96_defaultBehaviorRemains96() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val result = resizeSelectionToMax96(
            bitmap,
            RectPx.of(0, 0, 192, 96),
            downscaleMode = ResizeDownscaleMode.PixelArtStable,
        )

        assertEquals(true, result.applied)
        assertEquals(RectPx.of(0, 0, 96, 48), result.selection)
        assertEquals(240, result.bitmap.width)
        assertEquals(160, result.bitmap.height)
    }

    @Test
    fun resizeCanvas_enlargeTopLeft_preservesTopLeftPixel() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 1, Color.BLUE)

        val resized = resizeCanvas(bitmap, newW = 4, newH = 4, anchor = ResizeAnchor.TopLeft)

        assertEquals(Color.RED, resized.getPixel(0, 0))
        assertEquals(Color.BLUE, resized.getPixel(1, 1))
        assertEquals(0, Color.alpha(resized.getPixel(3, 3)))
    }

    @Test
    fun resizeCanvas_enlargeCenter_placesAtCenter() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.RED)

        val resized = resizeCanvas(bitmap, newW = 4, newH = 4, anchor = ResizeAnchor.Center)

        assertEquals(Color.RED, resized.getPixel(1, 1))
        assertEquals(0, Color.alpha(resized.getPixel(0, 0)))
    }

    @Test
    fun resizeCanvas_shrinkTopLeft_crops() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.GREEN)
        bitmap.setPixel(3, 3, Color.RED)

        val resized = resizeCanvas(bitmap, newW = 2, newH = 2, anchor = ResizeAnchor.TopLeft)

        assertEquals(Color.GREEN, resized.getPixel(0, 0))
        assertEquals(0, Color.alpha(resized.getPixel(1, 1)))
    }

    @Test
    fun calculateCanvasStretchTargetSize_noneReturnsRequestedSize() {
        val target = calculateCanvasStretchTargetSize(
            sourceWidth = 100,
            sourceHeight = 120,
            requestedWidth = 288,
            requestedHeight = 288,
            stretchMode = CanvasStretchMode.None,
        )

        assertEquals(288, target.width)
        assertEquals(288, target.height)
    }

    @Test
    fun calculateCanvasStretchTargetSize_stretchWidthToHeightUsesRequestedHeight() {
        val cases = listOf(
            Triple(100, 120, 120),
            Triple(40, 20, 20),
            Triple(1, 3, 3),
        )

        for ((requestedW, requestedH, expected) in cases) {
            val target = calculateCanvasStretchTargetSize(
                sourceWidth = requestedW,
                sourceHeight = requestedH,
                requestedWidth = requestedW,
                requestedHeight = requestedH,
                stretchMode = CanvasStretchMode.StretchWidthToHeight,
            )

            assertEquals(expected, target.width)
            assertEquals(expected, target.height)
        }
    }

    @Test
    fun calculateCanvasStretchTargetSize_stretchHeightToWidthUsesRequestedWidth() {
        val cases = listOf(
            Triple(100, 120, 100),
            Triple(40, 20, 40),
            Triple(1, 3, 1),
        )

        for ((requestedW, requestedH, expected) in cases) {
            val target = calculateCanvasStretchTargetSize(
                sourceWidth = requestedW,
                sourceHeight = requestedH,
                requestedWidth = requestedW,
                requestedHeight = requestedH,
                stretchMode = CanvasStretchMode.StretchHeightToWidth,
            )

            assertEquals(expected, target.width)
            assertEquals(expected, target.height)
        }
    }

    @Test
    fun calculateCanvasStretchTargetSize_clampsNonPositiveRequestedSizeToOne() {
        val target = calculateCanvasStretchTargetSize(
            sourceWidth = 2,
            sourceHeight = 3,
            requestedWidth = 0,
            requestedHeight = -4,
            stretchMode = CanvasStretchMode.None,
        )

        assertEquals(1, target.width)
        assertEquals(1, target.height)
    }

    @Test
    fun stretchCanvasToSize_widthToHeightScalesWholeBitmapWithoutPadding() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.BLUE)
        bitmap.setPixel(0, 1, Color.GREEN)
        bitmap.setPixel(1, 1, Color.CYAN)
        bitmap.setPixel(0, 2, Color.MAGENTA)
        bitmap.setPixel(1, 2, Color.YELLOW)

        val stretched = stretchCanvasToSize(bitmap, newW = 3, newH = 3)

        assertEquals(3, stretched.width)
        assertEquals(3, stretched.height)
        assertEquals(Color.BLUE, stretched.getPixel(2, 0))
        assertEquals(Color.YELLOW, stretched.getPixel(2, 2))
    }

    @Test
    fun stretchCanvasToSize_heightToWidthScalesWholeBitmapWithoutCropping() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.BLUE)
        bitmap.setPixel(0, 1, Color.GREEN)
        bitmap.setPixel(1, 1, Color.CYAN)
        bitmap.setPixel(0, 2, Color.MAGENTA)
        bitmap.setPixel(1, 2, Color.YELLOW)

        val stretched = stretchCanvasToSize(bitmap, newW = 2, newH = 2)

        assertEquals(2, stretched.width)
        assertEquals(2, stretched.height)
        assertEquals(Color.MAGENTA, stretched.getPixel(0, 1))
        assertEquals(Color.YELLOW, stretched.getPixel(1, 1))
    }

    @Test
    fun toGrayscale_convertsRgbAndPreservesAlpha() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(255, 255, 0, 0))
        bitmap.setPixel(1, 0, Color.argb(192, 0, 255, 0))
        bitmap.setPixel(0, 1, Color.argb(128, 0, 0, 255))
        bitmap.setPixel(1, 1, Color.argb(64, 40, 80, 120))

        val grayscale = toGrayscale(bitmap)

        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                val srcPixel = bitmap.getPixel(x, y)
                val grayPixel = grayscale.getPixel(x, y)
                assertEquals(Color.alpha(srcPixel), Color.alpha(grayPixel))
                assertEquals(Color.red(grayPixel), Color.green(grayPixel))
                assertEquals(Color.green(grayPixel), Color.blue(grayPixel))
            }
        }
    }

    @Test
    fun addOutline_adds8NeighborhoodOutlineAndKeepsSourcePixel() {
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(2, 2, Color.WHITE)

        val outlined = addOutline(bitmap)

        assertEquals(Color.WHITE, outlined.getPixel(2, 2))

        val outlinePositions = setOf(
            Pair(1, 1), Pair(2, 1), Pair(3, 1),
            Pair(1, 2), Pair(3, 2),
            Pair(1, 3), Pair(2, 3), Pair(3, 3),
        )

        for (y in 0 until 5) {
            for (x in 0 until 5) {
                val pixel = outlined.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (x == 2 && y == 2) {
                    assertTrue(alpha >= 16)
                } else if (Pair(x, y) in outlinePositions) {
                    assertEquals(Color.BLACK, pixel)
                    assertEquals(255, alpha)
                } else {
                    assertEquals(0, alpha)
                }
            }
        }
    }

    @Test
    fun clearTransparent_makesPixelFullyTransparent() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)

        val cleared = clearTransparent(bitmap, RectPx.of(0, 0, 1, 1))

        assertEquals(0, Color.alpha(cleared.getPixel(0, 0)))
    }

    @Test
    fun addOuterOutline_ignoresClosedInnerHole() {
        val bitmap = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 2..4) {
            for (x in 2..4) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }
        bitmap.setPixel(3, 3, Color.TRANSPARENT)

        val outlined = addOuterOutline(bitmap)

        assertEquals(0, Color.alpha(outlined.getPixel(3, 3)))
        assertEquals(Color.BLACK, outlined.getPixel(1, 2))
        assertEquals(Color.BLACK, outlined.getPixel(5, 4))
    }

    @Test
    fun addOuterOutline_drawsOnlyOnOutsideTransparentNeighbors() {
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(2, 2, Color.WHITE)

        val outlined = addOuterOutline(bitmap)

        assertEquals(Color.WHITE, outlined.getPixel(2, 2))
        val outlinePositions = setOf(
            Pair(1, 1), Pair(2, 1), Pair(3, 1),
            Pair(1, 2), Pair(3, 2),
            Pair(1, 3), Pair(2, 3), Pair(3, 3),
        )

        for (y in 0 until 5) {
            for (x in 0 until 5) {
                val pixel = outlined.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (x == 2 && y == 2) {
                    assertEquals(Color.WHITE, pixel)
                } else if (Pair(x, y) in outlinePositions) {
                    assertEquals(0xFF000000.toInt(), pixel)
                    assertEquals(255, alpha)
                } else {
                    assertEquals(0, alpha)
                }
            }
        }
    }


    @Test
    fun clearEdgeConnectedBackground_removesEdgeConnectedSolidBackground() {
        val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 2..3) {
            for (x in 2..3) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val cleared = clearEdgeConnectedBackground(bitmap)

        assertEquals(0, Color.alpha(cleared.getPixel(0, 0)))
        assertEquals(0, Color.alpha(cleared.getPixel(5, 5)))
        assertEquals(255, Color.alpha(cleared.getPixel(2, 2)))
        assertEquals(Color.BLACK, cleared.getPixel(2, 2))
        assertEquals(255, Color.alpha(cleared.getPixel(3, 3)))
    }

    @Test
    fun clearEdgeConnectedBackground_keepsSpriteOnTransparentCanvas() {
        val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 2..3) {
            for (x in 2..3) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val cleared = clearEdgeConnectedBackground(bitmap)

        assertEquals(255, Color.alpha(cleared.getPixel(2, 2)))
        assertEquals(Color.BLACK, cleared.getPixel(2, 2))
        assertEquals(255, Color.alpha(cleared.getPixel(3, 3)))
        assertEquals(0, Color.alpha(cleared.getPixel(0, 0)))
    }

    @Test
    fun clearConnectedRegionFromSelection_clearsOnlySeedConnectedComponent() {
        val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 1..2) {
            for (x in 1..2) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
        for (y in 4..5) {
            for (x in 4..5) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val cleared = clearConnectedRegionFromSelection(bitmap, RectPx.of(0, 0, 3, 3))

        assertEquals(0, Color.alpha(cleared.getPixel(1, 1)))
        assertEquals(0, Color.alpha(cleared.getPixel(2, 2)))
        assertEquals(255, Color.alpha(cleared.getPixel(4, 4)))
        assertEquals(Color.BLACK, cleared.getPixel(4, 4))
        assertEquals(255, Color.alpha(cleared.getPixel(5, 5)))
    }

    @Test
    fun clearConnectedRegionFromSelection_isNoOpWhenSelectionHasNoOpaquePixel() {
        val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(4, 4, Color.BLACK)

        val cleared = clearConnectedRegionFromSelection(bitmap, RectPx.of(0, 0, 2, 2))

        assertEquals(255, Color.alpha(cleared.getPixel(4, 4)))
        assertEquals(Color.BLACK, cleared.getPixel(4, 4))
        assertEquals(0, Color.alpha(cleared.getPixel(0, 0)))
    }

    @Test
    fun resizeSelectionToMax96_anchorTopLeft_pastesAtSelectionOrigin() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 2 until 6) {
            for (x in 2 until 6) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val result = resizeSelectionToMax96(
            bitmap,
            selection = RectPx.of(2, 2, 4, 4),
            maxSize = 2,
            anchor = ResizeAnchor.TopLeft,
        )

        assertEquals(255, Color.alpha(result.bitmap.getPixel(2, 2)))
        assertEquals(255, Color.alpha(result.bitmap.getPixel(3, 3)))
        assertEquals(0, Color.alpha(result.bitmap.getPixel(5, 5)))
    }

    @Test
    fun resizeSelectionToMax96_anchorCenter_pastesAtSelectionCenter() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 2 until 6) {
            for (x in 2 until 6) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val result = resizeSelectionToMax96(
            bitmap,
            selection = RectPx.of(2, 2, 4, 4),
            maxSize = 2,
            anchor = ResizeAnchor.Center,
        )

        assertEquals(0, Color.alpha(result.bitmap.getPixel(2, 2)))
        assertEquals(255, Color.alpha(result.bitmap.getPixel(3, 3)))
        assertEquals(0, Color.alpha(result.bitmap.getPixel(5, 5)))
    }

    @Test
    fun fillRegionFromTransparentSeeds_returnsNoOpWhenSelectionHasNoTransparentPixels() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val result = fillRegionFromTransparentSeeds(bitmap, RectPx.of(0, 0, 4, 4))

        assertEquals(FillRegionTransparentStatus.NO_TRANSPARENT_PIXELS_IN_SELECTION, result.status)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(Color.BLACK, result.bitmap.getPixel(x, y))
            }
        }
    }

    @Test
    fun fillRegionFromTransparentSeeds_fillsOnlyTransparentRegionInsideOpaqueWalls() {
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (y in 1..3) {
            for (x in 1..3) {
                bitmap.setPixel(x, y, Color.TRANSPARENT)
            }
        }

        val result = fillRegionFromTransparentSeeds(bitmap, RectPx.of(1, 1, 3, 3))

        assertEquals(FillRegionTransparentStatus.APPLIED, result.status)
        for (y in 1..3) {
            for (x in 1..3) {
                assertEquals(Color.WHITE, result.bitmap.getPixel(x, y))
            }
        }
        assertEquals(Color.BLACK, result.bitmap.getPixel(0, 0))
        assertEquals(Color.BLACK, result.bitmap.getPixel(4, 4))
    }


    @Test
    fun fillRegionFromTransparentSeeds_largeSelectionTransparentBackground_doesNotAbortAndKeepsOutsideUntouched() {
        val width = 560
        val height = 560
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(10, 11, Color.BLACK)
        bitmap.setPixel(12, 11, Color.BLACK)
        bitmap.setPixel(11, 10, Color.BLACK)
        bitmap.setPixel(11, 12, Color.BLACK)

        val selection = RectPx.of(1, 0, width - 1, height)
        val result = fillRegionFromTransparentSeeds(bitmap, selection)

        assertEquals(FillRegionTransparentStatus.APPLIED, result.status)
        assertEquals(Color.BLACK, result.bitmap.getPixel(0, 0))
        assertEquals(0, Color.alpha(result.bitmap.getPixel(11, 11)))

        for (y in 0 until height) {
            for (x in 1 until width) {
                if ((x == 10 && y == 11) || (x == 12 && y == 11) || (x == 11 && y == 10) || (x == 11 && y == 12)) {
                    assertEquals(Color.BLACK, result.bitmap.getPixel(x, y))
                } else if (x == 11 && y == 11) {
                    assertEquals(0, Color.alpha(result.bitmap.getPixel(x, y)))
                } else {
                    assertEquals(Color.WHITE, result.bitmap.getPixel(x, y))
                }
            }
        }
    }

    @Test
    fun centerContentInRect_movesContentToSelectionCenter() {
        val bitmap = Bitmap.createBitmap(9, 9, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 0..1) {
            for (x in 0..1) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        val centered = centerContentInRect(bitmap, RectPx.of(0, 0, 9, 9))

        var minX = 9
        var minY = 9
        var maxX = -1
        var maxY = -1
        for (y in 0 until 9) {
            for (x in 0 until 9) {
                val alpha = Color.alpha(centered.getPixel(x, y))
                if (alpha > 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        assertEquals(4, (minX + maxX) / 2)
        assertEquals(4, (minY + maxY) / 2)
        assertEquals(2, maxX - minX + 1)
        assertEquals(2, maxY - minY + 1)
    }

    @Test
    fun centerContentInRect_respectsTransparentAlphaThreshold() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.argb(FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD, 255, 0, 0))

        val ignored = centerContentInRect(bitmap, RectPx.of(0, 0, 3, 3))

        assertEquals(
            FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
            Color.alpha(ignored.getPixel(0, 0)),
        )
        assertEquals(0, Color.alpha(ignored.getPixel(1, 1)))

        val contentBitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        contentBitmap.eraseColor(Color.TRANSPARENT)
        contentBitmap.setPixel(0, 0, Color.argb(FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD + 1, 255, 0, 0))

        val centered = centerContentInRect(contentBitmap, RectPx.of(0, 0, 3, 3))

        assertEquals(0, Color.alpha(centered.getPixel(0, 0)))
        assertEquals(FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD + 1, Color.alpha(centered.getPixel(1, 1)))
    }

    @Test
    fun centerContentInRect_noContentIsNoOp() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val centered = centerContentInRect(bitmap, RectPx.of(0, 0, 4, 4))

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(0, Color.alpha(centered.getPixel(x, y)))
            }
        }
    }

    @Test
    fun fillRegionFromTransparentSeeds_abortsWhenFillCountExceedsLimit() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val result = fillRegionFromTransparentSeeds(
            bitmap,
            RectPx.of(0, 0, 4, 4),
            maxFillPixels = 3,
        )

        assertEquals(FillRegionTransparentStatus.ABORTED_TOO_LARGE, result.status)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(0, Color.alpha(result.bitmap.getPixel(x, y)))
            }
        }
    }


    @Test
    fun fillRegionFromTransparentSeeds_treatsLowAlphaAsTransparent() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        bitmap.setPixel(1, 1, Color.argb(1, 120, 130, 140))

        val result = fillRegionFromTransparentSeeds(bitmap, RectPx.of(1, 1, 1, 1))

        assertEquals(FillRegionTransparentStatus.APPLIED, result.status)
        assertEquals(Color.WHITE, result.bitmap.getPixel(1, 1))
        assertEquals(Color.BLACK, result.bitmap.getPixel(0, 0))
        assertEquals(Color.BLACK, result.bitmap.getPixel(2, 2))
    }

    @Test
    fun fillRegionFromTransparentSeeds_usesFourNeighborhoodForConnectivity() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        bitmap.setPixel(0, 0, Color.TRANSPARENT)
        bitmap.setPixel(1, 1, Color.TRANSPARENT)

        val result = fillRegionFromTransparentSeeds(bitmap, RectPx.of(0, 0, 1, 1))

        assertEquals(FillRegionTransparentStatus.APPLIED, result.status)
        assertEquals(Color.WHITE, result.bitmap.getPixel(0, 0))
        assertEquals(0, Color.alpha(result.bitmap.getPixel(1, 1)))
        assertEquals(Color.BLACK, result.bitmap.getPixel(2, 2))
    }

    @Test
    fun fillRegionFromTransparentSeeds_smallSelectionDoesNotAbortWhenOutsideIsLargeTransparentArea() {
        val width = 64
        val height = 64
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val selection = RectPx.of(0, 0, 4, 4)
        val result = fillRegionFromTransparentSeeds(bitmap, selection)

        assertEquals(FillRegionTransparentStatus.APPLIED, result.status)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = result.bitmap.getPixel(x, y)
                if (x in 0 until 4 && y in 0 until 4) {
                    assertEquals(Color.WHITE, pixel)
                } else {
                    assertEquals(0, Color.alpha(pixel))
                }
            }
        }
    }


    @Test
    fun fillConnectedToWhite_alphaMode_fillsConnectedComponentBeyondSelection() {
        val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (y in 1..4) {
            for (x in 1..4) {
                bitmap.setPixel(x, y, Color.TRANSPARENT)
            }
        }

        val result = fillConnectedToWhite(bitmap, RectPx.of(2, 2, 1, 1))

        assertEquals(Mode.Alpha, result.mode)
        assertEquals(false, result.aborted)
        assertEquals(16, result.filled)
        for (y in 1..4) {
            for (x in 1..4) {
                assertEquals(Color.WHITE, result.bitmap.getPixel(x, y))
            }
        }
        assertEquals(Color.BLACK, result.bitmap.getPixel(0, 0))
    }

    @Test
    fun fillConnectedToWhite_rgbFallback_fillsSimilarBackgroundComponent() {
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        val bg = Color.argb(255, 240, 240, 240)
        bitmap.eraseColor(bg)
        bitmap.setPixel(2, 2, Color.BLACK)

        val result = fillConnectedToWhite(bitmap, RectPx.of(0, 0, 1, 1))

        assertEquals(Mode.Rgb, result.mode)
        assertEquals(false, result.aborted)
        assertEquals(24, result.filled)
        assertEquals(Color.BLACK, result.bitmap.getPixel(2, 2))
        assertEquals(Color.WHITE, result.bitmap.getPixel(0, 0))
        assertEquals(Color.WHITE, result.bitmap.getPixel(4, 4))
    }

    @Test
    fun fillConnectedToWhite_largeTransparentBackground_doesNotAbortWithImageBasedLimit() {
        val width = 288
        val height = 288
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val result = fillConnectedToWhite(bitmap, RectPx.of(0, 0, 4, 4))

        assertEquals(false, result.aborted)
        assertEquals(Mode.Alpha, result.mode)
        assertEquals(width * height, result.filled)
        assertEquals(Color.WHITE, result.bitmap.getPixel(width - 1, height - 1))
        assertTrue(result.debugText.contains("limit=${width * height}"))
    }

    @Test
    fun toBinarize_keepsLowAlphaPixelsTransparent() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(0, 200, 200, 200))
        bitmap.setPixel(1, 0, Color.argb(8, 200, 200, 200))
        bitmap.setPixel(2, 0, Color.argb(15, 40, 40, 40))
        bitmap.setPixel(3, 0, Color.argb(255, 240, 240, 240))

        val result = toBinarize(bitmap)

        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertEquals(0, Color.alpha(result.getPixel(1, 0)))
        assertEquals(0, Color.alpha(result.getPixel(2, 0)))
        assertEquals(255, Color.alpha(result.getPixel(3, 0)))
    }

    @Test
    fun toBinarize_fallbackThresholdSplitsDarkAndBrightPixels() {
        val bitmap = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888)
        for (x in 0..3) {
            bitmap.setPixel(x, 0, Color.argb(255, 10, 10, 10))
        }
        for (x in 4..7) {
            bitmap.setPixel(x, 0, Color.argb(255, 240, 240, 240))
        }

        val result = toBinarize(bitmap)

        for (x in 0..3) {
            assertEquals(Color.BLACK, result.getPixel(x, 0))
        }
        for (x in 4..7) {
            assertEquals(Color.WHITE, result.getPixel(x, 0))
        }
    }

    @Test
    fun toBinarize_ignoresTransparentBackgroundInHistogram() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (x in 0..1) {
            bitmap.setPixel(x, 0, Color.argb(255, 20, 20, 20))
        }
        for (x in 2..3) {
            bitmap.setPixel(x, 0, Color.argb(255, 230, 230, 230))
        }
        for (x in 0..1) {
            bitmap.setPixel(x, 1, Color.argb(255, 20, 20, 20))
        }
        for (x in 2..3) {
            bitmap.setPixel(x, 1, Color.argb(255, 230, 230, 230))
        }

        val result = toBinarize(bitmap)

        assertEquals(Color.BLACK, result.getPixel(0, 0))
        assertEquals(Color.BLACK, result.getPixel(1, 1))
        assertEquals(Color.WHITE, result.getPixel(2, 0))
        assertEquals(Color.WHITE, result.getPixel(3, 1))
        assertEquals(0, Color.alpha(result.getPixel(0, 3)))
        assertEquals(0, Color.alpha(result.getPixel(3, 3)))
    }

    @Test
    fun countTransparentLikeInSelection_threshold8_countsAlphaEqualsThresholdAsTransparent() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(0, 0, 0, 0))
        bitmap.setPixel(1, 0, Color.argb(1, 0, 0, 0))
        bitmap.setPixel(2, 0, Color.argb(8, 0, 0, 0))
        bitmap.setPixel(3, 0, Color.argb(9, 0, 0, 0))

        val stats = countTransparentLikeInSelection(
            bitmap = bitmap,
            selection = RectPx.of(0, 0, 4, 1),
            transparentAlphaThreshold = 8,
        )

        assertEquals(3, stats.transparentCount)
        assertEquals(8, stats.threshold)
        assertEquals(0, stats.minAlpha)
        assertEquals(9, stats.maxAlpha)
    }

    @Test
    fun countTransparentLikeInSelection_ignoresPixelsOutsideSelection() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.argb(255, 10, 10, 10))
        bitmap.setPixel(0, 0, Color.argb(0, 20, 20, 20))
        bitmap.setPixel(2, 1, Color.argb(0, 30, 30, 30))

        val stats = countTransparentLikeInSelection(
            bitmap = bitmap,
            selection = RectPx.of(0, 0, 1, 1),
            transparentAlphaThreshold = 8,
        )

        assertEquals(1, stats.transparentCount)
        assertEquals(0, stats.minAlpha)
        assertEquals(0, stats.maxAlpha)
    }

    @Test
    fun resizeSelectionToMax96_whenAlreadySmall_returnsAppliedFalse_andSelectionUnchanged() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val selection = RectPx.of(0, 0, 32, 32)
        val result = resizeSelectionToMax96(bitmap, selection, maxSize = 96)

        assertTrue(!result.applied)
        assertEquals(selection, result.selection)
    }

    @Test
    fun resizeSelectionToMax96_scalesDownAndKeepsAspectRatio() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val selection = RectPx.of(10, 20, 200, 100)
        val result = resizeSelectionToMax96(bitmap, selection, maxSize = 96)

        assertTrue(result.applied)
        assertEquals(96, result.selection.w)
        assertEquals(48, result.selection.h)
    }

    @Test
    fun multiStep_allTransparent_staysTransparent() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val selection = RectPx.of(0, 0, 32, 32)

        val results = listOf(0.5f, 0.75f).map { stepFactor ->
            resizeSelectionToMax96(
                bitmap,
                selection = selection,
                maxSize = 8,
                anchor = ResizeAnchor.TopLeft,
                stepFactor = stepFactor,
            )
        }

        results.forEach { result ->
            for (y in result.selection.y until result.selection.y + result.selection.h) {
                for (x in result.selection.x until result.selection.x + result.selection.w) {
                    assertEquals(0, Color.alpha(result.bitmap.getPixel(x, y)))
                }
            }
        }
    }

    @Test
    fun thinLine_keepsSomeCoverage() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (y in 0 until 32) {
            bitmap.setPixel(16, y, Color.WHITE)
        }
        val selection = RectPx.of(0, 0, 32, 32)

        val results = listOf(0.5f, 0.75f).map { stepFactor ->
            resizeSelectionToMax96(
                bitmap,
                selection = selection,
                maxSize = 8,
                anchor = ResizeAnchor.TopLeft,
                stepFactor = stepFactor,
            )
        }

        results.forEach { result ->
            var foundAlpha = false
            var foundWhiteish = false
            for (y in result.selection.y until result.selection.y + result.selection.h) {
                for (x in result.selection.x until result.selection.x + result.selection.w) {
                    val pixel = result.bitmap.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha > 0) {
                        foundAlpha = true
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        if (r > 200 && g > 200 && b > 200) {
                            foundWhiteish = true
                        }
                    }
                }
            }
            assertTrue(foundAlpha)
            assertTrue(foundWhiteish)
        }
    }

    @Test
    fun downscaleNineSamplePremul_allTransparent_staysTransparent() {
        val srcPixels = IntArray(9) { Color.TRANSPARENT }

        val result = downscaleNineSamplePremul(
            srcPixels = srcPixels,
            srcW = 3,
            srcH = 3,
            dstW = 2,
            dstH = 2,
        )

        result.forEach { pixel ->
            assertEquals(0, Color.alpha(pixel))
        }
    }

    @Test
    fun flipHorizontal_preservesSize_andFlipsPixelsCorrectly() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.argb(255, 10, 20, 30))
        bitmap.setPixel(1, 0, Color.argb(128, 40, 50, 60))
        bitmap.setPixel(2, 0, Color.argb(255, 70, 80, 90))
        bitmap.setPixel(0, 1, Color.TRANSPARENT)
        bitmap.setPixel(1, 1, Color.argb(200, 11, 22, 33))
        bitmap.setPixel(2, 1, Color.argb(255, 44, 55, 66))

        val flipped = flipHorizontal(bitmap)

        assertEquals(3, flipped.width)
        assertEquals(2, flipped.height)
        assertEquals(bitmap.getPixel(2, 0), flipped.getPixel(0, 0))
        assertEquals(bitmap.getPixel(1, 0), flipped.getPixel(1, 0))
        assertEquals(bitmap.getPixel(0, 0), flipped.getPixel(2, 0))
        assertEquals(bitmap.getPixel(2, 1), flipped.getPixel(0, 1))
        assertEquals(bitmap.getPixel(1, 1), flipped.getPixel(1, 1))
        assertEquals(bitmap.getPixel(0, 1), flipped.getPixel(2, 1))
        assertEquals(0, Color.alpha(flipped.getPixel(2, 1)))
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun testBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

}
