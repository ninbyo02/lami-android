package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

class SpriteSettingsTalkLongPerStateRestoreTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private var lastAnchorCandidateDiagnostics: String = ""

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
                    composeTestRule.onNode(
                        hasText(label) and hasClickAction() and hasAnyAncestor(isPopup()),
                        useUnmergedTree = true
                    )
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
        ensureAnimTabSelected()
        waitForNodeWithTag("spriteBaseIntervalInput")
        val anchorTag = "spriteAnimTypeDropdownAnchor"
        scrollToAnimationDropdownAnchor(anchorTag)
        val anchorFound = runCatching {
            composeTestRule.waitUntil(timeoutMillis = 20_000) {
                hasNodeWithTag(anchorTag)
            }
            true
        }.getOrDefault(false)
        if (!anchorFound) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("anchor-missing") }
            val diagnostics = buildPopupFailureDiagnostics()
            throw AssertionError("アニメ種別のドロップダウンが見つかりません。anchorTag=$anchorTag $diagnostics")
        }
        val unmergedCollection = composeTestRule.onAllNodesWithTag(anchorTag, useUnmergedTree = true)
        val unmergedNodes = runCatching { unmergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())
        val mergedCollection = composeTestRule.onAllNodesWithTag(anchorTag)
        val mergedNodes = runCatching { mergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())

        data class AnchorCandidate(
            val fromMerged: Boolean,
            val index: Int,
            val node: SemanticsNode,
            val isDisplayed: Boolean,
            val hasClickAction: Boolean,
            val isEnabled: Boolean,
            val textMatches: Boolean,
            val area: Float,
        )

        fun isClickable(node: SemanticsNode): Boolean {
            val bounds = node.boundsInRoot
            val sizeOk = bounds.width > 0f && bounds.height > 0f
            val hasClick = node.config.contains(SemanticsActions.OnClick)
            val enabled = !node.config.contains(SemanticsProperties.Disabled)
            return sizeOk && hasClick && enabled
        }

        fun toCandidate(fromMerged: Boolean, index: Int, node: SemanticsNode): AnchorCandidate {
            val interaction = if (fromMerged) {
                mergedCollection[index]
            } else {
                unmergedCollection[index]
            }
            val isDisplayed = runCatching {
                interaction.assertIsDisplayed()
                true
            }.getOrDefault(false)
            val hasClick = node.config.contains(SemanticsActions.OnClick)
            val enabled = !node.config.contains(SemanticsProperties.Disabled)
            val bounds = node.boundsInRoot
            val sizeOk = bounds.width > 0f && bounds.height > 0f
            val candidateTexts = extractNodeTexts(node)
            val textMatches = candidateTexts.any { it in animationCandidates() }
            return AnchorCandidate(
                fromMerged = fromMerged,
                index = index,
                node = node,
                isDisplayed = isDisplayed,
                hasClickAction = hasClick && sizeOk,
                isEnabled = enabled,
                textMatches = textMatches,
                area = bounds.width * bounds.height,
            )
        }

        val anchorCandidates = buildList {
            unmergedNodes.forEachIndexed { index, node ->
                if (isClickable(node)) {
                    add(toCandidate(fromMerged = false, index = index, node = node))
                }
            }
            mergedNodes.forEachIndexed { index, node ->
                if (isClickable(node)) {
                    add(toCandidate(fromMerged = true, index = index, node = node))
                }
            }
        }.sortedWith(
            compareByDescending<AnchorCandidate> { it.isDisplayed }
                .thenByDescending { it.hasClickAction }
                .thenByDescending { it.textMatches }
                .thenByDescending { it.area }
        )
        lastAnchorCandidateDiagnostics = buildString {
            append("anchorCandidates=${anchorCandidates.size}")
            if (anchorCandidates.isNotEmpty()) {
                append(" details=[")
                append(
                    anchorCandidates.joinToString { candidate ->
                        "merged=${candidate.fromMerged}" +
                            " index=${candidate.index}" +
                            " displayed=${candidate.isDisplayed}" +
                            " clickable=${candidate.hasClickAction}" +
                            " enabled=${candidate.isEnabled}" +
                            " textMatch=${candidate.textMatches}" +
                            " area=${"%.1f".format(candidate.area)}"
                    }
                )
                append("]")
            }
        }

        for (candidate in anchorCandidates) {
            val interaction = if (candidate.fromMerged) {
                mergedCollection[candidate.index]
            } else {
                unmergedCollection[candidate.index]
            }
            scrollToAnimationDropdownAnchor(anchorTag)
            val clicked = runCatching {
                interaction.assertIsDisplayed()
                interaction.performClick()
                true
            }.getOrDefault(false)
            if (!clicked) {
                continue
            }
            val opened = runCatching {
                composeTestRule.waitUntil(timeoutMillis = 30_000) {
                    popupNodeCount() > 0
                }
                true
            }.getOrDefault(false)
            if (opened) {
                composeTestRule.waitForIdle()
                return anchorTag
            }
        }

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
                val target = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
                scrollToAnimationDropdownAnchor(tag)
                val clicked = runCatching {
                    target.performClick()
                    true
                }.getOrDefault(false)
                if (!clicked) {
                    continue
                }
                val opened = runCatching {
                    composeTestRule.waitUntil(timeoutMillis = 30_000) {
                        popupNodeCount() > 0
                    }
                    true
                }.getOrDefault(false)
                if (opened) {
                    composeTestRule.waitForIdle()
                    return tag
                }
            }
        }
        val candidates = listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
        val currentLabel = candidates.firstOrNull { label ->
            runCatching { composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode() }
                .isSuccess
        } ?: run {
            val diagnostics = buildPopupFailureDiagnostics()
            throw AssertionError(
                "アニメ種別のドロップダウンが見つかりません。anchorTag=$anchorTag " +
                    "clickableCandidates=${anchorCandidates.size} $diagnostics"
            )
        }
        composeTestRule.onNodeWithText(currentLabel, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = 20_000) {
                popupNodeCount() > 0
            }
        }
        return "spriteAnimationTypeFallback"
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
            if (System.currentTimeMillis() - startTime > 60_000) {
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

    private fun countAnchorNodes(): Int {
        val unmerged = runCatching {
            composeTestRule.onAllNodesWithTag("spriteAnimTypeDropdownAnchor", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size
        }.getOrDefault(0)
        val merged = runCatching {
            composeTestRule.onAllNodesWithTag("spriteAnimTypeDropdownAnchor").fetchSemanticsNodes().size
        }.getOrDefault(0)
        return maxOf(unmerged, merged)
    }

    private fun buildPopupFailureDiagnostics(): String {
        val popupCount = popupNodeCount()
        val popupTexts = popupTexts()
        val anchorCount = countAnchorNodes()
        if (anchorCount == 0) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("anchor-missing") }
        }
        if (popupCount == 0) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("popup-missing") }
        }
        runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("popup-failure") }
        return "popupNodes=$popupCount popupTexts=$popupTexts anchorTagCount=$anchorCount $lastAnchorCandidateDiagnostics"
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

    private fun animationCandidates(): List<String> {
        // ドロップダウン候補の一致判定に使うため固定順で保持する
        return listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
    }
}
