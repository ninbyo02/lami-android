package io.github.ninbyo02.lami.ui.screens.home

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
): String {
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    val lines = mutableListOf(
        "max_output_tokens=$maxOutputTokens",
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
        "failure_exception_class=${npuStandardRouteS1FailureExceptionClass(result)}",
        "failure_exception_message=${npuStandardRouteS1DevPreview(npuStandardRouteS1FailureExceptionMessage(result))}",
        "failure_stage=${npuStandardRouteS1FailureStage(result)}",
        "native_stage=${result.nativeDiagnostics.nativeStage}",
        "native_stage_history=${result.nativeDiagnostics.nativeStageHistory}",
        "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
        "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
        "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
        "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
        "npu_s1_total_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.totalMs)}",
        "npu_s1_decode_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.decodeMs)}",
        "npu_s1_ttft_ms=${NpuStandardRouteS1Contract.formatTimingMs(result.timing.ttftMs)}",
        "npu_s1_output_tokens=${result.timing.outputTokens?.toString() ?: "n/a"}",
        "npu_s1_token_count_mode=${result.timing.tokenCountMode}",
        "npu_s1_tokens_per_second=${NpuStandardRouteS1Contract.formatTokensPerSecond(result.timing.tokensPerSecond)}",
        "run_decode_reached=${result.runDecodeReached}",
        "timeout=${result.timeout}",
    )
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
) : String {
    val sections = mutableListOf(
        buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = input,
            result = result,
        ),
    )
    buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
        input = input,
        result = result,
        transientFallback = transientFallback,
    )?.let { sections += it }
    return sections.joinToString("\n\n")
}

internal fun buildNpuStandardRouteS1DiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
    transientFallback: String? = null,
    appHistoryText: String,
): String {
    val sections = mutableListOf(
        buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = input,
            result = result,
        ),
    )
    buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
        input = input,
        result = result,
        transientFallback = transientFallback,
        appHistoryText = appHistoryText,
    )?.let { sections += it }
    return sections.joinToString("\n\n")
}

internal fun buildNpuStandardRouteS1CompactDiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
): String {
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    return listOf(
        "[DEV診断: NPU S1 compact]",
        "input_prompt=${npuStandardRouteS1EscapeCopyValue(input)}",
        "final_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText.takeLast(200))}",
        "selected_prompt_profile=${promptRewrite.selectedPromptProfile}",
        "prompt_wrapper_used=${promptRewrite.promptWrapperUsed}",
        "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
        "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
        "raw_output=${npuStandardRouteS1EscapeCopyValue(result.rawOutput)}",
        "sanitized_output=${npuStandardRouteS1EscapeCopyValue(result.sanitizedOutput)}",
        "output_quality_candidate_status=${result.outputQualityCandidateStatus}",
        "output_quality_candidate_reason=${result.outputQualityCandidateReason}",
        "output_quality_candidate_prepared_output=${npuStandardRouteS1EscapeCopyValue(result.preparedOutput)}",
        "status=${result.status}",
        "reason=${result.reason}",
        "quality_classification=${result.qualityClassification}",
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
        "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
        "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
        "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
        "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
        "native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}",
    ).joinToString("\n")
}

internal fun buildNpuStandardRouteS1FailureDetailsDiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    transientFallback: String? = null,
    appHistoryText: String = "",
): String? {
    if (!shouldShowNpuStandardRouteS1FailureDetails(result, transientFallback)) return null
    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
    return buildList {
        add("[DEV診断: NPU S1 failure details]")
        add("failure_stage=${npuStandardRouteS1FailureStage(result)}")
        add("native_error_class=${result.nativeDiagnostics.nativeErrorClass}")
        add("native_error_message=${npuStandardRouteS1EscapeCopyValue(result.nativeDiagnostics.nativeErrorMessage)}")
        add("native_error_stage=${result.nativeDiagnostics.nativeErrorStage}")
        add("native_error_source=${result.nativeDiagnostics.nativeErrorSource}")
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
): String = appendNpuS1ShortOutputTelemetryForDev(
    text = run {
        val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(input)
        listOf(
            "[DEV診断: NPU S1 full dump]",
            "input_prompt=${npuStandardRouteS1EscapeCopyValue(input)}",
            "final_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText)}",
            "final_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.finalPromptText.takeLast(200))}",
            "selected_prompt_profile=${promptRewrite.selectedPromptProfile}",
            "prompt_wrapper_used=${promptRewrite.promptWrapperUsed}",
            "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
            "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
            "rewritten_prompt_text=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText)}",
            "rewritten_prompt_tail=${npuStandardRouteS1EscapeCopyValue(promptRewrite.rewrittenPromptText.takeLast(200))}",
            "max_output_tokens=$maxOutputTokens",
            "selected_model_name=${npuStandardRouteS1EscapeCopyValue(result.selectedModelName.ifBlank { "unknown" })}",
            "selected_model_file=${npuStandardRouteS1EscapeCopyValue(result.selectedModelFile.ifBlank { "unknown" })}",
            "npu_model_eligible=${result.npuModelEligible ?: "unknown"}",
            "raw_output=${npuStandardRouteS1EscapeCopyValue(result.rawOutput)}",
            "sanitized_output=${npuStandardRouteS1EscapeCopyValue(result.sanitizedOutput)}",
            "status=${result.status}",
            "reason=${result.reason}",
            "quality_classification=${result.qualityClassification}",
            "output_quality_candidate_status=${result.outputQualityCandidateStatus}",
            "output_quality_candidate_reason=${result.outputQualityCandidateReason}",
            "output_quality_candidate_prepared_output=${npuStandardRouteS1EscapeCopyValue(result.preparedOutput)}",
            "failure_exception_class=${npuStandardRouteS1FailureExceptionClass(result)}",
            "failure_exception_message=${npuStandardRouteS1EscapeCopyValue(npuStandardRouteS1FailureExceptionMessage(result))}",
            "failure_stage=${npuStandardRouteS1FailureStage(result)}",
            "native_stage=${result.nativeDiagnostics.nativeStage}",
            "native_stage_history=${result.nativeDiagnostics.nativeStageHistory}",
            "native_call_reached=${result.nativeDiagnostics.nativeCallReached}",
            "native_call_returned=${result.nativeDiagnostics.nativeCallReturned}",
            "native_decode_started=${result.nativeDiagnostics.nativeDecodeStarted}",
            "native_decode_finished=${result.nativeDiagnostics.nativeDecodeFinished}",
            "native_cleanup_reached=${result.nativeDiagnostics.nativeCleanupReached}",
            "native_session_destroy_reached=${result.nativeDiagnostics.nativeSessionDestroyReached}",
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
        ).joinToString("\n")
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

private fun String.isAvailableDevValue(): Boolean =
    isNotBlank() && this != "unavailable" && this != "unknown" && this != "none"

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

private const val NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT = 32
