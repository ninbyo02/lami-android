package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sonusid.ollama.ui.screens.settings.ReadyAnimationSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsIdlePerStateDefaultTest {

    // 動作確認手順:
    // 1) ./gradlew :app:compileDebugKotlin
    // 2) ./gradlew :app:connectedDebugAndroidTest
    // 3) adb shell pm clear <package>
    // 4) adb exec-out run-as <package> strings /data/data/<package>/datastore/ollama_settings.preferences_pb | awk '/sprite_animation_json_idle/{print}'
    // 失敗時のリカバリ: 3) を再実行して DataStore を初期化し、再度 1) から確認する

    @Test
    fun idle_per_state_json_uses_defaults_on_fresh_state() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetDataStore(context)
        val prefs = SettingsPreferences(context)

        val migrateResult = prefs.migrateLegacyAllAnimationsJsonToPerStateIfNeeded()
        if (migrateResult.isFailure) {
            fail("migrateLegacyAllAnimationsJsonToPerStateIfNeeded failed: ${migrateResult.exceptionOrNull()?.message}")
        }
        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        if (ensureResult.isFailure) {
            fail("ensurePerStateAnimationJsonsInitialized failed: ${ensureResult.exceptionOrNull()?.message}")
        }

        val idleJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.IDLE).first() }
        if (idleJson.isNullOrBlank()) {
            fail("idle json が未初期化")
            return@runBlocking
        }

        val result = prefs.parseAndValidatePerStateAnimationJson(idleJson, SpriteState.IDLE)
        assertTrue("idle json の解析に失敗: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val idle = result.getOrThrow()

        assertEquals("Idle", idle.animationKey)
        assertEquals(listOf(8, 8, 8, 8), idle.baseFrames)
        assertEquals(ReadyAnimationSettings.IDLE_DEFAULT.intervalMs, idle.baseIntervalMs)
        assertTrue("idle insertion.enabled が false になっている", idle.insertion.enabled)
        assertEquals(125, idle.insertion.intervalMs)
        assertEquals(4, idle.insertion.everyNLoops)
        assertEquals(50, idle.insertion.probabilityPercent)
        assertEquals(4, idle.insertion.cooldownLoops)
        assertTrue("idle insertion.exclusive が false になっている", idle.insertion.exclusive)
        assertEquals(
            listOf(
                InsertionPatternConfig(frames = listOf(5, 8, 5), weight = 3, intervalMs = 120),
                InsertionPatternConfig(frames = listOf(4, 8, 5), weight = 1, intervalMs = 120),
            ),
            idle.insertion.patterns,
        )
    }

    private fun resetDataStore(context: Context) {
        val dataDir = context.applicationInfo.dataDir
        val dataStoreFile = File(dataDir, "datastore/ollama_settings.preferences_pb")
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }
}
