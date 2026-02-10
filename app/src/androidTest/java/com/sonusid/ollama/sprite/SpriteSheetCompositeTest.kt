package com.sonusid.ollama.sprite

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteSheetCompositeTest {

    @Test
    fun compositePreserveTransparency_srcTransparent_keepsDstPixel() {
        val dst = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
            setPixel(1, 0, 0xFF00FF00.toInt())
        }
        val src = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0x00000000)
            setPixel(1, 0, 0xFF0000FF.toInt())
        }

        val result = compositePreserveTransparency(dst = dst, src = src)

        assertEquals(0xFFFF0000.toInt(), result.getPixel(0, 0))
        assertEquals(0xFF0000FF.toInt(), result.getPixel(1, 0))
    }
}
