package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE = "idle"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING = "running"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED = "completed"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED = "stopped"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_PERSISTENT_ENGINE_DEFAULT_COUNT = 20
internal const val NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS = 32
internal const val NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT = 512
internal const val NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT = "not_exposed"
internal const val NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE = "engine_config_max_num_tokens_total_limit"
internal const val NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_FIX_NOTE =
    "official_api_uses_max_num_tokens_as_total_input_context_limit_not_output_only"
internal const val NPU_S1_PERSISTENT_ENGINE_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuS1PersistentEngineDevProbe"

internal interface NpuS1PersistentEngineProbeRunner {
    suspend fun run(
        onUpdate: (NpuS1PersistentEngineProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentEngineProbeState
}

internal fun createNpuS1PersistentEngineProbeRunner(
    context: Context,
): NpuS1PersistentEngineProbeRunner? =
    runCatching {
        Class.forName(NPU_S1_PERSISTENT_ENGINE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuS1PersistentEngineProbeRunner
    }.getOrNull()

internal data class NpuS1PersistentEngineRunRecord(
    val runIndex: Int,
    val status: String,
    val reason: String,
    val conversationCreated: String = "unavailable",
    val conversationClosed: String = "unavailable",
    val sessionCreated: String = "unavailable",
    val sessionClosed: String = "unavailable",
    val decodeStarted: String = "unavailable",
    val decodeFinished: String = "unavailable",
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val qualityClassification: String = "unavailable",
    val totalMs: Long? = null,
    val decodeMs: Long? = null,
    val failureStage: String = "unavailable",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val nativeOrEngineDiagTail: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val promptTextLengthChars: Int? = null,
    val requestedMaxOutputTokens: Int = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
    val officialTotalTokenLimit: Int = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
    val officialOutputTokenLimit: String = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
    val tokenLimitSource: String = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
    val tokenLimitFailureDetected: String = "false",
    val tokenLimitFailureMessage: String = "unavailable",
)

internal data class NpuS1PersistentEngineProbeState(
    val persistentProbeStatus: String = NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE,
    val runCountRequested: Int = NPU_S1_PERSISTENT_ENGINE_DEFAULT_COUNT,
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val engineInitializeCount: Int = 0,
    val engineInitializeStartedAtElapsedRealtimeMs: Long? = null,
    val engineInitializeFinishedAtElapsedRealtimeMs: Long? = null,
    val engineInitializeDurationMs: Long? = null,
    val engineCloseReached: String = "unavailable",
    val engineCloseSuccess: String = "unavailable",
    val conversationCreateCount: Int = 0,
    val conversationCloseCount: Int = 0,
    val sessionCreateCount: String = "unavailable",
    val sessionCloseCount: String = "unavailable",
    val firstFailureRunIndex: Int? = null,
    val firstFailureStage: String = "unavailable",
    val firstFailureReason: String = "unavailable",
    val firstFailureExceptionClass: String = "unavailable",
    val firstFailureExceptionMessage: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val modelPathOrName: String = "unavailable",
    val cacheDir: String = "unavailable",
    val persistentEngineHypothesisResult: String = "unavailable",
    val promptTextLengthChars: Int? = null,
    val requestedMaxOutputTokens: Int = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
    val officialTotalTokenLimit: Int = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
    val officialOutputTokenLimit: String = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
    val tokenLimitSource: String = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
    val firstFailureTokenLimitMessage: String = "unavailable",
    val tokenLimitFixNote: String = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_FIX_NOTE,
    val records: List<NpuS1PersistentEngineRunRecord> = emptyList(),
) {
    val runCountCompleted: Int
        get() = records.size

    val successCount: Int
        get() = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }

    val failureCount: Int
        get() = records.count { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
}

internal fun formatNpuS1PersistentEngineDiagnosticsForDev(
    state: NpuS1PersistentEngineProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU S1 persistent engine summary]")
    appendLine("persistent_probe_status=${state.persistentProbeStatus}")
    appendLine("run_count_requested=${state.runCountRequested}")
    appendLine("run_count_completed=${state.runCountCompleted}")
    appendLine("success_count=${state.successCount}")
    appendLine("failure_count=${state.failureCount}")
    appendLine("engine_initialize_count=${state.engineInitializeCount}")
    appendLine("engine_initialize_started_at_elapsed_realtime_ms=${formatPersistentValue(state.engineInitializeStartedAtElapsedRealtimeMs)}")
    appendLine("engine_initialize_finished_at_elapsed_realtime_ms=${formatPersistentValue(state.engineInitializeFinishedAtElapsedRealtimeMs)}")
    appendLine("engine_initialize_duration_ms=${formatPersistentValue(state.engineInitializeDurationMs)}")
    appendLine("engine_close_reached=${state.engineCloseReached}")
    appendLine("engine_close_success=${state.engineCloseSuccess}")
    appendLine("conversation_create_count=${state.conversationCreateCount}")
    appendLine("conversation_close_count=${state.conversationCloseCount}")
    appendLine("session_create_count=${state.sessionCreateCount}")
    appendLine("session_close_count=${state.sessionCloseCount}")
    appendLine("first_failure_run_index=${formatPersistentValue(state.firstFailureRunIndex)}")
    appendLine("first_failure_stage=${state.firstFailureStage}")
    appendLine("first_failure_reason=${escapePersistentCopyValue(state.firstFailureReason)}")
    appendLine("first_failure_exception_class=${state.firstFailureExceptionClass}")
    appendLine("first_failure_exception_message=${escapePersistentCopyValue(state.firstFailureExceptionMessage)}")
    appendLine("backend_evidence=${escapePersistentCopyValue(state.backendEvidence)}")
    appendLine("model_path_or_name=${escapePersistentCopyValue(state.modelPathOrName)}")
    appendLine("cache_dir=${escapePersistentCopyValue(state.cacheDir)}")
    appendLine("persistent_engine_hypothesis_result=${state.persistentEngineHypothesisResult}")
    appendLine("prompt_text_length_chars=${formatPersistentValue(state.promptTextLengthChars)}")
    appendLine("requested_max_output_tokens=${state.requestedMaxOutputTokens}")
    appendLine("official_total_token_limit=${state.officialTotalTokenLimit}")
    appendLine("official_output_token_limit=${state.officialOutputTokenLimit}")
    appendLine("token_limit_source=${state.tokenLimitSource}")
    appendLine("first_failure_token_limit_message=${escapePersistentCopyValue(state.firstFailureTokenLimitMessage)}")
    appendLine("token_limit_fix_note=${state.tokenLimitFixNote}")
    appendLine()
    appendLine("[DEV診断: NPU S1 persistent engine details]")
    if (state.records.isEmpty()) {
        appendLine("records=empty")
    } else {
        state.records.forEach { record ->
            appendLine("run_index=${record.runIndex}")
            appendLine("status=${record.status}")
            appendLine("reason=${escapePersistentCopyValue(record.reason)}")
            appendLine("conversation_created=${record.conversationCreated}")
            appendLine("conversation_closed=${record.conversationClosed}")
            appendLine("session_created=${record.sessionCreated}")
            appendLine("session_closed=${record.sessionClosed}")
            appendLine("decode_started=${record.decodeStarted}")
            appendLine("decode_finished=${record.decodeFinished}")
            appendLine("raw_output=${escapePersistentCopyValue(record.rawOutput)}")
            appendLine("sanitized_output=${escapePersistentCopyValue(record.sanitizedOutput)}")
            appendLine("quality_classification=${record.qualityClassification}")
            appendLine("total_ms=${formatPersistentValue(record.totalMs)}")
            appendLine("decode_ms=${formatPersistentValue(record.decodeMs)}")
            appendLine("failure_stage=${record.failureStage}")
            appendLine("failure_exception_class=${record.failureExceptionClass}")
            appendLine("failure_exception_message=${escapePersistentCopyValue(record.failureExceptionMessage)}")
            appendLine("native_or_engine_diag_tail=${escapePersistentCopyValue(record.nativeOrEngineDiagTail)}")
            appendLine("backend_evidence=${escapePersistentCopyValue(record.backendEvidence)}")
            appendLine("prompt_text_length_chars=${formatPersistentValue(record.promptTextLengthChars)}")
            appendLine("requested_max_output_tokens=${record.requestedMaxOutputTokens}")
            appendLine("official_total_token_limit=${record.officialTotalTokenLimit}")
            appendLine("official_output_token_limit=${record.officialOutputTokenLimit}")
            appendLine("token_limit_source=${record.tokenLimitSource}")
            appendLine("token_limit_failure_detected=${record.tokenLimitFailureDetected}")
            appendLine("token_limit_failure_message=${escapePersistentCopyValue(record.tokenLimitFailureMessage)}")
        }
    }
}.trimEnd()

internal fun appendNpuS1PersistentEngineDiagnosticsForDev(
    text: String,
    state: NpuS1PersistentEngineProbeState,
): String = listOf(
    text,
    formatNpuS1PersistentEngineDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

private fun formatPersistentValue(value: Any?): String = value?.toString() ?: "unavailable"

private fun escapePersistentCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")

internal fun isNpuS1PersistentTokenLimitFailure(message: String): Boolean =
    message.contains("Input token ids are too long", ignoreCase = true) ||
        message.contains("Exceeding the maximum number of tokens allowed", ignoreCase = true)

internal fun npuS1PersistentFailureStage(
    conversationCreated: Boolean,
    decodeStarted: Boolean,
    message: String,
): String = when {
    isNpuS1PersistentTokenLimitFailure(message) -> "token_limit"
    conversationCreated && decodeStarted -> "decode"
    else -> "conversation_create"
}

internal fun npuS1PersistentHypothesisResultForFailureStage(stage: String): String = when (stage) {
    "engine_initialize" -> "engine_initialize_failed"
    "conversation_create" -> "conversation_create_failed"
    "token_limit" -> "token_limit_failed"
    "decode" -> "decode_failed"
    "conversation_close" -> "conversation_close_failed"
    "engine_close" -> "engine_close_failed"
    else -> "decode_failed"
}
