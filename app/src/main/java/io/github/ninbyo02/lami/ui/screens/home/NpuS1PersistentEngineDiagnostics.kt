package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import java.util.Locale

internal const val NPU_PERSISTENT_ENGINE_MULTI_TURN_TEST_NAME = "NPU Persistent Engine Multi-turn Test"
internal const val NPU_PERSISTENT_ENGINE_UI_ACTION_LABEL = "NPU Persistent Probe状態確認"
internal const val NPU_PERSISTENT_ENGINE_UI_BLOCKED_EXPLANATION =
    "session_api_blocked_and_standard_route_adapter_not_exposed"
internal const val NPU_PERSISTENT_ENGINE_USER_NEXT_ACTION =
    "copy_persistent_full_dump_or_investigate_standard_route_adapter"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE = "idle"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING = "running"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED = "completed"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED = "stopped"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED = "blocked"
internal const val NPU_S1_PERSISTENT_ENGINE_DEFAULT_COUNT = 10
internal const val NPU_S1_PERSISTENT_ENGINE_DEFAULT_WAIT_MS = 500L
internal const val NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS = 32
internal const val NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT = 512
internal const val NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT = "not_exposed"
internal const val NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE = "engine_config_max_num_tokens_total_limit"
internal const val NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_FIX_NOTE =
    "official_api_uses_max_num_tokens_as_total_input_context_limit_not_output_only"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_AUTO = "auto"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_STANDARD_ROUTE_ADAPTER = "standard_route_adapter"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION = "session"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_STREAMING = "streaming"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_CONVERSATION = "conversation"
internal const val NPU_S1_PERSISTENT_ENGINE_SESSION_API_NPU_BLOCK_REASON =
    "session_api_logits_output_not_supported_on_npu_backend"
internal const val NPU_S1_PERSISTENT_ENGINE_API_MODE_NOTE =
    "auto_prefers_session_generate_content_to_probe_non_conversation_decode_path"
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
    val prompt: String = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
    val conversationCreated: String = "unavailable",
    val conversationClosed: String = "unavailable",
    val sessionCreated: String = "unavailable",
    val sessionClosed: String = "unavailable",
    val decodeStarted: String = "unavailable",
    val decodeFinished: String = "unavailable",
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val qualityClassification: String = "unavailable",
    val fallbackUsed: String = "unavailable",
    val timeout: String = "unavailable",
    val freshCrash: String = "unavailable",
    val totalMs: Long? = null,
    val decodeMs: Long? = null,
    val tokensPerSecond: Double? = null,
    val failureStage: String = "unavailable",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val nativeOrEngineDiagTail: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val holderIdentity: String = "not_exposed",
    val providerInstanceId: String = "not_exposed",
    val adapterInstanceId: String = "not_exposed",
    val sessionId: String = "not_exposed",
    val nativeStageHistory: String = "unavailable",
    val promptTextLengthChars: Int? = null,
    val requestedMaxOutputTokens: Int = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
    val officialTotalTokenLimit: Int = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
    val officialOutputTokenLimit: String = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
    val tokenLimitSource: String = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
    val tokenLimitFailureDetected: String = "false",
    val tokenLimitFailureMessage: String = "unavailable",
    val apiModeUsed: String = "unavailable",
    val logitsFailureDetected: String = "false",
    val logitsFailureMessage: String = "unavailable",
    val streamingStarted: String = "unavailable",
    val streamingFinished: String = "unavailable",
)

internal data class NpuS1PersistentEngineProbeState(
    val persistentProbeStatus: String = NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE,
    val runCountRequested: Int = NPU_S1_PERSISTENT_ENGINE_DEFAULT_COUNT,
    val waitMs: Long = NPU_S1_PERSISTENT_ENGINE_DEFAULT_WAIT_MS,
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
    val blockedReason: String = "none",
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
    val persistentEngineApiMode: String = NPU_S1_PERSISTENT_ENGINE_API_MODE_AUTO,
    val attemptedApiModes: String = NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION,
    val selectedApiMode: String = "unavailable",
    val apiModeSelectionReason: String = "unavailable",
    val logitsOutputRequired: String = "unavailable",
    val logitsOutputBackendSupported: String = "unavailable",
    val logitsFailureDetected: String = "false",
    val logitsFailureMessage: String = "unavailable",
    val sessionApiAvailable: String = "unavailable",
    val sessionApiUsed: String = "false",
    val sessionApiBlockedForNpu: String = "false",
    val sessionApiBlockReason: String = "none",
    val conversationApiUsed: String = "false",
    val streamingApiUsed: String = "false",
    val standardRouteAdapterAvailable: String = "unavailable",
    val standardRouteAdapterUsed: String = "false",
    val standardRouteAdapterReason: String = "unavailable",
    val persistentStandardRouteAvailable: String = "unavailable",
    val persistentStandardRouteReason: String = "unavailable",
    val apiModeNote: String = NPU_S1_PERSISTENT_ENGINE_API_MODE_NOTE,
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
): String {
    val runDecodeReachedCount = state.records.count { persistentBoolean(it.decodeStarted) == true }
    val fallbackUsedCount = state.records.count { persistentBoolean(it.fallbackUsed) == true }
    val timeoutCount = state.records.count { persistentBoolean(it.timeout) == true }
    val freshCrashCount = state.records.count { persistentBoolean(it.freshCrash) == true }
    val engineCreateFailedCount = state.records.count(::isPersistentEngineCreateFailed)
    val averageTotalMs = averagePersistentLongsOrNull(state.records.mapNotNull { it.totalMs })
    val averageDecodeMs = averagePersistentLongsOrNull(state.records.mapNotNull { it.decodeMs })
    val averageTokensPerSecond = averagePersistentDoublesOrNull(state.records.mapNotNull { it.tokensPerSecond })
    val firstFailureRecord = state.records.firstOrNull { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
    val restartAppRecommended = engineCreateFailedCount > 0 ||
        state.firstFailureReason.contains("engine-create-failed", ignoreCase = true) ||
        state.firstFailureReason.contains("engine_create_failed", ignoreCase = true)
    val guardRecommendation = if (restartAppRecommended) {
        NPU_S1_REPEATED_RUN_GUARD_RECOMMENDATION_ENGINE_CREATE_FAILED
    } else {
        "unavailable"
    }
    val uiExecutionExpected = persistentUiExecutionExpected(state)
    val uiBlockedExpected = persistentUiBlockedExpected(state)
    val uiBlockedExplanation = if (uiBlockedExpected == "true") {
        NPU_PERSISTENT_ENGINE_UI_BLOCKED_EXPLANATION
    } else {
        "unavailable"
    }
    return buildString {
    appendLine("[DEV診断: NPU S1 persistent engine summary]")
    appendLine("test_name=$NPU_PERSISTENT_ENGINE_MULTI_TURN_TEST_NAME")
    appendLine("ui_action_label=$NPU_PERSISTENT_ENGINE_UI_ACTION_LABEL")
    appendLine("ui_execution_expected=$uiExecutionExpected")
    appendLine("ui_blocked_expected=$uiBlockedExpected")
    appendLine("ui_blocked_explanation=$uiBlockedExplanation")
    appendLine("user_next_action=$NPU_PERSISTENT_ENGINE_USER_NEXT_ACTION")
    appendLine("persistent_engine_requested=true")
    appendLine("persistent_engine_available=${persistentEngineAvailable(state)}")
    appendLine("engine_reuse_observed=unavailable")
    appendLine("engine_holder_id=not_exposed")
    appendLine("holder_identity=not_exposed")
    appendLine("provider_instance_id=not_exposed")
    appendLine("adapter_instance_id=not_exposed")
    appendLine("session_id=not_exposed")
    appendLine("run_count_requested=${state.runCountRequested}")
    appendLine("run_count_completed=${state.runCountCompleted}")
    appendLine("success_count=${state.successCount}")
    appendLine("failure_count=${state.failureCount}")
    appendLine("success_rate=${formatPersistentRate(state.successCount, state.runCountCompleted)}")
    appendLine("fallback_used_count=$fallbackUsedCount")
    appendLine("fallback_rate=${formatPersistentRate(fallbackUsedCount, state.runCountCompleted)}")
    appendLine("timeout_count=$timeoutCount")
    appendLine("timeout_rate=${formatPersistentRate(timeoutCount, state.runCountCompleted)}")
    appendLine("fresh_crash_count=$freshCrashCount")
    appendLine("fresh_crash_rate=${formatPersistentRate(freshCrashCount, state.runCountCompleted)}")
    appendLine("engine_create_failed_count=$engineCreateFailedCount")
    appendLine("run_decode_reached_count=$runDecodeReachedCount")
    appendLine("run_decode_reached_rate=${formatPersistentRate(runDecodeReachedCount, state.runCountCompleted)}")
    appendLine("average_total_ms=${formatPersistentAverage(averageTotalMs)}")
    appendLine("average_decode_ms=${formatPersistentAverage(averageDecodeMs)}")
    appendLine("average_tokens_per_second=${formatPersistentAverage(averageTokensPerSecond)}")
    appendLine("backend_evidence_summary=${summarizePersistentValues(state.records.map { it.backendEvidence }, state.backendEvidence)}")
    appendLine("quality_classification_summary=${summarizePersistentValues(state.records.map { it.qualityClassification }, "unavailable")}")
    appendLine("first_failure_run_index=${formatPersistentValue(state.firstFailureRunIndex ?: firstFailureRecord?.runIndex)}")
    appendLine("first_failure_reason=${escapePersistentCopyValue(state.firstFailureReason.takeIf { it != "unavailable" } ?: firstFailureRecord?.reason ?: "unavailable")}")
    appendLine("first_failure_native_diag_tail=${escapePersistentCopyValue(firstFailureRecord?.nativeOrEngineDiagTail ?: "unavailable")}")
    appendLine("guard_recommendation=$guardRecommendation")
    appendLine("restart_app_recommended=$restartAppRecommended")
    appendLine("wait_ms=${state.waitMs}")
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
    appendLine("blocked_reason=${state.blockedReason}")
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
    appendLine("persistent_engine_api_mode=${state.persistentEngineApiMode}")
    appendLine("attempted_api_modes=${state.attemptedApiModes}")
    appendLine("selected_api_mode=${state.selectedApiMode}")
    appendLine("api_mode_selection_reason=${state.apiModeSelectionReason}")
    appendLine("logits_output_required=${state.logitsOutputRequired}")
    appendLine("logits_output_backend_supported=${state.logitsOutputBackendSupported}")
    appendLine("logits_failure_detected=${state.logitsFailureDetected}")
    appendLine("logits_failure_message=${escapePersistentCopyValue(state.logitsFailureMessage)}")
    appendLine("session_api_available=${state.sessionApiAvailable}")
    appendLine("session_api_used=${state.sessionApiUsed}")
    appendLine("session_api_blocked_for_npu=${state.sessionApiBlockedForNpu}")
    appendLine("session_api_block_reason=${state.sessionApiBlockReason}")
    appendLine("conversation_api_used=${state.conversationApiUsed}")
    appendLine("streaming_api_used=${state.streamingApiUsed}")
    appendLine("standard_route_adapter_available=${state.standardRouteAdapterAvailable}")
    appendLine("standard_route_adapter_used=${state.standardRouteAdapterUsed}")
    appendLine("standard_route_adapter_reason=${state.standardRouteAdapterReason}")
    appendLine("persistent_standard_route_available=${state.persistentStandardRouteAvailable}")
    appendLine("persistent_standard_route_reason=${state.persistentStandardRouteReason}")
    appendLine("api_mode_note=${state.apiModeNote}")
    appendLine()
    appendLine("[DEV診断: NPU S1 persistent engine details]")
    if (state.records.isEmpty()) {
        appendLine("records=empty")
    } else {
        state.records.forEach { record ->
            appendLine("run_index=${record.runIndex}")
            appendLine("prompt=${escapePersistentCopyValue(record.prompt)}")
            appendLine("status=${record.status}")
            appendLine("reason=${escapePersistentCopyValue(record.reason)}")
            appendLine("run_decode_reached=${persistentBoolean(record.decodeStarted)?.toString() ?: "unavailable"}")
            appendLine("fallback_used=${record.fallbackUsed}")
            appendLine("timeout=${record.timeout}")
            appendLine("fresh_crash=${record.freshCrash}")
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
            appendLine("tokens_per_second=${formatPersistentDouble(record.tokensPerSecond)}")
            appendLine("failure_stage=${record.failureStage}")
            appendLine("failure_exception_class=${record.failureExceptionClass}")
            appendLine("failure_exception_message=${escapePersistentCopyValue(record.failureExceptionMessage)}")
            appendLine("native_or_engine_diag_tail=${escapePersistentCopyValue(record.nativeOrEngineDiagTail)}")
            appendLine("native_stage_history=${escapePersistentCopyValue(record.nativeStageHistory)}")
            appendLine("backend_evidence=${escapePersistentCopyValue(record.backendEvidence)}")
            appendLine("holder_identity=${record.holderIdentity}")
            appendLine("provider_instance_id=${record.providerInstanceId}")
            appendLine("adapter_instance_id=${record.adapterInstanceId}")
            appendLine("session_id=${record.sessionId}")
            appendLine("prompt_text_length_chars=${formatPersistentValue(record.promptTextLengthChars)}")
            appendLine("requested_max_output_tokens=${record.requestedMaxOutputTokens}")
            appendLine("official_total_token_limit=${record.officialTotalTokenLimit}")
            appendLine("official_output_token_limit=${record.officialOutputTokenLimit}")
            appendLine("token_limit_source=${record.tokenLimitSource}")
            appendLine("token_limit_failure_detected=${record.tokenLimitFailureDetected}")
            appendLine("token_limit_failure_message=${escapePersistentCopyValue(record.tokenLimitFailureMessage)}")
            appendLine("api_mode_used=${record.apiModeUsed}")
            appendLine("logits_failure_detected=${record.logitsFailureDetected}")
            appendLine("logits_failure_message=${escapePersistentCopyValue(record.logitsFailureMessage)}")
            appendLine("streaming_started=${record.streamingStarted}")
            appendLine("streaming_finished=${record.streamingFinished}")
        }
    }
}.trimEnd()
}

internal fun buildNpuPersistentEngineSummaryCopyText(
    state: NpuS1PersistentEngineProbeState,
): String = formatNpuS1PersistentEngineDiagnosticsForDev(state)
    .substringBefore("\n[DEV診断: NPU S1 persistent engine details]")
    .trimEnd()

internal fun buildNpuPersistentEngineFullDumpCopyText(
    state: NpuS1PersistentEngineProbeState,
): String = formatNpuS1PersistentEngineDiagnosticsForDev(state)

internal fun appendNpuS1PersistentEngineDiagnosticsForDev(
    text: String,
    state: NpuS1PersistentEngineProbeState,
): String = listOf(
    text,
    formatNpuS1PersistentEngineDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

private fun formatPersistentValue(value: Any?): String = value?.toString() ?: "unavailable"

private fun formatPersistentDouble(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private fun formatPersistentAverage(value: Double?): String =
    value?.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private fun formatPersistentRate(count: Int, total: Int): String =
    if (total > 0) {
        String.format(Locale.US, "%.2f", count.toDouble() / total.toDouble())
    } else {
        "unavailable"
    }

private fun averagePersistentLongsOrNull(values: List<Long>): Double? =
    values.takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average()

private fun averagePersistentDoublesOrNull(values: List<Double>): Double? =
    values.takeIf { it.isNotEmpty() }?.average()

private fun persistentBoolean(value: String): Boolean? =
    when (value.trim().lowercase(Locale.US)) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun persistentEngineAvailable(state: NpuS1PersistentEngineProbeState): String =
    when {
        state.engineInitializeFinishedAtElapsedRealtimeMs != null -> "true"
        state.firstFailureStage == "engine_initialize" || state.firstFailureStage == "runner_create" ||
            state.firstFailureStage == "model_resolve" -> "false"
        state.persistentProbeStatus == NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED -> "false"
        state.persistentProbeStatus == NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE -> "unavailable"
        else -> "unavailable"
    }

private fun persistentUiExecutionExpected(state: NpuS1PersistentEngineProbeState): String =
    if (
        state.persistentProbeStatus == NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED ||
        state.sessionApiBlockedForNpu == "true" ||
        state.persistentStandardRouteAvailable == "false"
    ) {
        "false"
    } else {
        "unavailable"
    }

private fun persistentUiBlockedExpected(state: NpuS1PersistentEngineProbeState): String =
    if (
        state.persistentProbeStatus == NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED ||
        state.sessionApiBlockedForNpu == "true"
    ) {
        "true"
    } else {
        "unavailable"
    }

private fun isPersistentEngineCreateFailed(record: NpuS1PersistentEngineRunRecord): Boolean =
    record.reason.contains("engine-create-failed", ignoreCase = true) ||
        record.reason.contains("engine_create_failed", ignoreCase = true) ||
        record.failureExceptionMessage.contains("engine-create-failed", ignoreCase = true) ||
        record.nativeOrEngineDiagTail.contains("engine-create-failed", ignoreCase = true) ||
        record.nativeOrEngineDiagTail.contains("EngineFactory::CreateDefault", ignoreCase = true)

private fun summarizePersistentValues(values: List<String>, fallback: String): String {
    val normalized = values
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "unavailable" }
    val source = normalized.ifEmpty {
        listOf(fallback).filter { it.isNotBlank() && it != "unavailable" }
    }
    if (source.isEmpty()) return "unavailable"
    return source
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString(",") { "${it.key}:${it.value}" }
}

private fun escapePersistentCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")

internal fun isNpuS1PersistentTokenLimitFailure(message: String): Boolean =
    message.contains("Input token ids are too long", ignoreCase = true) ||
        message.contains("Exceeding the maximum number of tokens allowed", ignoreCase = true)

internal fun isNpuS1PersistentLogitsFailure(message: String): Boolean =
    message.contains("Decode for logits output not implemented", ignoreCase = true) ||
        message.contains("logits output not implemented", ignoreCase = true)

internal fun npuS1PersistentFailureStage(
    conversationCreated: Boolean,
    decodeStarted: Boolean,
    message: String,
): String = when {
    isNpuS1PersistentTokenLimitFailure(message) -> "token_limit"
    isNpuS1PersistentLogitsFailure(message) -> "decode"
    conversationCreated && decodeStarted -> "decode"
    else -> "conversation_create"
}

internal fun npuS1PersistentHypothesisResultForFailureStage(stage: String): String = when (stage) {
    "engine_initialize" -> "engine_initialize_failed"
    "conversation_create" -> "conversation_create_failed"
    "token_limit" -> "token_limit_failed"
    "logits_output" -> "logits_output_not_supported_on_npu_backend"
    "decode" -> "decode_failed"
    "conversation_close" -> "conversation_close_failed"
    "engine_close" -> "engine_close_failed"
    else -> "decode_failed"
}

internal fun npuS1PersistentHypothesisResultForFailureMessage(
    stage: String,
    message: String,
): String =
    if (stage == "decode" && isNpuS1PersistentLogitsFailure(message)) {
        "logits_output_not_supported_on_npu_backend"
    } else {
        npuS1PersistentHypothesisResultForFailureStage(stage)
    }
