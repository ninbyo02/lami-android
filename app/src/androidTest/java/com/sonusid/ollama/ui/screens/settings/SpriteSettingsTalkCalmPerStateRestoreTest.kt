package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.sonusid.ollama.MainActivity
import com.sonusid.ollama.navigation.SettingsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsTalkCalmPerStateRestoreTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        runBlockingIo {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    @Test
    fun talkCalmInterval_usesPerStateJson_afterRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.TALK_CALM,
                buildTalkCalmPerStateJson(intervalMs = 345, frames = listOf(3, 0, 2))
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkCalm")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "345")
        assertIntervalInputText(expected = "345")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "4,1,3")
        assertFramesInputText(expected = "4,1,3")

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkCalm")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "345")
        assertIntervalInputText(expected = "345")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "4,1,3")
        assertFramesInputText(expected = "4,1,3")
    }

    private fun selectAnimationType(label: String) {
        ensureAnimTabSelected()
        clickPopupItemWithRetry(label)
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertIsDisplayed()
        composeTestRule.waitForIdle()
    }

    private fun clickPopupItemWithRetry(label: String, maxAttempts: Int = 3) {
        val matcher = hasText(label) and hasClickAction() and hasAnyAncestor(isPopup())
        var lastError: Throwable? = null
        var lastAnchorTag = "unknown"
        repeat(maxAttempts) { attempt ->
            val anchorTag = openAnimationDropdown()
            lastAnchorTag = anchorTag
            waitForDropdownMenuOpen()
            composeTestRule.waitUntil(timeoutMillis = 60_000) {
                nodeExists { composeTestRule.onNode(matcher, useUnmergedTree = true) }
            }
            val clicked = runCatching {
                composeTestRule.onNode(matcher, useUnmergedTree = true).performClick()
                true
            }.getOrElse { error ->
                lastError = error
                false
            }
            composeTestRule.waitForIdle()
            if (clicked) {
                val selectionConfirmed = runCatching {
                    waitForPopupClosed()
                    waitForSelectionSuccess(label, anchorTag)
                    true
                }.getOrElse { error ->
                    lastError = error
                    false
                }
                if (selectionConfirmed) {
                    return
                }
            }
            composeTestRule.waitForIdle()
        }
        throw AssertionError("Popup 内のクリックに失敗しました。label=$label anchorTag=$lastAnchorTag", lastError)
    }

    private fun openAnimationDropdown(): String {
        waitForNodeWithTag("spriteBaseIntervalInput")
        val dropdownTag = listOf("spriteAnimationTypeDropdown", "spriteAnimationTypeInput")
            .firstOrNull { tag ->
                nodeExists { composeTestRule.onNodeWithTag(tag) }
            }
        if (dropdownTag != null) {
            composeTestRule.onNodeWithTag(dropdownTag).performClick()
            composeTestRule.waitForIdle()
            return dropdownTag
        }
        val currentLabel = animationCandidates().firstOrNull { label ->
            nodeExists { composeTestRule.onNodeWithText(label) }
        } ?: run {
            val tags = dumpSemanticsTags()
            error("アニメ種別のドロップダウンが見つかりません。現在のタグ一覧: $tags")
        }
        composeTestRule.onNodeWithText(currentLabel).performClick()
        composeTestRule.waitForIdle()
        waitForDropdownMenuOpen()
        return "spriteAnimationTypeFallback"
    }

    private fun assertIntervalInputText(expected: String) {
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertTextEquals(expected)
    }

    private fun assertFramesInputText(expected: String) {
        composeTestRule.onNodeWithTag("spriteBaseFramesInput").assertTextEquals(expected)
    }

    private fun ensureAnimTabSelected() {
        waitForNodeWithTag("spriteTabAnim")
        val isSelected = runCatching {
            composeTestRule.onNodeWithTag("spriteTabAnim").assertIsSelected()
            true
        }.getOrDefault(false)
        if (!isSelected) {
            composeTestRule.onNodeWithTag("spriteTabAnim").performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun runBlockingIo(block: suspend () -> Unit) {
        runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun accessSettingsDataStore(context: Context): DataStore<Preferences> {
        val settingsClass = Class.forName(
            "com.sonusid.ollama.ui.screens.settings.SettingsPreferencesKt"
        )
        val getter = settingsClass.getDeclaredMethod("getDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    private fun recreateToSpriteSettings() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        waitForNodeWithTag("spriteTabAnim")
    }

    private fun waitForNodeWithTag(tag: String, timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                nodeExists { composeTestRule.onNodeWithTag(tag) }
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError("タグが見つかりません: $tag。現在のタグ一覧: $tags", error)
        }
    }

    private fun waitForEditableText(tag: String, expected: String, timeoutMillis: Long = 20_000) {
        waitForNodeWithTag(tag, timeoutMillis)
        composeTestRule.waitForIdle()
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                runCatching {
                    composeTestRule.onNodeWithTag(tag).assertTextEquals(expected)
                    true
                }.getOrDefault(false)
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError(
                "入力値が一致しません: tag=$tag expected=$expected。現在のタグ一覧: $tags",
                error
            )
        }
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(tag).assertTextEquals(expected)
    }

    private fun waitForDropdownMenuOpen(timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                nodeExists { composeTestRule.onNode(isPopup(), useUnmergedTree = true) }
            }
        } catch (error: AssertionError) {
            val diagnostics = buildSelectionDiagnostics("menu-open")
            throw AssertionError("ドロップダウンが開いていません。$diagnostics", error)
        }
    }

    private fun waitForPopupClosed(timeoutMillis: Long = 20_000) {
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                !nodeExists { composeTestRule.onNode(isPopup(), useUnmergedTree = true) }
            }
        }
    }

    private fun waitForSelectionSuccess(
        label: String,
        anchorTag: String,
        timeoutMillis: Long = 60_000
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            val anchorSelected = if (anchorTag == "spriteAnimationTypeFallback") {
                nodeExists { composeTestRule.onNodeWithText(label) }
            } else {
                nodeExists { composeTestRule.onNodeWithTag(anchorTag).assertTextEquals(label) }
            }
            anchorSelected && !nodeExists { composeTestRule.onNode(isPopup(), useUnmergedTree = true) }
        }
    }

    private fun nodeExists(block: () -> Unit): Boolean {
        return runCatching {
            block()
            true
        }.getOrDefault(false)
    }

    private fun buildSelectionDiagnostics(label: String): String {
        val tags = dumpSemanticsTags()
        return "label=$label tags=$tags"
    }

    private fun dumpSemanticsTags(): String {
        return "<printToString unavailable>"
    }

    private fun buildTalkCalmPerStateJson(intervalMs: Int, frames: List<Int>): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(frames))
            .put("intervalMs", intervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("intervalMs", 130)
            .put("everyNLoops", 1)
            .put("probabilityPercent", 50)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", "TalkCalm")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }

    private fun animationCandidates(): List<String> {
        return listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
    }
}
