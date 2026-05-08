package io.github.ninbyo02.lami.ui.screens.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsThinkingPerStateDefaultTest {

    // 動作確認手順:
    // 1) ./gradlew :app:compileDebugKotlin
    // 2) ./gradlew :app:connectedDebugAndroidTest
    // 3) adb exec-out run-as <package> strings /data/data/<package>/datastore/ollama_settings_android_test.preferences_pb | awk '/sprite_animation_json_thinking/{print}'

    @Test
    fun thinking_per_state_json_uses_defaults_on_fresh_state() = runBlocking {
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

        val thinkingJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.THINKING).first() }
        if (thinkingJson.isNullOrBlank()) {
            fail("thinking json が未初期化")
            return@runBlocking
        }

        val result = prefs.parseAndValidatePerStateAnimationJson(thinkingJson, SpriteState.THINKING)
        assertTrue("thinking json の解析に失敗: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val thinking = result.getOrThrow()

        assertEquals("Thinking", thinking.animationKey)
        assertEquals(listOf(7, 7, 7, 6, 7, 7, 6, 7), thinking.baseFrames)
        assertEquals(180, thinking.baseIntervalMs)
        assertTrue("thinking insertion.enabled が false になっている", thinking.insertion.enabled)
        val effectiveInsertionIntervalMs = thinking.insertion.intervalMs
            ?: thinking.insertion.patterns.firstOrNull()?.intervalMs
            ?: InsertionAnimationSettings.THINKING_DEFAULT.intervalMs
            ?: 0
        assertEquals(130, effectiveInsertionIntervalMs)
        assertEquals(5, thinking.insertion.everyNLoops)
        assertEquals(50, thinking.insertion.probabilityPercent)
        assertEquals(5, thinking.insertion.cooldownLoops)
        assertEquals(true, thinking.insertion.exclusive)
        assertEquals(
            listOf(
                InsertionPatternConfig(frames = listOf(5, 7), weight = 2, intervalMs = 130),
                InsertionPatternConfig(frames = listOf(4, 8, 4), weight = 1, intervalMs = 140),
            ),
            thinking.insertion.patterns,
        )
    }

    private fun resetDataStore(context: Context) {
        runBlocking {
            SettingsPreferences(context).clearAllPreferencesForTest()
        }
    }
}
