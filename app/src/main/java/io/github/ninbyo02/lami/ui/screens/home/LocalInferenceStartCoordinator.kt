package io.github.ninbyo02.lami.ui.screens.home

internal object LocalInferenceStartCoordinator {
    fun resolveStartAction(
        runState: LocalInferenceRunState,
        existingJobActive: Boolean,
    ): ExistingLocalGenerationJobPolicy = resolveExistingLocalGenerationJobPolicy(
        isLocalInferenceRunning = runState.running,
        existingJobActive = existingJobActive,
    )
}
