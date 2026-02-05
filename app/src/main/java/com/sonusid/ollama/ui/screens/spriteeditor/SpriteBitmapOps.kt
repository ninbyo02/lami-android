package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

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
