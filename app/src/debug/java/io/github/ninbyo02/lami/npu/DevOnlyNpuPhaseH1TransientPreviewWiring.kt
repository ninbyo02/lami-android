package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1TransientPreviewWiringResult(
    val shouldReadMetadata: Boolean,
    val previewVisible: Boolean,
    val renderedLines: List<String>,
    val renderedText: String,
    val reasonLabel: String,
    val sideEffectLines: List<String>,
)

object DevOnlyNpuPhaseH1TransientPreviewWiring {
    fun render(
        devEnableNpuChatScreenRoute: Boolean,
        nowMs: Long,
        metadataTextProvider: () -> String,
    ): DevOnlyNpuPhaseH1TransientPreviewWiringResult {
        val boundaryResult = DevOnlyNpuPhaseH1ArtifactMetadataParser.boundaryForToggle(
            devEnableNpuChatScreenRoute = devEnableNpuChatScreenRoute,
            nowMs = nowMs,
            metadataTextProvider = metadataTextProvider,
        )
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(boundaryResult.uiState)
        val renderedLines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(viewModel)
        return DevOnlyNpuPhaseH1TransientPreviewWiringResult(
            shouldReadMetadata = boundaryResult.shouldReadMetadata,
            previewVisible = renderedLines.isNotEmpty(),
            renderedLines = renderedLines,
            renderedText = DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(viewModel),
            reasonLabel = viewModel.reasonLabel,
            sideEffectLines = listOf(
                "selectedPathNpuSaved=false",
                "standard_route_connected=false",
                "normal_ui_route_connected=false",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "retry=false",
                "auto_fallback=false",
                "npu_generation=false",
                "engine_initialize=false",
                "run_decode=false",
            ),
        )
    }
}
