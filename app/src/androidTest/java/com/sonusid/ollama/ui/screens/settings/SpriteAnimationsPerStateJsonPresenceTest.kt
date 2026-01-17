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
class SpriteAnimationsPerStateJsonPresenceTest {

    @Test
    fun perState_json_exists_for_all_states() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        val states = listOf(
            SpriteState.READY,
            SpriteState.SPEAKING,
            SpriteState.IDLE,
            SpriteState.THINKING,
            SpriteState.OFFLINE,
            SpriteState.ERROR,
        )

        val missing = mutableListOf<String>()
        for (s in states) {
            val v = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(s).first() }
            if (v == null) missing += "$s:null"
            else if (v.isBlank()) missing += "$s:blank"
        }

        assertTrue("per-state json が欠損: $missing", missing.isEmpty())
    }
}
