package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1ComposeModel(
    val shouldShowSurface: Boolean,
    val title: String,
    val subtitle: String,
    val body: String?,
    val statusLabel: String,
    val reasonLabel: String,
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
            appendLine("subtitle=$subtitle")
            appendLine("statusLabel=$statusLabel")
            appendLine("reasonLabel=$reasonLabel")
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
            subtitle = card.subtitle,
            body = if (card.visible) card.body else null,
            statusLabel = card.statusLabel,
            reasonLabel = card.reasonLabel,
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
