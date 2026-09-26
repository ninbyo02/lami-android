package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalPartialStreamingState(
    val didReceiveRealPartial: Boolean = false,
    val realPartialChunkCount: Int = 0,
) {
    fun onPartialReceived(): LocalPartialStreamingState = copy(
        didReceiveRealPartial = true,
        realPartialChunkCount = realPartialChunkCount + 1,
    )
}
