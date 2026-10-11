package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState

internal const val LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT = "one-shot"
internal const val LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_FLOW = "official-flow"
internal const val LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_BLOCKING = "official-blocking"
internal const val LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_LEGACY = "session-legacy"
internal const val GPU_PREFILL_PROBE_DIAGNOSTIC_MESSAGE =
    "GPU prefill probe を実行しました。通常GPU生成は競合回避のためスキップしました。"
internal const val GPU_RAW_CALLBACK_PROBE_DIAGNOSTIC_MESSAGE =
    "GPU raw callback probe を実行しました。通常GPU生成の後段処理はスキップしました。"

internal const val GPU_MEMORY_PREFLIGHT_BLOCKED_MESSAGE =
    "GPUを安全に起動できる空きメモリが不足しています。CPUまたはNPUを使用してください。"

internal enum class LocalExecutionPath(
    val sourceLabel: String,
    val officialFlowAttempted: Boolean,
    val officialFlowUsed: Boolean,
    val usesOfficialConversationApi: Boolean,
) {
    HELD_OFFICIAL_FLOW(
        sourceLabel = "held-official-flow",
        officialFlowAttempted = true,
        officialFlowUsed = true,
        usesOfficialConversationApi = true,
    ),
    HELD_OFFICIAL_BLOCKING(
        sourceLabel = "held-official-blocking",
        officialFlowAttempted = true,
        officialFlowUsed = false,
        usesOfficialConversationApi = true,
    ),
    OFFICIAL_FLOW(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_FLOW,
        officialFlowAttempted = true,
        officialFlowUsed = true,
        usesOfficialConversationApi = true,
    ),
    OFFICIAL_BLOCKING(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_BLOCKING,
        officialFlowAttempted = true,
        officialFlowUsed = false,
        usesOfficialConversationApi = true,
    ),
    ONE_SHOT(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT,
        officialFlowAttempted = false,
        officialFlowUsed = false,
        usesOfficialConversationApi = false,
    ),
    SESSION_LEGACY(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_LEGACY,
        officialFlowAttempted = false,
        officialFlowUsed = false,
        usesOfficialConversationApi = false,
    );

    companion object {
        fun fromSourceLabel(raw: String?): LocalExecutionPath? {
            val normalized = raw?.trim().orEmpty()
            return values().firstOrNull { it.sourceLabel == normalized }
        }

        fun fromClosePath(raw: String?): LocalExecutionPath? {
            val normalized = raw?.trim().orEmpty()
            return when {
                normalized.contains("held-official-flow") -> HELD_OFFICIAL_FLOW
                normalized.contains("held-official-blocking") -> HELD_OFFICIAL_BLOCKING
                normalized.contains("official-flow") -> OFFICIAL_FLOW
                normalized.contains("official-blocking") -> OFFICIAL_BLOCKING
                normalized.contains("legacy") -> SESSION_LEGACY
                else -> null
            }
        }
    }
}

internal fun normalizeLocalInferenceRunResult(result: LocalInferenceRunResult?): LocalInferenceRunResult? {
    if (result == null) return null
    val executionPath = LocalExecutionPath.fromSourceLabel(result.trace.selectedAssistantResponseSource)
        ?: LocalExecutionPath.fromClosePath(result.closeLifecycleSummary?.path)
    val usesOfficialApi = executionPath?.usesOfficialConversationApi == true
    val officialFlowUsed = executionPath?.officialFlowUsed ?: result.trace.officialFlowUsed
    val officialFlowAttempted = when {
        executionPath != null -> executionPath.officialFlowAttempted
        officialFlowUsed -> true
        else -> result.trace.officialFlowAttempted
    }
    val officialFlowFallbackReason = if (officialFlowUsed) {
        null
    } else {
        result.trace.officialFlowFallbackReason
    }
    val normalizedTrace = result.trace.copy(
        selectedAssistantResponseSource = executionPath?.sourceLabel
            ?: result.trace.selectedAssistantResponseSource,
        officialFlowAttempted = officialFlowAttempted,
        officialFlowUsed = officialFlowUsed,
        officialFlowFallbackReason = officialFlowFallbackReason,
        officialConversationApiAvailable = when {
            result.trace.officialConversationApiAvailable != null -> result.trace.officialConversationApiAvailable
            usesOfficialApi -> true
            else -> null
        },
        outputTokenProbe = normalizeStatsProbeAvailability(
            probe = result.trace.outputTokenProbe,
            derivableNow = result.trace.sessionResponseTokens != null,
            usesOfficialApi = usesOfficialApi,
        ),
        evalTimeProbe = normalizeStatsProbeAvailability(
            probe = result.trace.evalTimeProbe,
            derivableNow = result.trace.localTraceStartElapsedRealtimeMs != null &&
                result.trace.localTraceCompletedElapsedRealtimeMs != null,
            usesOfficialApi = usesOfficialApi,
        ),
        firstTokenProbe = normalizeStatsProbeAvailability(
            probe = result.trace.firstTokenProbe,
            derivableNow = result.trace.localTraceStartElapsedRealtimeMs != null &&
                result.trace.localTraceFirstResponseElapsedRealtimeMs != null,
            usesOfficialApi = usesOfficialApi,
        ),
    )
    return result.copy(trace = normalizedTrace)
}

private fun normalizeStatsProbeAvailability(
    probe: LocalStatsCandidateProbe,
    derivableNow: Boolean,
    usesOfficialApi: Boolean,
): LocalStatsCandidateProbe {
    if (probe.availability != LocalStatsAvailability.NOT_FOUND) return probe
    return when {
        derivableNow -> probe.copy(availability = LocalStatsAvailability.DERIVABLE_NOW)
        usesOfficialApi -> probe.copy(availability = LocalStatsAvailability.API_CANDIDATE_ONLY)
        else -> probe
    }
}

internal const val GPU_TIMEOUT_PARTIAL_PRESERVED_APPLY_RESULT =
    "timeout-partial-output-preserved"

internal fun isGpuTimeoutPartialPreservedFailure(
    isErrorState: Boolean,
    response: String?,
    preferredBackendApplyResult: String?,
): Boolean =
    isErrorState &&
        !response.isNullOrBlank() &&
        preferredBackendApplyResult == GPU_TIMEOUT_PARTIAL_PRESERVED_APPLY_RESULT

internal fun shouldInsertLocalFailureAssistantMessage(
    runResult: LocalInferenceRunResult?,
): Boolean =
    runResult?.state == LocalInferenceEngineState.ERROR &&
        (runResult.response == GPU_EXPERIMENTAL_TIMEOUT_MESSAGE ||
            runResult.response == GPU_PREFILL_PROBE_DIAGNOSTIC_MESSAGE ||
            runResult.response == GPU_RAW_CALLBACK_PROBE_DIAGNOSTIC_MESSAGE ||
            runResult.response == GPU_MEMORY_PREFLIGHT_BLOCKED_MESSAGE ||
            isGpuTimeoutPartialPreservedFailure(
                isErrorState = true,
                response = runResult.response,
                preferredBackendApplyResult = runResult.trace.preferredBackendApplyResult,
            ))

internal fun buildCloseLifecycleText(summary: RunCloseLifecycleSummary?): String? {
    if (summary == null) return null
    fun formatOutcome(label: String, outcome: RunCloseTargetOutcome?): String {
        if (outcome == null) return "$label=status=none"
        return buildString {
            append(label).append("=status=").append(outcome.status)
            append(" strategy=").append(outcome.strategy ?: "none")
            append(" class=").append(outcome.targetClassName ?: "null")
            if (!outcome.errorClassName.isNullOrBlank()) {
                append(" error=").append(outcome.errorClassName)
            }
            if (!outcome.message.isNullOrBlank()) {
                append(" message=").append(outcome.message)
            }
        }
    }
    return buildString {
        append("CLOSE LIFECYCLE\n")
        append("path=").append(summary.path).append("\n")
        append("successReturned=").append(summary.successReturned).append("\n")
        append(formatOutcome("conversation", summary.conversationOutcome)).append("\n")
        append(formatOutcome("engine", summary.engineOutcome)).append("\n")
        append(formatOutcome("session", summary.sessionOutcome)).append("\n")
        append(formatOutcome("inference", summary.inferenceOutcome))
        summary.notes?.takeIf { it.isNotBlank() }?.let { note ->
            append("\nnotes=").append(note)
        }
    }
}

internal fun buildMeasuredTokenSnapshotSummary(trace: LocalInferenceTrace?): String? {
    if (trace == null) return null
    val measuredSnapshot = trace.measuredTokenSnapshot
    val inputTokens = measuredSnapshot?.inputTokens
    val outputTokens = measuredSnapshot?.outputTokens
    val totalTokens = measuredSnapshot?.totalTokens
    fun rawValueOrUnavailable(rawValue: String?): String = rawValue?.takeIf { it.isNotBlank() } ?: "unavailable"
    return buildString {
        append("in=$inputTokens / out=$outputTokens / total=$totalTokens")
        measuredSnapshot?.mediaPipeTokenizerSummary
            ?.takeIf { it.isNotBlank() }
            ?.let { mediaPipeSummary ->
                appendLine()
                append(mediaPipeSummary)
            }
        measuredSnapshot?.tokenizerRecountStatus?.takeIf { it.isNotBlank() }?.let { status ->
            appendLine()
            append("tokenizer-recount status: $status")
            measuredSnapshot.tokenizerSourceTraceSummary
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceTraceSummary ->
                    appendLine()
                    append(sourceTraceSummary)
                }
            if (status == "success" || measuredSnapshot.mediaPipeTokenizerStatus == "success") {
                appendLine()
                append("tokenizer-recount tokens: in=$inputTokens / out=$outputTokens / total=$totalTokens")
            }
        }
        appendLine()
        append("[BenchmarkInfo raw]")
        appendLine()
        append("prefillTokenCount: ${rawValueOrUnavailable(measuredSnapshot?.rawPrefillTokenCount)}")
        appendLine()
        append("decodeTokenCount: ${rawValueOrUnavailable(measuredSnapshot?.rawDecodeTokenCount)}")
        appendLine()
        append("prefillTokensPerSecond: ${rawValueOrUnavailable(measuredSnapshot?.rawPrefillTokensPerSecond)}")
        appendLine()
        append("decodeTokensPerSecond: ${rawValueOrUnavailable(measuredSnapshot?.rawDecodeTokensPerSecond)}")
        appendLine()
        append("timeToFirstTokenMs: ${rawValueOrUnavailable(measuredSnapshot?.rawTimeToFirstTokenMs)}")
        appendLine()
        append("modelInitMs: ${rawValueOrUnavailable(measuredSnapshot?.rawModelInitMs)}")
    }
}
