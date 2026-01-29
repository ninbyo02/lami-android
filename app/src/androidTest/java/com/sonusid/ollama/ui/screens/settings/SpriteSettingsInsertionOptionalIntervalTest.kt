package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.sonusid.ollama.MainActivity
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.screens.settings.Settings
import com.sonusid.ollama.ui.screens.settings.SpriteSettingsScreen
import com.sonusid.ollama.ui.theme.OllamaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsInsertionOptionalIntervalTest {
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
    fun insertionInterval_optionalWithPatterns_allowsSaveWithoutDefaultInterval() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.READY,
                buildReadyPerStateJsonWithPatterns()
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        setSpriteSettingsContent()
        ensureAnimTabSelected()
        scrollToTestTag("spriteInsertionIntervalInput")
        composeTestRule.waitForIdle()
        if (hasNodeWithTag("spriteInsertionIntervalInput")) {
            composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
                .performClick()
                .performTextClearance()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("デフォルト周期（ms）（任意）").assertIsDisplayed()
            composeTestRule.onNodeWithText("未入力の場合はパターンの周期を使用します").assertIsDisplayed()
        } else {
            composeTestRule.onAllNodesWithTag("spriteInsertionIntervalInput").assertCountEquals(0)
        }

        composeTestRule.onNodeWithContentDescription("保存").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("数値を入力してください").assertCountEquals(0)
        waitForText("保存しました")
        composeTestRule.onNodeWithText("保存しました").assertIsDisplayed()
    }

    @Test
    fun insertionInterval_empty_omitsIntervalMs_and_previewHidesDefaultInterval() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.READY,
                buildReadyPerStateJsonWithPatterns()
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        setSpriteSettingsContent()
        ensureAnimTabSelected()
        scrollToTestTag("spriteInsertionIntervalInput")
        composeTestRule.waitForIdle()
        if (hasNodeWithTag("spriteInsertionIntervalInput")) {
            composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
                .performClick()
                .performTextClearance()
            composeTestRule.waitForIdle()
        } else {
            composeTestRule.onAllNodesWithTag("spriteInsertionIntervalInput").assertCountEquals(0)
        }

        composeTestRule.onNodeWithContentDescription("保存").performClick()
        composeTestRule.waitForIdle()
        waitForText("保存しました")

        composeTestRule.onAllNodesWithText("D", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithText("90ms", substring = true, useUnmergedTree = true).assertIsDisplayed()

        runBlockingIo {
            val savedJson = prefs.spriteAnimationJsonFlow(SpriteState.READY).first()
            val root = JSONObject(savedJson!!)
            val insertionObject = root.getJSONObject("insertion")
            assertTrue("insertion.intervalMs は省略される", insertionObject.has("intervalMs").not())
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

    private fun waitForNodeWithTag(tag: String, timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError("タグが見つかりません: $tag。現在のタグ一覧: $tags", error)
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError("テキストが見つかりません: $text。現在のタグ一覧: $tags", error)
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

    private fun scrollToTestTag(tag: String) {
        waitForNodeWithTag("spriteAnimList")
        runCatching {
            composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true)[0]
                .performScrollToNode(hasTestTag(tag))
        }
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

    private fun buildReadyPerStateJsonWithPatterns(): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 1, 2)))
            .put("intervalMs", 180)
        val patternObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 1)))
            .put("weight", 1)
            .put("intervalMs", 90)
        val insertionObject = JSONObject()
            .put("enabled", true)
            .put("patterns", JSONArray(listOf(patternObject)))
            .put("intervalMs", 150)
            .put("everyNLoops", 2)
            .put("probabilityPercent", 50)
            .put("cooldownLoops", 1)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", "Ready")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }
}
