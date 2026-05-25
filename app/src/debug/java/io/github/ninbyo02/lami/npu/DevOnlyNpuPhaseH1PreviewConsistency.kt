package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1PreviewConsistencySnapshot(
    val xmlCardText: String,
    val previewRendererText: String,
    val previewHostText: String,
    val composeRenderText: String,
    val composeContractText: String,
    val hostContractText: String,
    val visible: Boolean,
    val showCard: Boolean,
    val shouldShowSurface: Boolean,
    val insertIntoAssistantList: Boolean,
    val persistToDb: Boolean,
    val speakTts: Boolean,
    val renderMarkdown: Boolean,
    val stream: Boolean,
    val showRetry: Boolean,
    val showFallback: Boolean,
    val readsMetadata: Boolean,
    val runsNpu: Boolean,
    val engineInitialize: Boolean,
    val runDecode: Boolean,
) {
    fun toSnapshotText(): String =
        buildString {
            appendLine("visible=$visible")
            appendLine("showCard=$showCard")
            appendLine("shouldShowSurface=$shouldShowSurface")
            appendLine("xmlCardText=${xmlCardText.ifBlank { "null" }}")
            appendLine("previewRendererText=${previewRendererText.ifBlank { "null" }}")
            appendLine("previewHostText=${previewHostText.ifBlank { "null" }}")
            appendLine("composeRenderText=${composeRenderText.ifBlank { "null" }}")
            appendLine("insertIntoAssistantList=$insertIntoAssistantList")
            appendLine("persistToDb=$persistToDb")
            appendLine("speakTts=$speakTts")
            appendLine("renderMarkdown=$renderMarkdown")
            appendLine("stream=$stream")
            appendLine("showRetry=$showRetry")
            appendLine("showFallback=$showFallback")
            appendLine("readsMetadata=$readsMetadata")
            appendLine("runsNpu=$runsNpu")
            appendLine("engineInitialize=$engineInitialize")
            append("runDecode=$runDecode")
        }

    fun allContractText(): String =
        listOf(
            xmlCardText,
            previewRendererText,
            previewHostText,
            composeRenderText,
            composeContractText,
            hostContractText,
            toSnapshotText(),
        ).joinToString(separator = "\n---\n")
}

object DevOnlyNpuPhaseH1PreviewConsistency {
    fun from(card: DevOnlyNpuPhaseH1CardViewModel): DevOnlyNpuPhaseH1PreviewConsistencySnapshot {
        val composeModel = DevOnlyNpuPhaseH1ComposeAdapter.from(card)
        val host = DevOnlyNpuPhaseH1PreviewHost.create(composeModel)
        val rendererText = DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(card)
        val xmlText = DevOnlyNpuPhaseH1XmlCardContract.renderText(
            visible = card.visible,
            renderedLines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(card),
        )
        val composeRenderText = DevOnlyNpuPhaseH1XmlCardContract.renderText(composeModel)

        return DevOnlyNpuPhaseH1PreviewConsistencySnapshot(
            xmlCardText = xmlText,
            previewRendererText = rendererText,
            previewHostText = host.renderText,
            composeRenderText = composeRenderText,
            composeContractText = composeModel.toContractText(),
            hostContractText = host.toContractText(),
            visible = host.visible,
            showCard = host.showCard,
            shouldShowSurface = composeModel.shouldShowSurface,
            insertIntoAssistantList = composeModel.insertIntoAssistantList || host.showAssistantInsertion,
            persistToDb = composeModel.persistToDb || host.showDbPersistence,
            speakTts = composeModel.speakTts || host.showTts,
            renderMarkdown = composeModel.renderMarkdown || host.showMarkdown,
            stream = composeModel.stream || host.showStreaming,
            showRetry = composeModel.showRetryButton || host.showRetry,
            showFallback = composeModel.showFallbackButton || host.showFallback,
            readsMetadata = host.readsMetadata,
            runsNpu = host.runsNpu,
            engineInitialize = host.engineInitialize,
            runDecode = host.runDecode,
        )
    }
}
