package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsTalkLongPerStateRestoreTest {
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
    fun talkLongInterval_usesPerStateJson_afterRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.TALK_LONG,
                buildTalkLongPerStateJson(intervalMs = 234, frames = listOf(2, 1, 0))
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        composeTestRule.activityRule.scenario.recreate()
        ensureAnimTabSelected()
        selectAnimationType("TalkLong")
        waitForIntervalInput(expected = "234")
        assertIntervalInputText(expected = "234")
        waitForFramesInput(expected = "3,2,1")
        assertFramesInputText(expected = "3,2,1")

        composeTestRule.activityRule.scenario.recreate()
        ensureAnimTabSelected()
        selectAnimationType("TalkLong")
        waitForIntervalInput(expected = "234")
        assertIntervalInputText(expected = "234")
        waitForFramesInput(expected = "3,2,1")
        assertFramesInputText(expected = "3,2,1")
    }

    private fun selectAnimationType(label: String) {
        ensureAnimTabSelected()
        openAnimationDropdown()
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitForIdle()
    }

    private fun openAnimationDropdown() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val dropdownTag = listOf("spriteAnimationTypeDropdown", "spriteAnimationTypeInput")
            .firstOrNull { tag ->
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        if (dropdownTag != null) {
            composeTestRule.onNodeWithTag(dropdownTag).performClick()
            return
        }
        val candidates = listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
        val currentLabel = candidates.firstOrNull { label ->
            runCatching { composeTestRule.onNodeWithText(label).fetchSemanticsNode() }.isSuccess
        } ?: error("アニメ種別のドロップダウンが見つかりません")
        composeTestRule.onNodeWithText(currentLabel).performClick()
    }

    private fun assertIntervalInputText(expected: String) {
        val text = composeTestRule.onNodeWithTag("spriteBaseIntervalInput")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals("TALK_LONG interval input should match per-state JSON", expected, text.trim())
    }

    private fun waitForIntervalInput(expected: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            val nodes = composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes()
            if (nodes.isEmpty()) {
                return@waitUntil false
            }
            val text = nodes.first().config[SemanticsProperties.EditableText].text.trim()
            text == expected
        }
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertIsDisplayed()
    }

    private fun assertFramesInputText(expected: String) {
        val text = composeTestRule.onNodeWithTag("spriteBaseFramesInput")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals("TALK_LONG frames input should match per-state JSON", expected, text.trim())
    }

    private fun waitForFramesInput(expected: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            val nodes = composeTestRule.onAllNodesWithTag("spriteBaseFramesInput")
                .fetchSemanticsNodes()
            if (nodes.isEmpty()) {
                return@waitUntil false
            }
            val text = nodes.first().config[SemanticsProperties.EditableText].text.trim()
            text == expected
        }
        composeTestRule.onNodeWithTag("spriteBaseFramesInput").assertIsDisplayed()
    }

    private fun ensureAnimTabSelected() {
        val tabNode = composeTestRule.onNodeWithTag("spriteTabAnim").fetchSemanticsNode()
        val isSelected = tabNode.config.contains(SemanticsProperties.Selected) &&
            tabNode.config[SemanticsProperties.Selected] == true
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

    private fun buildTalkLongPerStateJson(intervalMs: Int, frames: List<Int>): String {
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
            .put("animationKey", "TalkLong")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }
}
