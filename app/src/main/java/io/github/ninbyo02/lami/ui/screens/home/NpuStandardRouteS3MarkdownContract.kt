package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS3MarkdownSideEffects(
    val streaming: Boolean = false,
    val tts: Boolean = false,
    val backendNpuPersisted: Boolean = false,
) {
    val downstreamDisconnected: Boolean
        get() = !streaming &&
            !tts &&
            !backendNpuPersisted
}

internal data class NpuStandardRouteS3MarkdownCandidate(
    val sanitizedInputText: String,
    val sourceDisplayText: String,
    val finalizedText: String,
    val repairApplied: Boolean = finalizedText != sanitizedInputText,
    val sideEffects: NpuStandardRouteS3MarkdownSideEffects = NpuStandardRouteS3MarkdownSideEffects(),
) {
    val readyToRender: Boolean
        get() = sanitizedInputText.isNotBlank() &&
            sourceDisplayText.isNotBlank() &&
            finalizedText.isNotBlank() &&
            sideEffects.downstreamDisconnected
}

internal data class NpuStandardRouteS3MarkdownMapping(
    val markdownCandidate: NpuStandardRouteS3MarkdownCandidate?,
    val failureReason: String? = null,
) {
    val hasMarkdownCandidate: Boolean
        get() = markdownCandidate?.readyToRender == true
}

internal object NpuStandardRouteS3MarkdownContract {
    const val FAILURE_S1_NOT_SUCCESS = "s1_success_criteria_not_met"
    const val FAILURE_EMPTY_TEXT = "empty_text"
}
