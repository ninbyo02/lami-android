package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToLog
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

    private var lastAnchorCandidateDiagnostics: String = ""
    private var lastCandidateTexts: List<String> = emptyList()

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

    private fun clickPopupItemWithRetry(label: String, maxAttempts: Int = 2) {
        var lastError: Throwable? = null
        var lastAnchorTag = "unknown"
        lastCandidateTexts = emptyList()
        repeat(maxAttempts) { attempt ->
            val anchorTag = openAnimationDropdown()
            lastAnchorTag = anchorTag
            composeTestRule.waitForIdle()
            val anchorNode = findAnchorNode(anchorTag)
            val anchorTextsBefore = anchorNode?.let { extractNodeTexts(it) }.orEmpty()
            println(
                "Dropdown クリック試行: attempt=${attempt + 1} label=$label anchorTag=$anchorTag " +
                    "anchorTextsBefore=$anchorTextsBefore"
            )
            val candidates = collectOptionCandidates(label, anchorNode)
            lastCandidateTexts = candidates.allTexts.take(6)
            println(
                "候補ノード数: before=${candidates.totalCount} after=${candidates.filteredCount} " +
                    "candidateTextsSample=${lastCandidateTexts}"
            )
            val clicked = candidates.sortedCandidates.firstOrNull { candidate ->
                runCatching {
                    candidate.interaction.performClick()
                    true
                }.getOrDefault(false)
            } != null
            if (!clicked) {
                lastError = AssertionError("候補クリックに失敗しました: label=$label anchorTag=$anchorTag")
            }
            composeTestRule.waitForIdle()
            val anchorTextsAfter = anchorTexts(anchorTag)
            println("anchorTextsAfterClick=$anchorTextsAfter")
            if (clicked) {
                val selectionConfirmed = runCatching {
                    waitForSelectionSuccess(label, anchorTag)
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
            "候補のクリックに失敗しました。label=$label anchorTag=$lastAnchorTag " +
                "candidateTextsSample=$lastCandidateTexts $diagnostics",
            lastError
        )
    }

    private fun openAnimationDropdown(): String {
        ensureAnimTabSelected()
        waitForNodeWithTag("spriteBaseIntervalInput")
        val anchorTag = "spriteAnimTypeDropdownAnchor"
        scrollToAnimationDropdownAnchor(anchorTag)
        try {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                hasNodeWithTag(anchorTag)
            }
            composeTestRule.waitForIdle()
        } catch (error: AssertionError) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("anchor-missing") }
            val diagnostics = buildPopupFailureDiagnostics()
            throw AssertionError("アニメ種別のドロップダウンが見つかりません。anchorTag=$anchorTag $diagnostics", error)
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
            if (isEmpty()) {
                mergedNodes.forEachIndexed { index, node ->
                    if (isClickable(node)) {
                        add(toCandidate(fromMerged = true, index = index, node = node))
                    }
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
            composeTestRule.waitForIdle()
            return anchorTag
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
                composeTestRule.waitUntil(timeoutMillis = 5_000) {
                    hasNodeWithTag(tag)
                }
                true
            }.getOrDefault(false)
            if (!found) {
                continue
            }
            composeTestRule.waitForIdle()
            val target = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
            scrollToAnimationDropdownAnchor(tag)
            val clicked = runCatching {
                target.performClick()
                true
            }.getOrDefault(false)
            if (!clicked) {
                continue
            }
            composeTestRule.waitForIdle()
            return tag
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

    private fun waitForSelectionSuccess(
        label: String,
        anchorTag: String,
        timeoutMillis: Long = 5_000
    ): Boolean {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                val anchorTexts = anchorTexts(anchorTag)
                anchorTexts.contains(label)
            }
        } catch (error: AssertionError) {
            throw AssertionError("選択が反映されません: label=$label anchorTag=$anchorTag", error)
        }
        return true
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

    private fun buildSelectionDiagnostics(label: String): String {
        val tags = dumpSemanticsTags()
        return "label=$label tags=$tags"
    }

    private fun dumpSemanticsTags(): String {
        return "<printToString unavailable>"
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
        val anchorCount = countAnchorNodes()
        if (anchorCount == 0) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("anchor-missing") }
        }
        runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("popup-failure") }
        return "anchorTagCount=$anchorCount candidateTextsSample=$lastCandidateTexts $lastAnchorCandidateDiagnostics"
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
        // ドロップダウン候補の一致判定に使うため固定順で保持する
        return listOf("Ready", "Speaking", "TalkShort", "TalkLong", "TalkCalm")
    }

    private data class OptionCandidates(
        val totalCount: Int,
        val filteredCount: Int,
        val sortedCandidates: List<OptionCandidate>,
        val allTexts: List<String>,
    )

    private data class OptionCandidate(
        val interaction: androidx.compose.ui.test.SemanticsNodeInteraction,
        val node: SemanticsNode,
        val isDisplayed: Boolean,
        val area: Float,
    )

    private fun collectOptionCandidates(label: String, anchorNode: SemanticsNode?): OptionCandidates {
        val matcher = hasText(label) and hasClickAction()
        val collection = composeTestRule.onAllNodes(matcher, useUnmergedTree = true)
        val nodes = runCatching { collection.fetchSemanticsNodes() }.getOrDefault(emptyList())
        val anchorBounds = anchorNode?.boundsInRoot
        val candidates = nodes.mapIndexed { index, node ->
            val interaction = collection[index]
            val isDisplayed = runCatching {
                interaction.assertIsDisplayed()
                true
            }.getOrDefault(false)
            val bounds = node.boundsInRoot
            OptionCandidate(
                interaction = interaction,
                node = node,
                isDisplayed = isDisplayed,
                area = bounds.width * bounds.height,
            )
        }
        val filtered = candidates.filter { candidate ->
            val bounds = candidate.node.boundsInRoot
            val isSameAsAnchor = anchorBounds?.let { almostSameBounds(it, bounds) } ?: false
            !isSameAsAnchor
        }
        val sorted = filtered.sortedWith(
            compareByDescending<OptionCandidate> { it.isDisplayed }
                .thenByDescending { it.area }
        )
        val texts = candidates.flatMap { extractNodeTexts(it.node) }.distinct()
        return OptionCandidates(
            totalCount = candidates.size,
            filteredCount = filtered.size,
            sortedCandidates = sorted,
            allTexts = texts,
        )
    }

    private fun findAnchorNode(anchorTag: String): SemanticsNode? {
        val unmergedNodes = runCatching {
            composeTestRule.onAllNodesWithTag(anchorTag, useUnmergedTree = true).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        if (unmergedNodes.isNotEmpty()) {
            return unmergedNodes.first()
        }
        val mergedNodes = runCatching {
            composeTestRule.onAllNodesWithTag(anchorTag).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        return mergedNodes.firstOrNull()
    }

    private fun anchorTexts(anchorTag: String): List<String> {
        val anchorNode = findAnchorNode(anchorTag)
        return anchorNode?.let { extractNodeTexts(it) }.orEmpty()
    }

    private fun almostSameBounds(
        anchorBounds: androidx.compose.ui.geometry.Rect,
        candidateBounds: androidx.compose.ui.geometry.Rect,
        tolerance: Float = 1f,
    ): Boolean {
        val dx = kotlin.math.abs(anchorBounds.left - candidateBounds.left)
        val dy = kotlin.math.abs(anchorBounds.top - candidateBounds.top)
        val dw = kotlin.math.abs(anchorBounds.width - candidateBounds.width)
        val dh = kotlin.math.abs(anchorBounds.height - candidateBounds.height)
        return dx < tolerance && dy < tolerance && dw < tolerance && dh < tolerance
    }

    // 実機確認用: ./gradlew :app:connectedDebugAndroidTest --no-build-cache
}
