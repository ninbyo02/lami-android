package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.InferenceBackendSelection
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import io.github.ninbyo02.lami.ui.screens.settings.LocalInferenceRoutingDryRunInput
import io.github.ninbyo02.lami.ui.screens.settings.LocalInferenceRoutingDryRunDecision
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.NpuStandardRouteSelectionSource
import io.github.ninbyo02.lami.ui.screens.settings.dryRunRoutingDecision
import io.github.ninbyo02.lami.ui.screens.settings.localInferenceResidencyPolicyForUserFacingSelection
import java.security.MessageDigest

internal fun interface NpuStandardRouteS1Provider {
    fun invoke(
        userPrompt: String,
        maxOutputTokens: Int,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult
}

internal fun buildNpuRealPromptHandoffTrace(
    stage: String,
    userPrompt: String,
): String = buildString {
    append("NPU_REAL_PROMPT ")
    append(stage)
    append("_prompt_hash=")
    append(npuRealPromptHash(userPrompt))
    append(" ")
    append(stage)
    append("_prompt_length=")
    append(userPrompt.length)
    append(" ")
    append(stage)
    append("_prompt_code_points=")
    append(userPrompt.codePointCount(0, userPrompt.length))
    append(" ")
    append(stage)
    append("_prompt_preview=")
    append(npuRealPromptPreview(userPrompt))
}

internal fun buildNpuRealPromptResultTrace(
    status: String,
    reason: String,
    maxOutputTokens: Int,
    rawOutput: String,
    sanitizedOutput: String,
    qualityClassification: String,
    runDecodeReached: Boolean,
    fallbackUsed: Boolean,
    timeout: Boolean,
    freshCrash: Boolean,
    selectedModelName: String = "",
    selectedModelFile: String = "",
    npuModelEligible: Boolean? = null,
    timing: NpuStandardRouteS1Timing = NpuStandardRouteS1Timing(),
): String = buildString {
    val outputQualityCandidate = evaluateNpuS1PersistentCustomJniQualityCandidate(rawOutput, sanitizedOutput)
    append("NPU_REAL_PROMPT status=")
    append(status)
    append(" reason=")
    append(reason)
    if (selectedModelName.isNotBlank()) {
        append(" selected_model_name=")
        append(selectedModelName)
    }
    if (selectedModelFile.isNotBlank()) {
        append(" selected_model_file=")
        append(selectedModelFile)
    }
    if (npuModelEligible != null) {
        append(" npu_model_eligible=")
        append(npuModelEligible)
    }
    append(" max_output_tokens=")
    append(maxOutputTokens)
    append(" raw_output_hash=")
    append(npuRealPromptHash(rawOutput))
    append(" raw_output_length=")
    append(rawOutput.length)
    append(" raw_output_preview=")
    append(npuRealPromptPreview(rawOutput))
    append(" sanitized_output_hash=")
    append(npuRealPromptHash(sanitizedOutput))
    append(" sanitized_output_length=")
    append(sanitizedOutput.length)
    append(" sanitized_output_preview=")
    append(npuRealPromptPreview(sanitizedOutput))
    append(" quality_classification=")
    append(qualityClassification)
    append(" output_quality_candidate_status=")
    append(outputQualityCandidate.status)
    append(" output_quality_candidate_reason=")
    append(outputQualityCandidate.reason)
    append(" run_decode_reached=")
    append(runDecodeReached)
    append(" fallback_used=")
    append(fallbackUsed)
    append(" timeout=")
    append(timeout)
    append(" fresh_crash=")
    append(freshCrash)
    if (timing.hasAnyValue) {
        append(" npu_s1_total_ms=")
        append(NpuStandardRouteS1Contract.formatTimingMs(timing.totalMs))
        append(" npu_s1_decode_ms=")
        append(NpuStandardRouteS1Contract.formatTimingMs(timing.decodeMs))
        append(" npu_s1_ttft_ms=")
        append(NpuStandardRouteS1Contract.formatTimingMs(timing.ttftMs))
        append(" npu_s1_output_tokens=")
        append(timing.outputTokens?.toString() ?: "n/a")
        append(" npu_s1_token_count_mode=")
        append(timing.tokenCountMode)
        append(" npu_s1_tokens_per_second=")
        append(NpuStandardRouteS1Contract.formatTokensPerSecond(timing.tokensPerSecond))
    }
}

internal fun buildNpuStandardRouteS1DevTraceText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
    npuStandardRouteDevGatePropertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): String {
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    val backendDiagnostics = npuS1BackendDiagnosticsForResult(
        result = result,
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
    )
    val rolloutSelection = resolveNpuStandardRouteRolloutSelection(
        preferredBackend = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        selectionSource = npuStandardRouteSelectionSource,
        propertyReader = npuStandardRouteDevGatePropertyReader,
    )
    val phase1Diagnostics = buildNpuStandardRoutePhase1DiagnosticsForNpuS1Result(
        result = result,
        backendDiagnostics = backendDiagnostics,
        rolloutSelection = rolloutSelection,
        propertyReader = npuStandardRouteDevGatePropertyReader,
    )
    val lines = buildList {
        addAll(
            listOf(
                "max_output_tokens=$maxOutputTokens",
                "route_type=${result.selection.routeType}",
                "requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
                "effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
                "max_output_tokens_clamped=${npuStandardRouteMaxOutputTokensClamped(result)}",
                "max_output_tokens_clamp_limit=${NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT}",
                "max_output_tokens_clamp_reason=${npuStandardRouteMaxOutputTokensClampReason(result)}",
                "app_requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
                "native_requested_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
                "native_effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
                "input_hash=${npuRealPromptHash(input)}",
                "input_prompt=${npuStandardRouteS1DevPreview(input)}",
                "input_preview=${npuStandardRouteS1DevPreview(input)}",
                "final_prompt_tail=${npuStandardRouteS1DevPreview(NpuStandardRouteS1Contract.finalPromptTail(input))}",
                "selected_prompt_profile=${NpuStandardRouteS1Contract.PROMPT_WRAPPER_USED}",
                "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
                "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
                "rewritten_prompt_tail=${npuStandardRouteS1DevPreview(promptRewrite.rewrittenPromptText.takeLast(160))}",
                "input_length=${input.length}",
                "input_code_points=${input.codePointCount(0, input.length)}",
                "selected_model_name=${result.selectedModelName.ifBlank { "unknown" }}",
                "selected_model_file=${result.selectedModelFile.ifBlank { "unknown" }}",
                "npu_model_eligible=${result.npuModelEligible ?: "unknown"}",
                "raw_output_hash=${npuRealPromptHash(result.rawOutput)}",
                "raw_output_preview=${npuStandardRouteS1DevPreview(result.rawOutput)}",
                "raw_output_length=${result.rawOutput.length}",
                "raw_output_code_points=${result.rawOutput.codePointCount(0, result.rawOutput.length)}",
                "sanitized_output_hash=${npuRealPromptHash(result.sanitizedOutput)}",
                "sanitized_output_preview=${npuStandardRouteS1DevPreview(result.sanitizedOutput)}",
                "sanitized_output_length=${result.sanitizedOutput.length}",
                "sanitized_output_code_points=${result.sanitizedOutput.codePointCount(0, result.sanitizedOutput.length)}",
                "status=${result.status}",
                "reason=${result.reason}",
                "normal_chat_native_route_blocked=${result.reason == NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT}",
                "quality_classification=${result.qualityClassification}",
                "output_quality_candidate_status=${result.outputQualityCandidateStatus}",
                "output_quality_candidate_reason=${result.outputQualityCandidateReason}",
                "output_quality_candidate_prepared_output=${npuStandardRouteS1DevPreview(result.preparedOutput)}",
            ),
        )
        addAll(buildNpuStandardRoutePhase1DiagnosticLines(phase1Diagnostics))
        addAll(
            listOf(
                "failure_exception_class=${npuStandardRouteS1FailureExceptionClass(result)}",
                "failure_exception_message=${npuStandardRouteS1DevPreview(npuStandardRouteS1FailureExceptionMessage(result))}",
                "failure_stage=${npuStandardRouteS1FailureStage(result)}",
                "native_stage=${result.nativeDiagnostics.nativeStage}",
                "native_stage_history=${result.nativeDiagnostics.nativeStageHistory}",
                "native_call_started_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallStartedAtElapsedRealtimeMs}",
                "native_call_finished_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallFinishedAtElapsedRealtimeMs}",
                "native_call_duration_ms=${result.nativeDiagnostics.nativeCallDurationMs}",
                "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
                "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
                "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
                "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
                "native_cleanup_started=${result.nativeDiagnostics.nativeCleanupStarted}",
                "native_cleanup_finished=${result.nativeDiagnostics.nativeCleanupFinished}",
                "native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}",
                "native_result_available=${result.nativeDiagnostics.nativeResultAvailable}",
                "native_result_tail=${npuStandardRouteS1DevPreview(result.nativeDiagnostics.nativeResultTail)}",
                "native_diag_available=${result.nativeDiagnostics.nativeDiagAvailable}",
                "native_diag_tail=${npuStandardRouteS1DevPreview(result.nativeDiagnostics.nativeDiagTail)}",
                "native_link_failure_detected=${result.nativeDiagnostics.nativeLinkFailureDetected}",
                "native_link_failure_library=${result.nativeDiagnostics.nativeLinkFailureLibrary}",
                "native_load_order=${result.nativeDiagnostics.nativeLoadOrder}",
                "java_library_path=${npuStandardRouteS1DevPreview(result.nativeDiagnostics.javaLibraryPath)}",
                "supported_abis=${result.nativeDiagnostics.supportedAbis}",
                "npu_s1_total_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.totalMs)}",
                "npu_s1_decode_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.decodeMs)}",
                "npu_s1_ttft_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.ttftMs)}",
                "npu_s1_output_tokens=${result.timing.outputTokens?.toString() ?: "n/a"}",
                "npu_s1_token_count_mode=${result.timing.tokenCountMode}",
                "npu_s1_tokens_per_second=${NpuStandardRouteS1Contract.formatTokensPerSecond(result.timing.tokensPerSecond)}",
                "run_decode_reached=${result.runDecodeReached}",
                "timeout=${result.timeout}",
            ),
        )
    }.toMutableList()
    if (transientFallback == NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING) {
        lines += "original_status=${result.status}"
        lines += "original_reason=${result.reason}"
        lines += "original_quality_classification=${result.qualityClassification}"
        lines += "fallback=${NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING}"
    } else {
        lines += "fallback=${result.fallbackUsed}"
    }
    lines += "fresh_crash=${result.freshCrash}"
    return appendNpuS1ShortOutputTelemetryForDev(
        text = lines.joinToString("\n"),
        input = input,
        result = result,
    )
}

internal fun buildNpuStandardRouteS1DiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
) : String {
    val sections = mutableListOf(
        buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = input,
            result = result,
            preferredBackendSetting = preferredBackendSetting,
            npuStandardRouteMode = npuStandardRouteMode,
            npuStandardRouteSelectionSource = npuStandardRouteSelectionSource,
        ),
    )
    buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
        input = input,
        result = result,
        transientFallback = transientFallback,
        preferredBackendSetting = preferredBackendSetting,
    )?.let { sections += it }
    return sections.joinToString("\n\n")
}

internal fun buildNpuStandardRouteS1DiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    appHistoryText: String,
    residentRuntimeEvidence: LocalBackendRuntimeEvidence = LocalBackendRuntimeEvidence(),
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
): String {
    val sections = mutableListOf(
        buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = input,
            result = result,
            appHistoryText = appHistoryText,
            residentRuntimeEvidence = residentRuntimeEvidence,
            preferredBackendSetting = preferredBackendSetting,
            npuStandardRouteMode = npuStandardRouteMode,
            npuStandardRouteSelectionSource = npuStandardRouteSelectionSource,
        ),
    )
    buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
        input = input,
        result = result,
        transientFallback = transientFallback,
        appHistoryText = appHistoryText,
        preferredBackendSetting = preferredBackendSetting,
    )?.let { sections += it }
    return sections.joinToString("\n\n")
}

internal fun buildNpuStandardRouteS1CompactExplicitCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    appHistoryText: String = "",
    residentRuntimeEvidence: LocalBackendRuntimeEvidence = LocalBackendRuntimeEvidence(),
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
): String = buildNpuStandardRouteS1DiagnosticCopyText(
    input = input,
    result = result,
    maxOutputTokens = maxOutputTokens,
    transientFallback = transientFallback,
    appHistoryText = appHistoryText,
    residentRuntimeEvidence = residentRuntimeEvidence,
    preferredBackendSetting = preferredBackendSetting,
    npuStandardRouteMode = npuStandardRouteMode,
    npuStandardRouteSelectionSource = npuStandardRouteSelectionSource,
)

internal fun buildNpuStandardRouteS1FullDumpExplicitCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
): String = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
    input = input,
    result = result,
    maxOutputTokens = maxOutputTokens,
    transientFallback = transientFallback,
    preferredBackendSetting = preferredBackendSetting,
    npuStandardRouteMode = npuStandardRouteMode,
    npuStandardRouteSelectionSource = npuStandardRouteSelectionSource,
)

internal fun buildNpuStandardRouteS1CompactDiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    appHistoryText: String = "",
    residentRuntimeEvidence: LocalBackendRuntimeEvidence = LocalBackendRuntimeEvidence(),
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
    npuStandardRouteDevGatePropertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): String {
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    val backendDiagnostics = npuS1BackendDiagnosticsForResult(
        result = result,
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
    )
    val rolloutSelection = resolveNpuStandardRouteRolloutSelection(
        preferredBackend = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        selectionSource = npuStandardRouteSelectionSource,
        propertyReader = npuStandardRouteDevGatePropertyReader,
    )
    val residentRoutingDryRunDecision = localInferenceResidencyPolicyForUserFacingSelection(
        npuS1InferenceBackendSelectionForResidentPolicy(preferredBackendSetting),
        runtimeEvidence = residentRuntimeEvidence,
    ).dryRunRoutingDecision(
        LocalInferenceRoutingDryRunInput(
            promptTokenEstimate = estimateResidentRouterPromptTokens(input),
            requestedOutputTokens = result.selection.requestedMaxOutputTokens,
        ),
    )
    val phase1Diagnostics = buildNpuStandardRoutePhase1DiagnosticsForNpuS1Result(
        result = result,
        backendDiagnostics = backendDiagnostics,
        rolloutSelection = rolloutSelection,
        propertyReader = npuStandardRouteDevGatePropertyReader,
    )
    return buildList {
        addAll(
            listOf(
        "[DEV診断: NPU S1 compact]",
        "input_prompt=${npuStandardRouteS1EscapeCopyValue(input)}",
        "route_type=${result.selection.routeType}",
        "final_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText.takeLast(200))}",
        "selected_prompt_profile=${promptRewrite.selectedPromptProfile}",
        "prompt_wrapper_used=${promptRewrite.promptWrapperUsed}",
        "requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
        "effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
        "max_output_tokens_clamped=${npuStandardRouteMaxOutputTokensClamped(result)}",
        "max_output_tokens_clamp_limit=${NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT}",
        "max_output_tokens_clamp_reason=${npuStandardRouteMaxOutputTokensClampReason(result)}",
        "app_requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
        "native_requested_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
        "native_effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
        "selected_model_name=${npuStandardRouteS1EscapeCopyValue(result.selectedModelName.ifBlank { "unknown" })}",
        "selected_model_file=${npuStandardRouteS1EscapeCopyValue(result.selectedModelFile.ifBlank { "unknown" })}",
        "npu_model_eligible=${result.npuModelEligible ?: "unknown"}",
        "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
        "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
        "raw_output=${npuStandardRouteS1EscapeCopyValue(result.rawOutput)}",
        "sanitized_output=${npuStandardRouteS1EscapeCopyValue(result.sanitizedOutput)}",
        "output_quality_candidate_status=${result.outputQualityCandidateStatus}",
        "output_quality_candidate_reason=${result.outputQualityCandidateReason}",
        "output_quality_candidate_prepared_output=${npuStandardRouteS1EscapeCopyValue(result.preparedOutput)}",
        "arithmetic_tail_leak_detected=${result.outputQualityCandidate.arithmeticTailLeakDetected}",
        "arithmetic_tail_leak_ignored_for_display=${result.outputQualityCandidate.arithmeticTailLeakIgnoredForDisplay}",
        "actual_display_text=${npuStandardRouteS1EscapeCopyValue(result.actualDisplayText)}",
        "tts_text=${npuStandardRouteS1EscapeCopyValue(result.ttsText)}",
        "status=${result.status}",
        "reason=${result.reason}",
        "quality_classification=${result.qualityClassification}",
        "selected_backend=${backendDiagnostics.selectedBackend}",
        "requested_backend=${backendDiagnostics.requestedBackend}",
        "effective_backend=${backendDiagnostics.effectiveBackend}",
        "backend_evidence=${backendDiagnostics.backendEvidence}",
        "route_family=${backendDiagnostics.routeFamily}",
        "resident_dry_run_backend=${residentRoutingDryRunDecision.selectedBackend.name}",
        "resident_dry_run_reason=${residentRoutingDryRunDecision.reason}",
        "resident_dry_run_tokens=${residentRoutingDryRunDecision.estimatedTotalTokens ?: "unknown"}",
        "resident_dry_run_long_context_threshold=${residentRoutingDryRunDecision.longContextThreshold}",
        "resident_dry_run_fallback_backends=${residentRoutingDryRunDecision.fallbackBackends.joinToString(",") { it.name }.ifBlank { "none" }}",
            ),
        )
        addAll(buildNpuStandardRoutePhase1DiagnosticLines(phase1Diagnostics))
        addAll(
            listOf(
        "blocked_reason=${backendDiagnostics.blockedReason}",
        "guard_recommendation=${backendDiagnostics.guardRecommendation}",
        "npu_s1_failure_kind=${npuStandardRouteS1FailureKind(result)}",
        "engine_create_failure_count=${extractNpuStandardRouteS1HistoryValue(appHistoryText, "engine_create_failure_count")}",
        "failure_after_successful_npu_s1_request_count=" +
            extractNpuStandardRouteS1HistoryValue(appHistoryText, "failure_after_successful_npu_s1_request_count"),
        "native_crash_risk_hint=${npuStandardRouteS1NativeCrashRiskHint(result)}",
        "npu_s1_total_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.totalMs)}",
        "npu_s1_decode_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.decodeMs)}",
        "npu_s1_output_tokens=${result.timing.outputTokens?.toString() ?: "n/a"}",
        "npu_s1_token_count_mode=${result.timing.tokenCountMode}",
        "npu_s1_tokens_per_second=${NpuStandardRouteS1Contract.formatTokensPerSecond(result.timing.tokensPerSecond)}",
        "run_decode_reached=${result.runDecodeReached}",
        "timeout=${result.timeout}",
        "fallback=${result.fallbackUsed}",
        "fresh_crash=${result.freshCrash}",
        "failure_exception_class=${npuStandardRouteS1FailureExceptionClass(result)}",
        "failure_exception_message=${npuStandardRouteS1EscapeCopyValue(npuStandardRouteS1FailureExceptionMessage(result))}",
        "native_stage=${result.nativeDiagnostics.nativeStage}",
        "native_stage_history=${result.nativeDiagnostics.nativeStageHistory}",
        "native_call_started_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallStartedAtElapsedRealtimeMs}",
        "native_call_finished_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallFinishedAtElapsedRealtimeMs}",
        "native_call_duration_ms=${result.nativeDiagnostics.nativeCallDurationMs}",
        "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
        "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
        "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
        "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
        "native_cleanup_started=${result.nativeDiagnostics.nativeCleanupStarted}",
        "native_cleanup_finished=${result.nativeDiagnostics.nativeCleanupFinished}",
        "native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}",
        "native_session_destroy_started=${result.nativeDiagnostics.nativeSessionDestroyStarted}",
        "native_session_destroy_finished=${result.nativeDiagnostics.nativeSessionDestroyFinished}",
        "native_session_destroy_reached=${result.nativeDiagnostics.nativeSessionDestroyReached}",
        "native_result_available=${result.nativeDiagnostics.nativeResultAvailable}",
        "native_result_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeResultTail)}",
        "native_diag_available=${result.nativeDiagnostics.nativeDiagAvailable}",
        "native_diag_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeDiagTail)}",
        "native_link_failure_detected=${result.nativeDiagnostics.nativeLinkFailureDetected}",
        "native_link_failure_library=${result.nativeDiagnostics.nativeLinkFailureLibrary}",
        "native_load_order=${result.nativeDiagnostics.nativeLoadOrder}",
        "java_library_path=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.javaLibraryPath)}",
        "supported_abis=${result.nativeDiagnostics.supportedAbis}",
            ),
        )
    }.joinToString("\n")
}

private fun npuS1InferenceBackendSelectionForResidentPolicy(
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
): InferenceBackendSelection =
    when (preferredBackendDryRunSetting) {
        PreferredBackendDryRunSetting.CPU -> InferenceBackendSelection.CPU
        PreferredBackendDryRunSetting.GPU -> InferenceBackendSelection.GPU
        PreferredBackendDryRunSetting.NPU,
        PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU -> InferenceBackendSelection.NPU
        PreferredBackendDryRunSetting.DEFAULT -> InferenceBackendSelection.AUTOMATIC
    }

internal fun buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    transientFallback: String? = null,
    appHistoryText: String = "",
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
): String? {
    if (!shouldShowNpuStandardRouteS1FailureDetails(result, transientFallback)) return null
    val residentRoutingDryRunDecision = localInferenceResidencyPolicyForUserFacingSelection(
        npuS1InferenceBackendSelectionForResidentPolicy(preferredBackendSetting),
    ).dryRunRoutingDecision(
        LocalInferenceRoutingDryRunInput(
            promptTokenEstimate = estimateResidentRouterPromptTokens(input),
            requestedOutputTokens = result.selection.requestedMaxOutputTokens,
        ),
    )
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    return buildList {
        add("[DEV診断: NPU S1 failure details]")
        add("failure_stage=${npuStandardRouteS1FailureStage(result)}")
        addAll(npuS1ResidentDryRunDiagnosticLines(residentRoutingDryRunDecision))
        add("native_error_class=${result.nativeDiagnostics.nativeErrorClass}")
        add("native_error_message=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeErrorMessage)}")
        add("native_error_stage=${result.nativeDiagnostics.nativeErrorStage}")
        add("native_error_source=${result.nativeDiagnostics.nativeErrorSource}")
        add("native_call_started_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallStartedAtElapsedRealtimeMs}")
        add("native_call_finished_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallFinishedAtElapsedRealtimeMs}")
        add("native_call_duration_ms=${result.nativeDiagnostics.nativeCallDurationMs}")
        add("native_call_reached=${result.nativeDiagnostics.nativeCallReached}")
        add("native_call_returned=${result.nativeDiagnostics.nativeCallReturned}")
        add("native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}")
        add("native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}")
        add("native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}")
        add("native_result_available=${result.nativeDiagnostics.nativeResultAvailable}")
        add("native_result_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeResultTail)}")
        add("native_diag_available=${result.nativeDiagnostics.nativeDiagAvailable}")
        add("native_diag_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeDiagTail)}")
        add("native_link_failure_detected=${result.nativeDiagnostics.nativeLinkFailureDetected}")
        add("native_link_failure_library=${result.nativeDiagnostics.nativeLinkFailureLibrary}")
        add("native_load_order=${result.nativeDiagnostics.nativeLoadOrder}")
        add("java_library_path=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.javaLibraryPath)}")
        add("supported_abis=${result.nativeDiagnostics.supportedAbis}")
        add("requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}")
        add("effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}")
        add("max_output_tokens_clamped=${npuStandardRouteMaxOutputTokensClamped(result)}")
        add("max_output_tokens_clamp_limit=${NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT}")
        add("max_output_tokens_clamp_reason=${npuStandardRouteMaxOutputTokensClampReason(result)}")
        add("app_requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}")
        add("native_requested_max_output_tokens=${result.selection.effectiveMaxOutputTokens}")
        add("native_effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}")
        add("npu_s1_failure_kind=${npuStandardRouteS1FailureKind(result)}")
        add("npu_s1_failure_layer=${npuStandardRouteS1FailureLayer(result)}")
        add("npu_s1_failure_recovery_hint=${npuStandardRouteS1FailureRecoveryHint(result)}")
        add("final_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText)}")
        add("rewritten_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText)}")
        add("rewritten_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText.takeLast(200))}")
        add("output_quality_candidate_prepared_output=${npuStandardRouteS1EscapeCopyValue(result.preparedOutput)}")
        add("output_quality_candidate_reason=${result.outputQualityCandidateReason}")
        add("native_stage_history=${result.nativeDiagnostics.nativeStageHistory}")
        add("fallback=${transientFallback ?: result.fallbackUsed}")
        addAll(extractNpuStandardRouteS1FailureHistoryLines(appHistoryText))
    }.joinToString("\n")
}

internal fun buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
    npuStandardRouteDevGatePropertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): String = appendNpuS1ShortOutputTelemetryForDev(
    text = run {
        val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
        val backendDiagnostics = npuS1BackendDiagnosticsForResult(
            result = result,
            preferredBackendSetting = preferredBackendSetting,
            npuStandardRouteMode = npuStandardRouteMode,
        )
        val rolloutSelection = resolveNpuStandardRouteRolloutSelection(
            preferredBackend = preferredBackendSetting,
            npuStandardRouteMode = npuStandardRouteMode,
            selectionSource = npuStandardRouteSelectionSource,
            propertyReader = npuStandardRouteDevGatePropertyReader,
        )
        val residentRoutingDryRunDecision = localInferenceResidencyPolicyForUserFacingSelection(
            npuS1InferenceBackendSelectionForResidentPolicy(preferredBackendSetting),
        ).dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = estimateResidentRouterPromptTokens(input),
                requestedOutputTokens = result.selection.requestedMaxOutputTokens,
            ),
        )
        val phase1Diagnostics = buildNpuStandardRoutePhase1DiagnosticsForNpuS1Result(
            result = result,
            backendDiagnostics = backendDiagnostics,
            rolloutSelection = rolloutSelection,
            propertyReader = npuStandardRouteDevGatePropertyReader,
        )
        buildList {
            addAll(
                listOf(
            "[DEV診断: NPU S1 full dump]",
            "input_prompt=${npuStandardRouteS1EscapeCopyValue(input)}",
            "route_type=${result.selection.routeType}",
            "final_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText)}",
            "final_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText.takeLast(200))}",
            "selected_prompt_profile=${promptRewrite.selectedPromptProfile}",
            "prompt_wrapper_used=${promptRewrite.promptWrapperUsed}",
            "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
            "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
            "rewritten_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText)}",
            "rewritten_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText.takeLast(200))}",
            "max_output_tokens=$maxOutputTokens",
            "requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
            "effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
            "max_output_tokens_clamped=${npuStandardRouteMaxOutputTokensClamped(result)}",
            "max_output_tokens_clamp_limit=${NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT}",
            "max_output_tokens_clamp_reason=${npuStandardRouteMaxOutputTokensClampReason(result)}",
            "app_requested_max_output_tokens=${result.selection.requestedMaxOutputTokens}",
            "native_requested_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
            "native_effective_max_output_tokens=${result.selection.effectiveMaxOutputTokens}",
            "selected_model_name=${npuStandardRouteS1EscapeCopyValue(result.selectedModelName.ifBlank { "unknown" })}",
            "selected_model_file=${npuStandardRouteS1EscapeCopyValue(result.selectedModelFile.ifBlank { "unknown" })}",
            "npu_model_eligible=${result.npuModelEligible ?: "unknown"}",
            "raw_output=${npuStandardRouteS1EscapeCopyValue(result.rawOutput)}",
            "sanitized_output=${npuStandardRouteS1EscapeCopyValue(result.sanitizedOutput)}",
            "status=${result.status}",
            "reason=${result.reason}",
            "quality_classification=${result.qualityClassification}",
            "selected_backend=${backendDiagnostics.selectedBackend}",
            "requested_backend=${backendDiagnostics.requestedBackend}",
            "effective_backend=${backendDiagnostics.effectiveBackend}",
            "backend_evidence=${backendDiagnostics.backendEvidence}",
            "route_family=${backendDiagnostics.routeFamily}",
            "resident_dry_run_backend=${residentRoutingDryRunDecision.selectedBackend.name}",
            "resident_dry_run_reason=${residentRoutingDryRunDecision.reason}",
            "resident_dry_run_tokens=${residentRoutingDryRunDecision.estimatedTotalTokens ?: "unknown"}",
            "resident_dry_run_long_context_threshold=${residentRoutingDryRunDecision.longContextThreshold}",
            "resident_dry_run_fallback_backends=${residentRoutingDryRunDecision.fallbackBackends.joinToString(",") { it.name }.ifBlank { "none" }}",
                ),
            )
            addAll(buildNpuStandardRoutePhase1DiagnosticLines(phase1Diagnostics))
            addAll(
                listOf(
            "blocked_reason=${backendDiagnostics.blockedReason}",
            "guard_recommendation=${backendDiagnostics.guardRecommendation}",
            "npu_s1_failure_kind=${npuStandardRouteS1FailureKind(result)}",
            "npu_s1_failure_layer=${npuStandardRouteS1FailureLayer(result)}",
            "npu_s1_failure_recovery_hint=${npuStandardRouteS1FailureRecoveryHint(result)}",
            "native_crash_risk_hint=${npuStandardRouteS1NativeCrashRiskHint(result)}",
            "output_quality_candidate_status=${result.outputQualityCandidateStatus}",
            "output_quality_candidate_reason=${result.outputQualityCandidateReason}",
            "output_quality_candidate_prepared_output=${npuStandardRouteS1EscapeCopyValue(result.preparedOutput)}",
            "arithmetic_tail_leak_detected=${result.outputQualityCandidate.arithmeticTailLeakDetected}",
            "arithmetic_tail_leak_ignored_for_display=${result.outputQualityCandidate.arithmeticTailLeakIgnoredForDisplay}",
            "actual_display_text=${npuStandardRouteS1EscapeCopyValue(result.actualDisplayText)}",
            "tts_text=${npuStandardRouteS1EscapeCopyValue(result.ttsText)}",
            "failure_exception_class=${npuStandardRouteS1FailureExceptionClass(result)}",
            "failure_exception_message=${npuStandardRouteS1EscapeCopyValue(npuStandardRouteS1FailureExceptionMessage(result))}",
            "failure_stage=${npuStandardRouteS1FailureStage(result)}",
            "native_stage=${result.nativeDiagnostics.nativeStage}",
            "native_stage_history=${result.nativeDiagnostics.nativeStageHistory}",
            "native_call_started_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallStartedAtElapsedRealtimeMs}",
            "native_call_finished_at_elapsed_realtime_ms=${result.nativeDiagnostics.nativeCallFinishedAtElapsedRealtimeMs}",
            "native_call_duration_ms=${result.nativeDiagnostics.nativeCallDurationMs}",
            "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
            "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
            "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
            "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
            "native_cleanup_started=${result.nativeDiagnostics.nativeCleanupStarted}",
            "native_cleanup_finished=${result.nativeDiagnostics.nativeCleanupFinished}",
            "native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}",
            "native_session_destroy_started=${result.nativeDiagnostics.nativeSessionDestroyStarted}",
            "native_session_destroy_finished=${result.nativeDiagnostics.nativeSessionDestroyFinished}",
            "native_session_destroy_reached=${result.nativeDiagnostics.nativeSessionDestroyReached}",
            "native_result_available=${result.nativeDiagnostics.nativeResultAvailable}",
            "native_result_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeResultTail)}",
            "native_diag_available=${result.nativeDiagnostics.nativeDiagAvailable}",
            "native_diag_tail=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeDiagTail)}",
            "native_link_failure_detected=${result.nativeDiagnostics.nativeLinkFailureDetected}",
            "native_link_failure_library=${result.nativeDiagnostics.nativeLinkFailureLibrary}",
            "native_load_order=${result.nativeDiagnostics.nativeLoadOrder}",
            "java_library_path=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.javaLibraryPath)}",
            "supported_abis=${result.nativeDiagnostics.supportedAbis}",
            "native_error_class=${result.nativeDiagnostics.nativeErrorClass}",
            "native_error_message=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeErrorMessage)}",
            "native_error_stage=${result.nativeDiagnostics.nativeErrorStage}",
            "native_error_source=${result.nativeDiagnostics.nativeErrorSource}",
            "npu_s1_total_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.totalMs)}",
            "npu_s1_decode_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.decodeMs)}",
            "npu_s1_ttft_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.ttftMs)}",
            "npu_s1_output_tokens=${result.timing.outputTokens?.toString() ?: "n/a"}",
            "npu_s1_token_count_mode=${result.timing.tokenCountMode}",
            "npu_s1_tokens_per_second=${NpuStandardRouteS1Contract.formatTokensPerSecond(result.timing.tokensPerSecond)}",
            "run_decode_reached=${result.runDecodeReached}",
            "timeout=${result.timeout}",
            "fallback=${transientFallback ?: result.fallbackUsed}",
            "fresh_crash=${result.freshCrash}",
                ),
            )
        }.joinToString("\n")
    },
    input = input,
    result = result,
)

internal fun shouldShowNpuStandardRouteS1FailureDetails(
    result: NpuStandardRouteS1Result,
    transientFallback: String? = null,
): Boolean =
    !result.successCriteriaMet ||
        result.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL ||
        result.outputQualityCandidateReason.contains("special_token_leak") ||
        result.reason.contains("adapter_failure", ignoreCase = true) ||
        result.timeout ||
        result.freshCrash ||
        result.fallbackUsed ||
        transientFallback != null ||
        result.nativeDiagnostics.nativeErrorClass.isAvailableDevValue() ||
        result.nativeDiagnostics.nativeErrorMessage.isAvailableDevValue()

private fun npuS1ResidentDryRunDiagnosticLines(
    decision: LocalInferenceRoutingDryRunDecision,
): List<String> = listOf(
    "resident_dry_run_backend=${decision.selectedBackend.name}",
    "resident_dry_run_reason=${decision.reason}",
    "resident_dry_run_tokens=${decision.estimatedTotalTokens ?: "unknown"}",
    "resident_dry_run_long_context_threshold=${decision.longContextThreshold}",
    "resident_dry_run_fallback_backends=${decision.fallbackBackends.joinToString(",") { it.name }.ifBlank { "none" }}",
)

private fun extractNpuStandardRouteS1FailureHistoryLines(appHistoryText: String): List<String> {
    if (appHistoryText.isBlank()) return emptyList()
    val allowedKeys = setOf(
        "last_npu_s1_request_started_at_elapsed_realtime_ms",
        "last_npu_s1_request_finished_at_elapsed_realtime_ms",
        "last_npu_s1_prompt",
        "last_npu_s1_final_prompt_tail",
        "last_npu_s1_prompt_profile",
        "last_npu_s1_status",
        "last_npu_s1_reason",
        "last_npu_s1_exception_class",
        "last_npu_s1_exception_message",
        "last_npu_s1_native_stage",
        "last_npu_s1_native_stage_history",
        "last_successful_npu_s1_prompt",
        "last_failed_npu_s1_prompt",
        "successful_npu_s1_request_count",
        "last_engine_create_failure_at_elapsed_realtime_ms",
        "failure_after_successful_npu_s1_request_count",
        "failure_after_last_success_elapsed_ms",
        "engine_create_failure_count",
        "last_failure_was_engine_create_failed",
        "native_crash_risk_hint",
    )
    return appHistoryText
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
            val key = line.substringBefore("=", missingDelimiterValue = "")
            key in allowedKeys
        }
        .toList()
}

private fun extractNpuStandardRouteS1HistoryValue(
    appHistoryText: String,
    key: String,
): String {
    if (appHistoryText.isBlank()) return "unavailable"
    return appHistoryText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.substringBefore("=", missingDelimiterValue = "") == key }
        ?.substringAfter("=", missingDelimiterValue = "unavailable")
        ?.ifBlank { "unavailable" }
        ?: "unavailable"
}

private fun String.isAvailableDevValue(): Boolean =
    isNotBlank() && this != "unavailable" && this != "unknown" && this != "none"

internal fun estimateResidentRouterPromptTokens(input: String): Int? {
    if (input.isBlank()) return null
    // Conservative, tokenizer-free estimate for routing only. Japanese text is often
    // close to one token per code point; ASCII-heavy prompts are commonly lower.
    return input.codePointCount(0, input.length).coerceAtLeast(1)
}

internal fun npuRealPromptHash(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }.take(12)
}

internal fun npuRealPromptPreview(text: String): String =
    text.map { char -> if (char.isWhitespace()) ' ' else char }
        .joinToString(separator = "")
        .trim()
        .take(12)
        .ifBlank { "n/a" }

internal fun npuStandardRouteS1DevPreview(text: String): String {
    val normalized = text.map { char -> if (char.isWhitespace()) ' ' else char }
        .joinToString(separator = "")
        .trim()
    if (normalized.isBlank()) return "n/a"
    return if (normalized.length <= NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT) {
        normalized
    } else {
        normalized.take(NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT) + "..."
    }
}

internal fun npuStandardRouteS1EscapeCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")

internal fun npuStandardRouteMaxOutputTokensClamped(result: NpuStandardRouteS1Result): Boolean =
    result.selection.requestedMaxOutputTokens != result.selection.effectiveMaxOutputTokens

internal fun npuStandardRouteMaxOutputTokensClampReason(result: NpuStandardRouteS1Result): String =
    if (npuStandardRouteMaxOutputTokensClamped(result)) {
        NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NATIVE_LIMIT
    } else {
        NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE
    }

internal fun npuStandardRouteS1FailureExceptionClass(result: NpuStandardRouteS1Result): String =
    result.nativeDiagnostics.nativeErrorClass.takeUnless { it == "unavailable" || it.isBlank() }
        ?: inferNpuS1FailureExceptionClass(result.reason)

internal fun npuStandardRouteS1FailureExceptionMessage(result: NpuStandardRouteS1Result): String =
    result.nativeDiagnostics.nativeErrorMessage.takeUnless { it == "unavailable" || it.isBlank() }
        ?: result.reason

internal fun npuStandardRouteS1FailureStage(result: NpuStandardRouteS1Result): String =
    result.nativeDiagnostics.nativeErrorStage.takeUnless { it == "unavailable" || it.isBlank() }
        ?: inferNpuS1FailureStage(
            status = result.status,
            reason = result.reason,
            runDecodeReached = result.runDecodeReached,
            timeout = result.timeout,
        )

internal fun npuStandardRouteS1FailureKind(result: NpuStandardRouteS1Result): String =
    when {
        isNpuStandardRouteS1InvalidMaxOutputTokens(result) -> "invalid_max_output_tokens"
        isNpuStandardRouteS1EngineCreateFailed(result) -> NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED
        else -> "unavailable"
    }

internal fun npuStandardRouteS1FailureLayer(result: NpuStandardRouteS1Result): String =
    when {
        isNpuStandardRouteS1InvalidMaxOutputTokens(result) -> "native_input_validation"
        isNpuStandardRouteS1EngineCreateFailed(result) -> "litert_npu_compiled_model_executor"
        else -> "unavailable"
    }

internal fun npuStandardRouteS1FailureRecoveryHint(result: NpuStandardRouteS1Result): String =
    when {
        isNpuStandardRouteS1InvalidMaxOutputTokens(result) -> "clamp_max_output_tokens_to_512"
        isNpuStandardRouteS1EngineCreateFailed(result) -> "recreate_app_or_wait_before_retry"
        else -> "unavailable"
    }

internal fun npuStandardRouteS1NativeCrashRiskHint(result: NpuStandardRouteS1Result): String =
    if (isNpuStandardRouteS1EngineCreateFailed(result)) {
        "engine_create_failed_near_litert_compiled_model_dispatch_delegate_check_tombstone_dropbox"
    } else {
        "unavailable"
    }

internal fun isNpuStandardRouteS1EngineCreateFailed(result: NpuStandardRouteS1Result): Boolean {
    val exceptionClass = npuStandardRouteS1FailureExceptionClass(result)
    val hasLiteRtException = exceptionClass == "LiteRtLmJniException" ||
        result.reason.contains("LiteRtLmJniException", ignoreCase = true)
    if (!hasLiteRtException) return false
    return listOf(
        result.nativeDiagnostics.nativeErrorMessage,
        npuStandardRouteS1FailureExceptionMessage(result),
        result.reason,
    ).any { message ->
        message.contains("engine-create-failed", ignoreCase = true)
    }
}

internal fun isNpuStandardRouteS1InvalidMaxOutputTokens(result: NpuStandardRouteS1Result): Boolean =
    listOf(
        result.reason,
        result.nativeDiagnostics.nativeResultTail,
        result.nativeDiagnostics.nativeDiagTail,
        result.nativeDiagnostics.nativeErrorMessage,
    ).any { message ->
        message.contains("invalid_max_output_tokens", ignoreCase = true)
    }

internal const val NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED = "engine_create_failed"

private const val NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT = 32
