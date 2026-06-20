package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.util.Locale

internal const val NPU_LONG_GENERATION_TEST_NAME = "NPU Beta Long Generation Test"
internal const val NPU_LONG_GENERATION_DEFAULT_PROMPT =
    "こんにちは。日本語で、ローカルAIアシスタントとして自己紹介し、できることを具体例つきで説明してください。"
internal const val NPU_LONG_GENERATION_STATUS_IDLE = "idle"
internal const val NPU_LONG_GENERATION_STATUS_RUNNING = "running"
internal const val NPU_LONG_GENERATION_STATUS_COMPLETED = "completed"
internal const val NPU_LONG_GENERATION_STATUS_CANCELLED = "cancelled"
internal const val NPU_LONG_GENERATION_STATUS_STOPPED = "stopped"
internal const val NPU_LONG_GENERATION_BLOCKED_SELECTED_BACKEND_NOT_NPU = "selected_backend_not_npu"
internal val NPU_LONG_GENERATION_TOKEN_PLAN = listOf(32, 128, 512)
internal val NPU_LONG_GENERATION_SUPPORTED_TOKEN_PLAN = listOf(32, 128, 512, 1024)

internal data class NpuLongGenerationStartGate(
    val allowed: Boolean,
    val blockedReason: String = "none",
)

internal fun npuLongGenerationStartGate(
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
): NpuLongGenerationStartGate {
    val selectedBackend = npuS1BackendFromPreferredSetting(preferredBackendSetting, npuStandardRouteMode)
    return if (selectedBackend == NPU_S1_BACKEND_NPU || selectedBackend.startsWith("NPU_")) {
        NpuLongGenerationStartGate(allowed = true)
    } else {
        NpuLongGenerationStartGate(
            allowed = false,
            blockedReason = NPU_LONG_GENERATION_BLOCKED_SELECTED_BACKEND_NOT_NPU,
        )
    }
}

internal data class NpuLongGenerationCase(
    val caseIndex: Int,
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
    val status: String,
    val reason: String,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val runDecodeReached: Boolean,
    val totalMs: Long? = null,
    val decodeMs: Long? = null,
    val outputTokens: Int? = null,
    val tokenCountMode: String = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE,
    val tokensPerSecond: Double? = null,
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val qualityClassification: String = "unavailable",
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val finishReason: String = "unavailable",
    val stopReason: String = "unavailable",
    val eosDetected: String = "unavailable",
    val tokenizerOutputTokens: String = "unavailable",
)

internal data class NpuLongGenerationState(
    val status: String = NPU_LONG_GENERATION_STATUS_IDLE,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val prompt: String = NPU_LONG_GENERATION_DEFAULT_PROMPT,
    val tokenPlan: List<Int> = NPU_LONG_GENERATION_TOKEN_PLAN,
    val selectedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val requestedBackend: String = NPU_S1_BACKEND_NPU,
    val effectiveBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val routeFamily: String = NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
    val blockedReason: String = "none",
    val cases: List<NpuLongGenerationCase> = emptyList(),
    val stopped: Boolean = false,
    val stopReason: String = "none",
)

internal fun npuLongGenerationCaseFromResult(
    caseIndex: Int,
    requestedMaxOutputTokens: Int,
    result: NpuStandardRouteS1Result,
    backendDiagnostics: NpuS1BackendDiagnostics,
): NpuLongGenerationCase {
    val telemetry = buildNpuS1ShortOutputTelemetry(result.inputPrompt, result)
    return NpuLongGenerationCase(
        caseIndex = caseIndex,
        requestedMaxOutputTokens = requestedMaxOutputTokens,
        effectiveMaxOutputTokens = result.selection.effectiveMaxOutputTokens,
        status = result.status,
        reason = result.reason,
        fallbackUsed = result.fallbackUsed,
        timeout = result.timeout,
        freshCrash = result.freshCrash,
        runDecodeReached = result.runDecodeReached,
        totalMs = result.timing.totalMs,
        decodeMs = result.timing.decodeMs,
        outputTokens = result.timing.outputTokens,
        tokenCountMode = result.timing.tokenCountMode,
        tokensPerSecond = result.timing.tokensPerSecond,
        rawOutput = result.rawOutput,
        sanitizedOutput = result.sanitizedOutput,
        qualityClassification = result.qualityClassification,
        backendEvidence = backendDiagnostics.backendEvidence
            .takeIf { it.isNotBlank() && it != NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE }
            ?: result.npuBackendEvidence,
        finishReason = telemetry.finishReason,
        stopReason = telemetry.stopReason,
        eosDetected = telemetry.eosDetected,
        tokenizerOutputTokens = telemetry.tokenizerOutputTokens,
    )
}

internal fun formatNpuLongGenerationDiagnosticsForDev(
    state: NpuLongGenerationState,
): String = formatNpuLongGenerationDiagnosticsForDev(state, includeCases = true)

private fun formatNpuLongGenerationDiagnosticsForDev(
    state: NpuLongGenerationState,
    includeCases: Boolean,
): String {
    val cases = state.cases.sortedBy { it.caseIndex }
    val completedCases = cases.size
    val successCount = cases.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }
    val failedCount = completedCases - successCount
    val fallbackUsedCount = cases.count { it.fallbackUsed }
    val timeoutCount = cases.count { it.timeout }
    val freshCrashCount = cases.count { it.freshCrash }
    val runDecodeReachedCount = cases.count { it.runDecodeReached }
    val firstFailureReason = cases.firstOrNull {
        it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS
    }?.reason ?: "unavailable"
    return buildString {
        appendLine("[DEV診断: NPU Beta Long Generation summary]")
        appendLine("test_name=$NPU_LONG_GENERATION_TEST_NAME")
        appendLine("status=${state.status}")
        appendLine("prompt=${state.prompt}")
        appendLine("token_plan=${state.tokenPlan.joinToString(",")}")
        appendLine("completed_cases=$completedCases")
        appendLine("success_count=$successCount")
        appendLine("failed_count=$failedCount")
        appendLine("fallback_used_count=$fallbackUsedCount")
        appendLine("timeout_count=$timeoutCount")
        appendLine("fresh_crash_count=$freshCrashCount")
        appendLine("run_decode_reached_count=$runDecodeReachedCount")
        appendLine("average_tokens_per_second=${formatNpuLongGenerationDouble(cases.mapNotNull { it.tokensPerSecond }.averageOrNull())}")
        appendLine("first_failure_reason=$firstFailureReason")
        appendLine("backend_evidence_summary=${summarizeNpuLongGenerationValues(cases.map { it.backendEvidence }, state.backendEvidence)}")
        appendLine("quality_classification_summary=${summarizeNpuLongGenerationValues(cases.map { it.qualityClassification }, "unavailable")}")
        appendLine("selected_backend=${state.selectedBackend}")
        appendLine("requested_backend=${state.requestedBackend}")
        appendLine("effective_backend=${state.effectiveBackend}")
        appendLine("backend_evidence=${state.backendEvidence}")
        appendLine("route_family=${state.routeFamily}")
        appendLine("blocked_reason=${state.blockedReason}")
        appendLine("stopped=${state.stopped}")
        appendLine("stop_reason=${state.stopReason}")
        appendLine("started_at_ms=${formatNpuLongGenerationLong(state.startedAtMs)}")
        appendLine("finished_at_ms=${formatNpuLongGenerationLong(state.finishedAtMs)}")
        if (includeCases) {
            cases.forEach { case ->
                appendLine("[DEV診断: NPU Beta Long Generation case]")
                appendLine("case_index=${case.caseIndex}")
                appendLine("requested_max_output_tokens=${case.requestedMaxOutputTokens}")
                appendLine("effective_max_output_tokens=${case.effectiveMaxOutputTokens}")
                appendLine("status=${case.status}")
                appendLine("reason=${case.reason}")
                appendLine("fallback_used=${case.fallbackUsed}")
                appendLine("timeout=${case.timeout}")
                appendLine("fresh_crash=${case.freshCrash}")
                appendLine("run_decode_reached=${case.runDecodeReached}")
                appendLine("total_ms=${formatNpuLongGenerationLong(case.totalMs)}")
                appendLine("decode_ms=${formatNpuLongGenerationLong(case.decodeMs)}")
                appendLine("output_tokens=${case.outputTokens?.toString() ?: "unavailable"}")
                appendLine("token_count_mode=${case.tokenCountMode}")
                appendLine("tokens_per_second=${formatNpuLongGenerationDouble(case.tokensPerSecond)}")
                appendLine("raw_output=${case.rawOutput}")
                appendLine("sanitized_output=${case.sanitizedOutput}")
                appendLine("quality_classification=${case.qualityClassification}")
                appendLine("backend_evidence=${case.backendEvidence}")
                appendLine("finish_reason=${case.finishReason}")
                appendLine("stop_reason=${case.stopReason}")
                appendLine("eos_detected=${case.eosDetected}")
                appendLine("tokenizer_output_tokens=${case.tokenizerOutputTokens}")
            }
        }
    }.trimEnd()
}

internal fun buildNpuLongGenerationSummaryCopyText(
    state: NpuLongGenerationState,
): String = formatNpuLongGenerationDiagnosticsForDev(state, includeCases = false)

internal fun buildNpuLongGenerationFullDumpCopyText(
    state: NpuLongGenerationState,
): String = formatNpuLongGenerationDiagnosticsForDev(state, includeCases = true)

private fun formatNpuLongGenerationLong(value: Long?): String = value?.toString() ?: "unavailable"

private fun formatNpuLongGenerationDouble(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()

private fun summarizeNpuLongGenerationValues(
    values: List<String>,
    fallback: String,
): String {
    val normalized = values
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "unavailable" }
    val source = normalized.ifEmpty {
        listOf(fallback.trim()).filter { it.isNotBlank() && it != "unavailable" }
    }
    if (source.isEmpty()) return "unavailable"
    return source
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key },
        )
        .joinToString(",") { "${it.key}:${it.value}" }
}
