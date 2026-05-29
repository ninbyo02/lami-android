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
)

internal object NpuStandardRouteS1Mapper {
    fun map(raw: NpuStandardRouteS1RawResult): NpuStandardRouteS1Result {
        val successEquivalent = raw.success == true ||
            raw.status == NpuStandardRouteS1Contract.STATUS_SUCCESS ||
            raw.result == NpuStandardRouteS1Contract.STATUS_SUCCESS
        val sanitizedOutput = raw.sanitizedOutput.trim()
        val displayText = sanitizedOutput
        val selection = NpuStandardRouteS1Selection(
            enabled = true,
            requestedMaxOutputTokens = raw.requestedMaxOutputTokens,
            effectiveMaxOutputTokens = raw.effectiveMaxOutputTokens,
            sideEffects = NpuStandardRouteS1SideEffects(),
        )
        val status = if (successEquivalent) {
            NpuStandardRouteS1Contract.STATUS_SUCCESS
        } else {
            raw.status.ifBlank { raw.result }.ifBlank { "failure" }
        }
        val reason = if (successEquivalent) {
            NpuStandardRouteS1Contract.REASON_SUCCESS
        } else {
            raw.reason.ifBlank { status }
        }

        return NpuStandardRouteS1Result(
            selection = selection,
            status = status,
            reason = reason,
            rawOutput = raw.rawOutput,
            sanitizedOutput = sanitizedOutput,
            qualityClassification = raw.qualityClassification,
            runDecodeReached = raw.runDecodeReached,
            npuBackendEvidence = normalizeEvidence(raw.npuBackendEvidence),
            fallbackUsed = raw.fallbackUsed,
            timeout = raw.timeout,
            freshCrash = raw.freshCrash,
            displayText = displayText,
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
