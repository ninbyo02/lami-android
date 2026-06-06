package io.github.ninbyo02.lami.ui.screens.home

import android.os.Process
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal const val NPU_S1_LOGCAT_TAG = "LamiNpuS1"
internal const val NPU_S1_REPEATED_RUN_STATUS_IDLE = "idle"
internal const val NPU_S1_REPEATED_RUN_STATUS_RUNNING = "running"
internal const val NPU_S1_REPEATED_RUN_STATUS_COMPLETED = "completed"
internal const val NPU_S1_REPEATED_RUN_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_REPEATED_RUN_STATUS_STOPPED = "stopped"
internal const val NPU_S1_REPEATED_RUN_DEFAULT_PROMPT = "こんにちは"
internal const val NPU_S1_REPEATED_RUN_DEFAULT_COUNT = 20
internal const val NPU_S1_REPEATED_RUN_ABNORMAL_TOTAL_MS = 30_000L
internal const val NPU_S1_REPEATED_RUN_RECREATE_NOTE =
    "s1_direct_runner_engine_session_dispose_not_exposed_uses_safe_holder_recreate_api"

internal enum class NpuS1RepeatedRunMode(
    val wireValue: String,
    val displayLabel: String,
    val recreateAfterRun: Boolean,
    val postRecreateDelayMs: Long,
) {
    REUSE(
        wireValue = "reuse",
        displayLabel = "Reuse",
        recreateAfterRun = false,
        postRecreateDelayMs = 0L,
    ),
    RECREATE(
        wireValue = "recreate",
        displayLabel = "Recreate",
        recreateAfterRun = true,
        postRecreateDelayMs = 0L,
    ),
    RECREATE_3S(
        wireValue = "recreate_3s",
        displayLabel = "Recreate + 3s",
        recreateAfterRun = true,
        postRecreateDelayMs = 3_000L,
    ),
}

internal data class NpuS1RepeatedRunLifecyclePlan(
    val mode: NpuS1RepeatedRunMode,
    val recreateAfterRun: Boolean,
    val postRecreateDelayMs: Long,
)

internal fun npuS1RepeatedRunLifecyclePlan(mode: NpuS1RepeatedRunMode): NpuS1RepeatedRunLifecyclePlan =
    NpuS1RepeatedRunLifecyclePlan(
        mode = mode,
        recreateAfterRun = mode.recreateAfterRun,
        postRecreateDelayMs = mode.postRecreateDelayMs,
    )

internal data class NpuS1ShortOutputTelemetry(
    val finishReason: String = "unavailable",
    val stopReason: String = "unavailable",
    val eosDetected: String = "unavailable",
    val rawFinishStatus: String = "not_exposed",
    val generationEndReasonSource: String = "not_exposed",
    val tokenizerOutputTokens: String = "unavailable",
    val tokenizerInputTokens: String = "unavailable",
    val tokenizerTotalTokens: String = "unavailable",
    val outputTokenCountSource: String,
    val promptTokenCountSource: String = "code_points",
    val finalInputLengthChars: Int,
    val finalInputTailChars: Int,
    val finalInputTailPreview: String,
    val modelReportedOutputTokens: String = "unavailable",
    val modelReportedInputTokens: String = "unavailable",
    val stopSequenceMatched: String = "unavailable",
    val stopSequenceValue: String = "unavailable",
    val maxOutputTokensReached: Boolean,
)

internal data class NpuS1RepeatedRunRecord(
    val runIndex: Int,
    val runCount: Int,
    val repeatedRunMode: NpuS1RepeatedRunMode,
    val prompt: String,
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
    val status: String,
    val reason: String,
    val finishReason: String,
    val stopReason: String,
    val eosDetected: String,
    val rawOutput: String,
    val sanitizedOutput: String,
    val qualityClassification: String,
    val totalMs: Long?,
    val decodeMs: Long?,
    val outputTokens: Int?,
    val tokenCountMode: String,
    val tokensPerSecond: Double?,
    val runDecodeReached: Boolean,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val safetyGuardTriggered: Boolean,
    val ttsRequested: Boolean = false,
    val ttsStarted: Boolean = false,
    val ttsCompleted: Boolean = false,
    val conversationHistorySaved: Boolean = false,
    val memoryBeforeTotalPssMb: Long?,
    val memoryBeforeNativeHeapPssMb: Long?,
    val memoryBeforeLowMemory: Boolean?,
    val memoryAfterTotalPssMb: Long?,
    val memoryAfterNativeHeapPssMb: Long?,
    val memoryAfterLowMemory: Boolean?,
    val memoryRecovery5sTotalPssMb: Long?,
    val memoryRecovery5sNativeHeapPssMb: Long?,
    val memoryRecovery5sNativeHeapAllocMb: Long?,
    val memoryRecovery5sSystemAvailableMemoryMb: Long?,
    val memoryRecovery5sLowMemory: Boolean?,
    val recreateRequestedAfterRun: Boolean = false,
    val recreateResultAfterRun: String = "not_requested",
    val recreateDelayAfterRunMs: Long = 0L,
    val finalInputLengthChars: Int,
    val finalInputTailPreview: String,
    val tokenizerInputTokens: String = "unavailable",
    val tokenizerOutputTokens: String = "unavailable",
    val outputTokenCountSource: String,
    val promptTokenCountSource: String = "code_points",
    val maxOutputTokensReached: Boolean,
    val stopSequenceMatched: String = "unavailable",
)

internal data class NpuS1RepeatedRunState(
    val status: String = NPU_S1_REPEATED_RUN_STATUS_IDLE,
    val startedAtMs: Long? = null,
    val prompt: String = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
    val requestedRunCount: Int = NPU_S1_REPEATED_RUN_DEFAULT_COUNT,
    val maxOutputTokens: Int = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
    val repeatedRunMode: NpuS1RepeatedRunMode = NpuS1RepeatedRunMode.REUSE,
    val records: List<NpuS1RepeatedRunRecord> = emptyList(),
    val stopped: Boolean = false,
    val stopReason: String = "none",
)

internal data class NpuS1RepeatedRunSummary(
    val status: String,
    val runCountRequested: Int,
    val runCountCompleted: Int,
    val prompt: String,
    val maxOutputTokens: Int,
    val repeatedRunMode: NpuS1RepeatedRunMode,
    val stopped: Boolean,
    val stopReason: String,
    val successCount: Int,
    val fallbackCount: Int,
    val freshCrashCount: Int,
    val timeoutCount: Int,
    val safetyGuardCount: Int,
    val minTotalMs: Long?,
    val maxTotalMs: Long?,
    val avgTotalMs: Long?,
    val minTokensPerSecond: Double?,
    val maxTokensPerSecond: Double?,
    val avgTokensPerSecond: Double?,
    val uniqueOutputsCount: Int,
    val mostCommonOutput: String,
    val allOutputsSame: Boolean,
    val first5sTotalPssMb: Long?,
    val last5sTotalPssMb: Long?,
    val peak5sTotalPssMb: Long?,
    val first5sNativeHeapPssMb: Long?,
    val last5sNativeHeapPssMb: Long?,
    val peak5sNativeHeapPssMb: Long?,
    val first5sSystemAvailableMemoryMb: Long?,
    val last5sSystemAvailableMemoryMb: Long?,
    val memoryGrowthSuspected: Boolean,
)

internal data class NpuS1LogcatContext(
    val repeatedRunMode: NpuS1RepeatedRunMode,
    val runIndex: Int,
    val runCountRequested: Int,
    val promptLength: Int,
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
)

internal object NpuS1LogcatDiagnostics {
    private val currentContext = AtomicReference<NpuS1LogcatContext?>(null)

    fun setContext(context: NpuS1LogcatContext) {
        if (!enabled) return
        currentContext.set(context)
    }

    fun clearContext(context: NpuS1LogcatContext) {
        if (!enabled) return
        currentContext.compareAndSet(context, null)
    }

    fun clearCurrentContext() {
        if (!enabled) return
        currentContext.set(null)
    }

    fun logRepeatedRunStart(
        mode: NpuS1RepeatedRunMode,
        runIndex: Int,
        runCountRequested: Int,
        promptLength: Int,
        requestedMaxOutputTokens: Int,
        effectiveMaxOutputTokens: Int,
    ) {
        if (!enabled) return
        runCatching {
            Log.i(
                NPU_S1_LOGCAT_TAG,
                listOf(
                    "event=repeated_run_start",
                    commonFields(mode = mode, runIndex = runIndex),
                    "run_count_requested=$runCountRequested",
                    "prompt_length=$promptLength",
                    "requested_max_output_tokens=$requestedMaxOutputTokens",
                    "effective_max_output_tokens=$effectiveMaxOutputTokens",
                ).joinToString(" "),
            )
        }
    }

    fun logRunFinished(record: NpuS1RepeatedRunRecord) {
        if (!enabled) return
        runCatching {
            Log.i(
                NPU_S1_LOGCAT_TAG,
                listOf(
                    "event=run_finished",
                    commonFields(mode = record.repeatedRunMode, runIndex = record.runIndex),
                    "status=${record.status}",
                    "reason=${record.reason}",
                    "run_decode_reached=${record.runDecodeReached}",
                    "fallback_used=${record.fallbackUsed}",
                    "timeout=${record.timeout}",
                    "fresh_crash=${record.freshCrash}",
                    "safety_guard_triggered=${record.safetyGuardTriggered}",
                    "total_ms=${formatNullableLong(record.totalMs)}",
                    "decode_ms=${formatNullableLong(record.decodeMs)}",
                    "output_tokens=${record.outputTokens?.toString() ?: "unavailable"}",
                    "tokens_per_second=${formatNullableDouble(record.tokensPerSecond)}",
                    "sanitized_output_length=${record.sanitizedOutput.length}",
                    "quality_classification=${record.qualityClassification}",
                    "memory_before_total_pss_mb=${formatNullableLong(record.memoryBeforeTotalPssMb)}",
                    "memory_after_total_pss_mb=${formatNullableLong(record.memoryAfterTotalPssMb)}",
                    "memory_recovery_5s_total_pss_mb=${formatNullableLong(record.memoryRecovery5sTotalPssMb)}",
                    "memory_before_native_heap_pss_mb=${formatNullableLong(record.memoryBeforeNativeHeapPssMb)}",
                    "memory_after_native_heap_pss_mb=${formatNullableLong(record.memoryAfterNativeHeapPssMb)}",
                    "memory_recovery_5s_native_heap_pss_mb=${formatNullableLong(record.memoryRecovery5sNativeHeapPssMb)}",
                    "system_available_memory_mb=${formatNullableLong(record.memoryRecovery5sSystemAvailableMemoryMb)}",
                    "low_memory=${record.memoryRecovery5sLowMemory?.toString() ?: "unavailable"}",
                ).joinToString(" "),
            )
        }
    }

    fun logAdapterFailure(
        reason: String,
        throwable: Throwable,
        memorySnapshot: MemorySnapshot? = null,
        promptLength: Int? = null,
        effectiveMaxOutputTokens: Int? = null,
    ) {
        if (!enabled) return
        val context = currentContext.get()
        runCatching {
            Log.e(
                NPU_S1_LOGCAT_TAG,
                listOf(
                    "event=adapter_failure",
                    commonFields(mode = context?.repeatedRunMode, runIndex = context?.runIndex),
                    "run_count_requested=${context?.runCountRequested?.toString() ?: "unavailable"}",
                    "reason=$reason",
                    "exception_class=${throwable.javaClass.name}",
                    "message=${throwable.message ?: "unavailable"}",
                    "prompt_length=${promptLength ?: context?.promptLength ?: "unavailable"}",
                    "effective_max_output_tokens=${effectiveMaxOutputTokens ?: context?.effectiveMaxOutputTokens ?: "unavailable"}",
                    "requested_max_output_tokens=${context?.requestedMaxOutputTokens?.toString() ?: "unavailable"}",
                    "memory_total_pss_mb=${formatNullableLong(memorySnapshot?.totalPssMb)}",
                    "memory_native_heap_pss_mb=${formatNullableLong(memorySnapshot?.nativeHeapPssMb)}",
                    "system_available_memory_mb=${formatNullableLong(memorySnapshot?.availableSystemMemoryMb)}",
                    "low_memory=${memorySnapshot?.lowMemory?.toString() ?: "unavailable"}",
                ).joinToString(" "),
                throwable,
            )
        }
    }

    fun logStopped(state: NpuS1RepeatedRunState) {
        if (!enabled) return
        val summary = buildNpuS1RepeatedRunSummary(state)
        runCatching {
            Log.w(
                NPU_S1_LOGCAT_TAG,
                listOf(
                    "event=repeated_run_stopped",
                    commonFields(mode = summary.repeatedRunMode, runIndex = state.records.lastOrNull()?.runIndex),
                    "stop_reason=${summary.stopReason}",
                    "run_count_completed=${summary.runCountCompleted}",
                    "success_count=${summary.successCount}",
                    "fallback_count=${summary.fallbackCount}",
                    "timeout_count=${summary.timeoutCount}",
                    "fresh_crash_count=${summary.freshCrashCount}",
                    "safety_guard_count=${summary.safetyGuardCount}",
                    "first_5s_total_pss_mb=${formatNullableLong(summary.first5sTotalPssMb)}",
                    "last_5s_total_pss_mb=${formatNullableLong(summary.last5sTotalPssMb)}",
                    "peak_5s_total_pss_mb=${formatNullableLong(summary.peak5sTotalPssMb)}",
                    "first_5s_native_heap_pss_mb=${formatNullableLong(summary.first5sNativeHeapPssMb)}",
                    "last_5s_native_heap_pss_mb=${formatNullableLong(summary.last5sNativeHeapPssMb)}",
                    "peak_5s_native_heap_pss_mb=${formatNullableLong(summary.peak5sNativeHeapPssMb)}",
                    "memory_growth_suspected=${summary.memoryGrowthSuspected}",
                ).joinToString(" "),
            )
        }
    }

    private val enabled: Boolean
        get() = BuildConfig.DEBUG

    private fun commonFields(
        mode: NpuS1RepeatedRunMode?,
        runIndex: Int?,
    ): String = listOf(
        "timestamp_ms=${System.currentTimeMillis()}",
        "pid=${runCatching { Process.myPid().toString() }.getOrDefault("unavailable")}",
        "thread=${Thread.currentThread().name.ifBlank { "unavailable" }}",
        "repeated_run_mode=${mode?.wireValue ?: "unavailable"}",
        "run_index=${runIndex?.toString() ?: "unavailable"}",
    ).joinToString(" ")
}

internal fun buildNpuS1ShortOutputTelemetry(
    input: String,
    result: NpuStandardRouteS1Result,
): NpuS1ShortOutputTelemetry {
    val outputTokenCount = result.timing.outputTokens
    val inputTail = input.takeLast(NPU_S1_FINAL_INPUT_TAIL_CHARS)
    return NpuS1ShortOutputTelemetry(
        outputTokenCountSource = if (result.timing.tokenCountMode == NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS) {
            "estimated_code_points_not_tokenizer"
        } else {
            result.timing.tokenCountMode
        },
        finalInputLengthChars = input.length,
        finalInputTailChars = inputTail.length,
        finalInputTailPreview = npuStandardRouteS1DevPreview(inputTail),
        maxOutputTokensReached = outputTokenCount != null &&
            outputTokenCount >= result.selection.effectiveMaxOutputTokens,
    )
}

internal fun formatNpuS1ShortOutputTelemetryForDev(
    telemetry: NpuS1ShortOutputTelemetry,
): String = listOf(
    "[DEV診断: NPU S1 short output telemetry]",
    "finish_reason=${telemetry.finishReason}",
    "stop_reason=${telemetry.stopReason}",
    "eos_detected=${telemetry.eosDetected}",
    "raw_finish_status=${telemetry.rawFinishStatus}",
    "generation_end_reason_source=${telemetry.generationEndReasonSource}",
    "tokenizer_output_tokens=${telemetry.tokenizerOutputTokens}",
    "tokenizer_input_tokens=${telemetry.tokenizerInputTokens}",
    "tokenizer_total_tokens=${telemetry.tokenizerTotalTokens}",
    "output_token_count_source=${telemetry.outputTokenCountSource}",
    "prompt_token_count_source=${telemetry.promptTokenCountSource}",
    "final_input_length_chars=${telemetry.finalInputLengthChars}",
    "final_input_tail_chars=${telemetry.finalInputTailChars}",
    "final_input_tail_preview=${telemetry.finalInputTailPreview}",
    "model_reported_output_tokens=${telemetry.modelReportedOutputTokens}",
    "model_reported_input_tokens=${telemetry.modelReportedInputTokens}",
    "stop_sequence_matched=${telemetry.stopSequenceMatched}",
    "stop_sequence_value=${telemetry.stopSequenceValue}",
    "max_output_tokens_reached=${telemetry.maxOutputTokensReached}",
).joinToString("\n")

internal fun appendNpuS1ShortOutputTelemetryForDev(
    text: String,
    input: String,
    result: NpuStandardRouteS1Result,
): String = listOf(
    text,
    formatNpuS1ShortOutputTelemetryForDev(buildNpuS1ShortOutputTelemetry(input, result)),
).filter { it.isNotBlank() }.joinToString("\n\n")

internal fun buildNpuS1RepeatedRunSummary(
    state: NpuS1RepeatedRunState,
): NpuS1RepeatedRunSummary {
    val records = state.records
    val totalMsValues = records.mapNotNull { it.totalMs }
    val tokenSpeedValues = records.mapNotNull { it.tokensPerSecond }
    val outputCounts = records
        .groupingBy { it.sanitizedOutput.ifBlank { it.rawOutput } }
        .eachCount()
    val mostCommonOutput = outputCounts.maxWithOrNull(
        compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
    )?.key.orEmpty()
    val first = records.firstOrNull()
    val last = records.lastOrNull()
    val memoryGrowthSuspected = first?.memoryRecovery5sNativeHeapPssMb != null &&
        last?.memoryRecovery5sNativeHeapPssMb != null &&
        last.memoryRecovery5sNativeHeapPssMb - first.memoryRecovery5sNativeHeapPssMb >= NPU_S1_MEMORY_GROWTH_SUSPECTED_MB
    return NpuS1RepeatedRunSummary(
        status = state.status,
        runCountRequested = state.requestedRunCount,
        runCountCompleted = records.size,
        prompt = state.prompt,
        maxOutputTokens = state.maxOutputTokens,
        repeatedRunMode = state.repeatedRunMode,
        stopped = state.stopped,
        stopReason = state.stopReason,
        successCount = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS },
        fallbackCount = records.count { it.fallbackUsed },
        freshCrashCount = records.count { it.freshCrash },
        timeoutCount = records.count { it.timeout },
        safetyGuardCount = records.count { it.safetyGuardTriggered },
        minTotalMs = totalMsValues.minOrNull(),
        maxTotalMs = totalMsValues.maxOrNull(),
        avgTotalMs = totalMsValues.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        minTokensPerSecond = tokenSpeedValues.minOrNull(),
        maxTokensPerSecond = tokenSpeedValues.maxOrNull(),
        avgTokensPerSecond = tokenSpeedValues.takeIf { it.isNotEmpty() }?.average(),
        uniqueOutputsCount = outputCounts.size,
        mostCommonOutput = mostCommonOutput,
        allOutputsSame = records.isNotEmpty() && outputCounts.size == 1,
        first5sTotalPssMb = first?.memoryRecovery5sTotalPssMb,
        last5sTotalPssMb = last?.memoryRecovery5sTotalPssMb,
        peak5sTotalPssMb = records.mapNotNull { it.memoryRecovery5sTotalPssMb }.maxOrNull(),
        first5sNativeHeapPssMb = first?.memoryRecovery5sNativeHeapPssMb,
        last5sNativeHeapPssMb = last?.memoryRecovery5sNativeHeapPssMb,
        peak5sNativeHeapPssMb = records.mapNotNull { it.memoryRecovery5sNativeHeapPssMb }.maxOrNull(),
        first5sSystemAvailableMemoryMb = first?.memoryRecovery5sSystemAvailableMemoryMb,
        last5sSystemAvailableMemoryMb = last?.memoryRecovery5sSystemAvailableMemoryMb,
        memoryGrowthSuspected = memoryGrowthSuspected,
    )
}

internal fun repeatedRunSafetyStopReason(record: NpuS1RepeatedRunRecord): String? = when {
    record.memoryBeforeLowMemory == true -> "low_memory_before"
    record.memoryAfterLowMemory == true -> "low_memory_after"
    record.memoryRecovery5sLowMemory == true -> "low_memory"
    record.fallbackUsed -> "fallback_detected"
    record.freshCrash -> "fresh_crash_detected"
    record.timeout -> "timeout"
    record.safetyGuardTriggered -> "safety_guard_triggered"
    record.recreateResultAfterRun.startsWith("failed") -> "engine_recreate_failure"
    record.reason.startsWith("adapter_failure") -> "adapter_failure"
    record.reason.contains("LiteRtLmJniException") -> "adapter_failure"
    !record.runDecodeReached -> "run_decode_reached_false"
    record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS -> "status_${record.status}"
    record.totalMs != null && record.totalMs > NPU_S1_REPEATED_RUN_ABNORMAL_TOTAL_MS -> "run_too_long"
    else -> null
}

internal fun repeatedRunMemoryThresholdStopReason(snapshot: MemorySnapshot): String? {
    val available = snapshot.availableSystemMemoryMb ?: return null
    val threshold = snapshot.systemMemoryThresholdMb ?: return null
    return if (available <= threshold * 2L) {
        "system_memory_threshold_near"
    } else {
        null
    }
}

internal fun formatNpuS1RepeatedRunDiagnosticsForDev(
    state: NpuS1RepeatedRunState,
): String {
    val summary = buildNpuS1RepeatedRunSummary(state)
    return buildString {
        appendLine("[DEV診断: NPU S1 repeated run summary]")
        appendLine("repeated_run_status=${summary.status}")
        appendLine("run_count_requested=${summary.runCountRequested}")
        appendLine("run_count_completed=${summary.runCountCompleted}")
        appendLine("prompt=${summary.prompt}")
        appendLine("max_output_tokens=${summary.maxOutputTokens}")
        appendLine("repeated_run_mode=${summary.repeatedRunMode.wireValue}")
        appendLine("recreate_api_note=$NPU_S1_REPEATED_RUN_RECREATE_NOTE")
        appendLine("stopped=${summary.stopped}")
        appendLine("stop_reason=${summary.stopReason}")
        appendLine("success_count=${summary.successCount}")
        appendLine("fallback_count=${summary.fallbackCount}")
        appendLine("fresh_crash_count=${summary.freshCrashCount}")
        appendLine("timeout_count=${summary.timeoutCount}")
        appendLine("safety_guard_count=${summary.safetyGuardCount}")
        appendLine("min_total_ms=${formatNullableLong(summary.minTotalMs)}")
        appendLine("max_total_ms=${formatNullableLong(summary.maxTotalMs)}")
        appendLine("avg_total_ms=${formatNullableLong(summary.avgTotalMs)}")
        appendLine("min_tokens_per_second=${formatNullableDouble(summary.minTokensPerSecond)}")
        appendLine("max_tokens_per_second=${formatNullableDouble(summary.maxTokensPerSecond)}")
        appendLine("avg_tokens_per_second=${formatNullableDouble(summary.avgTokensPerSecond)}")
        appendLine("unique_outputs_count=${summary.uniqueOutputsCount}")
        appendLine("most_common_output=${summary.mostCommonOutput.ifBlank { "unavailable" }}")
        appendLine("all_outputs_same=${summary.allOutputsSame}")
        appendLine("first_5s_total_pss_mb=${formatNullableLong(summary.first5sTotalPssMb)}")
        appendLine("last_5s_total_pss_mb=${formatNullableLong(summary.last5sTotalPssMb)}")
        appendLine("peak_5s_total_pss_mb=${formatNullableLong(summary.peak5sTotalPssMb)}")
        appendLine("first_5s_native_heap_pss_mb=${formatNullableLong(summary.first5sNativeHeapPssMb)}")
        appendLine("last_5s_native_heap_pss_mb=${formatNullableLong(summary.last5sNativeHeapPssMb)}")
        appendLine("peak_5s_native_heap_pss_mb=${formatNullableLong(summary.peak5sNativeHeapPssMb)}")
        appendLine("first_5s_system_available_memory_mb=${formatNullableLong(summary.first5sSystemAvailableMemoryMb)}")
        appendLine("last_5s_system_available_memory_mb=${formatNullableLong(summary.last5sSystemAvailableMemoryMb)}")
        appendLine("memory_growth_suspected=${summary.memoryGrowthSuspected}")
        if (state.records.isNotEmpty()) {
            appendLine("[DEV診断: NPU S1 repeated run details]")
            state.records.forEach { record ->
                appendLine("run_index=${record.runIndex}")
                appendLine("run_count=${record.runCount}")
                appendLine("repeated_run_mode=${record.repeatedRunMode.wireValue}")
                appendLine("prompt=${record.prompt}")
                appendLine("requested_max_output_tokens=${record.requestedMaxOutputTokens}")
                appendLine("effective_max_output_tokens=${record.effectiveMaxOutputTokens}")
                appendLine("status=${record.status}")
                appendLine("reason=${record.reason}")
                appendLine("finish_reason=${record.finishReason}")
                appendLine("stop_reason=${record.stopReason}")
                appendLine("eos_detected=${record.eosDetected}")
                appendLine("raw_output=${record.rawOutput}")
                appendLine("sanitized_output=${record.sanitizedOutput}")
                appendLine("quality_classification=${record.qualityClassification}")
                appendLine("npu_s1_total_ms=${formatNullableLong(record.totalMs)}")
                appendLine("npu_s1_decode_ms=${formatNullableLong(record.decodeMs)}")
                appendLine("npu_s1_output_tokens=${record.outputTokens?.toString() ?: "unavailable"}")
                appendLine("npu_s1_token_count_mode=${record.tokenCountMode}")
                appendLine("npu_s1_tokens_per_second=${formatNullableDouble(record.tokensPerSecond)}")
                appendLine("run_decode_reached=${record.runDecodeReached}")
                appendLine("fallback_used=${record.fallbackUsed}")
                appendLine("timeout=${record.timeout}")
                appendLine("fresh_crash=${record.freshCrash}")
                appendLine("safety_guard_triggered=${record.safetyGuardTriggered}")
                appendLine("tts_requested=${record.ttsRequested}")
                appendLine("tts_started=${record.ttsStarted}")
                appendLine("tts_completed=${record.ttsCompleted}")
                appendLine("conversation_history_saved=${record.conversationHistorySaved}")
                appendLine("memory_before_total_pss_mb=${formatNullableLong(record.memoryBeforeTotalPssMb)}")
                appendLine("memory_before_native_heap_pss_mb=${formatNullableLong(record.memoryBeforeNativeHeapPssMb)}")
                appendLine("memory_before_low_memory=${record.memoryBeforeLowMemory?.toString() ?: "unavailable"}")
                appendLine("memory_after_total_pss_mb=${formatNullableLong(record.memoryAfterTotalPssMb)}")
                appendLine("memory_after_native_heap_pss_mb=${formatNullableLong(record.memoryAfterNativeHeapPssMb)}")
                appendLine("memory_after_low_memory=${record.memoryAfterLowMemory?.toString() ?: "unavailable"}")
                appendLine("memory_recovery_5s_total_pss_mb=${formatNullableLong(record.memoryRecovery5sTotalPssMb)}")
                appendLine("memory_recovery_5s_native_heap_pss_mb=${formatNullableLong(record.memoryRecovery5sNativeHeapPssMb)}")
                appendLine("memory_recovery_5s_native_heap_alloc_mb=${formatNullableLong(record.memoryRecovery5sNativeHeapAllocMb)}")
                appendLine("memory_recovery_5s_system_available_memory_mb=${formatNullableLong(record.memoryRecovery5sSystemAvailableMemoryMb)}")
                appendLine("memory_recovery_5s_low_memory=${record.memoryRecovery5sLowMemory?.toString() ?: "unavailable"}")
                appendLine("recreate_requested_after_run=${record.recreateRequestedAfterRun}")
                appendLine("recreate_result_after_run=${record.recreateResultAfterRun}")
                appendLine("recreate_delay_after_run_ms=${record.recreateDelayAfterRunMs}")
                appendLine("final_input_length_chars=${record.finalInputLengthChars}")
                appendLine("final_input_tail_preview=${record.finalInputTailPreview}")
                appendLine("tokenizer_input_tokens=${record.tokenizerInputTokens}")
                appendLine("tokenizer_output_tokens=${record.tokenizerOutputTokens}")
                appendLine("output_token_count_source=${record.outputTokenCountSource}")
                appendLine("prompt_token_count_source=${record.promptTokenCountSource}")
                appendLine("max_output_tokens_reached=${record.maxOutputTokensReached}")
                appendLine("stop_sequence_matched=${record.stopSequenceMatched}")
            }
        }
    }.trimEnd()
}

internal fun appendNpuS1RepeatedRunDiagnosticsForDev(
    text: String,
    state: NpuS1RepeatedRunState,
): String = listOf(
    text,
    formatNpuS1RepeatedRunDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

private fun formatNullableLong(value: Long?): String = value?.toString() ?: "unavailable"

private fun formatNullableDouble(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private const val NPU_S1_FINAL_INPUT_TAIL_CHARS = 80
private const val NPU_S1_MEMORY_GROWTH_SUSPECTED_MB = 32L
