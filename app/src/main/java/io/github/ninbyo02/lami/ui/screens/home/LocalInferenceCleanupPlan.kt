package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalInferenceCleanupPlan(
    val runState: LocalInferenceRunState,
    val streamingUiState: LocalStreamingUiState,
    val partialStreamingState: LocalPartialStreamingState,
    val clearPendingUserMessage: Boolean,
    val resetEngineStateToReady: Boolean,
)

internal object LocalInferenceCleanupPlanner {
    fun staleGeneration(
        runState: LocalInferenceRunState,
        streamingUiState: LocalStreamingUiState,
        partialStreamingState: LocalPartialStreamingState,
    ): LocalInferenceCleanupPlan = LocalInferenceCleanupPlan(
        runState = LocalInferenceRunController.finish(runState),
        streamingUiState = streamingUiState.clear(),
        partialStreamingState = partialStreamingState,
        clearPendingUserMessage = true,
        resetEngineStateToReady = true,
    )
}
