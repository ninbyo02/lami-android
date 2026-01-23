package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
        val found = runCatching {
            composeTestRule.waitUntil(timeoutMillis = 20_000) {
                hasNodeWithTag(anchorTag)
            }
            true
        }.getOrDefault(false)
        if (!found) {
            runCatching { composeTestRule.onRoot(useUnmergedTree = true).printToLog("anchor-missing") }
            val diagnostics = buildAnimationAnchorDiagnostics(listOf(anchorTag))
            throw AssertionError("アニメ種別のドロップダウンが見つかりません。$diagnostics")
        }
        val target = composeTestRule.onNodeWithTag(anchorTag, useUnmergedTree = true)
        runCatching { target.performScrollTo() }
            .recoverCatching {
                composeTestRule.onNodeWithTag("spriteAnimList")
                    .performScrollToNode(hasTestTag(anchorTag))
            }
        target.performClick()
        composeTestRule.waitForIdle()
        waitForDropdownMenuOpen()
        return anchorTag
    }

    private fun scrollToAnimationDropdownAnchor(anchorTag: String) {
        waitForNodeWithTag("spriteAnimList")
        runCatching {
            composeTestRule.onNodeWithTag(anchorTag, useUnmergedTree = true).performScrollTo()
        }.recoverCatching {
            composeTestRule.onNodeWithTag("spriteAnimList")
                .performScrollToNode(hasTestTag(anchorTag))
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

    private fun waitForDropdownMenuOpen(timeoutMillis: Long = 20_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                popupNodeCount() > 0
            }
        } catch (error: AssertionError) {
            val diagnostics = buildSelectionDiagnostics("menu-open")
            throw AssertionError("ドロップダウンが開いていません。$diagnostics", error)
        }
    }

    private fun waitForPopupClosed(timeoutMillis: Long = 20_000) {
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                popupNodeCount() == 0
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
                nodeExists {
                    composeTestRule.onNodeWithTag(anchorTag, useUnmergedTree = true).assertTextEquals(label)
                } || nodeExists { composeTestRule.onNodeWithTag(anchorTag).assertTextEquals(label) }
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

    private fun buildAnimationAnchorDiagnostics(tagCandidates: List<String>): String {
        val tags = dumpSemanticsTags()
        val hasRoot = nodeExists { composeTestRule.onNode(isRoot(), useUnmergedTree = true) }
        return "tagCandidates=$tagCandidates hasRoot=$hasRoot tags=$tags"
    }

    private fun dumpSemanticsTags(): String {
        return "<printToString unavailable>"
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
        return unmerged + merged
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
        return "popupNodes=$popupCount popupTexts=$popupTexts anchorTagCount=$anchorCount"
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
