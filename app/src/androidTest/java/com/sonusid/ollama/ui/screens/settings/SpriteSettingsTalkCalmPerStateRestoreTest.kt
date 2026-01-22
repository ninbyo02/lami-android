package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
        val anchorTag = openAnimationDropdown()
        waitForDropdownMenuOpen(anchorTag)
        val anchorMatcher = hasTestTag(anchorTag) or hasAnyAncestor(hasTestTag(anchorTag))
        val popupMatcher = runCatching { hasAnyAncestor(isPopup()) }.getOrNull()
        val candidates = buildSelectionCandidates(label, anchorMatcher, popupMatcher)
        var selectionSucceeded = false
        for (candidate in candidates) {
            candidate.interaction.performTouchInput { click() }
            composeTestRule.waitForIdle()
            val selected = runCatching {
                composeTestRule.waitUntil(timeoutMillis = 20_000) {
                    isLabelShownOnAnchor(anchorTag, label) || isLabelSolelyDisplayed(label)
                }
                true
            }.getOrDefault(false)
            if (selected) {
                selectionSucceeded = true
                break
            }
            if (composeTestRule.onAllNodes(isPopup(), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            ) {
                openAnimationDropdown()
                waitForDropdownMenuOpen(anchorTag)
            }
        }
        if (!selectionSucceeded) {
            val diagnostics = buildSelectionDiagnostics(label, anchorTag)
            throw AssertionError("メニュー項目が見つかりません。$diagnostics")
        }
        waitForAnimationSelection(label, anchorTag)
        waitForPopupClosed()
        composeTestRule.waitForIdle()
    }

    private fun openAnimationDropdown(): String {
        waitForNodeWithTag("spriteBaseIntervalInput")
        val dropdownTag = listOf("spriteAnimationTypeDropdown", "spriteAnimationTypeInput")
            .firstOrNull { tag ->
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        if (dropdownTag != null) {
            composeTestRule.onNodeWithTag(dropdownTag).performClick()
            composeTestRule.waitForIdle()
            return dropdownTag
        }
        val currentLabel = animationCandidates().firstOrNull { label ->
            runCatching { composeTestRule.onNodeWithText(label).fetchSemanticsNode() }.isSuccess
        } ?: run {
            val tags = dumpSemanticsTags()
            error("アニメ種別のドロップダウンが見つかりません。現在のタグ一覧: $tags")
        }
        composeTestRule.onNodeWithText(currentLabel).performClick()
        composeTestRule.waitForIdle()
        return "spriteAnimationTypeFallback"
    }

    private fun assertIntervalInputText(expected: String) {
        val text = currentEditableText("spriteBaseIntervalInput").trim()
        assertEquals(
            "TALK_CALM interval input should match per-state JSON。現在値=$text",
            expected,
            text
        )
    }

    private fun assertFramesInputText(expected: String) {
        val text = currentEditableText("spriteBaseFramesInput").trim()
        assertEquals(
            "TALK_CALM frames input should match per-state JSON。現在値=$text",
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
        composeTestRule.waitForIdle()
        waitForNodeWithTag("spriteTabAnim")
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

    private fun waitForAnimationSelection(
        label: String,
        anchorTag: String,
        timeoutMillis: Long = 60_000
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                isLabelShownOnAnchor(anchorTag, label) || isLabelSolelyDisplayed(label)
            }
        } catch (error: AssertionError) {
            val diagnostics = buildSelectionDiagnostics(label, anchorTag)
            throw AssertionError(
                "アニメ種別の選択が反映されません。$diagnostics",
                error
            )
        }
    }

    private fun isLabelShownOnAnchor(anchorTag: String, label: String): Boolean {
        val anchorNodes = composeTestRule
            .onAllNodesWithTag(anchorTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (anchorNodes.isEmpty()) {
            return false
        }
        return anchorNodes.any { node ->
            extractTexts(node.config).any { text -> text.contains(label) }
        }
    }

    private fun isLabelSolelyDisplayed(label: String): Boolean {
        val displayed = animationCandidates().filter { candidate ->
            val nodes = composeTestRule.onAllNodesWithText(candidate, useUnmergedTree = true)
            val count = nodes.fetchSemanticsNodes().size
            if (count == 0) {
                false
            } else {
                var isShown = false
                for (index in 0 until count) {
                    val node = nodes.get(index)
                    val shown = runCatching { node.isDisplayed() }.getOrDefault(false)
                    if (shown) {
                        isShown = true
                        break
                    }
                }
                isShown
            }
        }
        return label in displayed && displayed.size == 1
    }

    private fun waitForDropdownMenuOpen(
        anchorTag: String,
        timeoutMillis: Long = 20_000
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                animationCandidates().any { candidate ->
                    isLabelDisplayed(candidate)
                }
            }
        } catch (error: AssertionError) {
            val diagnostics = buildSelectionDiagnostics("menu-open", anchorTag)
            throw AssertionError("ドロップダウンが開いていません。$diagnostics", error)
        }
    }

    private fun isLabelDisplayed(label: String): Boolean {
        val nodes = composeTestRule.onAllNodesWithText(label, useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        if (count == 0) {
            return false
        }
        for (index in 0 until count) {
            val node = nodes.get(index)
            val shown = runCatching { node.isDisplayed() }.getOrDefault(false)
            if (shown) {
                return true
            }
        }
        return false
    }

    private data class SelectionCandidate(
        val interaction: SemanticsNodeInteraction,
        val priority: Int,
        val index: Int
    )

    private fun buildSelectionCandidates(
        label: String,
        anchorMatcher: SemanticsMatcher,
        popupMatcher: SemanticsMatcher?
    ): List<SelectionCandidate> {
        val allNodes = composeTestRule.onAllNodes(hasText(label), useUnmergedTree = true)
        val count = allNodes.fetchSemanticsNodes().size
        if (count == 0) {
            return emptyList()
        }
        val candidates = mutableListOf<SelectionCandidate>()
        for (index in 0 until count) {
            val interaction = allNodes.get(index)
            val semanticsNode = interaction.fetchSemanticsNode()
            if (anchorMatcher.matches(semanticsNode)) {
                continue
            }
            val clickable = hasClickAction().matches(semanticsNode)
            val displayed = runCatching { interaction.isDisplayed() }.getOrDefault(false)
            val inPopup = popupMatcher?.matches(semanticsNode) == true
            val priority = when {
                clickable && displayed && inPopup -> 0
                clickable && displayed -> 1
                clickable -> 2
                displayed -> 3
                else -> 4
            }
            candidates.add(
                SelectionCandidate(
                    interaction = interaction,
                    priority = priority,
                    index = index
                )
            )
        }
        return candidates.sortedWith(compareBy<SelectionCandidate> { it.priority }.thenBy { it.index })
    }

    private fun extractTexts(config: SemanticsConfiguration): List<String> {
        val texts = mutableListOf<String>()
        if (config.contains(SemanticsProperties.Text)) {
            config[SemanticsProperties.Text].forEach { annotated ->
                texts.add(annotated.text)
            }
        }
        if (config.contains(SemanticsProperties.EditableText)) {
            texts.add(config[SemanticsProperties.EditableText].text)
        }
        if (config.contains(SemanticsProperties.ContentDescription)) {
            texts.addAll(config[SemanticsProperties.ContentDescription])
        }
        return texts
    }

    private fun findDisplayedNode(matcher: SemanticsMatcher): SemanticsNodeInteraction? {
        val collection = composeTestRule.onAllNodes(matcher, useUnmergedTree = true)
        val count = collection.fetchSemanticsNodes().size
        for (index in 0 until count) {
            val node = collection.get(index)
            val shown = runCatching { node.isDisplayed() }.getOrDefault(false)
            if (shown) {
                return node
            }
        }
        return null
    }

    private fun findAnyNode(matcher: SemanticsMatcher): SemanticsNodeInteraction? {
        val collection = composeTestRule.onAllNodes(matcher, useUnmergedTree = true)
        val count = collection.fetchSemanticsNodes().size
        if (count == 0) {
            return null
        }
        return collection.get(0)
    }

    private fun waitForPopupClosed(timeoutMillis: Long = 20_000) {
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                composeTestRule.onAllNodes(isPopup(), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
        }
    }

    private fun buildSelectionDiagnostics(label: String, anchorTag: String): String {
        val allTextCount = composeTestRule
            .onAllNodes(hasText(label), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size
        val clickableCount = composeTestRule
            .onAllNodes(hasText(label) and hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size
        val displayedCount = runCatching {
            val nodes = composeTestRule.onAllNodes(hasText(label), useUnmergedTree = true)
            val count = nodes.fetchSemanticsNodes().size
            var displayed = 0
            for (index in 0 until count) {
                val node = nodes.get(index)
                if (runCatching { node.isDisplayed() }.getOrDefault(false)) {
                    displayed++
                }
            }
            displayed
        }.getOrDefault(0)
        val popupClickableCount = runCatching {
            composeTestRule.onAllNodes(
                hasText(label) and hasClickAction() and hasAnyAncestor(isPopup()),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        }.getOrNull()
        val popupDisplayedCount = runCatching {
            val nodes = composeTestRule.onAllNodes(
                hasText(label) and hasAnyAncestor(isPopup()),
                useUnmergedTree = true
            )
            val count = nodes.fetchSemanticsNodes().size
            var displayed = 0
            for (index in 0 until count) {
                val node = nodes.get(index)
                if (runCatching { node.isDisplayed() }.getOrDefault(false)) {
                    displayed++
                }
            }
            displayed
        }.getOrNull()
        val tags = dumpSemanticsTags()
        val anchorDump = runCatching {
            composeTestRule.onAllNodesWithTag(anchorTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .joinToString { node -> node.config.toString() }
        }.getOrElse { error -> "<anchor dump failed: ${error.message}>" }
        val anchorTexts = runCatching {
            composeTestRule.onAllNodesWithTag(anchorTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> extractTexts(node.config) }
                .distinct()
                .joinToString()
        }.getOrElse { error -> "<anchor texts failed: ${error.message}>" }
        val displayedCandidates = animationCandidates().joinToString { candidate ->
            val nodes = composeTestRule.onAllNodesWithText(candidate, useUnmergedTree = true)
            val count = nodes.fetchSemanticsNodes().size
            val shown = if (count == 0) {
                false
            } else {
                var isShown = false
                for (index in 0 until count) {
                    val node = nodes.get(index)
                    val displayed = runCatching { node.isDisplayed() }.getOrDefault(false)
                    if (displayed) {
                        isShown = true
                        break
                    }
                }
                isShown
            }
            "$candidate=$shown"
        }
        val popupInfo = popupClickableCount?.toString() ?: "unavailable"
        val popupDisplayedInfo = popupDisplayedCount?.toString() ?: "unavailable"
        return "label=$label anchorTag=$anchorTag " +
            "allTextCount=$allTextCount clickableCount=$clickableCount " +
            "displayedCount=$displayedCount popupClickableCount=$popupInfo " +
            "popupDisplayedCount=$popupDisplayedInfo displayedCandidates=[$displayedCandidates] " +
            "anchorTexts=[$anchorTexts] tags=$tags anchorNodeDump=$anchorDump"
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
