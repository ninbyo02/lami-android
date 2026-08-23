package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

internal const val NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT =
    "すみません、応答を生成できませんでした。"
internal const val NPU_STANDARD_ROUTE_S1_NORMAL_CHAT_BLOCKED_USER_MESSAGE =
    "NPU推論は安全確認中のため、通常チャットでは一時的に無効化されています。"

internal data class NpuStandardRouteS1TransientFallback(
    val text: String,
    val kind: String,
)

internal fun shouldFallbackNpuStandardRouteFailureToLocal(
    result: NpuStandardRouteS1Result,
): Boolean {
    if (result.successCriteriaMet) return false
    if (result.reason == NpuStandardRouteS1Contract.REASON_COMPLETED_ROUTE_KILL_SWITCH_DISABLED) return false
    if (result.reason == NpuStandardRouteS1Contract.REASON_MODEL_NOT_NPU_COMPATIBLE) return false
    if (result.reason == NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT) return false
    if (result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL) return true

    return result.reason.startsWith("adapter_failure", ignoreCase = true) ||
        result.nativeDiagnostics.nativeLinkFailureDetected == "true" ||
        result.nativeDiagnostics.nativeErrorClass == "UnsatisfiedLinkError" ||
        result.nativeDiagnostics.nativeErrorMessage.contains("UnsatisfiedLinkError", ignoreCase = true)
}

internal fun resolveNpuStandardRouteS1Fallback(
    userPrompt: String,
    result: NpuStandardRouteS1Result,
): NpuStandardRouteS1TransientFallback? {
    val safeGreetingFallback = resolveNpuStandardRouteS1SafeGreetingFallback(
        userPrompt = userPrompt,
        result = result,
    )
    if (safeGreetingFallback != null) return safeGreetingFallback
    return if (shouldShowNpuStandardRouteS1Fallback(result)) {
        NpuStandardRouteS1TransientFallback(
            text = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
            kind = "generic_failure_fallback",
        )
    } else {
        null
    }
}

internal fun resolveNpuStandardRouteFailureAssistantMessage(
    result: NpuStandardRouteS1Result,
    transientFallback: NpuStandardRouteS1TransientFallback?,
): String? {
    if (result.successCriteriaMet) return null
    if (
        result.status == NpuStandardRouteS1Contract.STATUS_SUCCESS &&
        result.reason == NpuStandardRouteS1Contract.REASON_SUCCESS &&
        result.usableDisplayOutput.isNotBlank() &&
        result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
    ) {
        return null
    }
    if (
        result.status == NpuStandardRouteS1Contract.STATUS_SUCCESS &&
        result.reason == NpuStandardRouteS1Contract.REASON_SUCCESS
    ) {
        val qualityReason = if (result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL) {
            result.outputQualityCandidateReason
        } else {
            result.qualityClassification
        }
        return transientFallback?.text
            ?: "NPU推論の応答生成に失敗しました: ${qualityReason.ifBlank { "quality_check_failed" }}"
    }
    if (result.reason == NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT) {
        return NPU_STANDARD_ROUTE_S1_NORMAL_CHAT_BLOCKED_USER_MESSAGE
    }
    if (result.reason == NpuStandardRouteS1Contract.REASON_COMPLETED_ROUTE_KILL_SWITCH_DISABLED) {
        return null
    }
    if (result.reason == NpuStandardRouteS1Contract.REASON_MODEL_NOT_NPU_COMPATIBLE) {
        return NpuStandardRouteS1Contract.MODEL_NOT_NPU_COMPATIBLE_MESSAGE
    }
    return transientFallback?.text
        ?: "NPU推論の応答生成に失敗しました: ${result.reason.ifBlank { "unknown" }}"
}

internal fun shouldCompleteNpuStandardRouteFallbackAsAssistantResponse(
    fallback: NpuStandardRouteS1TransientFallback?,
): Boolean =
    fallback?.kind == NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING &&
        fallback.text.isNotBlank()

internal fun shouldRunNpuStandardRouteGenericFallback(
    result: NpuStandardRouteS1Result,
    transientFallback: NpuStandardRouteS1TransientFallback?,
    localStopRequested: Boolean,
): Boolean =
    !localStopRequested &&
        !shouldCompleteNpuStandardRouteFallbackAsAssistantResponse(transientFallback) &&
        shouldFallbackNpuStandardRouteFailureToLocal(result)

internal fun resolveNpuStandardRouteS1SafeGreetingFallback(
    userPrompt: String,
    result: NpuStandardRouteS1Result,
): NpuStandardRouteS1TransientFallback? {
    if (!isNpuStandardRouteS1SafeGreetingFallbackFailure(result)) return null
    val fallbackText = NpuStandardRouteS1Contract.safeGreetingResponseForPrompt(userPrompt)
        ?: return null
    return NpuStandardRouteS1TransientFallback(
        text = fallbackText,
        kind = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
    )
}

private fun isNpuStandardRouteS1SafeGreetingFallbackFailure(
    result: NpuStandardRouteS1Result,
): Boolean {
    if (result.successCriteriaMet) return false
    val candidateFailureReasons = result.outputQualityCandidateReason
        .split('+')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    val candidateLanguageFailure =
        result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL &&
            candidateFailureReasons.any { reason ->
                reason == "unsupported_japanese_response_script" ||
                    reason == "greeting_response_mismatch"
            }
    val providerLanguageFailure =
        result.status == FailureNpuStandardRouteS1Provider.STATUS_FAILURE &&
            (
                result.reason == NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE ||
                    result.reason == NpuStandardRouteS1Contract.REASON_MIXED_LANGUAGE
                )
    return candidateLanguageFailure || providerLanguageFailure
}

internal fun shouldShowNpuStandardRouteS1Fallback(
    result: NpuStandardRouteS1Result,
): Boolean =
    result.status == FailureNpuStandardRouteS1Provider.STATUS_FAILURE &&
        (
            result.reason == NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE ||
                result.reason == NpuStandardRouteS1Contract.REASON_MIXED_LANGUAGE ||
                result.reason == NpuStandardRouteS1Contract.REASON_QUESTION_ECHO ||
                result.reason == NpuStandardRouteS1Contract.REASON_ASSISTANT_STUB
            )

internal enum class LocalInferenceOutputDisposition {
    ACCEPT,
    SAFE_FALLBACK,
    GENERIC_FALLBACK,
    REJECT,
    STOPPED,
}

internal data class LocalInferenceOutputDecision(
    val disposition: LocalInferenceOutputDisposition,
    val acceptedText: String = "",
    val terminalText: String? = null,
    val rejectionReason: String? = null,
    val transientFallback: NpuStandardRouteS1TransientFallback? = null,
) {
    val shouldRunGenericFallback: Boolean
        get() = disposition == LocalInferenceOutputDisposition.GENERIC_FALLBACK

    val shouldFallbackToNextBackend: Boolean
        get() = disposition == LocalInferenceOutputDisposition.REJECT

    val shouldFinalizeImmediately: Boolean
        get() = disposition == LocalInferenceOutputDisposition.SAFE_FALLBACK ||
            disposition == LocalInferenceOutputDisposition.REJECT

    val shouldFinalizeAsAssistantResponse: Boolean
        get() = disposition == LocalInferenceOutputDisposition.SAFE_FALLBACK
}

internal object LocalInferenceOutputPolicy {
    fun evaluateNpu(
        userPrompt: String,
        result: NpuStandardRouteS1Result,
        localStopRequested: Boolean,
    ): LocalInferenceOutputDecision {
        if (result.successCriteriaMet) {
            return LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.ACCEPT,
                acceptedText = result.actualDisplayText.trim(),
            )
        }

        val transientFallback = resolveNpuStandardRouteS1Fallback(
            userPrompt = userPrompt,
            result = result,
        )
        if (localStopRequested) {
            return LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.STOPPED,
                rejectionReason = npuRejectionReason(result),
                transientFallback = transientFallback,
            )
        }
        if (shouldCompleteNpuStandardRouteFallbackAsAssistantResponse(transientFallback)) {
            val fallbackText = transientFallback?.text.orEmpty()
            return LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.SAFE_FALLBACK,
                acceptedText = fallbackText,
                terminalText = fallbackText,
                rejectionReason = npuRejectionReason(result),
                transientFallback = transientFallback,
            )
        }
        if (
            shouldRunNpuStandardRouteGenericFallback(
                result = result,
                transientFallback = transientFallback,
                localStopRequested = false,
            )
        ) {
            return LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.GENERIC_FALLBACK,
                rejectionReason = npuRejectionReason(result),
                transientFallback = transientFallback,
            )
        }
        return LocalInferenceOutputDecision(
            disposition = LocalInferenceOutputDisposition.REJECT,
            terminalText = resolveNpuStandardRouteFailureAssistantMessage(
                result = result,
                transientFallback = transientFallback,
            ),
            rejectionReason = npuRejectionReason(result),
            transientFallback = transientFallback,
        )
    }

    fun evaluateLocalCandidate(
        userPrompt: String,
        response: String?,
    ): LocalInferenceOutputDecision {
        val finalResponse = response?.trim().orEmpty()
        val rejectionReason = localInferenceResponseRejectionReason(
            userPrompt = userPrompt,
            response = finalResponse,
        )
        return if (rejectionReason == null) {
            LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.ACCEPT,
                acceptedText = finalResponse,
            )
        } else {
            LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.REJECT,
                rejectionReason = rejectionReason,
            )
        }
    }

    fun evaluateLocalCompletion(
        userPrompt: String,
        successfulBackend: String?,
        response: String?,
    ): LocalInferenceOutputDecision {
        val normalizedBackend = successfulBackend
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it == "GPU" || it == "CPU" }
            ?: return LocalInferenceOutputDecision(
                disposition = LocalInferenceOutputDisposition.REJECT,
                rejectionReason = "unsupported_backend",
            )
        val candidate = evaluateLocalCandidate(
            userPrompt = userPrompt,
            response = response,
        )
        return if (candidate.disposition == LocalInferenceOutputDisposition.ACCEPT) {
            candidate.copy(rejectionReason = null)
        } else {
            candidate.copy(
                rejectionReason = listOfNotNull(
                    candidate.rejectionReason,
                    "backend=$normalizedBackend",
                ).joinToString("|"),
            )
        }
    }

    private fun npuRejectionReason(result: NpuStandardRouteS1Result): String =
        when {
            result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL ->
                result.outputQualityCandidateReason.ifBlank { "quality_check_failed" }
            result.reason.isNotBlank() -> result.reason
            else -> "unknown"
        }
}
