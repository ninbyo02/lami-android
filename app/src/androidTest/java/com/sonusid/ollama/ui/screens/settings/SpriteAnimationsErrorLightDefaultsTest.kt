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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsErrorLightDefaultsTest {

    @Test
    fun ensurePerStateAnimationJsonsInitialized_sets_error_light_defaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        dataStore.edit { preferences ->
            preferences.clear()
        }

        val prefs = SettingsPreferences(context)
        prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()

        val errorJson = withTimeout(5_000) {
            prefs.spriteAnimationJsonFlow(SpriteState.ERROR).first()
        }
        assertNotNull(errorJson)

        val root = JSONObject(errorJson!!)
        val base = root.getJSONObject("base")
        val insertion = root.getJSONObject("insertion")

        assertEquals("ErrorLight", root.getString("animationKey"))
        assertEquals(390, base.getInt("intervalMs"))
        assertEquals(listOf(4, 6, 7, 6, 4), base.getJSONArray("frames").toIntList())

        assertTrue(insertion.getBoolean("enabled"))
        assertEquals(390, insertion.getInt("intervalMs"))
        assertEquals(3, insertion.getInt("everyNLoops"))
        assertEquals(60, insertion.getInt("probabilityPercent"))
        assertEquals(4, insertion.getInt("cooldownLoops"))
        assertEquals(false, insertion.getBoolean("exclusive"))
        val patterns = insertion.getJSONArray("patterns")
        assertEquals(1, patterns.length())
        val pattern = patterns.getJSONObject(0)
        assertEquals(listOf(2, 4), pattern.getJSONArray("frames").toIntList())
        assertEquals(1, pattern.getInt("weight"))
        assertEquals(390, pattern.getInt("intervalMs"))
    }

    private fun JSONArray.toIntList(): List<Int> = buildList {
        for (index in 0 until length()) {
            add(getInt(index))
        }
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
