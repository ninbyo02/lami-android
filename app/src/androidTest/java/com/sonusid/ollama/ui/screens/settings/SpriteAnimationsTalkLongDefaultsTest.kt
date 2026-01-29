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
class SpriteAnimationsTalkLongDefaultsTest {

    // 検証手順:
    // - ./gradlew :app:compileDebugKotlin
    // - ./gradlew :app:compileDebugAndroidTestKotlin
    // - ./gradlew :app:connectedDebugAndroidTest
    @Test
    fun talkLong_perStateJson_is_initialized_with_new_defaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        dataStore.edit { preferences ->
            preferences.clear()
        }

        val prefs = SettingsPreferences(context)
        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        assertTrue(
            "per-state json の初期化に失敗しました: ${ensureResult.exceptionOrNull()?.message}",
            ensureResult.isSuccess,
        )

        val json = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.TALK_LONG).first() }
        assertNotNull("TALK_LONG の per-state JSON が null です", json)
        val jsonValue = requireNotNull(json)

        val root = JSONObject(jsonValue)
        assertEquals("TalkLong", root.getString("animationKey"))

        val base = root.getJSONObject("base")
        assertEquals(125, base.getInt("intervalMs"))
        assertEquals(
            listOf(0, 6, 1, 0, 6),
            base.getJSONArray("frames").toIntList(),
        )

        val insertion = root.getJSONObject("insertion")
        assertTrue(insertion.getBoolean("enabled"))
        assertEquals(false, insertion.has("intervalMs"))
        val (_, insertionDefaults) = prefs.defaultAnimationSettingsForState(SpriteState.TALK_LONG)
        assertEquals(insertionDefaults.everyNLoops, insertion.getInt("everyNLoops"))
        assertEquals(insertionDefaults.probabilityPercent, insertion.getInt("probabilityPercent"))
        assertEquals(insertionDefaults.cooldownLoops, insertion.getInt("cooldownLoops"))
        assertEquals(insertionDefaults.exclusive, insertion.getBoolean("exclusive"))

        val patterns = insertion.getJSONArray("patterns")
        assertEquals(insertionDefaults.patterns.size, patterns.length())

        val firstPattern = patterns.getJSONObject(0)
        val expectedFirstPattern = insertionDefaults.patterns[0]
        assertEquals(expectedFirstPattern.frameSequence, firstPattern.getJSONArray("frames").toIntList())
        assertEquals(expectedFirstPattern.weight, firstPattern.getInt("weight"))
        assertEquals(expectedFirstPattern.intervalMs, firstPattern.getInt("intervalMs"))

        val secondPattern = patterns.getJSONObject(1)
        val expectedSecondPattern = insertionDefaults.patterns[1]
        assertEquals(expectedSecondPattern.frameSequence, secondPattern.getJSONArray("frames").toIntList())
        assertEquals(expectedSecondPattern.weight, secondPattern.getInt("weight"))
        assertEquals(expectedSecondPattern.intervalMs, secondPattern.getInt("intervalMs"))
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

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).map { index -> getInt(index) }
}
