package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsPerStateJsonPresenceTest {

    @Test
    fun perState_json_exists_for_all_states() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsPreferences(context)

        val migrateResult = prefs.migrateLegacyAllAnimationsJsonToPerStateIfNeeded()
        if (migrateResult.isFailure) {
            fail("migrateLegacyAllAnimationsJsonToPerStateIfNeeded failed: " +
                "${migrateResult.exceptionOrNull()?.message} " +
                buildContextDebugInfo(context, prefs))
        }
        val ensureResult = prefs.ensurePerStateAnimationJsonsInitialized()
        if (ensureResult.isFailure) {
            fail("ensurePerStateAnimationJsonsInitialized failed: " +
                "${ensureResult.exceptionOrNull()?.message} " +
                buildContextDebugInfo(context, prefs))
        }

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

        if (missing.isNotEmpty()) {
            val debugInfo = buildContextDebugInfo(context, prefs)
            Log.d("SpriteTest", "per-state json missing: $missing $debugInfo")
        }
        assertTrue(
            "per-state json が欠損: $missing ${buildContextDebugInfo(context, prefs)}",
            missing.isEmpty()
        )
    }

    private suspend fun buildContextDebugInfo(
        context: Context,
        prefs: SettingsPreferences,
    ): String {
        val keys = withTimeout(5_000) { prefs.debugPreferenceKeysForTest(limit = 20) }
        return "context.packageName=${context.packageName} " +
            "dataDir=${context.applicationInfo.dataDir} " +
            "keys(sample)=$keys"
    }
}
