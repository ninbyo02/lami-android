package com.sonusid.ollama.ui.screens.settings

import android.content.Context
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
class SpriteAnimationsPerStateMetaTest {

    @Test
    fun ensurePerStateAnimationJsonsInitialized_sets_meta_defaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val result = prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()
        assertTrue("初期化が行われること", result)

        val json = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        assertNotNull("READY の per-state JSON が生成されること", json)

        val userModified = prefs.readMetaUserModifiedOrNull(json!!)
        val defaultVersion = prefs.readMetaDefaultVersionOrNull(json)
        assertEquals("meta.userModified は false の想定", false, userModified)
        assertEquals("meta.defaultVersion は 1 の想定", 1, defaultVersion)
    }

    @Test
    fun upgradePerStateDefaultsIfNeeded_skips_userModified_true() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val before = buildPerStateJsonWithMeta(
            animationKey = "Ready",
            baseIntervalMs = 999,
            userModified = true,
            defaultVersion = 0,
        )
        prefs.saveSpriteAnimationJson(SpriteState.READY, before)

        val upgraded = prefs.upgradePerStateDefaultsIfNeeded().getOrThrow()
        assertTrue("userModified=true のため upgrade は行われない", !upgraded)

        val after = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        val baseIntervalMs = JSONObject(after!!).getJSONObject("base").getInt("intervalMs")
        assertEquals("userModified=true のため base.intervalMs を維持する", 999, baseIntervalMs)
        assertEquals("meta.userModified は true を維持する", true, prefs.readMetaUserModifiedOrNull(after))
    }

    @Test
    fun upgradePerStateDefaultsIfNeeded_upgrades_when_meta_missing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val before = buildPerStateJsonWithoutMeta(
            animationKey = "Ready",
            baseIntervalMs = 180,
        )
        prefs.saveSpriteAnimationJson(SpriteState.READY, before)

        val upgraded = prefs.upgradePerStateDefaultsIfNeeded().getOrThrow()
        assertTrue("meta なしは upgrade 対象になる", upgraded)

        val after = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        assertNotNull("upgrade 後に JSON が保持される", after)
        assertEquals(
            "meta.defaultVersion が 1 になる",
            1,
            prefs.readMetaDefaultVersionOrNull(after!!)
        )
        assertEquals(
            "meta.userModified は false 扱いになる",
            false,
            prefs.readMetaUserModifiedOrNull(after)
        )
    }

    private fun buildPerStateJsonWithoutMeta(
        animationKey: String,
        baseIntervalMs: Int,
    ): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 0, 0)))
            .put("intervalMs", baseIntervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("everyNLoops", 0)
            .put("probabilityPercent", 0)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", animationKey)
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }

    private fun buildPerStateJsonWithMeta(
        animationKey: String,
        baseIntervalMs: Int,
        userModified: Boolean,
        defaultVersion: Int,
    ): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 0, 0)))
            .put("intervalMs", baseIntervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("everyNLoops", 0)
            .put("probabilityPercent", 0)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        val metaObject = JSONObject()
            .put("defaultVersion", defaultVersion)
            .put("userModified", userModified)
        return JSONObject()
            .put("animationKey", animationKey)
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .put("meta", metaObject)
            .toString()
    }
}
