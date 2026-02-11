package com.sonusid.ollama.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LamiSpriteSheetRepositoryOverrideTest {

    @Test
    fun loadLamiSpriteSheet_overrideEnabledAndFileExists_readsOverridePng() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsPreferences(context)
        val overrideFile = currentSpriteSheetOverrideFile(context)
        overrideFile.parentFile?.mkdirs()
        settings.clearAllPreferencesForTest()

        // 3x3 のセル計算が成立する最小サイズのPNGを保存する
        val overrideBitmap = Bitmap.createBitmap(9, 9, Bitmap.Config.ARGB_8888)
        FileOutputStream(overrideFile).use { output ->
            overrideBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        settings.saveSpriteCurrentSheetOverrideEnabled(enabled = true)
        val result = LamiSpriteSheetRepository.loadLamiSpriteSheet(context, forceReload = true)
        val data = (result as SpriteSheetLoadResult.Success).data

        assertEquals(9, data.bitmap.width)
        assertEquals(9, data.bitmap.height)

        overrideFile.delete()
    }

    @Test
    fun loadLamiSpriteSheet_overrideEnabledButFileMissing_fallsBackToDrawable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsPreferences(context)
        val overrideFile = currentSpriteSheetOverrideFile(context)
        settings.clearAllPreferencesForTest()
        if (overrideFile.exists()) {
            overrideFile.delete()
        }

        settings.saveSpriteCurrentSheetOverrideEnabled(enabled = true)
        val result = LamiSpriteSheetRepository.loadLamiSpriteSheet(context, forceReload = true)
        val data = (result as SpriteSheetLoadResult.Success).data

        val expected = BitmapFactory.decodeResource(context.resources, R.drawable.lami_sprite_3x3_288)
        assertTrue(expected != null)
        assertEquals(expected!!.width, data.bitmap.width)
        assertEquals(expected.height, data.bitmap.height)
    }

    private fun currentSpriteSheetOverrideFile(context: android.content.Context): File {
        return File(context.filesDir, "sprite_settings/current_sprite_sheet.png")
    }
}
