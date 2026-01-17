package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsJsonPersistenceTest {

    @Test
    fun legacy_spriteAnimationsJson_is_present_or_reported() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        // legacy が無いのか・遅いだけなのか切り分けるため、まずは nullable をそのまま読む
        val json = withTimeout(5_000) { prefs.spriteAnimationsJson.first() }

        // legacy が null なら明示的に落とす（= Copy が legacy 参照なら不具合確定の材料）
        assertNotNull("spriteAnimationsJson is null (legacy all-animations json missing)", json)
        json!!

        val expectedInternalKeys = listOf(
            "ErrorHeavy",
            "ErrorLight",
            "Idle",
            "OfflineEnter",
            "OfflineExit",
            "OfflineLoop",
            "Ready",
            "Talking",
            "TalkCalm",
            "TalkLong",
            "TalkShort",
            "Thinking",
        )

        val missing = expectedInternalKeys.filterNot { key ->
            json.contains("\"animationType\":\"$key\"")
        }

        assertTrue(
            "sprite_animations_json に欠損: $missing\n--- json(head) ---\n${json.take(800)}",
            missing.isEmpty()
        )
    }
}
