package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS2DbSideEffects(
    val dbConnected: Boolean = true,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
    val backendNpuPersisted: Boolean = false,
) {
    val onlyDbConnected: Boolean
        get() = dbConnected &&
            !tts &&
            !markdown &&
            !streaming &&
            !backendNpuPersisted
}

internal data class NpuStandardRouteS2DbUserMessageCandidate(
    val text: String,
    val isSendByMe: Boolean = true,
) {
    val valid: Boolean
        get() = isSendByMe && text.isNotBlank()
}

internal data class NpuStandardRouteS2DbAssistantMessageCandidate(
    val text: String,
    val sourceDisplayText: String,
    val isSendByMe: Boolean = false,
) {
    val valid: Boolean
        get() = !isSendByMe &&
            text.isNotBlank() &&
            sourceDisplayText.isNotBlank()
}

internal data class NpuStandardRouteS2DbSaveCandidate(
    val userMessage: NpuStandardRouteS2DbUserMessageCandidate,
    val assistantMessage: NpuStandardRouteS2DbAssistantMessageCandidate,
    val sideEffects: NpuStandardRouteS2DbSideEffects = NpuStandardRouteS2DbSideEffects(),
) {
    val readyToPersist: Boolean
        get() = userMessage.valid &&
            assistantMessage.valid &&
            sideEffects.onlyDbConnected
}

internal data class NpuStandardRouteS2DbMapping(
    val saveCandidate: NpuStandardRouteS2DbSaveCandidate?,
    val failureReason: String? = null,
) {
    val hasSaveCandidate: Boolean
        get() = saveCandidate?.readyToPersist == true
}

internal object NpuStandardRouteS2DbContract {
    const val FAILURE_S1_NOT_SUCCESS = "s1_success_criteria_not_met"
    const val FAILURE_BLANK_USER_MESSAGE = "blank_user_message"
}
