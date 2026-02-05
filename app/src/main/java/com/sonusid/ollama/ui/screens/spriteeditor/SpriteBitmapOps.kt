package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

const val BINARIZE_ALPHA_THRESHOLD = 16
const val BINARIZE_FALLBACK_THRESHOLD = 128
private const val BINARIZE_MIN_VALID_PIXELS = 16
private const val BINARIZE_MIN_OTSU_THRESHOLD = 40
private const val BINARIZE_MAX_OTSU_THRESHOLD = 220

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

// 指定矩形を透明でクリアした新しいBitmapを返す（元のBitmapは変更しない）
fun clearTransparent(src: Bitmap, rect: RectPx): Bitmap {
    val safeRect = rectNormalizeClamp(rect, src.width, src.height)
    val output = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    canvas.drawRect(
        safeRect.x.toFloat(),
        safeRect.y.toFloat(),
        (safeRect.x + safeRect.w).toFloat(),
        (safeRect.y + safeRect.h).toFloat(),
        paint
    )
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

// Bitmap全体をグレースケールへ焼き込み変換した新しいBitmapを返す（元のBitmapは変更しない）
fun toGrayscale(src: Bitmap): Bitmap {
    val safeSrc = ensureArgb8888(src)
    if (safeSrc.width <= 0 || safeSrc.height <= 0) {
        return safeSrc
    }
    val output = Bitmap.createBitmap(safeSrc.width, safeSrc.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(0f) },
        )
    }
    canvas.drawBitmap(safeSrc, 0f, 0f, paint)
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
