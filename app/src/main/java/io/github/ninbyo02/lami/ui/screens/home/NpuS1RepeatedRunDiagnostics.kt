package io.github.ninbyo02.lami.ui.screens.home

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.InferenceBackendSelection
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val NPU_S1_LOGCAT_TAG = "LamiNpuS1"
internal const val NPU_S1_REPEATED_RUN_STATUS_IDLE = "idle"
internal const val NPU_S1_REPEATED_RUN_STATUS_RUNNING = "running"
internal const val NPU_S1_REPEATED_RUN_STATUS_COMPLETED = "completed"
internal const val NPU_S1_REPEATED_RUN_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_REPEATED_RUN_STATUS_STOPPED = "stopped"
internal const val NPU_S1_REPEATED_RUN_DEFAULT_PROMPT = "こんにちは"
internal const val NPU_S1_REPEATED_RUN_DEFAULT_COUNT = 20
internal const val NPU_S1_REPEATED_RUN_SAFE_COUNT = 20
internal const val NPU_S1_REPEATED_RUN_SAFE_WAIT_MS = 500L
internal val NPU_S1_REPEATED_RUN_SAFE_MODE = NpuS1RepeatedRunMode.RECREATE
internal val NPU_S1_REPEATED_RUN_PROMPT_OPTIONS = listOf(
    "1+1は？",
    "１＋１は？",
    "こんにちは",
    "あなたは誰ですか？",
)
internal val NPU_S1_REPEATED_RUN_COUNT_OPTIONS = listOf(20, 50, 100)
internal val NPU_S1_REPEATED_RUN_WAIT_MS_OPTIONS = listOf(0L, 500L, 2_000L)
internal val NPU_S1_REPEATED_RUN_SAFE_COUNT_OPTIONS = listOf(NPU_S1_REPEATED_RUN_SAFE_COUNT)
internal val NPU_S1_REPEATED_RUN_SAFE_WAIT_MS_OPTIONS = listOf(NPU_S1_REPEATED_RUN_SAFE_WAIT_MS, 2_000L)
internal val NPU_S1_REPEATED_RUN_SAFE_MODE_OPTIONS = listOf(NPU_S1_REPEATED_RUN_SAFE_MODE)
internal const val NPU_S1_REPEATED_RUN_ABNORMAL_TOTAL_MS = 30_000L
internal const val NPU_S1_REPEATED_RUN_RECREATE_NOTE =
    "s1_direct_runner_engine_session_dispose_not_exposed_uses_safe_holder_recreate_api"
internal const val NPU_S1_FAILURE_STAGE_ENGINE_REQUEST = "engine_request"
internal const val NPU_S1_FAILURE_STAGE_ENGINE_CREATE = "engine_create"
internal const val NPU_S1_FAILURE_STAGE_DECODE = "decode"
internal const val NPU_S1_FAILURE_STAGE_ADAPTER = "adapter"
internal const val NPU_S1_FAILURE_STAGE_UNKNOWN = "unknown"
internal const val NPU_S1_TOMBSTONE_COMPARE_HINT =
    "compare_first_failure_wall_time_ms_with_adb_shell_ls_lt_data_tombstones_and_dumpsys_dropbox"
internal const val NPU_S1_COUNTER_NOTE =
    "counters_are_app_layer_attempts_engine_create_may_be_unavailable_if_not_exposed"
internal const val NPU_S1_ENGINE_CREATE_VISIBILITY = "not_exposed"
internal const val NPU_S1_ENGINE_CREATE_SOURCE = "not_exposed"
internal const val NPU_S1_BACKEND_NPU = "NPU"
internal const val NPU_S1_BACKEND_AUTOMATIC = "Automatic"
internal const val NPU_S1_BACKEND_NPU_S1 = "NPU_S1"
internal const val NPU_S1_BACKEND_NPU_S2 = "NPU_S2"
internal const val NPU_S1_BACKEND_NPU_S3 = "NPU_S3"
internal const val NPU_S1_BACKEND_NPU_S4 = "NPU_S4"
internal const val NPU_S1_BACKEND_NPU_S5 = "NPU_S5"
internal const val NPU_S1_BACKEND_GPU = "GPU"
internal const val NPU_S1_BACKEND_CPU = "CPU"
internal const val NPU_S1_BACKEND_UNAVAILABLE = "unavailable"
internal const val NPU_S1_ROUTE_FAMILY_NPU_S1 = "npu_s1"
internal const val NPU_S1_ROUTE_FAMILY_NPU_S2 = "npu_s2"
internal const val NPU_S1_ROUTE_FAMILY_NPU_S3 = "npu_s3"
internal const val NPU_S1_ROUTE_FAMILY_NPU_S4 = "npu_s4"
internal const val NPU_S1_ROUTE_FAMILY_NPU_S5 = "npu_s5"
internal const val NPU_S1_ROUTE_FAMILY_LOCAL_DEFAULT = "local_default"
internal const val NPU_S1_ROUTE_FAMILY_LOCAL_CPU = "local_cpu"
internal const val NPU_S1_ROUTE_FAMILY_LOCAL_GPU = "local_gpu"
internal const val NPU_S1_ROUTE_FAMILY_UNAVAILABLE = "unavailable"
internal const val NPU_S1_BACKEND_EVIDENCE_LOCAL_DEFAULT = "local_default"
internal const val NPU_S1_BACKEND_EVIDENCE_CPU_ROUTE = "cpu_route"
internal const val NPU_S1_BACKEND_EVIDENCE_GPU_ROUTE = "gpu_route"
internal const val NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE = "unavailable"
internal const val NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU = "selected_backend_not_npu"
internal const val NPU_S1_REPEATED_RUN_BLOCKED_REUSE_DISABLED = "reuse_disabled_for_safety"
internal const val NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_RUN_COUNT = "unsafe_run_count"
internal const val NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_WAIT_MS = "unsafe_wait_ms"
internal const val NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_MODE = "unsafe_repeated_run_mode"
internal const val NPU_S1_REPEATED_RUN_SAFETY_POLICY_STOP_ON_ENGINE_CREATE_FAILED =
    "stop_on_first_engine_create_failed"
internal const val NPU_S1_REPEATED_RUN_GUARD_RECOMMENDATION_ENGINE_CREATE_FAILED =
    "disable_npu_until_app_restart_or_cooldown"
internal const val NPU_S1_NATIVE_STAGE_ADAPTER_START = "adapter_start"
internal const val NPU_S1_NATIVE_STAGE_PROVIDER_START = "provider_start"
internal const val NPU_S1_NATIVE_STAGE_BEFORE_NATIVE_CALL = "before_native_call"
internal const val NPU_S1_NATIVE_STAGE_NATIVE_CALL = "native_call"
internal const val NPU_S1_NATIVE_STAGE_AFTER_NATIVE_CALL = "after_native_call"
internal const val NPU_S1_NATIVE_STAGE_NATIVE_RESULT_PARSE = "native_result_parse"
internal const val NPU_S1_NATIVE_STAGE_DECODE_STARTED = "decode_started"
internal const val NPU_S1_NATIVE_STAGE_DECODE_FINISHED = "decode_finished"
internal const val NPU_S1_NATIVE_STAGE_CLEANUP_STARTED = "cleanup_started"
internal const val NPU_S1_NATIVE_STAGE_CLEANUP_FINISHED = "cleanup_finished"
internal const val NPU_S1_NATIVE_STAGE_SESSION_DESTROY_STARTED = "session_destroy_started"
internal const val NPU_S1_NATIVE_STAGE_SESSION_DESTROY_FINISHED = "session_destroy_finished"
internal const val NPU_S1_NATIVE_STAGE_ADAPTER_SUCCESS = "adapter_success"
internal const val NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE = "adapter_failure"
internal const val NPU_S1_NATIVE_STAGE_PROVIDER_SUCCESS = "provider_success"
internal const val NPU_S1_NATIVE_STAGE_PROVIDER_FAILURE = "provider_failure"
internal const val NPU_S1_NATIVE_STAGE_UNKNOWN = "unknown"

data class NpuS1NativeStageDiagnostics(
    val nativeRunId: String = "unavailable",
    val nativeStage: String = "unknown",
    val nativeStageHistory: String = "unavailable",
    val nativeCallStartedAtElapsedRealtimeMs: String = "unavailable",
    val nativeCallFinishedAtElapsedRealtimeMs: String = "unavailable",
    val nativeCallDurationMs: String = "unavailable",
    val nativeCallReached: String = "unavailable",
    val nativeCallReturned: String = "unavailable",
    val nativeDecodeStarted: String = "unavailable",
    val nativeDecodeFinished: String = "unavailable",
    val nativeCleanupStarted: String = "unavailable",
    val nativeCleanupFinished: String = "unavailable",
    val nativeCleanupReached: String = "unavailable",
    val nativeSessionDestroyStarted: String = "unavailable",
    val nativeSessionDestroyFinished: String = "unavailable",
    val nativeSessionDestroyReached: String = "unavailable",
    val nativeResultAvailable: String = "unavailable",
    val nativeResultTail: String = "unavailable",
    val nativeDiagAvailable: String = "unavailable",
    val nativeDiagTail: String = "unavailable",
    val nativeErrorClass: String = "unavailable",
    val nativeErrorMessage: String = "unavailable",
    val nativeErrorStage: String = "unavailable",
    val nativeErrorSource: String = "unavailable",
)

internal enum class NpuS1RepeatedRunMode(
    val wireValue: String,
    val displayLabel: String,
    val recreateAfterRun: Boolean,
    val postRecreateDelayMs: Long,
    val waitAfterRunMs: Long,
) {
    REUSE(
        wireValue = "reuse",
        displayLabel = "Reuse",
        recreateAfterRun = false,
        postRecreateDelayMs = 0L,
        waitAfterRunMs = 0L,
    ),
    REUSE_10S(
        wireValue = "reuse_10s",
        displayLabel = "Reuse + 10s",
        recreateAfterRun = false,
        postRecreateDelayMs = 0L,
        waitAfterRunMs = 10_000L,
    ),
    REUSE_30S(
        wireValue = "reuse_30s",
        displayLabel = "Reuse + 30s",
        recreateAfterRun = false,
        postRecreateDelayMs = 0L,
        waitAfterRunMs = 30_000L,
    ),
    RECREATE(
        wireValue = "recreate",
        displayLabel = "Recreate",
        recreateAfterRun = true,
        postRecreateDelayMs = 0L,
        waitAfterRunMs = 0L,
    ),
    RECREATE_3S(
        wireValue = "recreate_3s",
        displayLabel = "Recreate + 3s",
        recreateAfterRun = true,
        postRecreateDelayMs = 3_000L,
        waitAfterRunMs = 0L,
    ),
}

internal data class NpuS1RepeatedRunLifecyclePlan(
    val mode: NpuS1RepeatedRunMode,
    val recreateAfterRun: Boolean,
    val postRecreateDelayMs: Long,
    val waitAfterRunMs: Long,
)

internal fun npuS1RepeatedRunLifecyclePlan(mode: NpuS1RepeatedRunMode): NpuS1RepeatedRunLifecyclePlan =
    NpuS1RepeatedRunLifecyclePlan(
        mode = mode,
        recreateAfterRun = mode.recreateAfterRun,
        postRecreateDelayMs = mode.postRecreateDelayMs,
        waitAfterRunMs = mode.waitAfterRunMs,
    )

internal data class NpuS1BackendDiagnostics(
    val selectedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val requestedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val effectiveBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val routeFamily: String = NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
    val blockedReason: String = "none",
    val guardRecommendation: String = "unavailable",
)

internal fun npuS1BackendFromPreferredSetting(
    setting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
): String = npuS1SelectedBackendForSettings(setting, npuStandardRouteMode)

internal fun npuS1SelectedBackendForSettings(
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
): String =
    when (
        InferenceBackendSelection.fromSettings(
            preferredBackend = preferredBackendSetting,
            npuStandardRouteMode = npuStandardRouteMode,
        )
    ) {
        InferenceBackendSelection.AUTOMATIC -> NPU_S1_BACKEND_AUTOMATIC
        InferenceBackendSelection.CPU -> NPU_S1_BACKEND_CPU
        InferenceBackendSelection.GPU -> NPU_S1_BACKEND_GPU
        InferenceBackendSelection.NPU_S1 -> NPU_S1_BACKEND_NPU_S1
        InferenceBackendSelection.NPU_S2 -> NPU_S1_BACKEND_NPU_S2
        InferenceBackendSelection.NPU_S3 -> NPU_S1_BACKEND_NPU_S3
        InferenceBackendSelection.NPU_S4 -> NPU_S1_BACKEND_NPU_S4
        InferenceBackendSelection.NPU_S5 -> NPU_S1_BACKEND_NPU_S5
    }

internal fun npuS1BackendDiagnosticsForPreferredSetting(
    setting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
): NpuS1BackendDiagnostics {
    val selection = InferenceBackendSelection.fromSettings(
        preferredBackend = setting,
        npuStandardRouteMode = npuStandardRouteMode,
    )
    val selectedBackend = npuS1BackendFromPreferredSetting(setting, npuStandardRouteMode)
    val requestedBackend = when (selection) {
        InferenceBackendSelection.AUTOMATIC -> NPU_S1_BACKEND_AUTOMATIC
        InferenceBackendSelection.CPU -> NPU_S1_BACKEND_CPU
        InferenceBackendSelection.GPU -> NPU_S1_BACKEND_GPU
        InferenceBackendSelection.NPU_S1,
        InferenceBackendSelection.NPU_S2,
        InferenceBackendSelection.NPU_S3,
        InferenceBackendSelection.NPU_S4,
        InferenceBackendSelection.NPU_S5 -> NPU_S1_BACKEND_NPU
    }
    val resolvedEvidence = when (selection) {
        InferenceBackendSelection.AUTOMATIC -> NPU_S1_BACKEND_EVIDENCE_LOCAL_DEFAULT
        InferenceBackendSelection.CPU -> NPU_S1_BACKEND_EVIDENCE_CPU_ROUTE
        InferenceBackendSelection.GPU -> NPU_S1_BACKEND_EVIDENCE_GPU_ROUTE
        InferenceBackendSelection.NPU_S1,
        InferenceBackendSelection.NPU_S2,
        InferenceBackendSelection.NPU_S3,
        InferenceBackendSelection.NPU_S4,
        InferenceBackendSelection.NPU_S5 -> backendEvidence
            .takeIf { it.isNotBlank() && it != NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE }
            ?: NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE
    }
    val hasNpuEvidence = backendEvidence == NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE ||
        backendEvidence.contains("QNN_HTP", ignoreCase = true) ||
        backendEvidence.contains("FastRPC", ignoreCase = true)
    return NpuS1BackendDiagnostics(
        selectedBackend = selectedBackend,
        requestedBackend = requestedBackend,
        effectiveBackend = when (selection) {
            InferenceBackendSelection.AUTOMATIC -> NPU_S1_BACKEND_AUTOMATIC
            InferenceBackendSelection.CPU -> NPU_S1_BACKEND_CPU
            InferenceBackendSelection.GPU -> NPU_S1_BACKEND_GPU
            InferenceBackendSelection.NPU_S1,
            InferenceBackendSelection.NPU_S2,
            InferenceBackendSelection.NPU_S3,
            InferenceBackendSelection.NPU_S4,
            InferenceBackendSelection.NPU_S5 -> if (hasNpuEvidence) NPU_S1_BACKEND_NPU else NPU_S1_BACKEND_UNAVAILABLE
        },
        backendEvidence = resolvedEvidence,
        routeFamily = when (selection) {
            InferenceBackendSelection.AUTOMATIC -> NPU_S1_ROUTE_FAMILY_LOCAL_DEFAULT
            InferenceBackendSelection.CPU -> NPU_S1_ROUTE_FAMILY_LOCAL_CPU
            InferenceBackendSelection.GPU -> NPU_S1_ROUTE_FAMILY_LOCAL_GPU
            InferenceBackendSelection.NPU_S1 -> NPU_S1_ROUTE_FAMILY_NPU_S1
            InferenceBackendSelection.NPU_S2 -> NPU_S1_ROUTE_FAMILY_NPU_S2
            InferenceBackendSelection.NPU_S3 -> NPU_S1_ROUTE_FAMILY_NPU_S3
            InferenceBackendSelection.NPU_S4 -> NPU_S1_ROUTE_FAMILY_NPU_S4
            InferenceBackendSelection.NPU_S5 -> NPU_S1_ROUTE_FAMILY_NPU_S5
        },
    )
}

internal fun npuS1BackendDiagnosticsForResult(
    result: NpuStandardRouteS1Result,
    preferredBackendSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
): NpuS1BackendDiagnostics =
    npuS1BackendDiagnosticsForPreferredSetting(
        setting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        backendEvidence = result.npuBackendEvidence.ifBlank { NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE },
    )

internal data class NpuS1RepeatedRunStartGate(
    val allowed: Boolean,
    val blockedReason: String = "none",
)

internal fun npuS1RepeatedRunStartGate(
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    mode: NpuS1RepeatedRunMode,
    runCount: Int,
    waitMs: Long,
): NpuS1RepeatedRunStartGate = when {
    npuS1BackendFromPreferredSetting(preferredBackendSetting, npuStandardRouteMode) != NPU_S1_BACKEND_NPU_S1 ->
        NpuS1RepeatedRunStartGate(false, NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU)
    mode == NpuS1RepeatedRunMode.REUSE ->
        NpuS1RepeatedRunStartGate(false, NPU_S1_REPEATED_RUN_BLOCKED_REUSE_DISABLED)
    mode !in NPU_S1_REPEATED_RUN_SAFE_MODE_OPTIONS ->
        NpuS1RepeatedRunStartGate(false, NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_MODE)
    runCount !in NPU_S1_REPEATED_RUN_SAFE_COUNT_OPTIONS ->
        NpuS1RepeatedRunStartGate(false, NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_RUN_COUNT)
    waitMs < NPU_S1_REPEATED_RUN_SAFE_WAIT_MS ->
        NpuS1RepeatedRunStartGate(false, NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_WAIT_MS)
    else -> NpuS1RepeatedRunStartGate(true)
}

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
    val outputQualityCandidateStatus: String = "unavailable",
    val outputQualityCandidateReason: String = "unavailable",
    val outputQualityCandidatePreparedOutput: String = "",
    val arithmeticTailLeakDetected: Boolean = false,
    val arithmeticTailLeakIgnoredForDisplay: Boolean = false,
    val actualDisplayText: String = sanitizedOutput.ifBlank { rawOutput.trim() },
    val ttsText: String = actualDisplayText,
    val npuS1FailureKind: String = "unavailable",
    val nativeCrashRiskHint: String = "unavailable",
    val selectedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val requestedBackend: String = NPU_S1_BACKEND_NPU,
    val effectiveBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val routeFamily: String = NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
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
    val waitAfterRunMs: Long = 0L,
    val waitStartedAtElapsedRealtimeMs: Long? = null,
    val waitFinishedAtElapsedRealtimeMs: Long? = null,
    val finalInputLengthChars: Int,
    val finalInputTailPreview: String,
    val tokenizerInputTokens: String = "unavailable",
    val tokenizerOutputTokens: String = "unavailable",
    val outputTokenCountSource: String,
    val promptTokenCountSource: String = "code_points",
    val maxOutputTokensReached: Boolean,
    val stopSequenceMatched: String = "unavailable",
    val processPid: Int? = null,
    val processName: String = "unavailable",
    val threadName: String = "unavailable",
    val runStartedAtWallTimeMs: Long? = null,
    val runStartedAtElapsedRealtimeMs: Long? = null,
    val runFinishedAtWallTimeMs: Long? = null,
    val runFinishedAtElapsedRealtimeMs: Long? = null,
    val runDurationWallMs: Long? = null,
    val engineRequestStartedAtElapsedRealtimeMs: Long? = null,
    val engineCreateStartedAtElapsedRealtimeMs: Long? = null,
    val engineCreateFinishedAtElapsedRealtimeMs: Long? = null,
    val decodeStartedAtElapsedRealtimeMs: Long? = null,
    val decodeFinishedAtElapsedRealtimeMs: Long? = null,
    val failureDetectedAtElapsedRealtimeMs: Long? = null,
    val failureDetectedAtWallTimeMs: Long? = null,
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val failureExceptionSource: String = "unavailable",
    val failureStage: String = NPU_S1_FAILURE_STAGE_UNKNOWN,
    val nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
)

internal data class NpuS1RepeatedRunState(
    val status: String = NPU_S1_REPEATED_RUN_STATUS_IDLE,
    val startedAtMs: Long? = null,
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val prompt: String = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
    val requestedRunCount: Int = NPU_S1_REPEATED_RUN_DEFAULT_COUNT,
    val maxOutputTokens: Int = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
    val repeatedRunMode: NpuS1RepeatedRunMode = NpuS1RepeatedRunMode.REUSE,
    val repeatedRunWaitMs: Long = 0L,
    val selectedBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val requestedBackend: String = NPU_S1_BACKEND_NPU,
    val effectiveBackend: String = NPU_S1_BACKEND_UNAVAILABLE,
    val backendEvidence: String = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
    val routeFamily: String = NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
    val blockedReason: String = "none",
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
    val selectedBackend: String,
    val requestedBackend: String,
    val effectiveBackend: String,
    val backendEvidence: String,
    val routeFamily: String,
    val blockedReason: String,
    val successCount: Int,
    val failureCount: Int,
    val engineCreateFailedCount: Int,
    val fallbackCount: Int,
    val freshCrashCount: Int,
    val timeoutCount: Int,
    val nativeCrashRiskHint: String,
    val guardRecommendation: String,
    val repeatedRunSafetyPolicy: String,
    val safetyGuardCount: Int,
    val qualityFailCount: Int,
    val arithmeticTailLeakCount: Int,
    val minTotalMs: Long?,
    val maxTotalMs: Long?,
    val avgTotalMs: Long?,
    val minDecodeMs: Long?,
    val maxDecodeMs: Long?,
    val avgDecodeMs: Long?,
    val minTokensPerSecond: Double?,
    val maxTokensPerSecond: Double?,
    val avgTokensPerSecond: Double?,
    val uniqueOutputsCount: Int,
    val mostCommonActualDisplayText: String,
    val mostCommonTtsText: String,
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
    val processPid: Int?,
    val repeatedRunStartedAtWallTimeMs: Long?,
    val repeatedRunStartedAtElapsedRealtimeMs: Long?,
    val repeatedRunFinishedAtWallTimeMs: Long?,
    val repeatedRunFinishedAtElapsedRealtimeMs: Long?,
    val firstFailureRunIndex: Int?,
    val lastFailureRunIndex: Int?,
    val firstEngineCreateFailureRunIndex: Int?,
    val firstFailureWallTimeMs: Long?,
    val firstFailureElapsedRealtimeMs: Long?,
    val firstFailureStage: String,
    val firstFailureReason: String,
    val firstFailureExceptionClass: String,
    val firstFailureExceptionMessage: String,
    val tombstoneCompareHint: String,
    val counterSnapshot: NpuS1RepeatedRunCounterSnapshot,
    val firstFailureCounterSnapshot: String,
    val counterNote: String,
    val failureAfterNSuccesses: Int?,
    val failureAfterLastSuccessElapsedMs: Long?,
    val failureAfterNAdapterCalls: Int?,
    val failureAfterNDecodeSuccesses: Int?,
    val failureAfterTotalWaitMs: Long?,
    val failurePatternHint: String,
    val repeatedRunWaitMs: Long,
    val totalWaitTimeMs: Long,
    val firstFailureNativeStage: String,
    val firstFailureNativeErrorStage: String,
    val firstFailureNativeErrorClass: String,
    val firstFailureNativeErrorSource: String,
    val firstFailureNativeStageHistory: String,
    val firstFailureNativeDiagTail: String,
)

internal data class NpuS1RepeatedRunCounterSnapshot(
    val engineRequestCount: Int,
    val engineRequestSuccessCount: Int,
    val engineRequestFailureCount: Int,
    val engineCreateAttemptCount: String = "unavailable",
    val engineCreateSuccessCount: String = "unavailable",
    val engineCreateFailureCount: String = "unavailable",
    val engineCreateVisibility: String = NPU_S1_ENGINE_CREATE_VISIBILITY,
    val engineCreateSource: String = NPU_S1_ENGINE_CREATE_SOURCE,
    val adapterCallCount: Int,
    val adapterSuccessCount: Int,
    val adapterFailureCount: Int,
    val decodeAttemptCount: Int,
    val decodeSuccessCount: Int,
    val decodeFailureCount: Int,
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
    private val helperAllowedLogged = AtomicBoolean(false)
    private val helperNoOpWarned = AtomicBoolean(false)

    fun setContext(context: NpuS1LogcatContext) {
        logHelperCalledOnce("set_context")
        if (!helperEnabledOrWarn("set_context")) return
        currentContext.set(context)
    }

    fun clearContext(context: NpuS1LogcatContext) {
        logHelperCalledOnce("clear_context")
        if (!helperEnabledOrWarn("clear_context")) return
        currentContext.compareAndSet(context, null)
    }

    fun clearCurrentContext() {
        logHelperCalledOnce("clear_current_context")
        if (!helperEnabledOrWarn("clear_current_context")) return
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
        logHelperCalledOnce("repeated_run_start")
        if (!helperEnabledOrWarn("repeated_run_start")) return
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
        logHelperCalledOnce("run_finished")
        if (!helperEnabledOrWarn("run_finished")) return
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
        logHelperCalledOnce("adapter_failure")
        if (!helperEnabledOrWarn("adapter_failure")) return
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
        logHelperCalledOnce("repeated_run_stopped")
        if (!helperEnabledOrWarn("repeated_run_stopped")) return
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

    private fun helperEnabledOrWarn(helperEvent: String): Boolean {
        if (enabled) return true
        if (BuildConfig.BUILD_TYPE != "release" && helperNoOpWarned.compareAndSet(false, true)) {
            runCatching {
                android.util.Log.w(
                    NPU_S1_LOGCAT_TAG,
                    listOf(
                        "event=helper_noop",
                        "helper_event=$helperEvent",
                        "helper_allowed_by_debug_flag=${BuildConfig.DEBUG}",
                        "reason=build_config_debug_false",
                        "build_type=${BuildConfig.BUILD_TYPE}",
                        commonFields(mode = currentContext.get()?.repeatedRunMode, runIndex = currentContext.get()?.runIndex),
                    ).joinToString(" "),
                )
            }
        }
        return false
    }

    private fun logHelperCalledOnce(helperEvent: String) {
        if (!BuildConfig.DEBUG) return
        if (!helperAllowedLogged.compareAndSet(false, true)) return
        runCatching {
            Log.i(
                NPU_S1_LOGCAT_TAG,
                listOf(
                    "event=helper_called",
                    "helper_event=$helperEvent",
                    "helper_allowed_by_debug_flag=${BuildConfig.DEBUG}",
                    commonFields(mode = currentContext.get()?.repeatedRunMode, runIndex = currentContext.get()?.runIndex),
                ).joinToString(" "),
            )
        }
    }

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

internal fun logNpuS1RepeatedRunnerEnteredDirectProbe(
    mode: NpuS1RepeatedRunMode,
    requestedRunCount: Int,
    promptLength: Int,
    maxOutputTokens: Int,
) {
    if (!BuildConfig.DEBUG) return
    runCatching {
        android.util.Log.i(
            NPU_S1_LOGCAT_TAG,
            listOf(
                "event=repeated_runner_entered",
                "source=NpuS1RepeatedRunDiagnostics",
                "mode=${mode.wireValue}",
                "run_count_requested=$requestedRunCount",
                "prompt_length=$promptLength",
                "max_output_tokens=$maxOutputTokens",
                "build_debug=${BuildConfig.DEBUG}",
                "pid=${runCatching { Process.myPid().toString() }.getOrDefault("unavailable")}",
                "thread_name=${Thread.currentThread().name.ifBlank { "unavailable" }}",
            ).joinToString(" "),
        )
    }
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
    val decodeMsValues = records.mapNotNull { it.decodeMs }
    val tokenSpeedValues = records.mapNotNull { it.tokensPerSecond }
    val outputCounts = records
        .groupingBy { it.actualDisplayText.ifBlank { it.sanitizedOutput.ifBlank { it.rawOutput } } }
        .eachCount()
    val mostCommonActualDisplayText = outputCounts.maxWithOrNull(
        compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
    )?.key.orEmpty()
    val ttsCounts = records
        .groupingBy { it.ttsText }
        .eachCount()
    val mostCommonTtsText = ttsCounts.maxWithOrNull(
        compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
    )?.key.orEmpty()
    val first = records.firstOrNull()
    val last = records.lastOrNull()
    val firstFailure = records.firstOrNull { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
    val lastFailure = records.lastOrNull { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
    val firstEngineCreateFailure = records.firstOrNull { it.isEngineCreateFailed() }
    val engineCreateFailedCount = records.count { it.isEngineCreateFailed() }
    val counterSnapshot = buildNpuS1RepeatedRunCounterSnapshot(records)
    val firstFailureRecords = firstFailure?.let { failure ->
        records.takeWhile { it !== failure } + failure
    }.orEmpty()
    val firstFailureCounterSnapshot = firstFailure?.let {
        formatNpuS1CounterSnapshot(buildNpuS1RepeatedRunCounterSnapshot(firstFailureRecords))
    } ?: "unavailable"
    val recordsBeforeFirstFailure = firstFailure?.let { failure ->
        records.takeWhile { it !== failure }
    }.orEmpty()
    val firstFailureSnapshot = firstFailure?.let {
        buildNpuS1RepeatedRunCounterSnapshot(firstFailureRecords)
    }
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
        selectedBackend = state.selectedBackend.takeUnless { it == NPU_S1_BACKEND_UNAVAILABLE }
            ?: first?.selectedBackend
            ?: NPU_S1_BACKEND_UNAVAILABLE,
        requestedBackend = state.requestedBackend.takeUnless { it == NPU_S1_BACKEND_UNAVAILABLE }
            ?: first?.requestedBackend
            ?: NPU_S1_BACKEND_NPU,
        effectiveBackend = state.effectiveBackend.takeUnless { it == NPU_S1_BACKEND_UNAVAILABLE }
            ?: first?.effectiveBackend
            ?: NPU_S1_BACKEND_UNAVAILABLE,
        backendEvidence = state.backendEvidence.takeUnless { it == NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE }
            ?: first?.backendEvidence
            ?: NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
        routeFamily = state.routeFamily.takeUnless { it == NPU_S1_ROUTE_FAMILY_UNAVAILABLE }
            ?: first?.routeFamily
            ?: NPU_S1_ROUTE_FAMILY_UNAVAILABLE,
        blockedReason = state.blockedReason,
        successCount = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS },
        failureCount = records.count { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS },
        engineCreateFailedCount = engineCreateFailedCount,
        fallbackCount = records.count { it.fallbackUsed },
        freshCrashCount = records.count { it.freshCrash },
        timeoutCount = records.count { it.timeout },
        nativeCrashRiskHint = records.firstOrNull { it.nativeCrashRiskHint != "unavailable" }?.nativeCrashRiskHint
            ?: if (records.any { it.freshCrash }) "fresh_crash_detected_stop_before_release" else "unavailable",
        guardRecommendation = if (engineCreateFailedCount > 0) {
            NPU_S1_REPEATED_RUN_GUARD_RECOMMENDATION_ENGINE_CREATE_FAILED
        } else {
            "unavailable"
        },
        repeatedRunSafetyPolicy = NPU_S1_REPEATED_RUN_SAFETY_POLICY_STOP_ON_ENGINE_CREATE_FAILED,
        safetyGuardCount = records.count { it.safetyGuardTriggered },
        qualityFailCount = records.count { it.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL },
        arithmeticTailLeakCount = records.count { it.arithmeticTailLeakDetected },
        minTotalMs = totalMsValues.minOrNull(),
        maxTotalMs = totalMsValues.maxOrNull(),
        avgTotalMs = totalMsValues.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        minDecodeMs = decodeMsValues.minOrNull(),
        maxDecodeMs = decodeMsValues.maxOrNull(),
        avgDecodeMs = decodeMsValues.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        minTokensPerSecond = tokenSpeedValues.minOrNull(),
        maxTokensPerSecond = tokenSpeedValues.maxOrNull(),
        avgTokensPerSecond = tokenSpeedValues.takeIf { it.isNotEmpty() }?.average(),
        uniqueOutputsCount = outputCounts.size,
        mostCommonActualDisplayText = mostCommonActualDisplayText,
        mostCommonTtsText = mostCommonTtsText,
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
        processPid = first?.processPid ?: runCatching { Process.myPid() }.getOrNull(),
        repeatedRunStartedAtWallTimeMs = state.startedAtMs ?: first?.runStartedAtWallTimeMs,
        repeatedRunStartedAtElapsedRealtimeMs = state.startedAtElapsedRealtimeMs ?: first?.runStartedAtElapsedRealtimeMs,
        repeatedRunFinishedAtWallTimeMs = state.finishedAtMs ?: last?.runFinishedAtWallTimeMs,
        repeatedRunFinishedAtElapsedRealtimeMs = state.finishedAtElapsedRealtimeMs ?: last?.runFinishedAtElapsedRealtimeMs,
        firstFailureRunIndex = firstFailure?.runIndex,
        lastFailureRunIndex = lastFailure?.runIndex,
        firstEngineCreateFailureRunIndex = firstEngineCreateFailure?.runIndex,
        firstFailureWallTimeMs = firstFailure?.failureDetectedAtWallTimeMs ?: firstFailure?.runFinishedAtWallTimeMs,
        firstFailureElapsedRealtimeMs = firstFailure?.failureDetectedAtElapsedRealtimeMs ?: firstFailure?.runFinishedAtElapsedRealtimeMs,
        firstFailureStage = firstFailure?.failureStage ?: NPU_S1_FAILURE_STAGE_UNKNOWN,
        firstFailureReason = firstFailure?.reason ?: "unavailable",
        firstFailureExceptionClass = firstFailure?.failureExceptionClass ?: "unavailable",
        firstFailureExceptionMessage = firstFailure?.failureExceptionMessage ?: "unavailable",
        tombstoneCompareHint = NPU_S1_TOMBSTONE_COMPARE_HINT,
        counterSnapshot = counterSnapshot,
        firstFailureCounterSnapshot = firstFailureCounterSnapshot,
        counterNote = NPU_S1_COUNTER_NOTE,
        failureAfterNSuccesses = firstFailure?.let {
            recordsBeforeFirstFailure.count { record -> record.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }
        },
        failureAfterLastSuccessElapsedMs = firstFailure?.let { failure ->
            recordsBeforeFirstFailure
                .lastOrNull { record -> record.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }
                ?.runFinishedAtElapsedRealtimeMs
                ?.let { lastSuccessFinishedAt ->
                    (failure.failureDetectedAtElapsedRealtimeMs ?: failure.runFinishedAtElapsedRealtimeMs)
                        ?.minus(lastSuccessFinishedAt)
                        ?.coerceAtLeast(0L)
                }
        },
        failureAfterNAdapterCalls = firstFailureSnapshot?.adapterCallCount,
        failureAfterNDecodeSuccesses = firstFailureSnapshot?.decodeSuccessCount,
        failureAfterTotalWaitMs = firstFailure?.let { recordsBeforeFirstFailure.sumOf { record -> record.waitAfterRunMs } },
        failurePatternHint = buildNpuS1FailurePatternHint(firstFailure, firstFailureSnapshot),
        repeatedRunWaitMs = state.repeatedRunWaitMs.takeIf { it > 0L } ?: state.repeatedRunMode.waitAfterRunMs,
        totalWaitTimeMs = records.sumOf { it.waitAfterRunMs },
        firstFailureNativeStage = firstFailure?.nativeDiagnostics?.nativeStage ?: "unavailable",
        firstFailureNativeErrorStage = firstFailure?.nativeDiagnostics?.nativeErrorStage ?: "unavailable",
        firstFailureNativeErrorClass = firstFailure?.nativeDiagnostics?.nativeErrorClass ?: "unavailable",
        firstFailureNativeErrorSource = firstFailure?.nativeDiagnostics?.nativeErrorSource ?: "unavailable",
        firstFailureNativeStageHistory = firstFailure?.nativeDiagnostics?.nativeStageHistory ?: "unavailable",
        firstFailureNativeDiagTail = firstFailure?.nativeDiagnostics?.nativeDiagTail ?: "unavailable",
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
    record.isEngineCreateFailed() -> NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED
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
        appendLine("repeated_run_wait_ms=${summary.repeatedRunWaitMs}")
        appendLine("total_wait_time_ms=${summary.totalWaitTimeMs}")
        appendLine("recreate_api_note=$NPU_S1_REPEATED_RUN_RECREATE_NOTE")
        appendLine("stopped=${summary.stopped}")
        appendLine("stop_reason=${summary.stopReason}")
        appendLine("blocked_reason=${summary.blockedReason}")
        appendLine("selected_backend=${summary.selectedBackend}")
        appendLine("requested_backend=${summary.requestedBackend}")
        appendLine("effective_backend=${summary.effectiveBackend}")
        appendLine("backend_evidence=${summary.backendEvidence}")
        appendLine("route_family=${summary.routeFamily}")
        appendLine("success_count=${summary.successCount}")
        appendLine("failure_count=${summary.failureCount}")
        appendLine("engine_create_failed_count=${summary.engineCreateFailedCount}")
        appendLine("fallback_count=${summary.fallbackCount}")
        appendLine("fresh_crash_count=${summary.freshCrashCount}")
        appendLine("timeout_count=${summary.timeoutCount}")
        appendLine("native_crash_risk_hint=${summary.nativeCrashRiskHint}")
        appendLine("guard_recommendation=${summary.guardRecommendation}")
        appendLine("repeated_run_safety_policy=${summary.repeatedRunSafetyPolicy}")
        appendLine("safety_guard_count=${summary.safetyGuardCount}")
        appendLine("quality_fail_count=${summary.qualityFailCount}")
        appendLine("arithmetic_tail_leak_count=${summary.arithmeticTailLeakCount}")
        appendLine("min_total_ms=${formatNullableLong(summary.minTotalMs)}")
        appendLine("max_total_ms=${formatNullableLong(summary.maxTotalMs)}")
        appendLine("avg_total_ms=${formatNullableLong(summary.avgTotalMs)}")
        appendLine("min_decode_ms=${formatNullableLong(summary.minDecodeMs)}")
        appendLine("max_decode_ms=${formatNullableLong(summary.maxDecodeMs)}")
        appendLine("avg_decode_ms=${formatNullableLong(summary.avgDecodeMs)}")
        appendLine("min_tokens_per_second=${formatNullableDouble(summary.minTokensPerSecond)}")
        appendLine("max_tokens_per_second=${formatNullableDouble(summary.maxTokensPerSecond)}")
        appendLine("avg_tokens_per_second=${formatNullableDouble(summary.avgTokensPerSecond)}")
        appendLine("unique_outputs_count=${summary.uniqueOutputsCount}")
        appendLine("most_common_actual_display_text=${summary.mostCommonActualDisplayText.ifBlank { "unavailable" }}")
        appendLine("most_common_tts_text=${summary.mostCommonTtsText.ifBlank { "unavailable" }}")
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
        appendLine("process_pid=${summary.processPid?.toString() ?: "unavailable"}")
        appendLine("repeated_run_started_at_wall_time_ms=${formatNullableLong(summary.repeatedRunStartedAtWallTimeMs)}")
        appendLine("repeated_run_started_at_elapsed_realtime_ms=${formatNullableLong(summary.repeatedRunStartedAtElapsedRealtimeMs)}")
        appendLine("repeated_run_finished_at_wall_time_ms=${formatNullableLong(summary.repeatedRunFinishedAtWallTimeMs)}")
        appendLine("repeated_run_finished_at_elapsed_realtime_ms=${formatNullableLong(summary.repeatedRunFinishedAtElapsedRealtimeMs)}")
        appendLine("first_failure_run_index=${summary.firstFailureRunIndex?.toString() ?: "unavailable"}")
        appendLine("last_failure_run_index=${summary.lastFailureRunIndex?.toString() ?: "unavailable"}")
        appendLine("first_engine_create_failure_run_index=${summary.firstEngineCreateFailureRunIndex?.toString() ?: "unavailable"}")
        appendLine("first_failure_wall_time_ms=${formatNullableLong(summary.firstFailureWallTimeMs)}")
        appendLine("first_failure_elapsed_realtime_ms=${formatNullableLong(summary.firstFailureElapsedRealtimeMs)}")
        appendLine("first_failure_stage=${summary.firstFailureStage}")
        appendLine("first_failure_reason=${summary.firstFailureReason}")
        appendLine("first_failure_exception_class=${summary.firstFailureExceptionClass}")
        appendLine("first_failure_exception_message=${summary.firstFailureExceptionMessage}")
        appendLine("tombstone_compare_hint=${summary.tombstoneCompareHint}")
        appendLine("engine_request_count=${summary.counterSnapshot.engineRequestCount}")
        appendLine("engine_request_success_count=${summary.counterSnapshot.engineRequestSuccessCount}")
        appendLine("engine_request_failure_count=${summary.counterSnapshot.engineRequestFailureCount}")
        appendLine("engine_create_attempt_count=${summary.counterSnapshot.engineCreateAttemptCount}")
        appendLine("engine_create_success_count=${summary.counterSnapshot.engineCreateSuccessCount}")
        appendLine("engine_create_failure_count=${summary.counterSnapshot.engineCreateFailureCount}")
        appendLine("engine_create_visibility=${summary.counterSnapshot.engineCreateVisibility}")
        appendLine("engine_create_source=${summary.counterSnapshot.engineCreateSource}")
        appendLine("adapter_call_count=${summary.counterSnapshot.adapterCallCount}")
        appendLine("adapter_success_count=${summary.counterSnapshot.adapterSuccessCount}")
        appendLine("adapter_failure_count=${summary.counterSnapshot.adapterFailureCount}")
        appendLine("decode_attempt_count=${summary.counterSnapshot.decodeAttemptCount}")
        appendLine("decode_success_count=${summary.counterSnapshot.decodeSuccessCount}")
        appendLine("decode_failure_count=${summary.counterSnapshot.decodeFailureCount}")
        appendLine("first_failure_counter_snapshot=${summary.firstFailureCounterSnapshot}")
        appendLine("counter_note=${summary.counterNote}")
        appendLine("failure_after_n_successes=${summary.failureAfterNSuccesses?.toString() ?: "unavailable"}")
        appendLine("failure_after_last_success_elapsed_ms=${summary.failureAfterLastSuccessElapsedMs?.toString() ?: "unavailable"}")
        appendLine("failure_after_n_adapter_calls=${summary.failureAfterNAdapterCalls?.toString() ?: "unavailable"}")
        appendLine("failure_after_n_decode_successes=${summary.failureAfterNDecodeSuccesses?.toString() ?: "unavailable"}")
        appendLine("failure_after_total_wait_ms=${summary.failureAfterTotalWaitMs?.toString() ?: "unavailable"}")
        appendLine("failure_pattern_hint=${summary.failurePatternHint}")
        appendLine("first_failure_native_stage=${summary.firstFailureNativeStage}")
        appendLine("first_failure_native_error_stage=${summary.firstFailureNativeErrorStage}")
        appendLine("first_failure_native_error_class=${summary.firstFailureNativeErrorClass}")
        appendLine("first_failure_native_error_source=${summary.firstFailureNativeErrorSource}")
        appendLine("first_failure_native_stage_history=${summary.firstFailureNativeStageHistory}")
        appendLine("first_failure_native_diag_tail=${summary.firstFailureNativeDiagTail}")
        val detailRecords = state.records
            .filter { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
            .let { failures ->
                listOfNotNull(failures.firstOrNull(), failures.lastOrNull())
                    .distinctBy { it.runIndex }
            }
        if (detailRecords.isNotEmpty()) {
            appendLine("[DEV診断: NPU S1 repeated run details]")
            detailRecords.forEach { record ->
                val index = state.records.indexOfFirst { it === record }.takeIf { it >= 0 }
                    ?: state.records.indexOfFirst { it.runIndex == record.runIndex }
                val recordsThroughRun = state.records.take(index + 1)
                val counterSnapshotAtRun = buildNpuS1RepeatedRunCounterSnapshot(recordsThroughRun)
                val failureCounterSnapshot = if (record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS) {
                    formatNpuS1CounterSnapshot(counterSnapshotAtRun)
                } else {
                    "unavailable"
                }
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
                appendLine("output_quality_candidate_status=${record.outputQualityCandidateStatus}")
                appendLine("output_quality_candidate_reason=${record.outputQualityCandidateReason}")
                appendLine("output_quality_candidate_prepared_output=${record.outputQualityCandidatePreparedOutput}")
                appendLine("arithmetic_tail_leak_detected=${record.arithmeticTailLeakDetected}")
                appendLine("arithmetic_tail_leak_ignored_for_display=${record.arithmeticTailLeakIgnoredForDisplay}")
                appendLine("actual_display_text=${record.actualDisplayText}")
                appendLine("tts_text=${record.ttsText}")
                appendLine("npu_s1_failure_kind=${record.npuS1FailureKind}")
                appendLine("native_crash_risk_hint=${record.nativeCrashRiskHint}")
                appendLine("selected_backend=${record.selectedBackend}")
                appendLine("requested_backend=${record.requestedBackend}")
                appendLine("effective_backend=${record.effectiveBackend}")
                appendLine("backend_evidence=${record.backendEvidence}")
                appendLine("route_family=${record.routeFamily}")
                appendLine("blocked_reason=none")
                appendLine(
                    "guard_recommendation=${
                        if (record.isEngineCreateFailed()) {
                            NPU_S1_REPEATED_RUN_GUARD_RECOMMENDATION_ENGINE_CREATE_FAILED
                        } else {
                            "unavailable"
                        }
                    }",
                )
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
                appendLine("wait_after_run_ms=${record.waitAfterRunMs}")
                appendLine("wait_started_at_elapsed_realtime_ms=${formatNullableLong(record.waitStartedAtElapsedRealtimeMs)}")
                appendLine("wait_finished_at_elapsed_realtime_ms=${formatNullableLong(record.waitFinishedAtElapsedRealtimeMs)}")
                appendLine("final_input_length_chars=${record.finalInputLengthChars}")
                appendLine("final_input_tail_preview=${record.finalInputTailPreview}")
                appendLine("tokenizer_input_tokens=${record.tokenizerInputTokens}")
                appendLine("tokenizer_output_tokens=${record.tokenizerOutputTokens}")
                appendLine("output_token_count_source=${record.outputTokenCountSource}")
                appendLine("prompt_token_count_source=${record.promptTokenCountSource}")
                appendLine("max_output_tokens_reached=${record.maxOutputTokensReached}")
                appendLine("stop_sequence_matched=${record.stopSequenceMatched}")
                appendLine("process_pid=${record.processPid?.toString() ?: "unavailable"}")
                appendLine("process_name=${record.processName.ifBlank { "unavailable" }}")
                appendLine("thread_name=${record.threadName.ifBlank { "unavailable" }}")
                appendLine("run_started_at_wall_time_ms=${formatNullableLong(record.runStartedAtWallTimeMs)}")
                appendLine("run_started_at_elapsed_realtime_ms=${formatNullableLong(record.runStartedAtElapsedRealtimeMs)}")
                appendLine("run_finished_at_wall_time_ms=${formatNullableLong(record.runFinishedAtWallTimeMs)}")
                appendLine("run_finished_at_elapsed_realtime_ms=${formatNullableLong(record.runFinishedAtElapsedRealtimeMs)}")
                appendLine("run_duration_wall_ms=${formatNullableLong(record.runDurationWallMs)}")
                appendLine("engine_request_started_at_elapsed_realtime_ms=${formatNullableLong(record.engineRequestStartedAtElapsedRealtimeMs)}")
                appendLine("engine_create_started_at_elapsed_realtime_ms=${formatNullableLong(record.engineCreateStartedAtElapsedRealtimeMs)}")
                appendLine("engine_create_finished_at_elapsed_realtime_ms=${formatNullableLong(record.engineCreateFinishedAtElapsedRealtimeMs)}")
                appendLine("decode_started_at_elapsed_realtime_ms=${formatNullableLong(record.decodeStartedAtElapsedRealtimeMs)}")
                appendLine("decode_finished_at_elapsed_realtime_ms=${formatNullableLong(record.decodeFinishedAtElapsedRealtimeMs)}")
                appendLine("failure_detected_at_elapsed_realtime_ms=${formatNullableLong(record.failureDetectedAtElapsedRealtimeMs)}")
                appendLine("failure_detected_at_wall_time_ms=${formatNullableLong(record.failureDetectedAtWallTimeMs)}")
                appendLine("failure_exception_class=${record.failureExceptionClass.ifBlank { "unavailable" }}")
                appendLine("failure_exception_message=${record.failureExceptionMessage.ifBlank { "unavailable" }}")
                appendLine("failure_exception_source=${record.failureExceptionSource.ifBlank { "unavailable" }}")
                appendLine("failure_stage=${record.failureStage.ifBlank { NPU_S1_FAILURE_STAGE_UNKNOWN }}")
                appendLine("native_run_id=${record.nativeDiagnostics.nativeRunId}")
                appendLine("native_stage=${record.nativeDiagnostics.nativeStage}")
                appendLine("native_stage_history=${record.nativeDiagnostics.nativeStageHistory}")
                appendLine("native_call_started_at_elapsed_realtime_ms=${record.nativeDiagnostics.nativeCallStartedAtElapsedRealtimeMs}")
                appendLine("native_call_finished_at_elapsed_realtime_ms=${record.nativeDiagnostics.nativeCallFinishedAtElapsedRealtimeMs}")
                appendLine("native_call_duration_ms=${record.nativeDiagnostics.nativeCallDurationMs}")
                appendLine("native_call_reached=${record.nativeDiagnostics.nativeCallReached}")
                appendLine("native_call_returned=${record.nativeDiagnostics.nativeCallReturned}")
                appendLine("native_decode_started=${record.nativeDiagnostics.nativeDecodeStarted}")
                appendLine("native_decode_finished=${record.nativeDiagnostics.nativeDecodeFinished}")
                appendLine("native_cleanup_started=${record.nativeDiagnostics.nativeCleanupStarted}")
                appendLine("native_cleanup_finished=${record.nativeDiagnostics.nativeCleanupFinished}")
                appendLine("native_cleanup_reached=${record.nativeDiagnostics.nativeCleanupReached}")
                appendLine("native_session_destroy_started=${record.nativeDiagnostics.nativeSessionDestroyStarted}")
                appendLine("native_session_destroy_finished=${record.nativeDiagnostics.nativeSessionDestroyFinished}")
                appendLine("native_session_destroy_reached=${record.nativeDiagnostics.nativeSessionDestroyReached}")
                appendLine("native_result_available=${record.nativeDiagnostics.nativeResultAvailable}")
                appendLine("native_result_tail=${record.nativeDiagnostics.nativeResultTail}")
                appendLine("native_diag_available=${record.nativeDiagnostics.nativeDiagAvailable}")
                appendLine("native_diag_tail=${record.nativeDiagnostics.nativeDiagTail}")
                appendLine("native_error_class=${record.nativeDiagnostics.nativeErrorClass}")
                appendLine("native_error_message=${record.nativeDiagnostics.nativeErrorMessage}")
                appendLine("native_error_stage=${record.nativeDiagnostics.nativeErrorStage}")
                appendLine("native_error_source=${record.nativeDiagnostics.nativeErrorSource}")
                appendLine("engine_request_count_at_run=${counterSnapshotAtRun.engineRequestCount}")
                appendLine("engine_request_success_count_at_run=${counterSnapshotAtRun.engineRequestSuccessCount}")
                appendLine("engine_request_failure_count_at_run=${counterSnapshotAtRun.engineRequestFailureCount}")
                appendLine("engine_create_attempt_count_at_run=${counterSnapshotAtRun.engineCreateAttemptCount}")
                appendLine("engine_create_success_count_at_run=${counterSnapshotAtRun.engineCreateSuccessCount}")
                appendLine("engine_create_failure_count_at_run=${counterSnapshotAtRun.engineCreateFailureCount}")
                appendLine("engine_create_visibility_at_run=${counterSnapshotAtRun.engineCreateVisibility}")
                appendLine("engine_create_source_at_run=${counterSnapshotAtRun.engineCreateSource}")
                appendLine("adapter_call_count_at_run=${counterSnapshotAtRun.adapterCallCount}")
                appendLine("adapter_success_count_at_run=${counterSnapshotAtRun.adapterSuccessCount}")
                appendLine("adapter_failure_count_at_run=${counterSnapshotAtRun.adapterFailureCount}")
                appendLine("decode_attempt_count_at_run=${counterSnapshotAtRun.decodeAttemptCount}")
                appendLine("decode_success_count_at_run=${counterSnapshotAtRun.decodeSuccessCount}")
                appendLine("decode_failure_count_at_run=${counterSnapshotAtRun.decodeFailureCount}")
                appendLine("failure_counter_snapshot=$failureCounterSnapshot")
                appendLine("failure_after_engine_request_count=${if (record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS) counterSnapshotAtRun.engineRequestCount.toString() else "unavailable"}")
                appendLine("failure_after_adapter_call_count=${if (record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS) counterSnapshotAtRun.adapterCallCount.toString() else "unavailable"}")
                appendLine("failure_after_decode_attempt_count=${if (record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS) counterSnapshotAtRun.decodeAttemptCount.toString() else "unavailable"}")
            }
        }
    }.trimEnd()
}

internal fun buildNpuS1RepeatedRunSummaryCopyText(
    state: NpuS1RepeatedRunState,
): String = formatNpuS1RepeatedRunDiagnosticsForDev(state)

internal fun appendNpuS1RepeatedRunDiagnosticsForDev(
    text: String,
    state: NpuS1RepeatedRunState,
): String = listOf(
    text,
    formatNpuS1RepeatedRunDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

internal fun buildNpuS1RepeatedRunCounterSnapshot(
    records: List<NpuS1RepeatedRunRecord>,
): NpuS1RepeatedRunCounterSnapshot {
    val engineRequestCount = records.size
    val engineRequestSuccessCount = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }
    val engineRequestFailureCount = engineRequestCount - engineRequestSuccessCount
    val adapterFailureCount = records.count { it.isAdapterFailure() }
    val adapterCallCount = records.size
    val decodeSuccessCount = records.count { it.runDecodeReached }
    return NpuS1RepeatedRunCounterSnapshot(
        engineRequestCount = engineRequestCount,
        engineRequestSuccessCount = engineRequestSuccessCount,
        engineRequestFailureCount = engineRequestFailureCount,
        adapterCallCount = adapterCallCount,
        adapterSuccessCount = adapterCallCount - adapterFailureCount,
        adapterFailureCount = adapterFailureCount,
        decodeAttemptCount = decodeSuccessCount,
        decodeSuccessCount = decodeSuccessCount,
        decodeFailureCount = records.count {
            it.runDecodeReached && it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS
        },
    )
}

internal fun formatNpuS1CounterSnapshot(snapshot: NpuS1RepeatedRunCounterSnapshot): String =
    listOf(
        "engine_request=${snapshot.engineRequestCount}",
        "adapter_call=${snapshot.adapterCallCount}",
        "decode_attempt=${snapshot.decodeAttemptCount}",
        "adapter_failure=${snapshot.adapterFailureCount}",
        "decode_success=${snapshot.decodeSuccessCount}",
    ).joinToString(",")

internal fun buildNpuS1FailurePatternHint(
    firstFailure: NpuS1RepeatedRunRecord?,
    firstFailureCounterSnapshot: NpuS1RepeatedRunCounterSnapshot?,
): String {
    if (firstFailure == null || firstFailureCounterSnapshot == null) return "unavailable"
    return when {
        firstFailure.isAdapterFailure() ->
            "adapter_failure_after_${firstFailureCounterSnapshot.decodeSuccessCount}_successful_decodes"
        firstFailure.runDecodeReached ->
            "decode_failure_after_${firstFailureCounterSnapshot.decodeSuccessCount}_decode_reached_runs"
        else -> "failure_before_decode_after_${firstFailureCounterSnapshot.adapterCallCount}_adapter_calls"
    }
}

private fun NpuS1RepeatedRunRecord.isAdapterFailure(): Boolean =
    reason.startsWith("adapter_failure") || reason.contains("LiteRtLmJniException")

private fun NpuS1RepeatedRunRecord.isEngineCreateFailed(): Boolean {
    if (npuS1FailureKind == NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED) return true
    val hasLiteRtException = failureExceptionClass == "LiteRtLmJniException" ||
        reason.contains("LiteRtLmJniException", ignoreCase = true) ||
        nativeDiagnostics.nativeErrorClass == "LiteRtLmJniException"
    if (!hasLiteRtException) return false
    return listOf(
        reason,
        failureExceptionMessage,
        nativeDiagnostics.nativeErrorMessage,
        nativeDiagnostics.nativeDiagTail,
    ).any { it.contains("engine-create-failed", ignoreCase = true) }
}

internal fun inferNpuS1FailureStage(
    status: String,
    reason: String,
    runDecodeReached: Boolean,
    timeout: Boolean,
): String = when {
    status == NpuStandardRouteS1Contract.STATUS_SUCCESS -> NPU_S1_FAILURE_STAGE_UNKNOWN
    reason.startsWith("adapter_failure") -> NPU_S1_FAILURE_STAGE_ADAPTER
    reason.contains("LiteRtLmJniException") -> NPU_S1_FAILURE_STAGE_ADAPTER
    reason.contains("engine_create", ignoreCase = true) -> NPU_S1_FAILURE_STAGE_ENGINE_CREATE
    reason.contains("engine_request", ignoreCase = true) -> NPU_S1_FAILURE_STAGE_ENGINE_REQUEST
    timeout -> NPU_S1_FAILURE_STAGE_DECODE
    runDecodeReached -> NPU_S1_FAILURE_STAGE_DECODE
    else -> NPU_S1_FAILURE_STAGE_UNKNOWN
}

internal fun inferNpuS1FailureExceptionClass(reason: String): String {
    val marker = "adapter_failure:"
    val markerIndex = reason.indexOf(marker)
    if (markerIndex >= 0) {
        val inferred = reason
            .substring(markerIndex + marker.length)
            .takeWhile { it != ':' && !it.isWhitespace() }
            .trim()
        if (inferred.isNotBlank()) return inferred
    }
    return when {
        reason.contains("LiteRtLmJniException") -> "LiteRtLmJniException"
        else -> "unavailable"
    }
}

internal fun npuS1FailureExceptionSource(reason: String, exceptionClass: String): String = when {
    exceptionClass == "unavailable" -> "unavailable"
    reason.startsWith("adapter_failure") || reason.contains("LiteRtLmJniException") -> "reason_string_inferred"
    else -> "unavailable"
}

internal fun currentNpuS1ProcessName(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching { Application.getProcessName() }.getOrDefault("unavailable")
    } else {
        "unavailable"
    }

private fun formatNullableLong(value: Long?): String = value?.toString() ?: "unavailable"

private fun formatNullableDouble(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private const val NPU_S1_FINAL_INPUT_TAIL_CHARS = 80
private const val NPU_S1_MEMORY_GROWTH_SUSPECTED_MB = 32L
