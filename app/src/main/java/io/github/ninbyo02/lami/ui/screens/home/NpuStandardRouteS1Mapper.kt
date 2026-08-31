package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS1RawResult(
    val status: String = "",
    val result: String = "",
    val success: Boolean? = null,
    val reason: String = "",
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val qualityClassification: String = "",
    val runDecodeReached: Boolean = false,
    val npuBackendEvidence: String = "",
    val fallbackUsed: Boolean = false,
    val timeout: Boolean = false,
    val freshCrash: Boolean = false,
    val requestedMaxOutputTokens: Int = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
    val effectiveMaxOutputTokens: Int = requestedMaxOutputTokens,
    val selectedModelName: String = "",
    val selectedModelFile: String = "",
    val npuModelEligible: Boolean? = null,
    val prefillMs: Long? = null,
    val nativeDecodeMs: Long? = null,
    val npuS1DecodeMs: Long? = null,
    val npuS1OutputTokens: Int? = null,
    val npuS1TokenCountMode: String = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE,
    val nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
    val inputPrompt: String = "",
)

internal object NpuStandardRouteS1Mapper {
    fun map(raw: NpuStandardRouteS1RawResult): NpuStandardRouteS1Result {
        val sanitizedOutput = raw.sanitizedOutput.trim()
        val qualityCandidate = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = raw.rawOutput,
            sanitizedOutput = sanitizedOutput,
            inputPrompt = raw.inputPrompt,
        )
        val rawRoleContaminationOverridesQuality =
            hasUnsafeNpuStandardRouteRawRoleContamination(
                rawOutput = raw.rawOutput,
                sanitizedOutput = sanitizedOutput,
                inputPrompt = raw.inputPrompt,
            )
        val successLikeRaw = raw.success == true ||
            raw.status == NpuStandardRouteS1Contract.STATUS_SUCCESS ||
            raw.result == NpuStandardRouteS1Contract.STATUS_SUCCESS
        val successEquivalent = !rawRoleContaminationOverridesQuality && successLikeRaw
        val displayText = if (qualityCandidate.status == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS) {
            qualityCandidate.preparedOutput.ifBlank { sanitizedOutput }
        } else {
            ""
        }
        val selection = NpuStandardRouteS1Selection(
            enabled = true,
            requestedMaxOutputTokens = raw.requestedMaxOutputTokens,
            effectiveMaxOutputTokens = raw.effectiveMaxOutputTokens,
            sideEffects = NpuStandardRouteS1SideEffects(),
        )
        val status = when {
            rawRoleContaminationOverridesQuality -> FailureNpuStandardRouteS1Provider.STATUS_FAILURE
            successEquivalent -> NpuStandardRouteS1Contract.STATUS_SUCCESS
            else -> raw.status.ifBlank { raw.result }.ifBlank { "failure" }
        }
        val reason = when {
            rawRoleContaminationOverridesQuality -> NpuStandardRouteS1Contract.REASON_RAW_ROLE_CONTAMINATION
            successEquivalent -> NpuStandardRouteS1Contract.REASON_SUCCESS
            else -> raw.reason.ifBlank { status }
        }
        val qualityClassification = if (rawRoleContaminationOverridesQuality) {
            NpuStandardRouteS1Contract.QUALITY_ROLE_CONTAMINATION
        } else {
            raw.qualityClassification
        }
        val outputTokens = raw.npuS1OutputTokens
            ?: NpuStandardRouteS1Contract.estimateOutputTokensFromText(displayText)
        val tokenCountMode = when {
            raw.npuS1OutputTokens != null && raw.npuS1TokenCountMode.isNotBlank() -> raw.npuS1TokenCountMode
            outputTokens != null -> NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS
            else -> NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE
        }
        val generationMs = raw.nativeDecodeMs ?: raw.npuS1DecodeMs
        val timing = NpuStandardRouteS1Timing(
            decodeMs = generationMs,
            prefillMs = raw.prefillMs,
            nativeDecodeMs = raw.nativeDecodeMs,
            ttftMs = null,
            outputTokens = outputTokens,
            tokenCountMode = tokenCountMode,
            tokensPerSecond = NpuStandardRouteS1Contract.tokensPerSecond(
                outputTokens = outputTokens,
                decodeMs = generationMs,
            ),
        )

        return NpuStandardRouteS1Result(
            selection = selection,
            status = status,
            reason = reason,
            rawOutput = raw.rawOutput,
            sanitizedOutput = sanitizedOutput,
            qualityClassification = qualityClassification,
            runDecodeReached = raw.runDecodeReached,
            npuBackendEvidence = normalizeEvidence(raw.npuBackendEvidence),
            fallbackUsed = raw.fallbackUsed,
            timeout = raw.timeout,
            freshCrash = raw.freshCrash,
            selectedModelName = raw.selectedModelName,
            selectedModelFile = raw.selectedModelFile,
            npuModelEligible = raw.npuModelEligible,
            timing = timing,
            displayText = displayText,
            nativeDiagnostics = raw.nativeDiagnostics,
            inputPrompt = raw.inputPrompt,
        )
    }

    private fun normalizeEvidence(evidence: String): String =
        if (hasS1NpuEvidence(evidence)) {
            NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE
        } else {
            evidence
        }

    private fun hasS1NpuEvidence(evidence: String): Boolean {
        val normalized = evidence.uppercase()
        return "QNN_HTP" in normalized || "FASTRPC" in normalized
    }
}

internal fun hasNpuStandardRouteRawRoleContamination(rawOutput: String): Boolean {
    if (rawOutput.isBlank()) return false
    return NPU_STANDARD_ROUTE_RAW_ROLE_MARKERS.any { marker ->
        rawOutput.contains(marker, ignoreCase = marker.first().code < 128)
    }
}

internal fun hasUnsafeNpuStandardRouteRawRoleContamination(
    rawOutput: String,
    sanitizedOutput: String,
    inputPrompt: String = "",
): Boolean {
    if (!hasNpuStandardRouteRawRoleContamination(rawOutput)) return false
    val userTurnMarker = NPU_STANDARD_ROUTE_PLAIN_USER_TURN_MARKER.find(rawOutput)
    val sanitizedMatchesSafePrefix = userTurnMarker != null &&
        rawOutput.substring(0, userTurnMarker.range.first).trim() == sanitizedOutput.trim()
    val qualityCandidatePassed = sanitizedMatchesSafePrefix &&
        evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = rawOutput,
            sanitizedOutput = sanitizedOutput,
            inputPrompt = inputPrompt,
        ).status == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
    return !qualityCandidatePassed
}

private val NPU_STANDARD_ROUTE_PLAIN_USER_TURN_MARKER = Regex(
    """(?im)(?:^|\n)\s*(?:ユーザー|User)\s*[:：]""",
)

private val NPU_STANDARD_ROUTE_RAW_ROLE_MARKERS = listOf(
    "ユーザー:",
    "ユーザー：",
    "アシスタント:",
    "アシスタント：",
    "User:",
    "User：",
    "Assistant:",
    "Assistant：",
)
