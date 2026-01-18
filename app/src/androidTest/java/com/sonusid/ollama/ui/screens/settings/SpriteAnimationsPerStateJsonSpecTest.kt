package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsPerStateJsonSpecTest {

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
        val isErrorKey = error.animationKey == "ErrorLight" || error.animationKey == "ErrorHeavy"
        assertTrue("error animationKey が不正: ${error.animationKey}", isErrorKey)
        assertTrue("error insertion.enabled が true になっている", !error.insertion.enabled)
        assertTrue("error insertion.patterns が空でない", error.insertion.patterns.isEmpty())
    }
}
