package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig
import java.util.Locale

internal const val NPU_NON_STREAMING_REPEATED_STABILITY_TEST_NAME =
    "NPU Non-Streaming Repeated Stability Test"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_ROUTE_TYPE =
    "dev_only_one_turn_conversation_non_streaming_repeat"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuNonStreamingRepeatedStabilityDevProbe"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_IDLE = "idle"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_RUNNING = "running"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_COMPLETED = "completed"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_CANCELLED = "cancelled"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_STOPPED = "stopped"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_FAILED = "failed"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_RUN_COUNT = 10
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_MAX_OUTPUT_TOKENS = 32
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_RUN_LABEL =
    "Run Non-Streaming Repeat Test"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_COPY_SUMMARY_LABEL =
    "Copy Non-Streaming Repeat Summary"
internal const val NPU_NON_STREAMING_REPEATED_STABILITY_COPY_FULL_DUMP_LABEL =
    "Copy Non-Streaming Repeat Full Dump"

internal val NPU_NON_STREAMING_REPEATED_STABILITY_PROMPTS = listOf(
    "こんにちは",
    "あなたは誰ですか",
    "Pythonとは何ですか",
    "Androidについて一言で説明して",
    "日本語で短く答えてください",
    "今日の挨拶をしてください",
    "ありがとう",
    "またね",
    "1+1は？",
    "短い俳句を作って",
)

internal data class NpuNonStreamingRepeatedStabilityRecord(
    val runIndex: Int,
    val prompt: String,
    val status: String,
    val reason: String,
    val runDecodeReached: Boolean,
    val backendEvidence: String,
    val qualityClassification: String,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val totalMs: Long?,
    val decodeMs: Long?,
    val rawOutput: String,
    val sanitizedOutput: String,
    val nativeStage: String,
    val nativeStageHistory: String,
    val nativeErrorStage: String,
    val nativeErrorClass: String,
    val nativeDiagTail: String,
)

internal data class NpuNonStreamingRepeatedStabilityState(
    val status: String = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_IDLE,
    val reason: String = "not_run",
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val runCountRequested: Int = NPU_NON_STREAMING_REPEATED_STABILITY_RUN_COUNT,
    val selectedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val requestedBackend: String = NPU_S1_BACKEND_NPU,
    val effectiveBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val routeFamily: String = NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
    val blockedReason: String = "none",
    val records: List<NpuNonStreamingRepeatedStabilityRecord> = emptyList(),
    val stopped: Boolean = false,
    val stopReason: String = "none",
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
)

internal interface NpuNonStreamingRepeatedStabilityProbeRunner {
    suspend fun run(
        onUpdate: (NpuNonStreamingRepeatedStabilityState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuNonStreamingRepeatedStabilityState
}

internal fun createNpuNonStreamingRepeatedStabilityProbeRunner(
    context: Context,
): NpuNonStreamingRepeatedStabilityProbeRunner? =
    runCatching {
        Class.forName(NPU_NON_STREAMING_REPEATED_STABILITY_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuNonStreamingRepeatedStabilityProbeRunner
    }.getOrNull()

internal fun formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(
    state: NpuNonStreamingRepeatedStabilityState,
): String = formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(state, includeDetails = true)

internal fun buildNpuNonStreamingRepeatedStabilitySummaryCopyText(
    state: NpuNonStreamingRepeatedStabilityState,
): String = formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(state, includeDetails = false)

internal fun buildNpuNonStreamingRepeatedStabilityFullDumpCopyText(
    state: NpuNonStreamingRepeatedStabilityState,
): String = formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(state, includeDetails = true)

private fun formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(
    state: NpuNonStreamingRepeatedStabilityState,
    includeDetails: Boolean,
): String {
    val records = state.records.sortedBy { it.runIndex }
    val completed = records.size
    val successCount = records.count { it.status == "success" }
    val failureCount = completed - successCount
    val fallbackUsedCount = records.count { it.fallbackUsed }
    val timeoutCount = records.count { it.timeout }
    val freshCrashCount = records.count { it.freshCrash }
    val runDecodeReachedCount = records.count { it.runDecodeReached }
    val firstFailure = records.firstOrNull { it.status != "success" }
    val engineCreateFailureDetected = records.any { it.engineCreateFailureDetected() }
    val suspectedFailureArea = when {
        engineCreateFailureDetected -> "engine_create"
        firstFailure != null -> firstFailure.suspectedFailureArea()
        else -> "unavailable"
    }
    val repeatedRecreateSuspected = engineCreateFailureDetected && completed > 1
    val trueEngineReuseInvestigationRecommended = repeatedRecreateSuspected
    val restartAppRecommended = records.any { it.freshCrash } ||
        firstFailure?.reason.orEmpty().contains("native", ignoreCase = true)
    val guardRecommendation = when {
        restartAppRecommended -> "restart_app_before_next_npu_probe"
        trueEngineReuseInvestigationRecommended -> "investigate_true_engine_reuse_with_staged_probe"
        failureCount > 0 -> "review_first_failure_before_true_engine_reuse"
        else -> "none"
    }
    return buildString {
        appendLine("[DEV診断: NPU Non-Streaming Repeated Stability summary]")
        appendLine("test_name=$NPU_NON_STREAMING_REPEATED_STABILITY_TEST_NAME")
        appendLine("route_type=$NPU_NON_STREAMING_REPEATED_STABILITY_ROUTE_TYPE")
        appendLine("status=${state.status}")
        appendLine("reason=${state.reason}")
        appendLine("streaming=false")
        appendLine("pseudo_streaming=false")
        appendLine("tts=false")
        appendLine("db=false")
        appendLine("markdown=false")
        appendLine("fallback_allowed=false")
        appendLine("run_count_requested=${state.runCountRequested}")
        appendLine("run_count_completed=$completed")
        appendLine("success_count=$successCount")
        appendLine("failure_count=$failureCount")
        appendLine("success_rate=${formatRate(successCount, completed)}")
        appendLine("run_decode_reached_count=$runDecodeReachedCount")
        appendLine("run_decode_reached_rate=${formatRate(runDecodeReachedCount, completed)}")
        appendLine("backend_evidence_summary=${summarizeNonStreamingRepeatValues(records.map { it.backendEvidence }, state.backendEvidence)}")
        appendLine("quality_classification_summary=${summarizeNonStreamingRepeatValues(records.map { it.qualityClassification }, "unavailable")}")
        appendLine("fallback_used_count=$fallbackUsedCount")
        appendLine("fallback_rate=${formatRate(fallbackUsedCount, completed)}")
        appendLine("timeout_count=$timeoutCount")
        appendLine("timeout_rate=${formatRate(timeoutCount, completed)}")
        appendLine("fresh_crash_count=$freshCrashCount")
        appendLine("fresh_crash_rate=${formatRate(freshCrashCount, completed)}")
        appendLine("average_total_ms=${formatLong(records.mapNotNull { it.totalMs }.averageLongOrNull())}")
        appendLine("average_decode_ms=${formatLong(records.mapNotNull { it.decodeMs }.averageLongOrNull())}")
        appendLine("first_failure_run_index=${firstFailure?.runIndex?.toString() ?: "unavailable"}")
        appendLine("first_failure_stage=${firstFailure?.nativeErrorStage?.ifUnavailable { firstFailure.nativeStage } ?: "unavailable"}")
        appendLine("first_failure_reason=${firstFailure?.reason ?: "unavailable"}")
        appendLine("first_failure_exception_class=${firstFailure?.nativeErrorClass ?: "unavailable"}")
        appendLine("first_failure_native_diag_tail=${firstFailure?.nativeDiagTail ?: "unavailable"}")
        appendLine("engine_create_failure_detected=$engineCreateFailureDetected")
        appendLine("suspected_failure_area=$suspectedFailureArea")
        appendLine("repeated_recreate_suspected=$repeatedRecreateSuspected")
        appendLine("streaming_ruled_out=true")
        appendLine("pseudo_streaming_ruled_out=true")
        appendLine("ui_side_effects_ruled_out=true")
        appendLine("true_engine_reuse_investigation_recommended=$trueEngineReuseInvestigationRecommended")
        appendLine("true_engine_probe_blocked_for_startup_safety=${trueEngineProbeBlockedForStartupSafety()}")
        appendLine("restart_app_recommended=$restartAppRecommended")
        appendLine("guard_recommendation=$guardRecommendation")
        appendLine("true_engine_probe_status=${trueEngineProbeStatusForNonStreamingRepeat()}")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("selected_backend=${state.selectedBackend}")
        appendLine("requested_backend=${state.requestedBackend}")
        appendLine("effective_backend=${state.effectiveBackend}")
        appendLine("backend_evidence=${state.backendEvidence}")
        appendLine("route_family=${state.routeFamily}")
        appendLine("blocked_reason=${state.blockedReason}")
        appendLine("stopped=${state.stopped}")
        appendLine("stop_reason=${state.stopReason}")
        appendLine("throwable_class=${state.throwableClass}")
        appendLine("throwable_message=${state.throwableMessage}")
        appendLine("started_at_ms=${formatLong(state.startedAtMs)}")
        appendLine("finished_at_ms=${formatLong(state.finishedAtMs)}")
        if (includeDetails) {
            records.forEach { record ->
                appendLine("[DEV診断: NPU Non-Streaming Repeated Stability detail]")
                appendLine("run_index=${record.runIndex}")
                appendLine("prompt=${record.prompt}")
                appendLine("status=${record.status}")
                appendLine("reason=${record.reason}")
                appendLine("run_decode_reached=${record.runDecodeReached}")
                appendLine("backend_evidence=${record.backendEvidence}")
                appendLine("quality_classification=${record.qualityClassification}")
                appendLine("fallback_used=${record.fallbackUsed}")
                appendLine("timeout=${record.timeout}")
                appendLine("fresh_crash=${record.freshCrash}")
                appendLine("total_ms=${formatLong(record.totalMs)}")
                appendLine("decode_ms=${formatLong(record.decodeMs)}")
                appendLine("raw_output_first_200_chars=${escapeNonStreamingRepeatValue(record.rawOutput.take(200))}")
                appendLine("sanitized_output=${escapeNonStreamingRepeatValue(record.sanitizedOutput)}")
                appendLine("native_stage=${record.nativeStage}")
                appendLine("native_stage_history=${record.nativeStageHistory}")
                appendLine("native_error_stage=${record.nativeErrorStage}")
                appendLine("native_error_class=${record.nativeErrorClass}")
            }
        }
    }.trimEnd()
}

private fun trueEngineProbeStatusForNonStreamingRepeat(): String =
    if (BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED) {
        "available"
    } else {
        "disabled_or_blocked"
    }

private fun trueEngineProbeBlockedForStartupSafety(): Boolean =
    !BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED

private fun NpuNonStreamingRepeatedStabilityRecord.engineCreateFailureDetected(): Boolean {
    val haystack = listOf(
        reason,
        nativeStage,
        nativeStageHistory,
        nativeErrorStage,
        nativeErrorClass,
        nativeDiagTail,
    ).joinToString("\n")
    return haystack.contains("engine-create-failed", ignoreCase = true) ||
        haystack.contains("EngineFactory::CreateDefault", ignoreCase = true) ||
        haystack.contains("compiled_model", ignoreCase = true)
}

private fun NpuNonStreamingRepeatedStabilityRecord.suspectedFailureArea(): String =
    when {
        engineCreateFailureDetected() -> "engine_create"
        nativeErrorStage.isNotBlank() && nativeErrorStage != "unavailable" -> nativeErrorStage
        nativeStage.isNotBlank() && nativeStage != "unavailable" -> nativeStage
        else -> "unavailable"
    }

private fun formatRate(numerator: Int, denominator: Int): String =
    if (denominator <= 0) {
        "unavailable"
    } else {
        String.format(Locale.US, "%.2f", numerator.toDouble() / denominator.toDouble())
    }

private fun formatLong(value: Long?): String = value?.toString() ?: "unavailable"

private fun List<Long>.averageLongOrNull(): Long? =
    if (isEmpty()) null else average().toLong()

private fun summarizeNonStreamingRepeatValues(
    values: List<String>,
    fallback: String,
): String {
    val normalized = values
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "unavailable" && it != "-" }
    val source = normalized.ifEmpty {
        listOf(fallback.trim()).filter { it.isNotBlank() && it != "unavailable" && it != "-" }
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

private fun escapeNonStreamingRepeatValue(value: String): String =
    value.replace("\\", "\\\\").replace("\n", "\\n")

private fun String.ifUnavailable(fallback: () -> String): String =
    if (isBlank() || this == "unavailable") fallback() else this
