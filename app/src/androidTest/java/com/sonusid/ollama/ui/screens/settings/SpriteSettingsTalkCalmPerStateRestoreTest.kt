package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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

    private val dropdownUtil by lazy {
        SpriteSettingsDropdownTestUtil(
            composeTestRule = composeTestRule,
            ensureAnimTabSelected = { ensureAnimTabSelected() },
            waitForNodeWithTag = { tag, timeout -> waitForNodeWithTag(tag, timeout) },
            scrollToAnimationDropdownAnchor = { anchorTag -> scrollToAnimationDropdownAnchor(anchorTag) },
        )
    }

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
        dropdownUtil.selectAnimationTypeByAnchor(label)
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertIsDisplayed()
        composeTestRule.waitForIdle()
    }

    private fun scrollToAnimationDropdownAnchor(anchorTag: String) {
        waitForNodeWithTag("spriteAnimList")
        val scrollableNodes = runCatching {
            composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        val scrollTarget = if (scrollableNodes.isNotEmpty()) {
            composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst()
        } else {
            composeTestRule.onNodeWithTag("spriteAnimList")
        }
        val scrolled = runCatching {
            scrollTarget.performScrollToNode(hasTestTag(anchorTag))
            true
        }.getOrDefault(false)
        if (scrolled) {
            return
        }
        val startTime = System.currentTimeMillis()
        val maxIndex = 30
        val listNode = composeTestRule.onNodeWithTag("spriteAnimList")
        for (index in 0..maxIndex) {
            runCatching { listNode.performScrollToIndex(index) }
            composeTestRule.waitForIdle()
            if (hasNodeWithTag(anchorTag)) {
                return
            }
            if (System.currentTimeMillis() - startTime > 10_000) {
                return
            }
        }
    }

    private fun assertIntervalInputText(expected: String) {
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertTextEquals(expected)
    }

    private fun assertFramesInputText(expected: String) {
        composeTestRule.onNodeWithTag("spriteBaseFramesInput").assertTextEquals(expected)
    }

    private fun ensureAnimTabSelected() {
        waitForNodeWithTag("spriteTabAnim")
        val tabNode = composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true)
        val isSelected = runCatching {
            tabNode.assertIsSelected()
            true
        }.getOrDefault(false)
        if (!isSelected) {
            tabNode.performClick()
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

    private fun nodeExists(block: () -> Unit): Boolean {
        return runCatching {
            block()
            true
        }.getOrDefault(false)
    }

    private fun hasNodeWithTag(tag: String): Boolean {
        val unmergedCount = runCatching {
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
        }.getOrDefault(0)
        if (unmergedCount > 0) {
            return true
        }
        val mergedCount = runCatching {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        }.getOrDefault(0)
        return mergedCount > 0
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

}
