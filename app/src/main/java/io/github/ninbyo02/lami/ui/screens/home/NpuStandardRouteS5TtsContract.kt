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

internal data class NpuStandardRouteS5TtsDiagnostics(
    val reason: String,
    val requested: Boolean,
    val started: Boolean,
    val completed: Boolean,
    val skipped: Boolean,
    val exceptionClass: String = NpuStandardRouteS5TtsContract.EXCEPTION_NONE,
    val exceptionMessage: String = NpuStandardRouteS5TtsContract.EXCEPTION_NONE,
    val textLength: Int,
    val inputSource: String = NpuStandardRouteS5TtsContract.INPUT_SOURCE_SANITIZED_OUTPUT,
)

internal object NpuStandardRouteS5TtsContract {
    const val ROUTE_TYPE = "standard_chat_screen_s5_npu_tts"
    const val FAILURE_S1_NOT_SUCCESS = "s1_success_criteria_not_met"
    const val FAILURE_TTS_DISABLED = "tts_disabled"
    const val FAILURE_STREAMING_ACTIVE = "streaming_active"
    const val FAILURE_EMPTY_TEXT = "empty_text"
    const val FAILURE_EMPTY_SPEAK_TEXT = "empty_speak_text"
    const val FAILURE_ROLE_CONTAMINATION = "role_contamination"
    const val REASON_SUCCESS = "success"
    const val REASON_TTS_EXCEPTION = "tts_exception"
    const val INPUT_SOURCE_SANITIZED_OUTPUT = "sanitized_output"
    const val EXCEPTION_NONE = "none"
    private const val EXCEPTION_MESSAGE_LIMIT = 160

    fun successDiagnostics(sanitizedOutput: String): NpuStandardRouteS5TtsDiagnostics =
        NpuStandardRouteS5TtsDiagnostics(
            reason = REASON_SUCCESS,
            requested = true,
            started = true,
            completed = true,
            skipped = false,
            textLength = sanitizedOutput.length,
        )

    fun exceptionDiagnostics(
        sanitizedOutput: String,
        throwable: Throwable,
    ): NpuStandardRouteS5TtsDiagnostics =
        NpuStandardRouteS5TtsDiagnostics(
            reason = REASON_TTS_EXCEPTION,
            requested = true,
            started = true,
            completed = false,
            skipped = false,
            exceptionClass = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name },
            exceptionMessage = throwable.message
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.take(EXCEPTION_MESSAGE_LIMIT)
                ?.ifBlank { EXCEPTION_NONE }
                ?: EXCEPTION_NONE,
            textLength = sanitizedOutput.length,
        )
}
