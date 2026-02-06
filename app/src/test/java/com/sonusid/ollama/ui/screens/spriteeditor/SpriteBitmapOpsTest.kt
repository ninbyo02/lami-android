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
@Config(manifest = Config.NONE, sdk = [34])
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

}
