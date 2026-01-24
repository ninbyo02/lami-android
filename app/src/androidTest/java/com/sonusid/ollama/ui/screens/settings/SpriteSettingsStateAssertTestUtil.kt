package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PerStateAnimationSnapshot(
    val animationKey: String,
    val intervalMs: Int,
    val frames: List<Int>,
)

fun ComposeTestRule.waitUntilPerStateAnimationSnapshot(
    context: Context,
    state: SpriteState,
    expected: PerStateAnimationSnapshot,
    timeoutMs: Long = 5_000,
) {
    var lastJson: String? = null
    var lastSnapshot: PerStateAnimationSnapshot? = null
    try {
        waitUntil(timeoutMillis = timeoutMs) {
            val json = readPerStateAnimationJson(context, state)
            lastJson = json
            val snapshot = parsePerStateAnimationSnapshot(json)
            lastSnapshot = snapshot
            snapshot == expected
        }
    } catch (error: AssertionError) {
        throw AssertionError(
            "per-state JSON が期待値になりません。state=${state.name} expected=$expected " +
                "actual=$lastSnapshot raw=${lastJson ?: "<null>"}",
            error
        )
    }
}

fun ComposeTestRule.waitUntilSelectedKeyPersisted(
    context: Context,
    state: SpriteState,
    expectedKey: String,
    timeoutMs: Long = 5_000,
) {
    var lastKey: String? = null
    try {
        waitUntil(timeoutMillis = timeoutMs) {
            val key = readSelectedKey(context, state)
            lastKey = key
            key == expectedKey
        }
    } catch (error: AssertionError) {
        throw AssertionError(
            "selectedKey が期待値になりません。state=${state.name} expected=$expectedKey " +
                "actual=${lastKey ?: "<null>"}",
            error
        )
    }
}

fun ComposeTestRule.waitUntilLastSelectedAnimationType(
    context: Context,
    expectedKey: String,
    timeoutMs: Long = 5_000,
) {
    var lastKey: String? = null
    try {
        waitUntil(timeoutMillis = timeoutMs) {
            val key = readLastSelectedAnimationType(context)
            lastKey = key
            key == expectedKey
        }
    } catch (error: AssertionError) {
        throw AssertionError(
            "lastSelectedAnimationType が期待値になりません。expected=$expectedKey " +
                "actual=${lastKey ?: "<null>"}",
            error
        )
    }
}

private fun readPerStateAnimationJson(context: Context, state: SpriteState): String? = runBlocking {
    withContext(Dispatchers.IO) {
        SettingsPreferences(context).spriteAnimationJsonFlow(state).first()
    }
}

private fun readSelectedKey(context: Context, state: SpriteState): String? = runBlocking {
    withContext(Dispatchers.IO) {
        SettingsPreferences(context).selectedKeyFlow(state).first()
    }
}

private fun readLastSelectedAnimationType(context: Context): String? = runBlocking {
    withContext(Dispatchers.IO) {
        SettingsPreferences(context).lastSelectedAnimationType.first()
    }
}

private fun parsePerStateAnimationSnapshot(json: String?): PerStateAnimationSnapshot? {
    if (json.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val root = JSONObject(json)
        val animationKey = root.optString("animationKey")
        val base = root.optJSONObject("base")
        val intervalMs = base?.optInt("intervalMs", -1) ?: -1
        val framesArray = base?.optJSONArray("frames") ?: JSONArray()
        val frames = buildList {
            for (index in 0 until framesArray.length()) {
                add(framesArray.optInt(index))
            }
        }
        if (animationKey.isBlank() || intervalMs < 0) {
            null
        } else {
            PerStateAnimationSnapshot(
                animationKey = animationKey,
                intervalMs = intervalMs,
                frames = frames,
            )
        }
    }.getOrNull()
}
