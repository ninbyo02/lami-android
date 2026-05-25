package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1ComposeModel(
    val shouldShowSurface: Boolean,
    val title: String,
    val body: String?,
    val statusLabel: String,
    val detailLines: List<String>,
    val devBadge: String,
    val insertIntoAssistantList: Boolean = false,
    val persistToDb: Boolean = false,
    val speakTts: Boolean = false,
    val renderMarkdown: Boolean = false,
    val stream: Boolean = false,
    val showRetryButton: Boolean = false,
    val showFallbackButton: Boolean = false,
) {
    fun toContractText(): String =
        buildString {
            appendLine("shouldShowSurface=$shouldShowSurface")
            appendLine("title=$title")
            appendLine("statusLabel=$statusLabel")
            appendLine("devBadge=$devBadge")
            appendLine("body=${body ?: "null"}")
            appendLine("detailLines=${detailLines.joinToString("|")}")
            appendLine("insertIntoAssistantList=$insertIntoAssistantList")
            appendLine("persistToDb=$persistToDb")
            appendLine("speakTts=$speakTts")
            appendLine("renderMarkdown=$renderMarkdown")
            appendLine("stream=$stream")
            appendLine("showRetryButton=$showRetryButton")
            append("showFallbackButton=$showFallbackButton")
        }
}

object DevOnlyNpuPhaseH1ComposeAdapter {
    fun from(card: DevOnlyNpuPhaseH1CardViewModel): DevOnlyNpuPhaseH1ComposeModel =
        DevOnlyNpuPhaseH1ComposeModel(
            shouldShowSurface = card.visible,
            title = card.title,
            body = if (card.visible) card.body else null,
            statusLabel = card.statusLabel,
            detailLines = if (card.visible) card.detailLines else emptyList(),
            devBadge = card.devBadge,
            insertIntoAssistantList = false,
            persistToDb = false,
            speakTts = false,
            renderMarkdown = false,
            stream = false,
            showRetryButton = false,
            showFallbackButton = false,
        )
}
