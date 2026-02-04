package com.sonusid.ollama

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.ui.screens.spriteeditor.RectPx
import com.sonusid.ollama.ui.screens.spriteeditor.clearTransparent
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SpriteBitmapOpsTest {
    @Test
    fun clearTransparent_clearsPixelsInRect() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }

        val cleared = clearTransparent(bitmap, RectPx.of(2, 3, 2, 2))

        val clearedPixel = cleared.getPixel(2, 3)
        val untouchedPixel = cleared.getPixel(0, 0)
        assertEquals(0, android.graphics.Color.alpha(clearedPixel))
        assertEquals(255, android.graphics.Color.alpha(untouchedPixel))
    }
}
