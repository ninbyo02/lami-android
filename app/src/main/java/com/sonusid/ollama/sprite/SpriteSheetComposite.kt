package com.sonusid.ollama.sprite

import android.graphics.Bitmap

/**
 * src の完全透明ピクセル(alpha=0)では dst を保持し、それ以外は src を採用して合成する。
 */
fun compositePreserveTransparency(dst: Bitmap, src: Bitmap): Bitmap {
    require(dst.width == src.width && dst.height == src.height) {
        "Bitmap size mismatch: dst=${dst.width}x${dst.height}, src=${src.width}x${src.height}"
    }

    val width = dst.width
    val height = dst.height
    val pixelCount = width * height
    val dstPixels = IntArray(pixelCount)
    val srcPixels = IntArray(pixelCount)
    dst.getPixels(dstPixels, 0, width, 0, 0, width, height)
    src.getPixels(srcPixels, 0, width, 0, 0, width, height)

    val outPixels = IntArray(pixelCount)
    for (i in 0 until pixelCount) {
        val srcPixel = srcPixels[i]
        val srcAlpha = (srcPixel ushr 24) and 0xFF
        outPixels[i] = if (srcAlpha == 0) dstPixels[i] else srcPixel
    }

    return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
}

