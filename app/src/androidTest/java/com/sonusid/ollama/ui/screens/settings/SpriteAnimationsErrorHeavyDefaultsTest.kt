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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsErrorHeavyDefaultsTest {

    @Test
    fun ensurePerStateAnimationJsonsInitialized_sets_error_heavy_defaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        dataStore.edit { preferences ->
            preferences.clear()
        }

        val prefs = SettingsPreferences(context)
        prefs.saveSelectedKey(SpriteState.ERROR, "ErrorHeavy")
        prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()

        val errorJson = withTimeout(5_000) {
            prefs.spriteAnimationJsonFlow(SpriteState.ERROR).first()
        }
        assertNotNull(errorJson)

        val root = JSONObject(errorJson!!)
        val base = root.getJSONObject("base")
        val insertion = root.getJSONObject("insertion")

        assertEquals("ErrorHeavy", root.getString("animationKey"))
        val (baseDefaults, insertionDefaults) = prefs.defaultErrorAnimationSettingsForKey("ErrorHeavy")
        assertEquals(baseDefaults.intervalMs, base.getInt("intervalMs"))
        assertEquals(baseDefaults.frameSequence, base.getJSONArray("frames").toIntList())

        assertTrue(insertion.getBoolean("enabled"))
        assertFalse("intervalMs は省略可能", insertion.has("intervalMs"))
        assertEquals(insertionDefaults.everyNLoops, insertion.getInt("everyNLoops"))
        assertEquals(
            insertionDefaults.probabilityPercent,
            insertion.getInt("probabilityPercent"),
        )
        assertEquals(insertionDefaults.cooldownLoops, insertion.getInt("cooldownLoops"))
        assertEquals(insertionDefaults.exclusive, insertion.getBoolean("exclusive"))
        val patterns = insertion.getJSONArray("patterns")
        assertEquals(insertionDefaults.patterns.size, patterns.length())
        val primaryPattern = patterns.getJSONObject(0)
        val expectedPattern = insertionDefaults.patterns[0]
        assertEquals(expectedPattern.frameSequence, primaryPattern.getJSONArray("frames").toIntList())
        assertEquals(expectedPattern.weight, primaryPattern.getInt("weight"))
        assertEquals(expectedPattern.intervalMs, primaryPattern.getInt("intervalMs"))
    }

    private fun JSONArray.toIntList(): List<Int> = buildList {
        for (index in 0 until length()) {
            add(getInt(index))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun accessSettingsDataStore(context: Context): DataStore<Preferences> {
        val settingsClass = Class.forName(
            "com.sonusid.ollama.ui.screens.settings.SettingsPreferencesKt",
        )
        val getter = settingsClass.getDeclaredMethod("getDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }
}
