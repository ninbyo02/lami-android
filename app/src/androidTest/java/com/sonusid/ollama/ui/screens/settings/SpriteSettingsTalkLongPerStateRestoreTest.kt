package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
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
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.screens.settings.Settings
import com.sonusid.ollama.ui.screens.settings.SpriteSettingsScreen
import com.sonusid.ollama.ui.theme.OllamaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkLong")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "234")
        assertIntervalInputText(expected = "234")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "3,2,1")
        assertFramesInputText(expected = "3,2,1")

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkLong")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "234")
        assertIntervalInputText(expected = "234")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "3,2,1")
        assertFramesInputText(expected = "3,2,1")
    }

    private fun selectAnimationType(label: String) {
        composeTestRule.selectAnimationTypeByAnchor(
            label = label,
            ensureAnimTabSelected = { ensureAnimTabSelected() },
            waitForNodeWithTag = { tag, timeout -> waitForNodeWithTag(tag, timeout) },
            scrollToAnimationDropdownAnchor = { anchorTag -> scrollToAnimationDropdownAnchor(anchorTag) },
        )
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
        val text = currentEditableText("spriteBaseIntervalInput").trim()
        assertEquals(
            "TALK_LONG interval input should match per-state JSON。現在値=$text",
            expected,
            text
        )
    }

    private fun assertFramesInputText(expected: String) {
        val text = currentEditableText("spriteBaseFramesInput").trim()
        assertEquals(
            "TALK_LONG frames input should match per-state JSON。現在値=$text",
            expected,
            text
        )
    }

    private fun ensureAnimTabSelected() {
        waitForNodeWithTag("spriteTabAnim")
        val tabNode = composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true)
            .fetchSemanticsNode()
        val isSelected = tabNode.config.contains(SemanticsProperties.Selected) &&
            tabNode.config[SemanticsProperties.Selected] == true
        if (!isSelected) {
            composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true).performClick()
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
        setSpriteSettingsContent()
        waitForNodeWithTag("spriteTabAnim")
    }

    private fun setSpriteSettingsContent() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val navController = rememberNavController()
                OllamaTheme(dynamicColor = false) {
                    NavHost(
                        navController = navController,
                        startDestination = SettingsRoute.SpriteSettings.route
                    ) {
                        composable(SettingsRoute.SpriteSettings.route) {
                            SpriteSettingsScreen(navController)
                        }
                        composable(Routes.SETTINGS) {
                            Settings(navController)
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForNodeWithTag(tag: String, timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                hasNodeWithTag(tag)
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
                currentEditableText(tag).trim() == expected
            }
        } catch (error: AssertionError) {
            val actual = currentEditableText(tag).trim()
            val tags = dumpSemanticsTags()
            throw AssertionError(
                "入力値が一致しません: tag=$tag expected=$expected actual=$actual。現在のタグ一覧: $tags",
                error
            )
        }
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        val actual = currentEditableText(tag).trim()
        assertEquals("入力値が一致しません: tag=$tag 現在値=$actual", expected, actual)
    }

    private fun currentEditableText(tag: String): String {
        return composeTestRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
    }

    private fun dumpSemanticsTags(): String {
        val rootNodes: List<SemanticsNode> = composeTestRule
            .onAllNodes(isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val tags: MutableSet<String> = mutableSetOf()
        rootNodes.forEach { node: SemanticsNode ->
            collectTestTags(node, tags)
        }
        val sortedTags = tags.toList().sorted()
        return if (sortedTags.isEmpty()) {
            "<none>"
        } else {
            sortedTags.joinToString()
        }
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

    private fun collectTestTags(node: SemanticsNode, tags: MutableSet<String>) {
        val config: SemanticsConfiguration = node.config
        val tag: String? = if (config.contains(SemanticsProperties.TestTag)) {
            config[SemanticsProperties.TestTag]
        } else {
            null
        }
        if (tag != null) {
            tags.add(tag)
        }
        node.children.forEach { child: SemanticsNode ->
            collectTestTags(child, tags)
        }
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
