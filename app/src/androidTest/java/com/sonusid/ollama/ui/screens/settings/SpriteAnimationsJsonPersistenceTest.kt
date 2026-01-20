package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsJsonPersistenceTest {

    @Test
    fun perState_spriteAnimationJson_is_initialized_for_all_states() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        val states: List<SpriteState> = listOf(
            SpriteState.READY,
            SpriteState.IDLE,
            SpriteState.SPEAKING,
            SpriteState.TALK_SHORT,
            SpriteState.TALK_LONG,
            SpriteState.TALK_CALM,
            SpriteState.THINKING,
            SpriteState.ERROR,
            SpriteState.OFFLINE,
        )

        val missing = mutableListOf<SpriteState>()
        val invalid = mutableListOf<SpriteState>()

        states.forEach { state: SpriteState ->
            val json: String? = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(state).first() }
            if (json.isNullOrBlank()) {
                missing.add(state)
            } else if (!json.contains("\"animationKey\"")) {
                invalid.add(state)
            }
        }

        assertTrue(
            "state別のspriteAnimationJsonが未初期化: $missing",
            missing.isEmpty(),
        )
        assertTrue(
            "state別のspriteAnimationJsonが不正( animationKey 不在 ): $invalid",
            invalid.isEmpty(),
        )
    }

    @Test
    fun legacy_spriteAnimationsJson_may_be_null_in_fresh_install() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        // legacy は読み取り専用 fallback のため、null でもOK（初期化はしない）
        val json: String? = withTimeout(5_000) { prefs.spriteAnimationsJson.first() }
        if (!json.isNullOrBlank()) {
            assertTrue("legacy json に version が含まれていない", json.contains("\"version\""))
            assertTrue("legacy json に animations が含まれていない", json.contains("\"animations\""))
        }
    }
}
