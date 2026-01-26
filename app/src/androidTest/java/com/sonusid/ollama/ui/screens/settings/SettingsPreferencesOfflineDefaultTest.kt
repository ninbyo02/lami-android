package com.sonusid.ollama.ui.screens.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesOfflineDefaultTest {

    @Test
    fun defaultKeyForState_offline_is_offlineLoop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsPreferences = SettingsPreferences(context)

        assertEquals("OfflineLoop", settingsPreferences.defaultKeyForState(SpriteState.OFFLINE))
    }

    @Test
    fun ensurePerStateAnimationJsonsInitialized_setsOfflineLoopDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsPreferences = SettingsPreferences(context)

        settingsPreferences.clearAllPreferencesForTest()
        val initialized = settingsPreferences.ensurePerStateAnimationJsonsInitialized().getOrThrow()
        assertTrue(initialized)

        val offlineJson = settingsPreferences.spriteAnimationJsonFlow(SpriteState.OFFLINE).first()
        assertNotNull(offlineJson)

        val config = settingsPreferences
            .parseAndValidatePerStateAnimationJson(offlineJson!!, SpriteState.OFFLINE)
            .getOrThrow()

        assertEquals("OfflineLoop", config.animationKey)
    }
}
