package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuInferenceStartPreparation(
    val common: LocalInferenceStartPreparation,
    val runState: LocalInferenceRunState,
    val clearRouteDisplayText: Boolean = true,
    val clearPhaseUiAppendText: Boolean = true,
    val clearFallbackText: Boolean = true,
    val clearPseudoStreamingText: Boolean = true,
    val resetPseudoStreamingActive: Boolean = true,
    val resetStreamingSentenceTtsBlocked: Boolean = true,
    val collapseDevDiagnostics: Boolean = true,
)

internal object NpuInferenceStartPreparationPlanner {
    fun prepare(
        requestPrompt: String,
        streamingUiState: LocalStreamingUiState,
        runState: LocalInferenceRunState,
    ): NpuInferenceStartPreparation {
        val common = LocalInferenceStartPreparationPlanner.prepare(
            requestPrompt = requestPrompt,
            streamingUiState = streamingUiState,
            runState = runState,
        )
        return NpuInferenceStartPreparation(
            common = common,
            runState = LocalInferenceRunController.start(common.runState),
        )
    }
}
