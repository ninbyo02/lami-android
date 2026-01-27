package com.sonusid.ollama.ui.screens.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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

    @Test
    fun offlinePerStateJson_omitsInsertionInterval_and_restoresDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsPreferences = SettingsPreferences(context)

        settingsPreferences.clearAllPreferencesForTest()
        settingsPreferences.ensurePerStateAnimationJsonsInitialized().getOrThrow()

        val offlineJson = settingsPreferences.spriteAnimationJsonFlow(SpriteState.OFFLINE).first()
        assertNotNull(offlineJson)

        val root = JSONObject(requireNotNull(offlineJson))
        val insertionObject = root.getJSONObject("insertion")
        assertEquals(false, insertionObject.getBoolean("enabled"))
        assertTrue("insertion.intervalMs は無効時に省略される", insertionObject.has("intervalMs").not())

        val config = settingsPreferences
            .parseAndValidatePerStateAnimationJson(offlineJson, SpriteState.OFFLINE)
            .getOrThrow()
        assertEquals(
            InsertionAnimationSettings.OFFLINE_DEFAULT.intervalMs,
            config.insertion.intervalMs,
        )
    }
}
