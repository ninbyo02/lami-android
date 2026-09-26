package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalInferenceRunState(
    val running: Boolean = false,
    val stopRequested: Boolean = false,
) {
    fun start(): LocalInferenceRunState = copy(running = true, stopRequested = false)
    fun requestStop(): LocalInferenceRunState = copy(stopRequested = true)
    fun finish(): LocalInferenceRunState = copy(running = false)
    fun clearStopRequest(): LocalInferenceRunState = copy(stopRequested = false)
}
