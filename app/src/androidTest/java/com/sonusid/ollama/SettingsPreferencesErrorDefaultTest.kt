package com.sonusid.ollama

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        assertTrue(!insertion.enabled)
        assertEquals(390, insertion.intervalMs)
        assertEquals(1, insertion.everyNLoops)
        assertEquals(0, insertion.probabilityPercent)
        assertEquals(0, insertion.cooldownLoops)
        assertEquals(false, insertion.exclusive)

        assertEquals(0, insertion.patterns.size)
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
        assertTrue(!insertionDefaults.enabled)
        assertEquals(390, insertionDefaults.intervalMs)
        assertEquals(1, insertionDefaults.everyNLoops)
        assertEquals(0, insertionDefaults.probabilityPercent)
        assertEquals(0, insertionDefaults.cooldownLoops)
        assertEquals(false, insertionDefaults.exclusive)
        assertEquals(0, insertionDefaults.patterns.size)
    }
}
