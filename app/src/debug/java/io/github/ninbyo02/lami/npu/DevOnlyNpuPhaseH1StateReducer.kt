package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1RefreshPolicy(
    val readsArtifactMetadataOnly: Boolean = true,
    val runsNpu: Boolean = false,
    val initializesEngine: Boolean = false,
    val runsDecode: Boolean = false,
)

object DevOnlyNpuPhaseH1StateReducer {
    val refreshPolicy = DevOnlyNpuPhaseH1RefreshPolicy()

    fun initial(): DevOnlyNpuPhaseH1UiState =
        hidden(reasonCode = "initial")

    fun onValidArtifactLoaded(input: DevOnlyNpuPhaseH1UiInput): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1Presenter.present(input)

    fun onClearByNewInput(current: DevOnlyNpuPhaseH1UiState): DevOnlyNpuPhaseH1UiState =
        current.hiddenWith(reasonCode = "clear_new_input")

    fun onClearByNavigationAway(current: DevOnlyNpuPhaseH1UiState): DevOnlyNpuPhaseH1UiState =
        current.hiddenWith(reasonCode = "clear_navigation_away")

    fun onClearByToggleOff(current: DevOnlyNpuPhaseH1UiState): DevOnlyNpuPhaseH1UiState =
        current.hiddenWith(reasonCode = "clear_toggle_off")

    fun onClearByFailureRollback(current: DevOnlyNpuPhaseH1UiState): DevOnlyNpuPhaseH1UiState =
        current.hiddenWith(reasonCode = "clear_failure_rollback")

    fun onAppRestart(): DevOnlyNpuPhaseH1UiState =
        hidden(reasonCode = "app_restart")

    fun onRefreshWithArtifactMetadata(
        values: Map<String, String>,
        nowMs: Long,
    ): DevOnlyNpuPhaseH1UiState {
        val freshness = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = DevOnlyNpuPhaseH1ArtifactFreshness.timestampFrom(values),
            nowMs = nowMs,
        )
        val mapped = DevOnlyNpuPhaseH1ArtifactMapper.from(values)
        val input = if (freshness.artifactFresh) {
            mapped
        } else {
            mapped.copy(
                success = false,
                sanitizedOutput = null,
                reasonCode = freshness.reasonCode,
                rollback = true,
                artifactFresh = false,
            )
        }

        return DevOnlyNpuPhaseH1Presenter.present(input)
    }

    private fun DevOnlyNpuPhaseH1UiState.hiddenWith(reasonCode: String): DevOnlyNpuPhaseH1UiState =
        hidden(
            reasonCode = reasonCode,
            decodeMsText = decodeMsText,
            backendEvidenceText = backendEvidenceText,
            maxOutputTokensText = maxOutputTokensText,
            artifactText = artifactText,
        )

    private fun hidden(
        reasonCode: String,
        decodeMsText: String = "decode_ms=unknown",
        backendEvidenceText: String = "backendEvidence=none",
        maxOutputTokensText: String = "maxOutputTokens=${DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS}",
        artifactText: String = "artifact=none",
    ): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1UiState(
            visible = false,
            devLabel = DevOnlyNpuPhaseH1Presenter.DEV_LABEL,
            status = DevOnlyNpuPhaseH1UiState.Status.HIDDEN,
            outputPreview = null,
            reasonCode = reasonCode,
            decodeMsText = decodeMsText,
            backendEvidenceText = backendEvidenceText,
            maxOutputTokensText = maxOutputTokensText,
            artifactText = artifactText,
            rollback = false,
            shouldPersistToDb = false,
            shouldSpeakTts = false,
            shouldRenderMarkdown = false,
            shouldStream = false,
        )
}
