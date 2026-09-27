package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalInferenceStartPreparation(
    val pendingUserMessageText: String,
    val streamingUiState: LocalStreamingUiState,
    val runState: LocalInferenceRunState,
    val resetEngineStateToReady: Boolean = true,
    val suppressDevDiagnosticsUntilReplyDisplayed: Boolean = true,
    val clearComposer: Boolean = true,
    val clearSelectedImages: Boolean = true,
)

internal object LocalInferenceStartPreparationPlanner {
    fun prepare(
        requestPrompt: String,
        streamingUiState: LocalStreamingUiState,
        runState: LocalInferenceRunState,
    ): LocalInferenceStartPreparation = LocalInferenceStartPreparation(
        pendingUserMessageText = requestPrompt,
        streamingUiState = streamingUiState.withDelayedPlaceholder(false),
        runState = LocalInferenceRunController.clearStopRequest(runState),
    )
}
