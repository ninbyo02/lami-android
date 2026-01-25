package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsPerStateJsonSpecTest {

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        runBlocking {
            withContext(Dispatchers.IO) {
                dataStore.edit { preferences ->
                    preferences.clear()
                }
            }
        }
    }

    @Test
    fun perState_offline_error_json_meets_spec() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        val migrateResult = prefs.migrateLegacyAllAnimationsJsonToPerStateIfNeeded()
        if (migrateResult.isFailure) {
            fail("migrateLegacyAllAnimationsJsonToPerStateIfNeeded failed: ${migrateResult.exceptionOrNull()?.message}")
        }
        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        if (ensureResult.isFailure) {
            fail("ensurePerStateAnimationJsonsInitialized failed: ${ensureResult.exceptionOrNull()?.message}")
        }

        val offlineJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.OFFLINE).first() }
        if (offlineJson.isNullOrBlank()) {
            fail("offline json が未初期化")
            return@runBlocking
        }
        val offlineResult = prefs.parseAndValidatePerStateAnimationJson(offlineJson, SpriteState.OFFLINE)
        assertTrue("offline json の解析に失敗: ${offlineResult.exceptionOrNull()?.message}", offlineResult.isSuccess)
        val offline = offlineResult.getOrThrow()
        assertTrue("offline animationKey が不正: ${offline.animationKey}", offline.animationKey == "OfflineLoop")
        assertTrue("offline intervalMs が不足: ${offline.baseIntervalMs}", offline.baseIntervalMs >= 500)
        assertTrue("offline insertion.enabled が true になっている", !offline.insertion.enabled)
        assertTrue("offline insertion.patterns が空でない", offline.insertion.patterns.isEmpty())

        val errorJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.ERROR).first() }
        if (errorJson.isNullOrBlank()) {
            fail("error json が未初期化")
            return@runBlocking
        }
        val errorResult = prefs.parseAndValidatePerStateAnimationJson(errorJson, SpriteState.ERROR)
        assertTrue("error json の解析に失敗: ${errorResult.exceptionOrNull()?.message}", errorResult.isSuccess)
        val error = errorResult.getOrThrow()
        assertEquals("error animationKey が不正: ${error.animationKey}", "ErrorLight", error.animationKey)
        assertEquals("error baseFrames が不正: ${error.baseFrames}", listOf(4, 6, 7, 6, 4), error.baseFrames)
        assertEquals("error baseIntervalMs が不正: ${error.baseIntervalMs}", 390, error.baseIntervalMs)
        assertTrue("error insertion.enabled が false になっていない", !error.insertion.enabled)
        assertEquals("error insertion.intervalMs が不正: ${error.insertion.intervalMs}", 390, error.insertion.intervalMs)
        assertEquals("error insertion.everyNLoops が不正: ${error.insertion.everyNLoops}", 1, error.insertion.everyNLoops)
        assertEquals(
            "error insertion.probabilityPercent が不正: ${error.insertion.probabilityPercent}",
            0,
            error.insertion.probabilityPercent
        )
        assertEquals(
            "error insertion.cooldownLoops が不正: ${error.insertion.cooldownLoops}",
            0,
            error.insertion.cooldownLoops
        )
        assertTrue("error insertion.exclusive が true", !error.insertion.exclusive)
        assertEquals("error insertion.patterns の件数が不正: ${error.insertion.patterns}", 0, error.insertion.patterns.size)
    }

    @Test
    fun ensurePerStateAnimationJsonsInitialized_sets_error_json_to_default() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        if (ensureResult.isFailure) {
            fail("ensurePerStateAnimationJsonsInitialized failed: ${ensureResult.exceptionOrNull()?.message}")
        }

        val errorJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.ERROR).first() }
        if (errorJson.isNullOrBlank()) {
            fail("error json が未初期化")
            return@runBlocking
        }
        val errorResult = prefs.parseAndValidatePerStateAnimationJson(errorJson, SpriteState.ERROR)
        assertTrue("error json の解析に失敗: ${errorResult.exceptionOrNull()?.message}", errorResult.isSuccess)
        val error = errorResult.getOrThrow()
        assertEquals("error animationKey が不正: ${error.animationKey}", "ErrorLight", error.animationKey)
        assertEquals("error baseFrames が不正: ${error.baseFrames}", listOf(4, 6, 7, 6, 4), error.baseFrames)
        assertEquals("error baseIntervalMs が不正: ${error.baseIntervalMs}", 390, error.baseIntervalMs)
        assertTrue("error insertion.enabled が false になっていない", !error.insertion.enabled)
        assertEquals("error insertion.intervalMs が不正: ${error.insertion.intervalMs}", 390, error.insertion.intervalMs)
        assertEquals("error insertion.everyNLoops が不正: ${error.insertion.everyNLoops}", 1, error.insertion.everyNLoops)
        assertEquals(
            "error insertion.probabilityPercent が不正: ${error.insertion.probabilityPercent}",
            0,
            error.insertion.probabilityPercent
        )
        assertEquals(
            "error insertion.cooldownLoops が不正: ${error.insertion.cooldownLoops}",
            0,
            error.insertion.cooldownLoops
        )
        assertTrue("error insertion.exclusive が true", !error.insertion.exclusive)
        assertEquals("error insertion.patterns の件数が不正: ${error.insertion.patterns}", 0, error.insertion.patterns.size)
    }

    @Test
    fun perState_error_json_uses_selected_key_when_initialized() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        prefs.saveSelectedKey(SpriteState.ERROR, "ErrorHeavy")

        val migrateResult = prefs.migrateLegacyAllAnimationsJsonToPerStateIfNeeded()
        if (migrateResult.isFailure) {
            fail("migrateLegacyAllAnimationsJsonToPerStateIfNeeded failed: ${migrateResult.exceptionOrNull()?.message}")
        }
        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        if (ensureResult.isFailure) {
            fail("ensurePerStateAnimationJsonsInitialized failed: ${ensureResult.exceptionOrNull()?.message}")
        }

        val errorJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.ERROR).first() }
        if (errorJson.isNullOrBlank()) {
            fail("error json が未初期化")
            return@runBlocking
        }
        val errorResult = prefs.parseAndValidatePerStateAnimationJson(errorJson, SpriteState.ERROR)
        assertTrue("error json の解析に失敗: ${errorResult.exceptionOrNull()?.message}", errorResult.isSuccess)
        val error = errorResult.getOrThrow()
        assertEquals("error animationKey が不正: ${error.animationKey}", "ErrorHeavy", error.animationKey)
        assertEquals("error baseFrames が不正: ${error.baseFrames}", listOf(5, 5, 5, 7, 5), error.baseFrames)
        assertEquals("error baseIntervalMs が不正: ${error.baseIntervalMs}", 400, error.baseIntervalMs)
        assertTrue("error insertion.enabled が true になっていない", error.insertion.enabled)
        assertEquals("error insertion.intervalMs が不正: ${error.insertion.intervalMs}", 400, error.insertion.intervalMs)
        assertEquals("error insertion.everyNLoops が不正: ${error.insertion.everyNLoops}", 6, error.insertion.everyNLoops)
        assertEquals(
            "error insertion.probabilityPercent が不正: ${error.insertion.probabilityPercent}",
            100,
            error.insertion.probabilityPercent
        )
        assertEquals(
            "error insertion.cooldownLoops が不正: ${error.insertion.cooldownLoops}",
            0,
            error.insertion.cooldownLoops
        )
        assertTrue("error insertion.exclusive が false", error.insertion.exclusive)
        assertEquals("error insertion.patterns の件数が不正: ${error.insertion.patterns}", 1, error.insertion.patterns.size)
        val pattern = error.insertion.patterns.first()
        assertEquals("error insertion.pattern.frames が不正: ${pattern.frames}", listOf(2), pattern.frames)
        assertEquals("error insertion.pattern.weight が不正: ${pattern.weight}", 1, pattern.weight)
        assertEquals("error insertion.pattern.intervalMs が不正: ${pattern.intervalMs}", 400, pattern.intervalMs)
    }

    @Test
    fun error_default_settings_are_resolved_by_key() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        val (lightBase, lightInsertion) = prefs.defaultErrorAnimationSettingsForKey("ErrorLight")
        assertEquals("ErrorLight baseFrames が不正: ${lightBase.frameSequence}", listOf(4, 6, 7, 6, 4), lightBase.frameSequence)
        assertEquals("ErrorLight baseIntervalMs が不正: ${lightBase.intervalMs}", 390, lightBase.intervalMs)
        assertTrue("ErrorLight insertion.enabled が false になっていない", !lightInsertion.enabled)
        assertEquals("ErrorLight insertion.intervalMs が不正: ${lightInsertion.intervalMs}", 390, lightInsertion.intervalMs)
        assertEquals("ErrorLight insertion.everyNLoops が不正: ${lightInsertion.everyNLoops}", 1, lightInsertion.everyNLoops)
        assertEquals(
            "ErrorLight insertion.probabilityPercent が不正: ${lightInsertion.probabilityPercent}",
            0,
            lightInsertion.probabilityPercent
        )
        assertEquals(
            "ErrorLight insertion.cooldownLoops が不正: ${lightInsertion.cooldownLoops}",
            0,
            lightInsertion.cooldownLoops
        )
        assertTrue("ErrorLight insertion.exclusive が true", !lightInsertion.exclusive)
        assertEquals("ErrorLight insertion.patterns の件数が不正: ${lightInsertion.patterns}", 0, lightInsertion.patterns.size)

        val (heavyBase, heavyInsertion) = prefs.defaultErrorAnimationSettingsForKey("ErrorHeavy")
        assertEquals("ErrorHeavy baseFrames が不正: ${heavyBase.frameSequence}", listOf(5, 5, 5, 7, 5), heavyBase.frameSequence)
        assertEquals("ErrorHeavy baseIntervalMs が不正: ${heavyBase.intervalMs}", 400, heavyBase.intervalMs)
        assertTrue("ErrorHeavy insertion.enabled が true になっていない", heavyInsertion.enabled)
        assertEquals("ErrorHeavy insertion.intervalMs が不正: ${heavyInsertion.intervalMs}", 400, heavyInsertion.intervalMs)
        assertEquals("ErrorHeavy insertion.everyNLoops が不正: ${heavyInsertion.everyNLoops}", 6, heavyInsertion.everyNLoops)
        assertEquals(
            "ErrorHeavy insertion.probabilityPercent が不正: ${heavyInsertion.probabilityPercent}",
            100,
            heavyInsertion.probabilityPercent
        )
        assertEquals(
            "ErrorHeavy insertion.cooldownLoops が不正: ${heavyInsertion.cooldownLoops}",
            0,
            heavyInsertion.cooldownLoops
        )
        assertTrue("ErrorHeavy insertion.exclusive が false", heavyInsertion.exclusive)
        assertEquals("ErrorHeavy insertion.patterns の件数が不正: ${heavyInsertion.patterns}", 1, heavyInsertion.patterns.size)
        val heavyPattern = heavyInsertion.patterns.first()
        assertEquals("ErrorHeavy insertion.pattern.frames が不正: ${heavyPattern.frameSequence}", listOf(2), heavyPattern.frameSequence)
        assertEquals("ErrorHeavy insertion.pattern.weight が不正: ${heavyPattern.weight}", 1, heavyPattern.weight)
        assertEquals("ErrorHeavy insertion.pattern.intervalMs が不正: ${heavyPattern.intervalMs}", 400, heavyPattern.intervalMs)
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
