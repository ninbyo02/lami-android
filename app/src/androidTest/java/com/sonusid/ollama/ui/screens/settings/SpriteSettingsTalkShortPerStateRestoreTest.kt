package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.inRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
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

class SpriteSettingsTalkShortPerStateRestoreTest {
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
    fun talkShortInterval_usesPerStateJson_afterRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.TALK_SHORT,
                buildTalkShortPerStateJson(intervalMs = 123, frames = listOf(0, 1, 2))
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkShort")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "123")
        assertIntervalInputText(expected = "123")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "1,2,3")
        assertFramesInputText(expected = "1,2,3")

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkShort")
        waitForEditableText(tag = "spriteBaseIntervalInput", expected = "123")
        assertIntervalInputText(expected = "123")
        waitForEditableText(tag = "spriteBaseFramesInput", expected = "1,2,3")
        assertFramesInputText(expected = "1,2,3")
    }

    private fun selectAnimationType(label: String) {
        ensureAnimTabSelected()
        clickPopupItemWithRetry(label)
        composeTestRule.waitForIdle()
    }

    private fun clickPopupItemWithRetry(label: String, maxAttempts: Int = 3) {
        var lastError: Throwable? = null
        var lastAnchorTag = "unknown"
        repeat(maxAttempts) { attempt ->
            val anchorTag = openAnimationDropdown()
            lastAnchorTag = anchorTag
            waitForDropdownMenuOpen()
            val clickableMatcher = hasText(label) and hasClickAction() and hasAnyAncestor(isPopup())
            val clickableCandidates = composeTestRule.onAllNodes(clickableMatcher, useUnmergedTree = true)
            val clickableNodes = runCatching { clickableCandidates.fetchSemanticsNodes() }.getOrDefault(emptyList())
            val candidates = if (clickableNodes.isNotEmpty()) {
                clickableCandidates
            } else {
                composeTestRule.onAllNodes(hasText(label) and hasAnyAncestor(isPopup()), useUnmergedTree = true)
            }
            val candidateNodes = runCatching { candidates.fetchSemanticsNodes() }.getOrDefault(emptyList())
            val candidateTexts = candidateNodes.flatMap { extractNodeTexts(it) }.distinct()
            val popupCount = popupNodeCount()
            println(
                "Popup クリック試行: attempt=${attempt + 1} popupNodes=$popupCount candidates=${candidateNodes.size} texts=$candidateTexts"
            )
            val clicked = runCatching {
                val popupClicked = runCatching {
                    composeTestRule.onNode(hasText(label) and hasClickAction(), useUnmergedTree = true)
                        .inRoot(isPopup())
                        .performClick()
                    true
                }.getOrDefault(false)
                if (!popupClicked) {
                    if (candidateNodes.isEmpty()) {
                        throw AssertionError("Popup 内の候補が見つかりません: label=$label")
                    }
                    val target = candidates.onFirst()
                    val clickSucceeded = runCatching {
                        target.performClick()
                        true
                    }.getOrDefault(false)
                    if (!clickSucceeded) {
                        target.performTouchInput {
                            down(center)
                            up()
                        }
                    }
                }
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
        val diagnostics = buildPopupFailureDiagnostics()
        throw AssertionError(
            "Popup 内のクリックに失敗しました。label=$label anchorTag=$lastAnchorTag $diagnostics",
            lastError
        )
    }

    private fun openAnimationDropdown(): String {
        waitForNodeWithTag("spriteBaseIntervalInput")
        scrollToAnimationDropdownAnchor("spriteAnimTypeDropdownAnchor")
        val tagCandidates = listOf(
            "spriteAnimTypeDropdownAnchor",
            "spriteAnimationTypeDropdown",
            "spriteAnimationTypeInput",
            "spriteAnimationType",
            "spriteAnimationTypeField",
            "spriteAnimationTypeExposedDropdown"
        )
        for (tag in tagCandidates) {
            val found = runCatching {
                composeTestRule.waitUntil(timeoutMillis = 20_000) {
                    hasNodeWithTag(tag)
                }
                true
            }.getOrDefault(false)
            if (found) {
                val nodes = composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                val fallbackNodes = composeTestRule.onAllNodesWithTag(tag)
                val target = if (runCatching { nodes.fetchSemanticsNodes() }.getOrDefault(emptyList()).isNotEmpty()) {
                    nodes.onFirst()
                } else {
                    fallbackNodes.onFirst()
                }
                target.performClick()
                composeTestRule.waitForIdle()
                return tag
            }
        }
        val candidates = listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
        val currentLabel = candidates.firstOrNull { label ->
            runCatching { composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode() }
                .isSuccess
        } ?: error("アニメ種別のドロップダウンが見つかりません")
        composeTestRule.onNodeWithText(currentLabel, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        return "spriteAnimationTypeFallback"
    }

    private fun scrollToAnimationDropdownAnchor(anchorTag: String) {
        waitForNodeWithTag("spriteAnimList")
        runCatching {
            composeTestRule.onNodeWithTag("spriteAnimList")
                .performScrollToNode(hasTestTag(anchorTag))
        }
    }

    private fun assertIntervalInputText(expected: String) {
        val text = currentEditableText("spriteBaseIntervalInput").trim()
        assertEquals(
            "TALK_SHORT interval input should match per-state JSON。現在値=$text",
            expected,
            text
        )
    }

    private fun assertFramesInputText(expected: String) {
        val text = currentEditableText("spriteBaseFramesInput").trim()
        assertEquals(
            "TALK_SHORT frames input should match per-state JSON。現在値=$text",
            expected,
            text
        )
    }

    private fun ensureAnimTabSelected() {
        waitForNodeWithTag("spriteTabAnim")
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

    private fun waitForDropdownMenuOpen(timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                popupNodeCount() > 0
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError("ドロップダウンが開いていません。tags=$tags", error)
        }
    }

    private fun waitForPopupClosed(timeoutMillis: Long = 20_000) {
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                popupNodeCount() == 0
            }
        }
    }

    private fun waitForSelectionSuccess(label: String, anchorTag: String, timeoutMillis: Long = 20_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            val anchorSelected = if (anchorTag == "spriteAnimationTypeFallback") {
                nodeExists { composeTestRule.onNodeWithText(label) }
            } else {
                nodeExists {
                    composeTestRule.onNodeWithTag(anchorTag, useUnmergedTree = true)
                        .assertTextEquals(label)
                } || nodeExists { composeTestRule.onNodeWithTag(anchorTag).assertTextEquals(label) }
            }
            anchorSelected && !nodeExists { composeTestRule.onNode(isPopup(), useUnmergedTree = true) }
        }
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

    private fun popupNodeCount(): Int {
        return runCatching {
            composeTestRule.onAllNodes(isPopup(), useUnmergedTree = true).fetchSemanticsNodes().size
        }.getOrDefault(0)
    }

    private fun popupTexts(): List<String> {
        val nodes = runCatching {
            composeTestRule.onAllNodes(hasAnyAncestor(isPopup()), useUnmergedTree = true).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        return nodes.flatMap { extractNodeTexts(it) }.distinct()
    }

    private fun extractNodeTexts(node: SemanticsNode): List<String> {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        return listOfNotNull(text, editable, contentDescription)
    }

    private fun countFallbackAnchorNodes(): Int {
        val unmerged = runCatching {
            composeTestRule.onAllNodesWithTag("spriteAnimationTypeFallback", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size
        }.getOrDefault(0)
        val merged = runCatching {
            composeTestRule.onAllNodesWithTag("spriteAnimationTypeFallback").fetchSemanticsNodes().size
        }.getOrDefault(0)
        return unmerged + merged
    }

    private fun buildPopupFailureDiagnostics(): String {
        val popupCount = popupNodeCount()
        val popupTexts = popupTexts()
        val fallbackCount = countFallbackAnchorNodes()
        runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("popup-failure") }
        return "popupNodes=$popupCount popupTexts=$popupTexts anchorTagCount=$fallbackCount"
    }

    private fun buildTalkShortPerStateJson(intervalMs: Int, frames: List<Int>): String {
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
            .put("animationKey", "TalkShort")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }
}
