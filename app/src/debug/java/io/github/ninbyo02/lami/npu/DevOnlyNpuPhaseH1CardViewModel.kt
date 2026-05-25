package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1CardViewModel(
    val visible: Boolean,
    val title: String,
    val subtitle: String,
    val body: String?,
    val statusLabel: String,
    val reasonLabel: String,
    val detailLines: List<String>,
    val warningLines: List<String>,
    val devBadge: String,
    val showRawOutput: Boolean = false,
    val showRetryButton: Boolean = false,
    val showPersistButton: Boolean = false,
    val showTtsButton: Boolean = false,
    val showMarkdownButton: Boolean = false,
    val showStreamingIndicator: Boolean = false,
) {
    fun toContractText(): String =
        buildString {
            appendLine("visible=$visible")
            appendLine("title=$title")
            appendLine("subtitle=$subtitle")
            appendLine("statusLabel=$statusLabel")
            appendLine("reasonLabel=$reasonLabel")
            appendLine("devBadge=$devBadge")
            appendLine("body=${body ?: "null"}")
            appendLine("detailLines=${detailLines.joinToString("|")}")
            appendLine("warningLines=${warningLines.joinToString("|")}")
            appendLine("showRawOutput=$showRawOutput")
            appendLine("showRetryButton=$showRetryButton")
            appendLine("showPersistButton=$showPersistButton")
            appendLine("showTtsButton=$showTtsButton")
            appendLine("showMarkdownButton=$showMarkdownButton")
            append("showStreamingIndicator=$showStreamingIndicator")
        }
}

object DevOnlyNpuPhaseH1CardViewModelMapper {
    private const val DEV_BADGE = "DEV ONLY"
    private const val HIDDEN_SUBTITLE = "Hidden until a fresh gated artifact passes"
    private const val SUCCESS_SUBTITLE = "Read-only sanitized output"
    private const val ROLLBACK_SUBTITLE = "Hidden by promotion gate"

    fun from(state: DevOnlyNpuPhaseH1UiState): DevOnlyNpuPhaseH1CardViewModel {
        val statusLabel = state.status.name
        val warningLines = when (state.status) {
            DevOnlyNpuPhaseH1UiState.Status.SUCCESS -> emptyList()
            DevOnlyNpuPhaseH1UiState.Status.FAILURE -> listOf("failure=${state.reasonCode}")
            DevOnlyNpuPhaseH1UiState.Status.ROLLBACK -> listOf("rollback=${state.reasonCode}")
            DevOnlyNpuPhaseH1UiState.Status.HIDDEN -> emptyList()
        }

        return DevOnlyNpuPhaseH1CardViewModel(
            visible = state.visible && state.status == DevOnlyNpuPhaseH1UiState.Status.SUCCESS,
            title = state.devLabel,
            subtitle = when (state.status) {
                DevOnlyNpuPhaseH1UiState.Status.SUCCESS -> SUCCESS_SUBTITLE
                DevOnlyNpuPhaseH1UiState.Status.FAILURE -> ROLLBACK_SUBTITLE
                DevOnlyNpuPhaseH1UiState.Status.ROLLBACK -> ROLLBACK_SUBTITLE
                DevOnlyNpuPhaseH1UiState.Status.HIDDEN -> HIDDEN_SUBTITLE
            },
            body = if (state.status == DevOnlyNpuPhaseH1UiState.Status.SUCCESS) {
                state.outputPreview
            } else {
                null
            },
            statusLabel = statusLabel,
            reasonLabel = "reasonCode=${state.reasonCode}",
            detailLines = detailLines(state),
            warningLines = warningLines,
            devBadge = DEV_BADGE,
            showRawOutput = false,
            showRetryButton = false,
            showPersistButton = false,
            showTtsButton = false,
            showMarkdownButton = false,
            showStreamingIndicator = false,
        )
    }

    private fun detailLines(state: DevOnlyNpuPhaseH1UiState): List<String> =
        listOf(
            state.maxOutputTokensText,
            state.decodeMsText,
            state.backendEvidenceText,
            state.artifactText,
            "selectedPathSaved=false",
            "db=false",
            "tts=false",
            "markdown=false",
            "streaming=false",
        )
}
