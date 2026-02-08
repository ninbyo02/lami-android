package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val BINARIZE_ALPHA_THRESHOLD = 16
const val BINARIZE_FALLBACK_THRESHOLD = 128
private const val BINARIZE_MIN_VALID_PIXELS = 16
private const val BINARIZE_MIN_OTSU_THRESHOLD = 40
private const val BINARIZE_MAX_OTSU_THRESHOLD = 220
private const val CLEAR_BG_EDGE_SAMPLE_LIMIT = 32
private const val CLEAR_BG_COLOR_DISTANCE_THRESHOLD = 40
private const val CLEAR_BG_MIN_ALPHA = 8
private const val CLEAR_REGION_COLOR_DISTANCE_THRESHOLD = 30
private const val FILL_REGION_ABSOLUTE_MAX_PIXELS = 2_000_000
// Fill Connectedで透明とみなすalphaの上限値（alpha=0以外のほぼ透明背景も対象にする）
const val FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD = 8
const val FILL_CONNECTED_RGB_TOLERANCE = 24

enum class Mode { Alpha, Rgb }

data class FillConnectedResult(
    val bitmap: Bitmap,
    val filled: Int,
    val aborted: Boolean,
    val mode: Mode,
    val debugText: String,
)

data class TransparentSelectionStats(
    val transparentCount: Int,
    val threshold: Int,
    val minAlpha: Int,
    val maxAlpha: Int,
)

data class ResizeSelectionResult(
    val bitmap: Bitmap,
    val selection: RectPx,
    val applied: Boolean,
    val debugText: String,
)

enum class ResizeAnchor {
    TopLeft,
    Center,
}

// Bitmapのキャンバスサイズを変更する（元のBitmapは変更しない）
fun resizeCanvas(
    src: Bitmap,
    newW: Int,
    newH: Int,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val safeW = newW.coerceAtLeast(1)
    val safeH = newH.coerceAtLeast(1)
    val output = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
    output.eraseColor(0)
    val dstX = when (anchor) {
        ResizeAnchor.TopLeft -> 0
        ResizeAnchor.Center -> (safeW - safeSrc.width) / 2
    }
    val dstY = when (anchor) {
        ResizeAnchor.TopLeft -> 0
        ResizeAnchor.Center -> (safeH - safeSrc.height) / 2
    }
    val canvas = Canvas(output)
    canvas.drawBitmap(safeSrc, dstX.toFloat(), dstY.toFloat(), null)
    return output
}

fun countTransparentLikeInSelection(
    bitmap: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): TransparentSelectionStats {
    val safeBitmap = ensureArgb8888(bitmap)
    val width = safeBitmap.width
    val height = safeBitmap.height
    if (width <= 0 || height <= 0) {
        return TransparentSelectionStats(
            transparentCount = 0,
            threshold = transparentAlphaThreshold,
            minAlpha = 0,
            maxAlpha = 0,
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeBitmap.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var transparentCount = 0
    var minAlpha = 255
    var maxAlpha = 0
    for (pixel in selectionPixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha <= transparentAlphaThreshold) {
            transparentCount += 1
        }
        minAlpha = minOf(minAlpha, alpha)
        maxAlpha = maxOf(maxAlpha, alpha)
    }

    return TransparentSelectionStats(
        transparentCount = transparentCount,
        threshold = transparentAlphaThreshold,
        minAlpha = minAlpha,
        maxAlpha = maxAlpha,
    )
}

// 既存BitmapをARGB_8888で複製する（元のBitmapは変更しない）
fun ensureArgb8888(src: Bitmap): Bitmap {
    return if (src.config == Bitmap.Config.ARGB_8888) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src.copy(Bitmap.Config.ARGB_8888, false)
    }
}

// 選択矩形を画像範囲内に正規化する（矩形の最小サイズを維持）
fun rectNormalizeClamp(rect: RectPx, imageW: Int, imageH: Int): RectPx {
    val safeImageW = imageW.coerceAtLeast(1)
    val safeImageH = imageH.coerceAtLeast(1)
    val safeW = rect.w.coerceAtLeast(1).coerceAtMost(safeImageW)
    val safeH = rect.h.coerceAtLeast(1).coerceAtMost(safeImageH)
    val maxX = (safeImageW - safeW).coerceAtLeast(0)
    val maxY = (safeImageH - safeH).coerceAtLeast(0)
    val safeX = rect.x.coerceIn(0, maxX)
    val safeY = rect.y.coerceIn(0, maxY)
    return RectPx.of(safeX, safeY, safeW, safeH)
}

// 指定矩形を切り出した新しいBitmapを返す（元のBitmapは変更しない）
fun copyRect(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    return Bitmap.createBitmap(src, safeRect.x, safeRect.y, safeRect.w, safeRect.h)
}

// 画像を水平反転した新しいBitmapを返す（元のBitmapは変更しない）
fun flipHorizontal(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rowPixels = IntArray(width)
    val flippedRow = IntArray(width)
    for (y in 0 until height) {
        safeSrc.getPixels(rowPixels, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            flippedRow[width - 1 - x] = rowPixels[x]
        }
        output.setPixels(flippedRow, 0, width, 0, y, width, 1)
    }
    return output
}

// 指定矩形を透明でクリアした新しいBitmapを返す（元のBitmapは変更しない）
fun clearTransparent(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    for (y in safeRect.y until (safeRect.y + safeRect.h)) {
        for (x in safeRect.x until (safeRect.x + safeRect.w)) {
            output.setPixel(x, y, Color.TRANSPARENT)
        }
    }
    return output
}

// 指定矩形を黒で塗りつぶした新しいBitmapを返す（元のBitmapは変更しない）
fun fillBlack(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    val paint = Paint().apply { color = android.graphics.Color.BLACK }
    canvas.drawRect(
        safeRect.x.toFloat(),
        safeRect.y.toFloat(),
        (safeRect.x + safeRect.w).toFloat(),
        (safeRect.y + safeRect.h).toFloat(),
        paint
    )
    return output
}

fun findContentBoundsInRect(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): RectPx? {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return null
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeSrc.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var minX = safeSelection.w
    var minY = safeSelection.h
    var maxX = -1
    var maxY = -1
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val alpha = (selectionPixels[rowOffset + x] ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }

    if (maxX < 0 || maxY < 0) {
        return null
    }

    return RectPx.of(
        x = minX,
        y = minY,
        w = maxX - minX + 1,
        h = maxY - minY + 1,
    )
}

fun centerContentInRect(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val selectionPixels = IntArray(safeSelection.w * safeSelection.h)
    safeSrc.getPixels(
        selectionPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )

    var minX = safeSelection.w
    var minY = safeSelection.h
    var maxX = -1
    var maxY = -1
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val alpha = (selectionPixels[rowOffset + x] ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }

    if (maxX < 0 || maxY < 0) {
        return safeSrc
    }

    val contentCenterX = (minX + maxX) / 2
    val contentCenterY = (minY + maxY) / 2
    val selectionCenterX = (safeSelection.w - 1) / 2
    val selectionCenterY = (safeSelection.h - 1) / 2
    val dx = selectionCenterX - contentCenterX
    val dy = selectionCenterY - contentCenterY
    if (dx == 0 && dy == 0) {
        return safeSrc
    }

    val outputPixels = selectionPixels.copyOf()
    val contentPixels = IntArray(selectionPixels.size)
    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val index = rowOffset + x
            val pixel = selectionPixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                contentPixels[index] = pixel
                outputPixels[index] = 0
            }
        }
    }

    for (y in 0 until safeSelection.h) {
        val rowOffset = y * safeSelection.w
        for (x in 0 until safeSelection.w) {
            val index = rowOffset + x
            val pixel = contentPixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > transparentAlphaThreshold) {
                val dstX = x + dx
                val dstY = y + dy
                if (dstX in 0 until safeSelection.w && dstY in 0 until safeSelection.h) {
                    outputPixels[dstY * safeSelection.w + dstX] = pixel
                }
            }
        }
    }

    val output = safeSrc.copy(Bitmap.Config.ARGB_8888, true)
    output.setPixels(
        outputPixels,
        0,
        safeSelection.w,
        safeSelection.x,
        safeSelection.y,
        safeSelection.w,
        safeSelection.h,
    )
    return output
}

// クリップBitmapを貼り付けた新しいBitmapを返す（元のBitmapは変更しない）
fun paste(src: Bitmap, clip: Bitmap, dstX: Int, dstY: Int): Bitmap {
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    var safeDstX = dstX
    var safeDstY = dstY
    var srcX = 0
    var srcY = 0
    if (safeDstX < 0) {
        srcX = -safeDstX
        safeDstX = 0
    }
    if (safeDstY < 0) {
        srcY = -safeDstY
        safeDstY = 0
    }
    val maxW = minOf(clip.width - srcX, src.width - safeDstX)
    val maxH = minOf(clip.height - srcY, src.height - safeDstY)
    if (maxW <= 0 || maxH <= 0) {
        return output
    }
    val srcRect = Rect(srcX, srcY, srcX + maxW, srcY + maxH)
    val dstRect = Rect(safeDstX, safeDstY, safeDstX + maxW, safeDstY + maxH)
    val canvas = Canvas(output)
    canvas.drawBitmap(clip, srcRect, dstRect, null)
    return output
}

// 9点サンプリング(3x3)でpremultiplied alpha平均のダウンサンプル
fun downscaleNineSamplePremul(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
): IntArray {
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val output = IntArray(safeDstW * safeDstH)
    val samplePoints = floatArrayOf(0.17f, 0.5f, 0.83f)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    val maxX = (safeSrcW - 1).toFloat()
    val maxY = (safeSrcH - 1).toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            var accR = 0f
            var accG = 0f
            var accB = 0f
            var accA = 0f
            var maxAlpha = 0
            var brightestPixel = Color.TRANSPARENT
            var brightestScore = -1
            for (sy in samplePoints) {
                val sampleY = (srcTop + (srcBottom - srcTop) * sy).coerceIn(0f, maxY)
                val iy = sampleY.toInt()
                val rowOffset = iy * safeSrcW
                for (sx in samplePoints) {
                    val sampleX = (srcLeft + (srcRight - srcLeft) * sx).coerceIn(0f, maxX)
                    val ix = sampleX.toInt()
                    val pixel = srcPixels[rowOffset + ix]
                    val a = (pixel ushr 24) and 0xFF
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (a > 0) {
                        val brightness = r + g + b
                        if (a > maxAlpha || (a == maxAlpha && brightness > brightestScore)) {
                            maxAlpha = a
                            brightestScore = brightness
                            brightestPixel = pixel
                        }
                    }
                    accA += a.toFloat()
                    accR += r * a.toFloat()
                    accG += g * a.toFloat()
                    accB += b * a.toFloat()
                }
            }
            val avgA = accA / 9f
            val outA = avgA.roundToInt().coerceIn(0, 255)
            val outR: Int
            val outG: Int
            val outB: Int
            if (accA <= 0f) {
                outR = 0
                outG = 0
                outB = 0
            } else if (outA == 0 && maxAlpha > 0) {
                // 極細線の消失を防ぐため、代表ピクセルを採用する
                outR = (brightestPixel ushr 16) and 0xFF
                outG = (brightestPixel ushr 8) and 0xFF
                outB = brightestPixel and 0xFF
            } else {
                val invA = 1f / accA
                outR = (accR * invA).roundToInt().coerceIn(0, 255)
                outG = (accG * invA).roundToInt().coerceIn(0, 255)
                outB = (accB * invA).roundToInt().coerceIn(0, 255)
            }
            output[y * safeDstW + x] =
                ((if (outA == 0 && maxAlpha > 0) maxAlpha else outA) shl 24) or
                    (outR shl 16) or (outG shl 8) or outB
        }
    }
    return output
}

private fun downscaleNineSamplePremulAlphaWeighted(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    minAlphaCutoff: Int = 4,
): IntArray {
    val safeSrcW = srcW.coerceAtLeast(1)
    val safeSrcH = srcH.coerceAtLeast(1)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val output = IntArray(safeDstW * safeDstH)
    val samplePoints = floatArrayOf(0.17f, 0.5f, 0.83f)
    val scaleX = safeSrcW.toFloat() / safeDstW.toFloat()
    val scaleY = safeSrcH.toFloat() / safeDstH.toFloat()
    val maxX = (safeSrcW - 1).toFloat()
    val maxY = (safeSrcH - 1).toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            var accR = 0f
            var accG = 0f
            var accB = 0f
            var accA = 0f
            var accW = 0f
            var maxAlpha = 0
            var maxR = 0
            var maxG = 0
            var maxB = 0
            for (sy in samplePoints) {
                val sampleY = (srcTop + (srcBottom - srcTop) * sy).coerceIn(0f, maxY)
                val iy = sampleY.toInt()
                val rowOffset = iy * safeSrcW
                for (sx in samplePoints) {
                    val sampleX = (srcLeft + (srcRight - srcLeft) * sx).coerceIn(0f, maxX)
                    val ix = sampleX.toInt()
                    val pixel = srcPixels[rowOffset + ix]
                    val a = (pixel ushr 24) and 0xFF
                    if (a < minAlphaCutoff) {
                        continue
                    }
                    val weight = a.toFloat() / 255f
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (a > maxAlpha) {
                        maxAlpha = a
                        maxR = r
                        maxG = g
                        maxB = b
                    }
                    accW += weight
                    accA += a * weight
                    accR += r * a * weight
                    accG += g * a * weight
                    accB += b * a * weight
                }
            }
            val outA: Int
            val outR: Int
            val outG: Int
            val outB: Int
            if (accW <= 0f || accA <= 0f) {
                outA = 0
                outR = 0
                outG = 0
                outB = 0
            } else {
                val avgA = (accA / accW).roundToInt().coerceIn(0, 255)
                outA = maxOf(avgA, maxAlpha)
                if (outA == maxAlpha && maxAlpha > 0) {
                    outR = maxR
                    outG = maxG
                    outB = maxB
                } else {
                    val invA = 1f / accA
                    outR = (accR * invA).roundToInt().coerceIn(0, 255)
                    outG = (accG * invA).roundToInt().coerceIn(0, 255)
                    outB = (accB * invA).roundToInt().coerceIn(0, 255)
                }
            }
            output[y * safeDstW + x] =
                (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }
    return output
}

private fun downscaleMultiStepAlphaWeightedPremul(
    srcPixels: IntArray,
    srcW: Int,
    srcH: Int,
    dstW: Int,
    dstH: Int,
    stepFactor: Float,
    minAlphaCutoff: Int = 4,
    maxSteps: Int = 16,
): IntArray {
    var curW = srcW.coerceAtLeast(1)
    var curH = srcH.coerceAtLeast(1)
    var curPixels = srcPixels
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    var stepCount = 0
    while (curW > safeDstW || curH > safeDstH) {
        var nextW = maxOf(safeDstW, (curW * stepFactor).roundToInt())
        var nextH = maxOf(safeDstH, (curH * stepFactor).roundToInt())
        if (nextW == curW && curW > safeDstW) {
            nextW = curW - 1
        }
        if (nextH == curH && curH > safeDstH) {
            nextH = curH - 1
        }
        if (nextW >= curW && nextH >= curH) {
            break
        }
        stepCount += 1
        if (stepCount > maxSteps) {
            break
        }
        curPixels = downscaleNineSamplePremulAlphaWeighted(
            srcPixels = curPixels,
            srcW = curW,
            srcH = curH,
            dstW = nextW,
            dstH = nextH,
            minAlphaCutoff = minAlphaCutoff,
        )
        curW = nextW
        curH = nextH
    }
    return if (curW == safeDstW && curH == safeDstH) {
        curPixels
    } else {
        downscaleNineSamplePremulAlphaWeighted(
            srcPixels = curPixels,
            srcW = curW,
            srcH = curH,
            dstW = safeDstW,
            dstH = safeDstH,
            minAlphaCutoff = minAlphaCutoff,
        )
    }
}

private fun downscaleRegionMaxAlpha(
    src: Bitmap,
    rect: RectPx,
    dstW: Int,
    dstH: Int,
): IntArray {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val safeDstW = dstW.coerceAtLeast(1)
    val safeDstH = dstH.coerceAtLeast(1)
    val srcW = safeRect.w.coerceAtLeast(1)
    val srcH = safeRect.h.coerceAtLeast(1)
    val selectionPixels = IntArray(srcW * srcH)
    val rowBuffer = IntArray(srcW)
    // 選択範囲のオフセットを必ず反映して取得する（座標系ズレ防止）
    for (y in 0 until srcH) {
        src.getPixels(rowBuffer, 0, srcW, safeRect.x, safeRect.y + y, srcW, 1)
        System.arraycopy(rowBuffer, 0, selectionPixels, y * srcW, srcW)
    }
    val outPixels = IntArray(safeDstW * safeDstH)
    val scaleX = srcW.toFloat() / safeDstW.toFloat()
    val scaleY = srcH.toFloat() / safeDstH.toFloat()
    for (y in 0 until safeDstH) {
        val srcTop = y * scaleY
        val srcBottom = (y + 1) * scaleY
        // floor/ceilの混在でサンプル範囲の空を避ける
        var sy0 = floor(srcTop).toInt().coerceIn(0, srcH - 1)
        var sy1 = (ceil(srcBottom).toInt() - 1).coerceIn(0, srcH - 1)
        if (sy1 < sy0) {
            sy1 = sy0
        }
        for (x in 0 until safeDstW) {
            val srcLeft = x * scaleX
            val srcRight = (x + 1) * scaleX
            // floor/ceilの混在でサンプル範囲の空を避ける
            var sx0 = floor(srcLeft).toInt().coerceIn(0, srcW - 1)
            var sx1 = (ceil(srcRight).toInt() - 1).coerceIn(0, srcW - 1)
            if (sx1 < sx0) {
                sx1 = sx0
            }
            var bestPixel = Color.TRANSPARENT
            var bestAlpha = -1
            var bestBrightness = -1
            // 縮小先の代表ピクセルは「最大alpha優先」、同点は最も明るい色を採用
            for (sy in sy0..sy1) {
                val row = sy * srcW
                for (sx in sx0..sx1) {
                    val pixel = selectionPixels[row + sx]
                    val alpha = (pixel ushr 24) and 0xFF
                    val brightness = ((pixel ushr 16) and 0xFF) +
                        ((pixel ushr 8) and 0xFF) +
                        (pixel and 0xFF)
                    if (alpha > bestAlpha || (alpha == bestAlpha && brightness > bestBrightness)) {
                        bestAlpha = alpha
                        bestBrightness = brightness
                        bestPixel = pixel
                    }
                }
            }
            outPixels[y * safeDstW + x] = bestPixel
        }
    }
    return outPixels
}

// 選択矩形内を最大サイズに合わせて縮小する
fun resizeSelectionToMax96(
    src: Bitmap,
    selection: RectPx,
    maxSize: Int = 96,
    anchor: ResizeAnchor = ResizeAnchor.TopLeft,
    stepFactor: Float = 0.5f,
    minAlphaCutoff: Int = 4,
): ResizeSelectionResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return ResizeSelectionResult(safeSrc, selection, false, "invalid bitmap")
    }
    val safeSelection = rectNormalizeClamp(selection, width, height)
    val maxDim = maxOf(safeSelection.w, safeSelection.h)
    if (maxDim <= maxSize) {
        return ResizeSelectionResult(safeSrc, safeSelection, false, "already <= max")
    }
    val scale = maxSize.toFloat() / maxDim.toFloat()
    val dstW = (safeSelection.w * scale).roundToInt().coerceAtLeast(1)
    val dstH = (safeSelection.h * scale).roundToInt().coerceAtLeast(1)
    val pasteX = when (anchor) {
        ResizeAnchor.TopLeft -> safeSelection.x
        // 選択範囲の中心に合わせるため、縮小後サイズとの差分を半分だけずらす
        ResizeAnchor.Center -> safeSelection.x + (safeSelection.w - dstW) / 2
    }
    val pasteY = when (anchor) {
        ResizeAnchor.TopLeft -> safeSelection.y
        // 選択範囲の中心に合わせるため、縮小後サイズとの差分を半分だけずらす
        ResizeAnchor.Center -> safeSelection.y + (safeSelection.h - dstH) / 2
    }
    val newSelection = rectNormalizeClamp(RectPx.of(pasteX, pasteY, dstW, dstH), width, height)
    // clamp 後の座標に合わせて貼り付ける（座標ズレ防止）
    val dstX = newSelection.x
    val dstY = newSelection.y

    val downscaledPixels = downscaleRegionMaxAlpha(
        src = safeSrc,
        rect = safeSelection,
        dstW = newSelection.w,
        dstH = newSelection.h,
    )
    val clipBitmap = Bitmap.createBitmap(newSelection.w, newSelection.h, Bitmap.Config.ARGB_8888)
    clipBitmap.setPixels(
        downscaledPixels,
        0,
        newSelection.w,
        0,
        0,
        newSelection.w,
        newSelection.h,
    )

    // 元の選択範囲をクリアしてから縮小結果を貼り付ける
    val cleared = clearTransparent(safeSrc, safeSelection)
    val output = cleared.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    canvas.drawBitmap(clipBitmap, dstX.toFloat(), dstY.toFloat(), null)
    val debugText = "scale=$scale new=${newSelection.w}x${newSelection.h} step=$stepFactor cutoff=$minAlphaCutoff"
    return ResizeSelectionResult(output, newSelection, true, debugText)
}

// Bitmap全体をグレースケールへ焼き込み変換した新しいBitmapを返す（元のBitmapは変更しない）
fun toGrayscale(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    if (safeSrc.width <= 0 || safeSrc.height <= 0) {
        return safeSrc
    }
    val width = safeSrc.width
    val height = safeSrc.height
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(size)
    for (index in 0 until size) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val gray = (299 * red + 587 * green + 114 * blue + 500) / 1000
        outPixels[index] = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体を大津の二値化で白黒変換した新しいBitmapを返す（元のBitmapは変更しない）
fun toBinarize(src: Bitmap, alphaThreshold: Int = BINARIZE_ALPHA_THRESHOLD): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)

    val otsu = otsuThresholdFromPixels(srcPixels, alphaThreshold)
    val threshold = if (otsu == null || otsu < BINARIZE_MIN_OTSU_THRESHOLD || otsu > BINARIZE_MAX_OTSU_THRESHOLD) {
        BINARIZE_FALLBACK_THRESHOLD
    } else {
        otsu
    }

    val outPixels = IntArray(size)
    for (index in 0 until size) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < alphaThreshold) {
            outPixels[index] = 0
            continue
        }

        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val luminance = (299 * red + 587 * green + 114 * blue + 500) / 1000
        outPixels[index] = if (luminance < threshold) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

private fun otsuThresholdFromPixels(pixels: IntArray, alphaThreshold: Int): Int? {
    val histogram = IntArray(256)
    var validPixelCount = 0
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < alphaThreshold) {
            continue
        }
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        val luminance = (299 * red + 587 * green + 114 * blue + 500) / 1000
        histogram[luminance] += 1
        validPixelCount += 1
    }

    if (validPixelCount < BINARIZE_MIN_VALID_PIXELS) {
        return null
    }

    var sum = 0.0
    for (i in histogram.indices) {
        sum += i * histogram[i].toDouble()
    }

    var backgroundWeight = 0.0
    var backgroundSum = 0.0
    var bestVariance = -1.0
    var bestThreshold = 0
    val total = validPixelCount.toDouble()

    for (i in histogram.indices) {
        backgroundWeight += histogram[i].toDouble()
        if (backgroundWeight <= 0.0) {
            continue
        }

        val foregroundWeight = total - backgroundWeight
        if (foregroundWeight <= 0.0) {
            break
        }

        backgroundSum += i * histogram[i].toDouble()
        val backgroundMean = backgroundSum / backgroundWeight
        val foregroundMean = (sum - backgroundSum) / foregroundWeight
        val betweenClassVariance = backgroundWeight * foregroundWeight *
            (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)

        if (betweenClassVariance > bestVariance) {
            bestVariance = betweenClassVariance
            bestThreshold = i
        }
    }

    return bestThreshold
}

// Bitmap全体に8近傍ベースの外側1pxアウトラインを焼き込んだ新しいBitmapを返す（元のBitmapは変更しない）
fun addOutline(
    src: Bitmap,
    outlineColor: Int = android.graphics.Color.BLACK,
    thresholdAlpha: Int = 16,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val srcPixels = IntArray(width * height)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val neighborOffsets = arrayOf(
        intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        intArrayOf(-1, 0), intArrayOf(1, 0),
        intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1),
    )

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val alpha = (srcPixels[index] ushr 24) and 0xFF
            val isBody = alpha >= thresholdAlpha
            if (isBody) {
                continue
            }

            var hasBodyNeighbor = false
            for (offset in neighborOffsets) {
                val nx = x + offset[0]
                val ny = y + offset[1]
                if (nx !in 0 until width || ny !in 0 until height) {
                    continue
                }
                val nIndex = ny * width + nx
                val nAlpha = (srcPixels[nIndex] ushr 24) and 0xFF
                if (nAlpha >= thresholdAlpha) {
                    hasBodyNeighbor = true
                    break
                }
            }

            if (hasBodyNeighbor) {
                outPixels[index] = outlineColor
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体に「外側背景に接する境界のみ」1pxアウトラインを焼き込んだ新しいBitmapを返す（元のBitmapは変更しない）
fun addOuterOutline(
    src: Bitmap,
    outlineColor: Int = android.graphics.Color.BLACK,
    thresholdAlpha: Int = 16,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)

    val isOpaque = BooleanArray(size)
    for (index in 0 until size) {
        val alpha = (srcPixels[index] ushr 24) and 0xFF
        isOpaque[index] = alpha >= thresholdAlpha
    }

    val outside = BooleanArray(size)
    val queue = ArrayDeque<Int>()

    fun enqueueIfOutside(x: Int, y: Int) {
        val idx = y * width + x
        if (!isOpaque[idx] && !outside[idx]) {
            outside[idx] = true
            queue.addLast(idx)
        }
    }

    for (x in 0 until width) {
        enqueueIfOutside(x, 0)
        enqueueIfOutside(x, height - 1)
    }
    for (y in 0 until height) {
        enqueueIfOutside(0, y)
        enqueueIfOutside(width - 1, y)
    }

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        val x = idx % width
        val y = idx / width

        if (x > 0) {
            val left = idx - 1
            if (!isOpaque[left] && !outside[left]) {
                outside[left] = true
                queue.addLast(left)
            }
        }
        if (x < width - 1) {
            val right = idx + 1
            if (!isOpaque[right] && !outside[right]) {
                outside[right] = true
                queue.addLast(right)
            }
        }
        if (y > 0) {
            val top = idx - width
            if (!isOpaque[top] && !outside[top]) {
                outside[top] = true
                queue.addLast(top)
            }
        }
        if (y < height - 1) {
            val bottom = idx + width
            if (!isOpaque[bottom] && !outside[bottom]) {
                outside[bottom] = true
                queue.addLast(bottom)
            }
        }
    }

    val outPixels = srcPixels.copyOf()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            if (!outside[idx]) {
                continue
            }

            var hasOpaqueNeighbor = false
            for (ny in (y - 1)..(y + 1)) {
                if (ny !in 0 until height) continue
                for (nx in (x - 1)..(x + 1)) {
                    if (nx !in 0 until width) continue
                    if (nx == x && ny == y) continue
                    val neighborIdx = ny * width + nx
                    if (isOpaque[neighborIdx]) {
                        hasOpaqueNeighbor = true
                        break
                    }
                }
                if (hasOpaqueNeighbor) {
                    break
                }
            }

            if (hasOpaqueNeighbor) {
                outPixels[idx] = outlineColor
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// Bitmap全体から外周に接する背景領域を透明化した新しいBitmapを返す（元のBitmapは変更しない）
fun clearEdgeConnectedBackground(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val bgSample = sampleEdgeBackgroundRgb(srcPixels, width, height)
    val bgR = bgSample?.first ?: 0
    val bgG = bgSample?.second ?: 0
    val bgB = bgSample?.third ?: 0
    val transparentOnly = bgSample == null

    val visited = BooleanArray(size)
    val queue = ArrayDeque<Int>()

    fun tryEnqueue(index: Int) {
        val pixel = srcPixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        val isBackground = if (transparentOnly) {
            alpha == 0
        } else {
            isBackgroundLike(pixel, bgR, bgG, bgB)
        }
        if (!visited[index] && isBackground) {
            visited[index] = true
            queue.addLast(index)
        }
    }

    for (x in 0 until width) {
        tryEnqueue(x)
        tryEnqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
        tryEnqueue(y * width)
        tryEnqueue(y * width + (width - 1))
    }

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        val x = index % width
        val y = index / width

        outPixels[index] = srcPixels[index] and 0x00FFFFFF

        if (x > 0) tryEnqueue(index - 1)
        if (x < width - 1) tryEnqueue(index + 1)
        if (y > 0) tryEnqueue(index - width)
        if (y < height - 1) tryEnqueue(index + width)
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// 選択矩形内の代表点から連結成分を透明化した新しいBitmapを返す（元のBitmapは変更しない）
fun clearConnectedRegionFromSelection(
    src: Bitmap,
    selection: RectPx,
): Bitmap {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return safeSrc
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    var seedIndex = -1
    val endY = safeSelection.y + safeSelection.h
    val endX = safeSelection.x + safeSelection.w
    for (y in safeSelection.y until endY) {
        for (x in safeSelection.x until endX) {
            val index = y * width + x
            val alpha = (srcPixels[index] ushr 24) and 0xFF
            if (alpha > 0) {
                seedIndex = index
                break
            }
        }
        if (seedIndex >= 0) {
            break
        }
    }

    if (seedIndex < 0) {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    val seedPixel = srcPixels[seedIndex]
    val seedR = (seedPixel ushr 16) and 0xFF
    val seedG = (seedPixel ushr 8) and 0xFF
    val seedB = seedPixel and 0xFF

    val visited = BooleanArray(size)
    val queue = ArrayDeque<Int>()
    visited[seedIndex] = true
    queue.addLast(seedIndex)

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        val x = index % width
        val y = index / width

        outPixels[index] = srcPixels[index] and 0x00FFFFFF

        if (x > 0) enqueueIfRegionMatch(index - 1, srcPixels, visited, queue, seedR, seedG, seedB)
        if (x < width - 1) enqueueIfRegionMatch(index + 1, srcPixels, visited, queue, seedR, seedG, seedB)
        if (y > 0) enqueueIfRegionMatch(index - width, srcPixels, visited, queue, seedR, seedG, seedB)
        if (y < height - 1) enqueueIfRegionMatch(index + width, srcPixels, visited, queue, seedR, seedG, seedB)
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return output
}

// 選択矩形内の透明ピクセルをseedに4近傍で連結探索し、到達領域を白で塗る（元のBitmapは変更しない）
enum class FillRegionTransparentStatus {
    APPLIED,
    NO_TRANSPARENT_PIXELS_IN_SELECTION,
    ABORTED_TOO_LARGE,
}

data class FillRegionTransparentResult(
    val bitmap: Bitmap,
    val status: FillRegionTransparentStatus,
)

fun fillRegionFromTransparentSeeds(
    src: Bitmap,
    selection: RectPx,
    maxFillPixels: Int = selection.w * selection.h,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
): FillRegionTransparentResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return FillRegionTransparentResult(
            bitmap = safeSrc,
            status = FillRegionTransparentStatus.NO_TRANSPARENT_PIXELS_IN_SELECTION,
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()

    val visited = BooleanArray(size)
    val queue = IntArray(size)
    val white = 0xFFFFFFFF.toInt()
    val selectionArea = safeSelection.w * safeSelection.h
    val selectionBasedLimit = selectionArea.coerceAtLeast(1)
        .coerceAtMost(FILL_REGION_ABSOLUTE_MAX_PIXELS)
    val fillLimit = maxFillPixels.coerceAtLeast(1)
        .coerceAtMost(selectionBasedLimit)

    fun isTransparent(index: Int): Boolean {
        val alpha = (srcPixels[index] ushr 24) and 0xFF
        return alpha < transparentAlphaThreshold
    }

    val sy = safeSelection.y
    val ey = safeSelection.y + safeSelection.h
    val sx = safeSelection.x
    val ex = safeSelection.x + safeSelection.w

    var head = 0
    var tail = 0
    fun enqueueSeed(x: Int, y: Int) {
        val seedIndex = y * width + x
        if (visited[seedIndex] || !isTransparent(seedIndex)) {
            return
        }
        visited[seedIndex] = true
        queue[tail++] = seedIndex
    }

    for (x in sx until ex) {
        enqueueSeed(x, sy)
        if (ey - 1 != sy) {
            enqueueSeed(x, ey - 1)
        }
    }
    for (y in sy until ey) {
        enqueueSeed(sx, y)
        if (ex - 1 != sx) {
            enqueueSeed(ex - 1, y)
        }
    }

    if (tail == 0) {
        return FillRegionTransparentResult(
            bitmap = safeSrc,
            status = FillRegionTransparentStatus.NO_TRANSPARENT_PIXELS_IN_SELECTION,
        )
    }

    var filledCount = 0
    while (head < tail) {
        val index = queue[head++]
        outPixels[index] = white
        filledCount += 1
        if (filledCount > fillLimit) {
            return FillRegionTransparentResult(
                bitmap = safeSrc,
                status = FillRegionTransparentStatus.ABORTED_TOO_LARGE,
            )
        }

        val px = index % width
        val py = index / width

        if (px > sx) {
            val left = index - 1
            if (!visited[left] && isTransparent(left)) {
                visited[left] = true
                queue[tail++] = left
            }
        }
        if (px + 1 < ex) {
            val right = index + 1
            if (!visited[right] && isTransparent(right)) {
                visited[right] = true
                queue[tail++] = right
            }
        }
        if (py > sy) {
            val up = index - width
            if (!visited[up] && isTransparent(up)) {
                visited[up] = true
                queue[tail++] = up
            }
        }
        if (py + 1 < ey) {
            val down = index + width
            if (!visited[down] && isTransparent(down)) {
                visited[down] = true
                queue[tail++] = down
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    return FillRegionTransparentResult(
        bitmap = output,
        status = FillRegionTransparentStatus.APPLIED,
    )
}

fun fillConnectedToWhite(
    src: Bitmap,
    selection: RectPx,
    transparentAlphaThreshold: Int = FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD,
    rgbTolerance: Int = FILL_CONNECTED_RGB_TOLERANCE,
): FillConnectedResult {
    val safeSrc = ensureArgb8888(src)
    val width = safeSrc.width
    val height = safeSrc.height
    if (width <= 0 || height <= 0) {
        return FillConnectedResult(
            bitmap = safeSrc,
            filled = 0,
            aborted = false,
            mode = Mode.Alpha,
            debugText = "Fill: mode=alpha T=0 thr=$transparentAlphaThreshold filled=0",
        )
    }

    val safeSelection = rectNormalizeClamp(selection, width, height)
    val imageArea = width * height
    val maxFillPixels = minOf(imageArea, FILL_REGION_ABSOLUTE_MAX_PIXELS).coerceAtLeast(1)
    val size = width * height
    val srcPixels = IntArray(size)
    safeSrc.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = srcPixels.copyOf()
    val visited = BooleanArray(size)
    val queue = IntArray(size)

    val sx = safeSelection.x
    val sy = safeSelection.y
    val ex = safeSelection.x + safeSelection.w
    val ey = safeSelection.y + safeSelection.h

    var transparentCount = 0
    var rgbCount = 0
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    for (y in sy until ey) {
        for (x in sx until ex) {
            val pixel = srcPixels[y * width + x]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < transparentAlphaThreshold) {
                transparentCount += 1
            } else {
                sumR += (pixel ushr 16) and 0xFF
                sumG += (pixel ushr 8) and 0xFF
                sumB += pixel and 0xFF
                rgbCount += 1
            }
        }
    }

    val mode = if (transparentCount > 0) Mode.Alpha else Mode.Rgb
    val avgR = if (rgbCount > 0) (sumR / rgbCount).toInt() else 0
    val avgG = if (rgbCount > 0) (sumG / rgbCount).toInt() else 0
    val avgB = if (rgbCount > 0) (sumB / rgbCount).toInt() else 0

    fun isTarget(pixel: Int): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        return if (mode == Mode.Alpha) {
            alpha < transparentAlphaThreshold
        } else {
            if (alpha < transparentAlphaThreshold) return false
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            kotlin.math.abs(red - avgR) + kotlin.math.abs(green - avgG) + kotlin.math.abs(blue - avgB) <= rgbTolerance
        }
    }

    var head = 0
    var tail = 0
    for (y in sy until ey) {
        for (x in sx until ex) {
            val index = y * width + x
            if (!visited[index] && isTarget(srcPixels[index])) {
                visited[index] = true
                queue[tail++] = index
            }
        }
    }

    if (tail == 0) {
        val debugText = if (mode == Mode.Alpha) {
            "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=0 limit=$maxFillPixels"
        } else {
            "Fill: mode=rgb tol=$rgbTolerance filled=0 limit=$maxFillPixels"
        }
        return FillConnectedResult(safeSrc, 0, false, mode, debugText)
    }

    val white = 0xFFFFFFFF.toInt()
    var filledCount = 0
    while (head < tail) {
        val index = queue[head++]
        outPixels[index] = white
        filledCount += 1
        if (filledCount > maxFillPixels) {
            val debugText = if (mode == Mode.Alpha) {
                "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=$filledCount limit=$maxFillPixels"
            } else {
                "Fill: mode=rgb tol=$rgbTolerance filled=$filledCount limit=$maxFillPixels"
            }
            return FillConnectedResult(safeSrc, filledCount, true, mode, debugText)
        }

        val px = index % width
        val py = index / width
        if (px > 0) {
            val left = index - 1
            if (!visited[left] && isTarget(srcPixels[left])) {
                visited[left] = true
                queue[tail++] = left
            }
        }
        if (px < width - 1) {
            val right = index + 1
            if (!visited[right] && isTarget(srcPixels[right])) {
                visited[right] = true
                queue[tail++] = right
            }
        }
        if (py > 0) {
            val up = index - width
            if (!visited[up] && isTarget(srcPixels[up])) {
                visited[up] = true
                queue[tail++] = up
            }
        }
        if (py < height - 1) {
            val down = index + width
            if (!visited[down] && isTarget(srcPixels[down])) {
                visited[down] = true
                queue[tail++] = down
            }
        }
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(outPixels, 0, width, 0, 0, width, height)
    val debugText = if (mode == Mode.Alpha) {
        "Fill: mode=alpha T=$transparentCount thr=$transparentAlphaThreshold filled=$filledCount limit=$maxFillPixels"
    } else {
        "Fill: mode=rgb tol=$rgbTolerance filled=$filledCount limit=$maxFillPixels"
    }
    return FillConnectedResult(output, filledCount, false, mode, debugText)
}

private fun sampleEdgeBackgroundRgb(
    pixels: IntArray,
    width: Int,
    height: Int,
): Triple<Int, Int, Int>? {
    val sampleIndices = linkedSetOf<Int>()
    for (x in 0 until width) {
        sampleIndices.add(x)
        sampleIndices.add((height - 1) * width + x)
    }
    for (y in 0 until height) {
        sampleIndices.add(y * width)
        sampleIndices.add(y * width + (width - 1))
    }

    val reds = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)
    val greens = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)
    val blues = ArrayList<Int>(CLEAR_BG_EDGE_SAMPLE_LIMIT)

    for (index in sampleIndices) {
        val pixel = pixels[index]
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha <= 0) {
            continue
        }
        reds.add((pixel ushr 16) and 0xFF)
        greens.add((pixel ushr 8) and 0xFF)
        blues.add(pixel and 0xFF)
        if (reds.size >= CLEAR_BG_EDGE_SAMPLE_LIMIT) {
            break
        }
    }

    if (reds.isEmpty()) {
        return null
    }

    reds.sort()
    greens.sort()
    blues.sort()
    val mid = reds.size / 2
    return Triple(reds[mid], greens[mid], blues[mid])
}

private fun isBackgroundLike(pixel: Int, bgR: Int, bgG: Int, bgB: Int): Boolean {
    val alpha = (pixel ushr 24) and 0xFF
    if (alpha == 0) {
        return true
    }
    if (alpha < CLEAR_BG_MIN_ALPHA) {
        return false
    }

    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    val colorDistance = kotlin.math.abs(red - bgR) +
        kotlin.math.abs(green - bgG) +
        kotlin.math.abs(blue - bgB)
    return colorDistance <= CLEAR_BG_COLOR_DISTANCE_THRESHOLD
}

private fun enqueueIfRegionMatch(
    index: Int,
    srcPixels: IntArray,
    visited: BooleanArray,
    queue: ArrayDeque<Int>,
    seedR: Int,
    seedG: Int,
    seedB: Int,
) {
    if (visited[index]) {
        return
    }
    val pixel = srcPixels[index]
    val alpha = (pixel ushr 24) and 0xFF
    if (alpha <= 0) {
        return
    }

    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    val colorDistance = kotlin.math.abs(red - seedR) +
        kotlin.math.abs(green - seedG) +
        kotlin.math.abs(blue - seedB)
    if (colorDistance > CLEAR_REGION_COLOR_DISTANCE_THRESHOLD) {
        return
    }

    visited[index] = true
    queue.addLast(index)
}
