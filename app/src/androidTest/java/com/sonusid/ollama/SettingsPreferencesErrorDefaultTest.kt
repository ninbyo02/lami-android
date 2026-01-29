package com.sonusid.ollama

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sonusid.ollama.ui.screens.settings.InsertionAnimationSettings
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.screens.settings.SpriteState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesErrorDefaultTest {

    @Test
    fun ensurePerStateAnimationJsonsInitialized_setsErrorLightDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsPreferences = SettingsPreferences(context)

        // pm clear 相当の状態を作るためDataStoreを初期化する
        settingsPreferences.clearAllPreferencesForTest()

        val initialized = settingsPreferences.ensurePerStateAnimationJsonsInitialized().getOrThrow()
        assertTrue(initialized)

        val errorJson = settingsPreferences.spriteAnimationJsonFlow(SpriteState.ERROR).first()
        assertNotNull(errorJson)

        val config = settingsPreferences
            .parseAndValidatePerStateAnimationJson(errorJson!!, SpriteState.ERROR)
            .getOrThrow()

        assertEquals("ErrorLight", config.animationKey)
        assertEquals(listOf(4, 6, 7, 6, 4), config.baseFrames)
        assertEquals(390, config.baseIntervalMs)

        val insertion = config.insertion
        assertTrue(insertion.enabled)
        val effectiveInsertionIntervalMs = insertion.intervalMs
            ?: insertion.patterns.firstOrNull()?.intervalMs
            ?: InsertionAnimationSettings.ERROR_DEFAULT.intervalMs
            ?: 0
        assertEquals(390, effectiveInsertionIntervalMs)
        assertEquals(3, insertion.everyNLoops)
        assertEquals(60, insertion.probabilityPercent)
        assertEquals(4, insertion.cooldownLoops)
        assertEquals(false, insertion.exclusive)

        assertEquals(1, insertion.patterns.size)
        val pattern = insertion.patterns.first()
        assertEquals(listOf(2, 4), pattern.frames)
        assertEquals(1, pattern.weight)
        assertEquals(390, pattern.intervalMs)
    }

    @Test
    fun defaultAnimationSettingsForState_returnsErrorLightDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsPreferences = SettingsPreferences(context)

        // ErrorLight のデフォルト参照が新設定に統一されていることを確認する
        val (baseDefaults, insertionDefaults) =
            settingsPreferences.defaultAnimationSettingsForState(SpriteState.ERROR)

        assertEquals(listOf(4, 6, 7, 6, 4), baseDefaults.frameSequence)
        assertEquals(390, baseDefaults.intervalMs)
        assertTrue(insertionDefaults.enabled)
        assertEquals("intervalMs は省略可能", null, insertionDefaults.intervalMs)
        assertEquals(3, insertionDefaults.everyNLoops)
        assertEquals(60, insertionDefaults.probabilityPercent)
        assertEquals(4, insertionDefaults.cooldownLoops)
        assertEquals(false, insertionDefaults.exclusive)
        assertEquals(1, insertionDefaults.patterns.size)
        val pattern = insertionDefaults.patterns.first()
        assertEquals(listOf(2, 4), pattern.frameSequence)
        assertEquals(1, pattern.weight)
        assertEquals(390, pattern.intervalMs)
    }
}
