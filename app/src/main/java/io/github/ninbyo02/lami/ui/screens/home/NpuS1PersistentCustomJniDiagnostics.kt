package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_IDLE = "idle"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_RUNNING = "running"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_COMPLETED = "completed"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED = "stopped"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT = 20
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND = "NPU"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION =
    "persistent_custom_jni_holder_poc_v1"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuS1PersistentCustomJniDevProbe"

internal enum class NpuS1PersistentCustomJniProbeMode(
    val wireValue: String,
    val displayLabel: String,
) {
    ENTRYPOINT_ONLY("entrypoint_only", "Entrypoint only"),
    MODEL_ASSETS_ONLY("model_assets_only", "ModelAssets only"),
    ENGINE_SETTINGS_ONLY("engine_settings_only", "EngineSettings only"),
    BEFORE_ENGINE_CREATE("before_engine_create", "Before engine create"),
    ENGINE_CREATE_ONLY("engine_create_only", "Engine create only"),
    FULL_20("full_20", "Full 20"),
}

internal interface NpuS1PersistentCustomJniProbeRunner {
    suspend fun run(
        mode: NpuS1PersistentCustomJniProbeMode,
        onUpdate: (NpuS1PersistentCustomJniProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentCustomJniProbeState
}

internal fun createNpuS1PersistentCustomJniProbeRunner(
    context: Context,
): NpuS1PersistentCustomJniProbeRunner? =
    runCatching {
        Class.forName(NPU_S1_PERSISTENT_CUSTOM_JNI_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuS1PersistentCustomJniProbeRunner
    }.getOrNull()

internal data class NpuS1PersistentCustomJniHolderKey(
    val modelPath: String = "unavailable",
    val modelFileLastModified: String = "unavailable",
    val modelFileSize: String = "unavailable",
    val backend: String = NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND,
    val cacheDir: String = "unavailable",
    val maxTokenBudget: String = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS.toString(),
    val engineConfigVersion: String = NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION,
) {
    fun stableText(): String = listOf(
        "model_path=$modelPath",
        "model_file_last_modified=$modelFileLastModified",
        "model_file_size=$modelFileSize",
        "backend=$backend",
        "cache_dir=$cacheDir",
        "max_token_budget=$maxTokenBudget",
        "engine_config_version=$engineConfigVersion",
    ).joinToString(";")
}

internal data class NpuS1PersistentCustomJniRunRecord(
    val runIndex: Int,
    val status: String,
    val reason: String,
    val sessionCreated: String = "unavailable",
    val sessionClosed: String = "unavailable",
    val prefillStarted: String = "unavailable",
    val prefillFinished: String = "unavailable",
    val decodeStarted: String = "unavailable",
    val decodeFinished: String = "unavailable",
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val qualityClassification: String = "unavailable",
    val totalMs: Long? = null,
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
    val cleanupMs: Long? = null,
    val failureStage: String = "unavailable",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val nativeDiagTail: String = "unavailable",
)

internal data class NpuS1PersistentCustomJniProbeState(
    val persistentCustomJniStatus: String = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_IDLE,
    val runCountRequested: Int = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
    val runCountCompletedOverride: Int? = null,
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val engineCreateCount: String = "unavailable",
    val decodeAttemptCount: String = "unavailable",
    val decodeSuccessCount: String = "unavailable",
    val engineCloseReached: String = "unavailable",
    val engineCloseSuccess: String = "unavailable",
    val holderKey: NpuS1PersistentCustomJniHolderKey = NpuS1PersistentCustomJniHolderKey(),
    val holderGeneration: String = "unavailable",
    val holderReusedCount: String = "unavailable",
    val holderInvalidated: String = "unavailable",
    val holderKeyMismatchDetected: String = "unavailable",
    val holderKeyMismatchReason: String = "unavailable",
    val nativeHolderEntrypointAvailable: String = "unavailable",
    val selectedNativeProbeMode: String = NpuS1PersistentCustomJniProbeMode.FULL_20.wireValue,
    val lastNativeStage: String = "unavailable",
    val nativeEntrypointReached: String = "unavailable",
    val modelAssetsCreateReached: String = "unavailable",
    val modelAssetsCreateReturned: String = "unavailable",
    val engineSettingsCreateReached: String = "unavailable",
    val engineSettingsCreateReturned: String = "unavailable",
    val engineCreateReached: String = "unavailable",
    val engineCreateReturned: String = "unavailable",
    val sessionCreateReached: String = "unavailable",
    val prefillReached: String = "unavailable",
    val decodeReached: String = "unavailable",
    val nativeDiagFlushCount: String = "unavailable",
    val nativeResultFlushCount: String = "unavailable",
    val firstFailureRunIndex: Int? = null,
    val firstFailureStage: String = "unavailable",
    val firstFailureReason: String = "unavailable",
    val firstFailureExceptionClass: String = "unavailable",
    val firstFailureDiagTail: String = "unavailable",
    val modelPath: String = "unavailable",
    val modelFileSize: String = "unavailable",
    val modelFileLastModified: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val persistentCustomJniHypothesisResult: String = "unavailable",
    val records: List<NpuS1PersistentCustomJniRunRecord> = emptyList(),
) {
    val runCountCompleted: Int
        get() = runCountCompletedOverride ?: records.size

    val successCount: Int
        get() = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }

    val failureCount: Int
        get() = records.count { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
}

internal fun formatNpuS1PersistentCustomJniDiagnosticsForDev(
    state: NpuS1PersistentCustomJniProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU S1 persistent custom JNI summary]")
    appendLine("persistent_custom_jni_status=${state.persistentCustomJniStatus}")
    appendLine("run_count_requested=${state.runCountRequested}")
    appendLine("run_count_completed=${state.runCountCompleted}")
    appendLine("success_count=${state.successCount}")
    appendLine("failure_count=${state.failureCount}")
    appendLine("engine_create_count=${state.engineCreateCount}")
    appendLine("decode_attempt_count=${state.decodeAttemptCount}")
    appendLine("decode_success_count=${state.decodeSuccessCount}")
    appendLine("engine_close_reached=${state.engineCloseReached}")
    appendLine("engine_close_success=${state.engineCloseSuccess}")
    appendLine("holder_key=${escapePersistentCustomJniCopyValue(state.holderKey.stableText())}")
    appendLine("holder_key_model_path=${escapePersistentCustomJniCopyValue(state.holderKey.modelPath)}")
    appendLine("holder_key_model_file_last_modified=${state.holderKey.modelFileLastModified}")
    appendLine("holder_key_model_file_size=${state.holderKey.modelFileSize}")
    appendLine("holder_key_backend=${state.holderKey.backend}")
    appendLine("holder_key_cache_dir=${escapePersistentCustomJniCopyValue(state.holderKey.cacheDir)}")
    appendLine("holder_key_max_token_budget=${state.holderKey.maxTokenBudget}")
    appendLine("holder_key_engine_config_version=${state.holderKey.engineConfigVersion}")
    appendLine("holder_generation=${state.holderGeneration}")
    appendLine("holder_reused_count=${state.holderReusedCount}")
    appendLine("holder_invalidated=${state.holderInvalidated}")
    appendLine("holder_key_mismatch_detected=${state.holderKeyMismatchDetected}")
    appendLine("holder_key_mismatch_reason=${escapePersistentCustomJniCopyValue(state.holderKeyMismatchReason)}")
    appendLine("native_holder_entrypoint_available=${state.nativeHolderEntrypointAvailable}")
    appendLine("selected_native_probe_mode=${state.selectedNativeProbeMode}")
    appendLine("last_native_stage=${state.lastNativeStage}")
    appendLine("native_entrypoint_reached=${state.nativeEntrypointReached}")
    appendLine("model_assets_create_reached=${state.modelAssetsCreateReached}")
    appendLine("model_assets_create_returned=${state.modelAssetsCreateReturned}")
    appendLine("engine_settings_create_reached=${state.engineSettingsCreateReached}")
    appendLine("engine_settings_create_returned=${state.engineSettingsCreateReturned}")
    appendLine("engine_create_reached=${state.engineCreateReached}")
    appendLine("engine_create_returned=${state.engineCreateReturned}")
    appendLine("session_create_reached=${state.sessionCreateReached}")
    appendLine("prefill_reached=${state.prefillReached}")
    appendLine("decode_reached=${state.decodeReached}")
    appendLine("native_diag_flush_count=${state.nativeDiagFlushCount}")
    appendLine("native_result_flush_count=${state.nativeResultFlushCount}")
    appendLine("first_failure_run_index=${formatPersistentCustomJniValue(state.firstFailureRunIndex)}")
    appendLine("first_failure_stage=${state.firstFailureStage}")
    appendLine("first_failure_reason=${escapePersistentCustomJniCopyValue(state.firstFailureReason)}")
    appendLine("first_failure_exception_class=${state.firstFailureExceptionClass}")
    appendLine("first_failure_diag_tail=${escapePersistentCustomJniCopyValue(state.firstFailureDiagTail)}")
    appendLine("model_path=${escapePersistentCustomJniCopyValue(state.modelPath)}")
    appendLine("model_file_size=${state.modelFileSize}")
    appendLine("model_file_last_modified=${state.modelFileLastModified}")
    appendLine("backend_evidence=${escapePersistentCustomJniCopyValue(state.backendEvidence)}")
    appendLine("persistent_custom_jni_hypothesis_result=${state.persistentCustomJniHypothesisResult}")
    appendLine()
    appendLine("[DEV診断: NPU S1 persistent custom JNI details]")
    if (state.records.isEmpty()) {
        appendLine("records=empty")
    } else {
        state.records.forEach { record ->
            appendLine("run_index=${record.runIndex}")
            appendLine("status=${record.status}")
            appendLine("reason=${escapePersistentCustomJniCopyValue(record.reason)}")
            appendLine("session_created=${record.sessionCreated}")
            appendLine("session_closed=${record.sessionClosed}")
            appendLine("prefill_started=${record.prefillStarted}")
            appendLine("prefill_finished=${record.prefillFinished}")
            appendLine("decode_started=${record.decodeStarted}")
            appendLine("decode_finished=${record.decodeFinished}")
            appendLine("raw_output=${escapePersistentCustomJniCopyValue(record.rawOutput)}")
            appendLine("sanitized_output=${escapePersistentCustomJniCopyValue(record.sanitizedOutput)}")
            appendLine("quality_classification=${record.qualityClassification}")
            appendLine("total_ms=${formatPersistentCustomJniValue(record.totalMs)}")
            appendLine("prefill_ms=${formatPersistentCustomJniValue(record.prefillMs)}")
            appendLine("decode_ms=${formatPersistentCustomJniValue(record.decodeMs)}")
            appendLine("cleanup_ms=${formatPersistentCustomJniValue(record.cleanupMs)}")
            appendLine("failure_stage=${record.failureStage}")
            appendLine("failure_exception_class=${record.failureExceptionClass}")
            appendLine("failure_exception_message=${escapePersistentCustomJniCopyValue(record.failureExceptionMessage)}")
            appendLine("native_diag_tail=${escapePersistentCustomJniCopyValue(record.nativeDiagTail)}")
        }
    }
}.trimEnd()

internal fun appendNpuS1PersistentCustomJniDiagnosticsForDev(
    text: String,
    state: NpuS1PersistentCustomJniProbeState,
): String = listOf(
    text,
    formatNpuS1PersistentCustomJniDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

private fun formatPersistentCustomJniValue(value: Any?): String = value?.toString() ?: "unavailable"

private fun escapePersistentCustomJniCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")
