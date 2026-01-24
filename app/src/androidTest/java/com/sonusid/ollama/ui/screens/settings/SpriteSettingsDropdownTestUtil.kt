package com.sonusid.ollama.ui.screens.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up

// 実機向けの3テストのみ実行する例:
// ./gradlew :app:connectedDebugAndroidTest --no-build-cache \
//   -Pandroid.testInstrumentationRunnerArguments.class=com.sonusid.ollama.ui.screens.settings.SpriteSettingsTalkShortPerStateRestoreTest
// ./gradlew :app:connectedDebugAndroidTest --no-build-cache \
//   -Pandroid.testInstrumentationRunnerArguments.class=com.sonusid.ollama.ui.screens.settings.SpriteSettingsTalkLongPerStateRestoreTest
// ./gradlew :app:connectedDebugAndroidTest --no-build-cache \
//   -Pandroid.testInstrumentationRunnerArguments.class=com.sonusid.ollama.ui.screens.settings.SpriteSettingsTalkCalmPerStateRestoreTest
fun ComposeTestRule.selectAnimationTypeByAnchor(
    label: String,
    anchorTag: String = "spriteAnimTypeDropdownAnchor",
    timeoutMs: Long = 5_000,
    maxAttempts: Int = 2,
    ensureAnimTabSelected: () -> Unit,
    waitForNodeWithTag: (String, Long) -> Unit,
    scrollToAnimationDropdownAnchor: (String) -> Unit,
) {
    var lastAnchorCandidateDiagnostics = ""
    var lastLabelNodeCountDiagnostics = ""
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        ensureAnimTabSelected()
        waitForNodeWithTag("spriteBaseIntervalInput", timeoutMs)
        scrollToAnimationDropdownAnchor(anchorTag)
        waitForIdle()

        val anchorCandidates = collectAnchorCandidates(anchorTag)
        lastAnchorCandidateDiagnostics = buildAnchorCandidateDiagnostics(anchorCandidates)
        println("Dropdown クリック試行: attempt=${attempt + 1} label=$label anchorTag=$anchorTag")
        val anchorClicked = anchorCandidates.firstOrNull { candidate ->
            tapInteraction(candidate.interaction)
        } != null
        if (!anchorClicked) {
            lastError = AssertionError("アンカーのタップに失敗しました: label=$label anchorTag=$anchorTag")
            return@repeat
        }

        waitForIdle()
        val labelResult = clickLabelNode(label, timeoutMs)
        lastLabelNodeCountDiagnostics = labelResult.diagnostics
        if (!labelResult.clicked) {
            lastError = AssertionError("候補のタップに失敗しました: label=$label anchorTag=$anchorTag")
            return@repeat
        }

        val selectionConfirmed = runCatching {
            waitForSelectionSuccess(label, anchorTag, timeoutMs)
        }.getOrElse { error ->
            lastError = error
            false
        }
        if (selectionConfirmed) {
            return
        }
        waitForIdle()
    }
    val diagnostics = buildFailureDiagnostics(
        label = label,
        anchorTag = anchorTag,
        lastAnchorCandidateDiagnostics = lastAnchorCandidateDiagnostics,
        lastLabelNodeCountDiagnostics = lastLabelNodeCountDiagnostics,
    )
    throw AssertionError(
        "アニメ種別の選択に失敗しました。label=$label anchorTag=$anchorTag $diagnostics",
        lastError
    )
}

private fun ComposeTestRule.collectAnchorCandidates(anchorTag: String): List<AnchorCandidate> {
    val unmergedCollection = onAllNodesWithTag(anchorTag, useUnmergedTree = true)
    val unmergedNodes = runCatching { unmergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())
    val mergedCollection = onAllNodesWithTag(anchorTag)
    val mergedNodes = runCatching { mergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())

    val candidates = buildList {
        unmergedNodes.forEachIndexed { index, node ->
            add(toAnchorCandidate(unmergedCollection[index], node))
        }
        if (unmergedNodes.isEmpty()) {
            mergedNodes.forEachIndexed { index, node ->
                add(toAnchorCandidate(mergedCollection[index], node))
            }
        }
    }
    return candidates.sortedWith(
        compareByDescending<AnchorCandidate> { it.isDisplayed }
            .thenByDescending { it.isClickable }
            .thenByDescending { it.isEnabled }
            .thenByDescending { it.area }
    )
}

private fun toAnchorCandidate(
    interaction: SemanticsNodeInteraction,
    node: SemanticsNode,
): AnchorCandidate {
    val isDisplayed = runCatching {
        interaction.assertIsDisplayed()
        true
    }.getOrDefault(false)
    val bounds = node.boundsInRoot
    val sizeOk = bounds.width > 0f && bounds.height > 0f
    val hasClick = node.config.contains(SemanticsActions.OnClick)
    val isEnabled = !node.config.contains(SemanticsProperties.Disabled)
    return AnchorCandidate(
        interaction = interaction,
        node = node,
        isDisplayed = isDisplayed,
        isClickable = hasClick && sizeOk,
        isEnabled = isEnabled,
        area = bounds.width * bounds.height,
        texts = extractNodeTexts(node),
    )
}

private fun ComposeTestRule.clickLabelNode(label: String, timeoutMillis: Long): LabelClickResult {
    runCatching {
        waitUntil(timeoutMillis = timeoutMillis) {
            labelNodeCount(label) > 0
        }
    }
    val unmergedCollection = onAllNodesWithText(label, useUnmergedTree = true)
    val unmergedNodes = runCatching { unmergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())
    val mergedCollection = onAllNodesWithText(label)
    val mergedNodes = runCatching { mergedCollection.fetchSemanticsNodes() }.getOrDefault(emptyList())
    val diagnostics = "labelNodes(unmerged=${unmergedNodes.size}, merged=${mergedNodes.size})"

    val candidates = if (unmergedNodes.isNotEmpty()) {
        unmergedNodes.mapIndexed { index, node ->
            toLabelCandidate(unmergedCollection[index], node)
        }
    } else {
        mergedNodes.mapIndexed { index, node ->
            toLabelCandidate(mergedCollection[index], node)
        }
    }
    val sorted = candidates.sortedWith(
        compareByDescending<LabelCandidate> { it.isDisplayed }
            .thenByDescending { it.area }
    )
    val clicked = sorted.firstOrNull { candidate ->
        tapInteraction(candidate.interaction)
    } != null
    return LabelClickResult(clicked = clicked, diagnostics = diagnostics)
}

private fun toLabelCandidate(interaction: SemanticsNodeInteraction, node: SemanticsNode): LabelCandidate {
    val isDisplayed = runCatching {
        interaction.assertIsDisplayed()
        true
    }.getOrDefault(false)
    val bounds = node.boundsInRoot
    return LabelCandidate(
        interaction = interaction,
        isDisplayed = isDisplayed,
        area = bounds.width * bounds.height,
    )
}

private data class AnchorCandidate(
    val interaction: SemanticsNodeInteraction,
    val node: SemanticsNode,
    val isDisplayed: Boolean,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val area: Float,
    val texts: List<String>,
)

private data class LabelCandidate(
    val interaction: SemanticsNodeInteraction,
    val isDisplayed: Boolean,
    val area: Float,
)

private data class LabelClickResult(
    val clicked: Boolean,
    val diagnostics: String,
)

private fun tapInteraction(interaction: SemanticsNodeInteraction): Boolean {
    return runCatching {
        interaction.performTouchInput {
            down(center)
            up()
        }
        true
    }.getOrElse {
        runCatching {
            interaction.performClick()
            true
        }.getOrDefault(false)
    }
}

private fun ComposeTestRule.waitForSelectionSuccess(
    label: String,
    anchorTag: String,
    timeoutMillis: Long,
): Boolean {
    try {
        waitUntil(timeoutMillis = timeoutMillis) {
            anchorShowsLabel(label, anchorTag)
        }
    } catch (error: AssertionError) {
        throw AssertionError("選択が反映されません: label=$label anchorTag=$anchorTag", error)
    }
    return true
}

private fun ComposeTestRule.anchorShowsLabel(label: String, anchorTag: String): Boolean {
    val unmerged = onAllNodesWithTag(anchorTag, useUnmergedTree = true)
    val unmergedNodes = runCatching { unmerged.fetchSemanticsNodes() }.getOrDefault(emptyList())
    val merged = onAllNodesWithTag(anchorTag)
    val mergedNodes = runCatching { merged.fetchSemanticsNodes() }.getOrDefault(emptyList())
    val anchorNodes = if (unmergedNodes.isNotEmpty()) unmergedNodes else mergedNodes
    val textMatch = anchorNodes.any { node ->
        extractNodeTexts(node).any { it.contains(label) }
    }
    if (textMatch) {
        return true
    }
    val descendantMatch = runCatching {
        onAllNodes(
            hasAnyAncestor(hasTestTag(anchorTag)) and hasText(label),
            useUnmergedTree = true
        ).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)
    return descendantMatch
}

private fun extractNodeTexts(node: SemanticsNode): List<String> {
    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text
    val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    return listOfNotNull(text, editable, contentDescription)
}

private fun ComposeTestRule.buildFailureDiagnostics(
    label: String,
    anchorTag: String,
    lastAnchorCandidateDiagnostics: String,
    lastLabelNodeCountDiagnostics: String,
): String {
    val anchorCount = countAnchorNodes(anchorTag)
    val labelCounts = if (lastLabelNodeCountDiagnostics.isNotBlank()) {
        lastLabelNodeCountDiagnostics
    } else {
        labelNodeCounts(label)
    }
    return "anchorTagCount=$anchorCount $lastAnchorCandidateDiagnostics $labelCounts"
}

private fun ComposeTestRule.countAnchorNodes(anchorTag: String): Int {
    val unmerged = runCatching {
        onAllNodesWithTag(anchorTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size
    }.getOrDefault(0)
    val merged = runCatching {
        onAllNodesWithTag(anchorTag).fetchSemanticsNodes().size
    }.getOrDefault(0)
    return maxOf(unmerged, merged)
}

private fun ComposeTestRule.labelNodeCount(label: String): Int {
    val unmerged = runCatching {
        onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
    }.getOrDefault(0)
    val merged = runCatching {
        onAllNodesWithText(label).fetchSemanticsNodes().size
    }.getOrDefault(0)
    return maxOf(unmerged, merged)
}

private fun ComposeTestRule.labelNodeCounts(label: String): String {
    val unmerged = runCatching {
        onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
    }.getOrDefault(0)
    val merged = runCatching {
        onAllNodesWithText(label).fetchSemanticsNodes().size
    }.getOrDefault(0)
    return "labelNodes(unmerged=$unmerged, merged=$merged)"
}

private fun buildAnchorCandidateDiagnostics(candidates: List<AnchorCandidate>): String {
    val sample = candidates.take(3).joinToString { candidate ->
        val textSample = candidate.texts.joinToString(limit = 2)
        "displayed=${candidate.isDisplayed} clickable=${candidate.isClickable} " +
            "enabled=${candidate.isEnabled} texts=[$textSample]"
    }
    return "anchorCandidates=${candidates.size} sample=[$sample]"
}
