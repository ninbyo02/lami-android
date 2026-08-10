package io.github.ninbyo02.lami.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SpriteAdaptivePaletteReducerTest {
    @Test
    fun allTransparent_hiddenRgbIsIgnoredAndPreservedWithoutAllocation() {
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.setPixels(
            intArrayOf(0x00112233, 0x00ABCDEF, 0x00010203),
            0,
            3,
            0,
            0,
            3,
            1,
        )
        val before = pixelsOf(bitmap)

        val result = reduceToAdaptivePalette(bitmap)

        assertFalse(result.changed)
        assertFalse(result.rejected)
        assertSame(bitmap, result.bitmap)
        assertTrue(result.rgbAnchors.isEmpty())
        assertEquals(before.toList(), pixelsOf(bitmap).toList())
    }

    @Test
    fun exact256VisibleRgbColors_doNotReduceAndTransparencyConsumesNoAnchor() {
        val bitmap = Bitmap.createBitmap(257, 1, Bitmap.Config.ARGB_8888)
        for (x in 0 until 256) {
            bitmap.setPixel(x, 0, Color.rgb(x, 255 - x, (x * 73) and 0xFF))
        }
        bitmap.setPixel(256, 0, Color.TRANSPARENT)

        val result = reduceToAdaptivePalette(bitmap)

        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertTrue(result.rgbAnchors.isEmpty())
    }

    @Test
    fun reduction_usesAtMostRequestedRgbAnchorsAndIsDeterministic() {
        val firstSource = colorfulBitmap(384, reverse = false)
        val secondSource = colorfulBitmap(384, reverse = false)

        val first = reduceToAdaptivePalette(firstSource, maxRgbColors = 32)
        val second = reduceToAdaptivePalette(secondSource, maxRgbColors = 32)

        assertTrue(first.changed)
        assertTrue(second.changed)
        assertNotSame(firstSource, first.bitmap)
        assertEquals(first.rgbAnchors, second.rgbAnchors)
        assertEquals(32, first.rgbAnchors.size)
        assertEquals(first.rgbAnchors.size, first.rgbAnchors.toSet().size)
        assertTrue(first.rgbAnchors.all { Color.alpha(it) == 255 })
        try {
            @Suppress("UNCHECKED_CAST")
            (first.rgbAnchors as MutableList<Int>).add(Color.WHITE)
            throw AssertionError("rgbAnchors must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
        assertTrue(visibleRgb(first.bitmap).size <= 32)
        assertEquals(pixelsOf(first.bitmap).toList(), pixelsOf(second.bitmap).toList())
    }

    @Test
    fun histogramInputOrder_doesNotChangeGeneratedAnchorOrder() {
        val forward = reduceToAdaptivePalette(colorfulBitmap(384, reverse = false), maxRgbColors = 24)
        val reverse = reduceToAdaptivePalette(colorfulBitmap(384, reverse = true), maxRgbColors = 24)

        assertTrue(forward.changed)
        assertTrue(reverse.changed)
        assertEquals(forward.rgbAnchors, reverse.rgbAnchors)
    }

    @Test
    fun reduction_preservesEveryAlphaAndDoesNotLearnTransparentHiddenRgb() {
        val firstSource = mixedAlphaBitmap(hiddenRgb = 0x00112233)
        val secondSource = mixedAlphaBitmap(hiddenRgb = 0x00FEDCBA)
        val firstAlpha = pixelsOf(firstSource).map { Color.alpha(it) }
        val secondAlpha = pixelsOf(secondSource).map { Color.alpha(it) }

        val first = reduceToAdaptivePalette(firstSource, maxRgbColors = 16)
        val second = reduceToAdaptivePalette(secondSource, maxRgbColors = 16)

        assertTrue(first.changed)
        assertTrue(second.changed)
        assertEquals(first.rgbAnchors, second.rgbAnchors)
        assertEquals(firstAlpha, pixelsOf(first.bitmap).map { Color.alpha(it) })
        assertEquals(secondAlpha, pixelsOf(second.bitmap).map { Color.alpha(it) })
        assertEquals(0x00112233, pixelsOf(first.bitmap).last())
        assertEquals(0x00FEDCBA, pixelsOf(second.bitmap).last())
        assertEquals(
            pixelsOf(first.bitmap).dropLast(1).map { it and 0x00FFFFFF },
            pixelsOf(second.bitmap).dropLast(1).map { it and 0x00FFFFFF },
        )
    }

    @Test
    fun alphaWeighting_preventsManyBarelyVisibleNoiseColorsFromDisplacingOpaqueSubject() {
        val bitmap = Bitmap.createBitmap(310, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        for (x in 0 until 300) {
            bitmap.setPixel(x, 0, Color.argb(1, x and 0xFF, (x * 47) and 0xFF, (x * 91) and 0xFF))
        }
        for (x in 300 until 310) {
            bitmap.setPixel(x, 0, Color.RED)
        }

        val result = reduceToAdaptivePalette(bitmap, maxRgbColors = 1)

        assertTrue(result.changed)
        assertEquals(1, result.rgbAnchors.size)
        val anchor = result.rgbAnchors.single()
        assertTrue("anchor=${hex(anchor)}", Color.red(anchor) >= 240)
        assertTrue("anchor=${hex(anchor)}", Color.green(anchor) <= 32)
        assertTrue("anchor=${hex(anchor)}", Color.blue(anchor) <= 32)
    }

    @Test
    fun warmOnlySource_doesNotCollapseRepresentativeAnchorsToExactGray() {
        val bitmap = Bitmap.createBitmap(320, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        for (x in 0 until bitmap.width) {
            val red = 150 + (x % 106)
            val green = 75 + ((x * 7) % 130)
            val blue = 45 + ((x * 11) % 105)
            bitmap.setPixel(x, 0, Color.rgb(red, green, blue))
        }

        val result = reduceToAdaptivePalette(bitmap, maxRgbColors = 16)

        assertTrue(result.changed)
        assertTrue(result.rgbAnchors.isNotEmpty())
        assertTrue(result.rgbAnchors.all { color ->
            Color.red(color) != Color.green(color) || Color.green(color) != Color.blue(color)
        })
    }

    @Test
    fun reusedAnchors_makePremultipliedLowAlphaRepeatAPhysicalNoOp() {
        val bitmap = Bitmap.createBitmap(320, 1, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitmap.width) {
            val alpha = 1 + (x % 255)
            bitmap.setPixel(
                x,
                0,
                Color.argb(alpha, (x * 31) and 0xFF, (x * 67) and 0xFF, (x * 101) and 0xFF),
            )
        }

        val first = reduceToAdaptivePalette(bitmap, maxRgbColors = 16)
        val second = reduceToAdaptivePalette(
            first.bitmap,
            maxRgbColors = 16,
            reuseRgbAnchors = first.rgbAnchors,
        )

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertSame(first.bitmap, second.bitmap)
        assertEquals(first.rgbAnchors, second.rgbAnchors)
    }

    @Test
    fun cancellationDuringClustering_failsClosedAndKeepsSource() {
        val bitmap = colorfulBitmap(512, reverse = false)
        var checks = 0

        val result = reduceToAdaptivePalette(bitmap, maxRgbColors = 16) {
            checks += 1
            checks > bitmap.height + 4
        }

        assertTrue(result.cancelled)
        assertTrue(result.rejected)
        assertEquals(PaletteBitmapRejectionReason.CANCELLED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun cancellationInsideWideOutputRow_failsClosedWithinBoundedPixelInterval() {
        val bitmap = Bitmap.createBitmap(4096, 1, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitmap.width) {
            bitmap.setPixel(x, 0, Color.rgb((x * 17) and 0xFF, (x * 43) and 0xFF, (x * 89) and 0xFF))
        }
        var checks = 0

        val result = reduceToAdaptivePalette(
            bitmap,
            maxRgbColors = 1,
            reuseRgbAnchors = listOf(Color.BLACK),
        ) {
            val cancel = checks >= 2
            checks += 1
            cancel
        }

        assertTrue(result.cancelled)
        assertTrue(result.rejected)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
        assertTrue("checks=$checks", checks >= 3)
    }

    @Test
    fun hardwareBitmap_isRejectedFailClosedWhenRobolectricSupportsIt() {
        val bitmap = runCatching { Bitmap.createBitmap(1, 1, Bitmap.Config.HARDWARE) }.getOrNull() ?: return

        val result = reduceToAdaptivePalette(bitmap)

        assertEquals(PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG, result.rejectionReason)
        assertTrue(result.rejected)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    @Test
    fun recycledBitmap_isRejectedWithoutCrash() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val result = reduceToAdaptivePalette(bitmap)

        assertTrue(result.rejected)
        assertEquals(PaletteBitmapRejectionReason.RECYCLED, result.rejectionReason)
        assertFalse(result.changed)
        assertSame(bitmap, result.bitmap)
    }

    private fun colorfulBitmap(size: Int, reverse: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(size, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        for (position in 0 until size) {
            val value = if (reverse) size - 1 - position else position
            bitmap.setPixel(
                position,
                0,
                Color.rgb(value and 0xFF, (value ushr 1) and 0xFF, (value * 97) and 0xFF),
            )
        }
        return bitmap
    }

    private fun mixedAlphaBitmap(hiddenRgb: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(321, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        for (x in 0 until 320) {
            val alpha = 1 + (x % 255)
            bitmap.setPixel(
                x,
                0,
                Color.argb(alpha, (x * 13) and 0xFF, (x * 37) and 0xFF, (x * 71) and 0xFF),
            )
        }
        bitmap.setPixel(320, 0, hiddenRgb)
        return bitmap
    }

    private fun visibleRgb(bitmap: Bitmap): Set<Int> = pixelsOf(bitmap)
        .filter { Color.alpha(it) != 0 }
        .mapTo(linkedSetOf()) { it and 0x00FFFFFF }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun hex(color: Int): String = "#%08X".format(color)
}
