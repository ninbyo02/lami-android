package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS5TtsMapper {
    @Suppress("UNUSED_PARAMETER")
    fun map(
        s1Result: NpuStandardRouteS1Result,
        finalAssistantText: String,
        ttsEnabled: Boolean,
        streamingActive: Boolean = false,
        sanitizeForTts: (String) -> String = { it.trim() },
    ): NpuStandardRouteS5TtsMapping {
        val normalizedFinalText = finalAssistantText.trim()
        if (
            hasNpuStandardRouteRawRoleContamination(s1Result.rawOutput) ||
            s1Result.qualityClassification == NpuStandardRouteS1Contract.QUALITY_ROLE_CONTAMINATION ||
            s1Result.reason == NpuStandardRouteS1Contract.REASON_RAW_ROLE_CONTAMINATION
        ) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_ROLE_CONTAMINATION,
            )
        }
        if (normalizedFinalText.isBlank()) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT,
            )
        }
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_S1_NOT_SUCCESS,
            )
        }
        if (!ttsEnabled) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_TTS_DISABLED,
            )
        }
        if (streamingActive) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_STREAMING_ACTIVE,
            )
        }

        val speakText = sanitizeForTts(normalizedFinalText).trim()
        if (speakText.isBlank()) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT,
            )
        }

        return NpuStandardRouteS5TtsMapping(
            ttsCandidate = NpuStandardRouteS5TtsCandidate(
                finalAssistantText = normalizedFinalText,
                speakText = speakText,
            ),
        )
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun buildNpuStandardRouteS5TtsSavedResult(
    s1Result: NpuStandardRouteS1Result,
    finalAssistantText: String,
    ttsDiagnostics: NpuStandardRouteS5TtsDiagnostics =
        NpuStandardRouteS5TtsContract.successDiagnostics(finalAssistantText.trim()),
): NpuStandardRouteS1Result {
    val s5Selection = s1Result.selection.copy(
        routeType = NpuStandardRouteS5TtsContract.ROUTE_TYPE,
        sideEffects = s1Result.selection.sideEffects.copy(
            db = true,
            conversationHistorySaved = true,
            markdown = true,
            streaming = true,
            tts = ttsDiagnostics.completed,
        ),
    )
    val normalizedFinalText = finalAssistantText.trim()
    return s1Result.copy(
        selection = s5Selection,
        sanitizedOutput = normalizedFinalText.ifBlank { s1Result.sanitizedOutput },
        s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        s5TtsDiagnostics = ttsDiagnostics,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = s5Selection,
            status = s1Result.status,
            reason = s1Result.reason,
            rawOutput = s1Result.rawOutput,
            sanitizedOutput = normalizedFinalText.ifBlank { s1Result.sanitizedOutput },
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
            s5TtsDiagnostics = ttsDiagnostics,
        ),
    )
}
