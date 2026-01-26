package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.ui.animation.SpriteAnimationDefaults
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
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_BASE_INTERVAL_MS, base.getInt("intervalMs"))
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_FRAMES, base.getJSONArray("frames").toIntList())

        assertTrue(insertion.getBoolean("enabled"))
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_INTERVAL_MS,
            insertion.getInt("intervalMs"),
        )
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_EVERY_N_LOOPS, insertion.getInt("everyNLoops"))
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_PROBABILITY_PERCENT,
            insertion.getInt("probabilityPercent"),
        )
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_COOLDOWN_LOOPS, insertion.getInt("cooldownLoops"))
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_EXCLUSIVE, insertion.getBoolean("exclusive"))
        val patterns = insertion.getJSONArray("patterns")
        assertEquals(SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS.size, patterns.length())
        val primaryPattern = patterns.getJSONObject(0)
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[0].frames,
            primaryPattern.getJSONArray("frames").toIntList(),
        )
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[0].weight,
            primaryPattern.getInt("weight"),
        )
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[0].intervalMs,
            primaryPattern.getInt("intervalMs"),
        )
        val secondaryPattern = patterns.getJSONObject(1)
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[1].frames,
            secondaryPattern.getJSONArray("frames").toIntList(),
        )
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[1].weight,
            secondaryPattern.getInt("weight"),
        )
        assertEquals(
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_PATTERNS[1].intervalMs,
            secondaryPattern.getInt("intervalMs"),
        )
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
