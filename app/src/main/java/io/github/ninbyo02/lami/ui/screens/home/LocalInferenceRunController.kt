package io.github.ninbyo02.lami.ui.screens.home

internal object LocalInferenceRunController {
    fun start(state: LocalInferenceRunState): LocalInferenceRunState =
        state.copy(running = true, stopRequested = false)

    fun requestStop(state: LocalInferenceRunState): LocalInferenceRunState =
        state.copy(stopRequested = true)

    fun finish(state: LocalInferenceRunState): LocalInferenceRunState =
        state.copy(running = false)

    fun clearStopRequest(state: LocalInferenceRunState): LocalInferenceRunState =
        state.copy(stopRequested = false)
}
