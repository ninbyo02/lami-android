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
        if (hasNpuStandardRouteRawRoleContamination(s1Result.rawOutput)) {
            return NpuStandardRouteS2DbMapping(
                saveCandidate = null,
                failureReason = NpuStandardRouteS2DbContract.FAILURE_RAW_ROLE_CONTAMINATION,
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

internal fun buildNpuStandardRouteS2DbSavedResult(
    s1Result: NpuStandardRouteS1Result,
): NpuStandardRouteS1Result =
    s1Result.copy(
        selection = s1Result.selection.copy(
            routeType = NpuStandardRouteS1Contract.ROUTE_TYPE_S2_DB_SAVE,
            sideEffects = s1Result.selection.sideEffects.copy(
                db = true,
                conversationHistorySaved = true,
            ),
        ),
        s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = s1Result.selection.copy(
                routeType = NpuStandardRouteS1Contract.ROUTE_TYPE_S2_DB_SAVE,
                sideEffects = s1Result.selection.sideEffects.copy(
                    db = true,
                    conversationHistorySaved = true,
                ),
            ),
            status = s1Result.status,
            reason = s1Result.reason,
            rawOutput = s1Result.rawOutput,
            sanitizedOutput = s1Result.sanitizedOutput,
            qualityClassification = s1Result.qualityClassification,
            runDecodeReached = s1Result.runDecodeReached,
            npuBackendEvidence = s1Result.npuBackendEvidence,
            fallbackUsed = s1Result.fallbackUsed,
            timeout = s1Result.timeout,
            freshCrash = s1Result.freshCrash,
            selectedModelName = s1Result.selectedModelName,
            selectedModelFile = s1Result.selectedModelFile,
            npuModelEligible = s1Result.npuModelEligible,
            timing = s1Result.timing,
            s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        ),
    )

internal fun buildNpuStandardRouteS2DbSkippedResult(
    s1Result: NpuStandardRouteS1Result,
    failureReason: String?,
): NpuStandardRouteS1Result {
    val s2Selection = s1Result.selection.copy(
        routeType = NpuStandardRouteS1Contract.ROUTE_TYPE_S2_DB_SAVE,
        sideEffects = s1Result.selection.sideEffects.copy(
            db = false,
            conversationHistorySaved = false,
        ),
    )
    val s2DbReason = failureReason?.takeIf { it.isNotBlank() } ?: s1Result.reason
    return s1Result.copy(
        selection = s2Selection,
        s2DbReason = s2DbReason,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = s2Selection,
            status = s1Result.status,
            reason = s1Result.reason,
            rawOutput = s1Result.rawOutput,
            sanitizedOutput = s1Result.sanitizedOutput,
            qualityClassification = s1Result.qualityClassification,
            runDecodeReached = s1Result.runDecodeReached,
            npuBackendEvidence = s1Result.npuBackendEvidence,
            fallbackUsed = s1Result.fallbackUsed,
            timeout = s1Result.timeout,
            freshCrash = s1Result.freshCrash,
            selectedModelName = s1Result.selectedModelName,
            selectedModelFile = s1Result.selectedModelFile,
            npuModelEligible = s1Result.npuModelEligible,
            timing = s1Result.timing,
            s2DbReason = s2DbReason,
        ),
    )
}
