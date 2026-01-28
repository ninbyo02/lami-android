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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.sonusid.ollama.ui.animation.SpriteAnimationDefaults

@RunWith(AndroidJUnit4::class)
class SpriteAnimationsPerStateMetaTest {

    @Test
    fun ensurePerStateAnimationJsonsInitialized_sets_meta_defaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val result = prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()
        assertTrue("初期化が行われること", result)
        assertEquals("defaultVersion の定数は 3 を想定", 3, prefs.currentDefaultAnimationVersion())

        val json = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        assertNotNull("READY の per-state JSON が生成されること", json)

        val userModified = prefs.readMetaUserModifiedOrNull(json!!)
        val defaultVersion = prefs.readMetaDefaultVersionOrNull(json)
        assertEquals("meta.userModified は false の想定", false, userModified)
        assertEquals(
            "meta.defaultVersion は CURRENT_DEFAULT_VERSION の想定",
            prefs.currentDefaultAnimationVersion(),
            defaultVersion
        )

        val idleJson = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.IDLE).first() }
        assertNotNull("IDLE の per-state JSON が生成されること", idleJson)
        val idleBaseIntervalMs = JSONObject(idleJson!!).getJSONObject("base").getInt("intervalMs")
        assertEquals(
            "IDLE の base.intervalMs はデフォルト値を反映する",
            ReadyAnimationSettings.IDLE_DEFAULT.intervalMs,
            idleBaseIntervalMs
        )
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_skips_userModified_true() = runBlocking {
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

        withTimeout(5_000) {
            while (true) {
                val json = prefs.spriteAnimationJsonFlow(SpriteState.READY).first()
                val userModified = json?.let { prefs.readMetaUserModifiedOrNull(it) }
                if (userModified == true) {
                    break
                }
            }
        }

        val after = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        val baseIntervalMs = JSONObject(after!!).getJSONObject("base").getInt("intervalMs")
        assertEquals("userModified=true のため base.intervalMs を維持する", 999, baseIntervalMs)
        assertEquals("meta.userModified は true を維持する", true, prefs.readMetaUserModifiedOrNull(after))
        assertEquals(
            "meta.defaultVersion は更新されない",
            0,
            prefs.readMetaDefaultVersionOrNull(after)
        )
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_skips_when_meta_missing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val before = buildPerStateJsonWithoutMeta(
            animationKey = "Ready",
            baseIntervalMs = 180,
        )
        prefs.saveSpriteAnimationJson(SpriteState.READY, before)

        val after = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.READY).first() }
        assertNotNull("meta なしでも JSON は保持される", after)
        assertEquals(
            "meta.defaultVersion は付与されない",
            null,
            prefs.readMetaDefaultVersionOrNull(after!!)
        )
        assertEquals(
            "meta.userModified は null のまま",
            null,
            prefs.readMetaUserModifiedOrNull(after)
        )
        val baseIntervalMs = JSONObject(after).getJSONObject("base").getInt("intervalMs")
        assertEquals("meta なしのため base.intervalMs は維持する", 180, baseIntervalMs)
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_upgrades_unmodified_old_version() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val before = buildPerStateJsonWithMeta(
            animationKey = "Ready",
            baseIntervalMs = 999,
            userModified = false,
            defaultVersion = 1,
        )
        prefs.saveSpriteAnimationJson(SpriteState.READY, before)

        val after = awaitDefaultVersion(
            prefs = prefs,
            state = SpriteState.READY,
            expectedVersion = prefs.currentDefaultAnimationVersion(),
        )
        assertEquals(
            "meta.defaultVersion が更新される",
            prefs.currentDefaultAnimationVersion(),
            prefs.readMetaDefaultVersionOrNull(after)
        )
        assertEquals(
            "meta.userModified は false 扱いになる",
            false,
            prefs.readMetaUserModifiedOrNull(after)
        )
        val baseIntervalMs = JSONObject(after).getJSONObject("base").getInt("intervalMs")
        assertEquals(
            "base.intervalMs はデフォルトに更新される",
            ReadyAnimationSettings.READY_DEFAULT.intervalMs,
            baseIntervalMs
        )
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_keeps_disabled_insertion_minimal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val states = listOf(SpriteState.OFFLINE, SpriteState.TALK_SHORT, SpriteState.TALK_CALM)
        states.forEach { state ->
            val before = buildPerStateJsonWithMeta(
                animationKey = prefs.defaultKeyForState(state),
                baseIntervalMs = 200,
                userModified = false,
                defaultVersion = 1,
                includeDisabledInsertionInterval = true,
            )
            prefs.saveSpriteAnimationJson(state, before)
        }

        states.forEach { state ->
            val after = awaitDefaultVersion(
                prefs = prefs,
                state = state,
                expectedVersion = prefs.currentDefaultAnimationVersion(),
            )
            val insertionObject = JSONObject(after).getJSONObject("insertion")
            assertFalse(
                "insertion.enabled=false の場合 intervalMs を保持しない: state=${state.name}",
                insertionObject.has("intervalMs")
            )
        }
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_preserves_error_heavy_key() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        val before = buildPerStateJsonWithMeta(
            animationKey = "ErrorHeavy",
            baseIntervalMs = 999,
            userModified = false,
            defaultVersion = 1,
        )
        prefs.saveSpriteAnimationJson(SpriteState.ERROR, before)

        val after = awaitDefaultVersion(
            prefs = prefs,
            state = SpriteState.ERROR,
            expectedVersion = prefs.currentDefaultAnimationVersion(),
        )
        val root = JSONObject(after)
        assertEquals("animationKey は ErrorHeavy を維持する", "ErrorHeavy", root.getString("animationKey"))
        val baseFrames = root.getJSONObject("base").getJSONArray("frames").toIntList()
        assertEquals(
            "ErrorHeavy の base frames を維持する",
            SpriteAnimationDefaults.ERROR_HEAVY_FRAMES,
            baseFrames
        )
        val insertionEnabled = root.getJSONObject("insertion").getBoolean("enabled")
        assertEquals(
            "ErrorHeavy の insertion enabled を維持する",
            SpriteAnimationDefaults.ERROR_HEAVY_INSERTION_ENABLED,
            insertionEnabled
        )
    }

    @Test
    fun upgradePerStateAnimationJsonsIfNeeded_is_idempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        prefs.clearAllPreferencesForTest()

        prefs.ensurePerStateAnimationJsonsInitialized().getOrThrow()
        val before = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.TALK_SHORT).first() }
        assertNotNull("TALK_SHORT の per-state JSON が生成されること", before)

        prefs.upgradePerStateAnimationJsonsIfNeeded().getOrThrow()
        val afterFirst = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.TALK_SHORT).first() }
        prefs.upgradePerStateAnimationJsonsIfNeeded().getOrThrow()
        val afterSecond = withTimeout(5_000) { prefs.spriteAnimationJsonFlow(SpriteState.TALK_SHORT).first() }

        assertEquals("upgrade を繰り返しても JSON が変化しないこと", afterFirst, afterSecond)
    }

    private suspend fun awaitDefaultVersion(
        prefs: SettingsPreferences,
        state: SpriteState,
        expectedVersion: Int,
    ): String = withTimeout(5_000) {
        while (true) {
            val json = prefs.spriteAnimationJsonFlow(state).first() ?: continue
            val defaultVersion = prefs.readMetaDefaultVersionOrNull(json)
            if (defaultVersion == expectedVersion) {
                return@withTimeout json
            }
        }
        error("timeout")
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
        includeDisabledInsertionInterval: Boolean = false,
    ): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 0, 0)))
            .put("intervalMs", baseIntervalMs)
        val insertionObject = JSONObject().apply {
            put("enabled", false)
            put("patterns", JSONArray())
            if (includeDisabledInsertionInterval) {
                put("intervalMs", 120)
            }
            put("everyNLoops", 0)
            put("probabilityPercent", 0)
            put("cooldownLoops", 0)
            put("exclusive", false)
        }
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

    private fun JSONArray.toIntList(): List<Int> {
        return buildList {
            for (index in 0 until length()) {
                add(getInt(index))
            }
        }
    }
}
