package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesOfflineIntervalClampTest {

    @Test
    fun offlinePerStateJson_keepsIntervalWithinReadyAnimationBounds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        dataStore.edit { preferences ->
            preferences.clear()
        }

        val prefs = SettingsPreferences(context)
        val offlineJson = buildOfflinePerStateJson(intervalMs = 120)
        prefs.saveSpriteAnimationJson(SpriteState.OFFLINE, offlineJson)

        prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()

        val storedJson = withTimeout(5_000) {
            prefs.spriteAnimationJsonFlow(SpriteState.OFFLINE).first()
        }
        val baseIntervalMs = JSONObject(storedJson!!).getJSONObject("base").getInt("intervalMs")

        assertEquals(120, baseIntervalMs)
    }

    private fun buildOfflinePerStateJson(intervalMs: Int): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(8, 8, 5, 5)))
            .put("intervalMs", intervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("everyNLoops", 0)
            .put("probabilityPercent", 0)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", "OfflineLoop")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun accessSettingsDataStore(context: Context): DataStore<Preferences> {
        val settingsClass = Class.forName(
            "com.sonusid.ollama.ui.screens.settings.SettingsPreferencesKt"
        )
        val getter = settingsClass.getDeclaredMethod("getDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }
}
