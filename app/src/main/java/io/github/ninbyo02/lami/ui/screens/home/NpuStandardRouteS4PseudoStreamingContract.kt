package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS4PseudoStreamingSideEffects(
    val realTokenStreaming: Boolean = false,
    val tts: Boolean = false,
    val backendNpuPersisted: Boolean = false,
) {
    val disconnected: Boolean
        get() = !realTokenStreaming &&
            !tts &&
            !backendNpuPersisted
}

internal data class NpuStandardRouteS4PseudoStreamingCandidate(
    val finalText: String,
    val chunks: List<String>,
    val dbPersistedText: String = finalText,
    val sourceDisplayText: String,
    val sideEffects: NpuStandardRouteS4PseudoStreamingSideEffects = NpuStandardRouteS4PseudoStreamingSideEffects(),
) {
    val readyToDisplay: Boolean
        get() = finalText.isNotBlank() &&
            sourceDisplayText.isNotBlank() &&
            chunks.isNotEmpty() &&
            chunks.all { it.isNotBlank() } &&
            chunks.last() == finalText &&
            dbPersistedText == finalText &&
            sideEffects.disconnected
}

internal data class NpuStandardRouteS4PseudoStreamingMapping(
    val pseudoStreamingCandidate: NpuStandardRouteS4PseudoStreamingCandidate?,
    val failureReason: String? = null,
) {
    val hasPseudoStreamingCandidate: Boolean
        get() = pseudoStreamingCandidate?.readyToDisplay == true
}

internal object NpuStandardRouteS4PseudoStreamingContract {
    const val ROUTE_TYPE = "standard_chat_screen_s4a_npu_pseudo_streaming"
    const val STREAMING_TYPE = "pseudo_streaming_full_text_chunking"
    const val FAILURE_S1_NOT_SUCCESS = "s1_success_criteria_not_met"
    const val FAILURE_EMPTY_TEXT = "empty_text"
    const val DEFAULT_MIN_CHUNKS = 3
    const val DEFAULT_MAX_CHUNKS = 5
}
