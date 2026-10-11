package io.github.ninbyo02.lami.ui.screens.home

internal interface LocalInferenceCleanupEffects {
    fun cancelGpuWatchdog()
    fun cancelInferenceJob()
    fun resetStreamingPlaceholder(reason: String)
    fun stopTts()
}

internal object LocalInferenceCleanupExecutor {
    fun executeStaleGenerationSideEffects(
        reason: String,
        effects: LocalInferenceCleanupEffects,
    ) {
        effects.cancelGpuWatchdog()
        effects.cancelInferenceJob()
        effects.resetStreamingPlaceholder(reason)
        effects.stopTts()
    }
}
