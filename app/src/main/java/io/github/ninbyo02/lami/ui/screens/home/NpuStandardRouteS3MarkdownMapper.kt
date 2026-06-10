package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS3MarkdownMapper {
    fun map(
        s1Result: NpuStandardRouteS1Result,
        finalizeMarkdown: (String) -> String = { it.trim() },
    ): NpuStandardRouteS3MarkdownMapping {
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS,
            )
        }

        val sanitizedInput = s1Result.actualDisplayText.trim()
        val sourceDisplayText = s1Result.displayText.trim()
        if (sanitizedInput.isBlank() || sourceDisplayText.isBlank()) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT,
            )
        }

        val finalizedText = finalizeMarkdown(sanitizedInput).trim()
        if (finalizedText.isBlank()) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT,
            )
        }

        return NpuStandardRouteS3MarkdownMapping(
            markdownCandidate = NpuStandardRouteS3MarkdownCandidate(
                sanitizedInputText = sanitizedInput,
                sourceDisplayText = sourceDisplayText,
                finalizedText = finalizedText,
            ),
        )
    }
}

internal fun buildNpuStandardRouteS3MarkdownSavedResult(
    s1Result: NpuStandardRouteS1Result,
    finalizedText: String,
): NpuStandardRouteS1Result {
    val s3Selection = s1Result.selection.copy(
        routeType = NpuStandardRouteS3MarkdownContract.ROUTE_TYPE,
        sideEffects = s1Result.selection.sideEffects.copy(
            db = true,
            conversationHistorySaved = true,
            markdown = true,
            streaming = false,
            tts = false,
        ),
    )
    val normalizedFinalizedText = finalizedText.trim()
    return s1Result.copy(
        selection = s3Selection,
        sanitizedOutput = normalizedFinalizedText.ifBlank { s1Result.sanitizedOutput },
        s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = s3Selection,
            status = s1Result.status,
            reason = s1Result.reason,
            rawOutput = s1Result.rawOutput,
            sanitizedOutput = normalizedFinalizedText.ifBlank { s1Result.sanitizedOutput },
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
}
