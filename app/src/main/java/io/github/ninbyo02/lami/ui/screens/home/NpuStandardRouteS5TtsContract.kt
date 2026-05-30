package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS5TtsSideEffects(
    val ttsInvoked: Boolean = false,
    val streaming: Boolean = false,
    val backendNpuPersisted: Boolean = false,
) {
    val disconnected: Boolean
        get() = !ttsInvoked &&
            !streaming &&
            !backendNpuPersisted
}

internal data class NpuStandardRouteS5TtsCandidate(
    val finalAssistantText: String,
    val speakText: String,
    val sideEffects: NpuStandardRouteS5TtsSideEffects = NpuStandardRouteS5TtsSideEffects(),
) {
    val readyToSpeak: Boolean
        get() = finalAssistantText.isNotBlank() &&
            speakText.isNotBlank() &&
            sideEffects.disconnected
}

internal data class NpuStandardRouteS5TtsMapping(
    val ttsCandidate: NpuStandardRouteS5TtsCandidate?,
    val failureReason: String? = null,
) {
    val hasTtsCandidate: Boolean
        get() = ttsCandidate?.readyToSpeak == true
}

internal object NpuStandardRouteS5TtsContract {
    const val ROUTE_TYPE = "standard_chat_screen_s5_npu_tts"
    const val FAILURE_S1_NOT_SUCCESS = "s1_success_criteria_not_met"
    const val FAILURE_TTS_DISABLED = "tts_disabled"
    const val FAILURE_STREAMING_ACTIVE = "streaming_active"
    const val FAILURE_EMPTY_TEXT = "empty_text"
    const val FAILURE_EMPTY_SPEAK_TEXT = "empty_speak_text"
}
