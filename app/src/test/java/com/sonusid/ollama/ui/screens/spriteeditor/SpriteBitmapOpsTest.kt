package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SpriteBitmapOpsTest {
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

}
