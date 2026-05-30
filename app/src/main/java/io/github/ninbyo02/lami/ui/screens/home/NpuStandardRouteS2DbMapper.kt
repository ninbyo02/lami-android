package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS2DbMapper {
    fun map(
        userPrompt: String,
        s1Result: NpuStandardRouteS1Result,
    ): NpuStandardRouteS2DbMapping {
        val userText = userPrompt.trim()
        if (userText.isBlank()) {
            return NpuStandardRouteS2DbMapping(
                saveCandidate = null,
                failureReason = NpuStandardRouteS2DbContract.FAILURE_BLANK_USER_MESSAGE,
            )
        }
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS2DbMapping(
                saveCandidate = null,
                failureReason = NpuStandardRouteS2DbContract.FAILURE_S1_NOT_SUCCESS,
            )
        }

        return NpuStandardRouteS2DbMapping(
            saveCandidate = NpuStandardRouteS2DbSaveCandidate(
                userMessage = NpuStandardRouteS2DbUserMessageCandidate(
                    text = userText,
                ),
                assistantMessage = NpuStandardRouteS2DbAssistantMessageCandidate(
                    text = s1Result.sanitizedOutput.trim(),
                    sourceDisplayText = s1Result.displayText,
                ),
            ),
        )
    }
}
