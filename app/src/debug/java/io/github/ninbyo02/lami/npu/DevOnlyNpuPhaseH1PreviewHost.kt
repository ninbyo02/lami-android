package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1PreviewHostState(
    val visible: Boolean,
    val renderText: String,
    val showCard: Boolean,
    val showRetry: Boolean = false,
    val showFallback: Boolean = false,
    val showAssistantInsertion: Boolean = false,
    val showDbPersistence: Boolean = false,
    val showTts: Boolean = false,
    val showMarkdown: Boolean = false,
    val showStreaming: Boolean = false,
    val readsMetadata: Boolean = false,
    val runsNpu: Boolean = false,
    val engineInitialize: Boolean = false,
    val runDecode: Boolean = false,
) {
    fun toContractText(): String =
        buildString {
            appendLine("visible=$visible")
            appendLine("showCard=$showCard")
            appendLine("renderText=${renderText.ifBlank { "null" }}")
            appendLine("showRetry=$showRetry")
            appendLine("showFallback=$showFallback")
            appendLine("showAssistantInsertion=$showAssistantInsertion")
            appendLine("showDbPersistence=$showDbPersistence")
            appendLine("showTts=$showTts")
            appendLine("showMarkdown=$showMarkdown")
            appendLine("showStreaming=$showStreaming")
            appendLine("readsMetadata=$readsMetadata")
            appendLine("runsNpu=$runsNpu")
            appendLine("engineInitialize=$engineInitialize")
            append("runDecode=$runDecode")
        }
}

object DevOnlyNpuPhaseH1PreviewHost {
    fun create(model: DevOnlyNpuPhaseH1ComposeModel): DevOnlyNpuPhaseH1PreviewHostState {
        val visible = model.shouldShowSurface
        return DevOnlyNpuPhaseH1PreviewHostState(
            visible = visible,
            showCard = visible,
            renderText = if (visible) render(model) else "",
            showRetry = false,
            showFallback = false,
            showAssistantInsertion = false,
            showDbPersistence = false,
            showTts = false,
            showMarkdown = false,
            showStreaming = false,
            readsMetadata = false,
            runsNpu = false,
            engineInitialize = false,
            runDecode = false,
        )
    }

    private fun render(model: DevOnlyNpuPhaseH1ComposeModel): String =
        buildList {
            add("${model.devBadge} - ${model.title}")
            add("Status: ${model.statusLabel}")
            model.body?.let { body ->
                add("Output:")
                add(body)
            }
            if (model.detailLines.isNotEmpty()) {
                add("Details:")
                model.detailLines.forEach { line -> add("- $line") }
            }
        }.joinToString(separator = "\n")
}
