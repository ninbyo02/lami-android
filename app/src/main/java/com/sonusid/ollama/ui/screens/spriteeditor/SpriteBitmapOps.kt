package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.roundToInt

// 既存BitmapをARGB_8888で複製する（元のBitmapは変更しない）
fun ensureArgb8888(src: Bitmap): Bitmap {
    return if (src.config == Bitmap.Config.ARGB_8888) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src.copy(Bitmap.Config.ARGB_8888, false)
    }
}

// 全体をグレースケール化した新しいBitmapを返す（元のBitmapは変更しない）
fun toGrayscale(src: Bitmap): Bitmap {
    val safe = ensureArgb8888(src)
    val width = safe.width
    val height = safe.height
    val pixels = IntArray(width * height)
    safe.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        val color = pixels[index]
        val alpha = android.graphics.Color.alpha(color)
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        val gray = (0.299f * red + 0.587f * green + 0.114f * blue).roundToInt()
        val clamped = gray.coerceIn(0, 255)
        pixels[index] = android.graphics.Color.argb(alpha, clamped, clamped, clamped)
    }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    return output
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
