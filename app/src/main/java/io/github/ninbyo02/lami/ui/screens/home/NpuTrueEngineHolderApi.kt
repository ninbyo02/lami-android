package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig

internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_TEST_NAME =
    "NPU True Engine Holder Create Close Probe"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuTrueEngineHolderCreateCloseDevProbe"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NO_RESULT =
    "no true engine holder create/close probe result available"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_UI_TITLE =
    "NPU True Engine Holder Create/Close Probe"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RUN_LABEL =
    "Run True Engine Holder Create/Close Probe"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL =
    "Copy True Engine Holder Summary"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL =
    "Copy True Engine Holder Full Dump"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NATIVE_PROBE_MODE =
    "true_engine_create_close_only"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RECOMMENDED_NEXT_STEP =
    "review_true_engine_create_close_device_result_then_implement_held_engine_run_once"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON =
    "temporarily_blocked_to_restore_startup"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_CRASH_DISABLED_REASON =
    "temporarily_disabled_after_startup_crash"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_DISABLED_REASON =
    "isolated_flavor_created_but_native_execution_not_enabled"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_PAYLOAD_STAGED_DISABLED_REASON =
    "isolated_native_payload_staged_but_execution_not_enabled"
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_SAFE_RECOMMENDED_NEXT_STEP =
    "restore_startup_then_rebuild_native_create_close_mode_in_isolated_flavor"

internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_TEST_NAME =
    "NPU True Engine Held Run Once Probe"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuTrueEngineHeldRunOnceDevProbe"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_NO_RESULT =
    "no true engine held run once probe result available"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_UI_TITLE =
    "NPU True Engine Held Run Once Probe"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_RUN_LABEL =
    "Run True Engine Held Run Once Probe"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_COPY_SUMMARY_LABEL =
    "Copy True Engine Held Run Once Summary"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_COPY_FULL_DUMP_LABEL =
    "Copy True Engine Held Run Once Full Dump"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_NATIVE_PROBE_MODE = "held_engine_run_once"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_BLOCK_REASON =
    "held_engine_run_once_probe_unavailable_for_this_variant"
internal const val NPU_TRUE_ENGINE_HELD_RUN_ONCE_RECOMMENDED_NEXT_STEP =
    "run_held_engine_run_once_on_device_then_implement_held_engine_multi_turn"

internal const val NPU_TRUE_ENGINE_ENTRYPOINT_TEST_NAME =
    "NPU True Engine Entrypoint Probe"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuTrueEngineEntrypointDevProbe"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_NO_RESULT =
    "no true engine entrypoint probe result available"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_UI_TITLE =
    "NPU True Engine Entrypoint Probe"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_RUN_LABEL =
    "Run True Engine Entrypoint Probe"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_COPY_SUMMARY_LABEL =
    "Copy True Engine Entrypoint Summary"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_COPY_FULL_DUMP_LABEL =
    "Copy True Engine Entrypoint Full Dump"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE = "entrypoint_only"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_BLOCK_REASON =
    "entrypoint_only_probe_unavailable_for_this_variant"
internal const val NPU_TRUE_ENGINE_ENTRYPOINT_RECOMMENDED_NEXT_STEP =
    "run_entrypoint_only_on_device_then_enable_model_assets_only_button_probe"

internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_TEST_NAME =
    "NPU True Engine ModelAssets Probe"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuTrueEngineModelAssetsDevProbe"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_NO_RESULT =
    "no true engine model assets probe result available"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_UI_TITLE =
    "NPU True Engine ModelAssets Probe"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_RUN_LABEL =
    "Run True Engine ModelAssets Probe"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_COPY_SUMMARY_LABEL =
    "Copy True Engine ModelAssets Summary"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_COPY_FULL_DUMP_LABEL =
    "Copy True Engine ModelAssets Full Dump"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE = "model_assets_only"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_BLOCK_REASON =
    "model_assets_only_probe_unavailable_for_this_variant"
internal const val NPU_TRUE_ENGINE_MODEL_ASSETS_RECOMMENDED_NEXT_STEP =
    "run_model_assets_only_on_device_then_enable_engine_settings_only_button_probe"


internal fun npuTrueEngineHolderCreateCloseProbeExecutionAvailable(): Boolean =
    BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED

internal data class NpuTrueEngineHolderNativeResult(
    val nativeReturn: String = "unavailable",
    val resultText: String = "",
    val diagText: String = "",
    val throwableClass: String = "unavailable",
    val throwableMessage: String = "unavailable",
)

internal data class NpuTrueEngineHolderCreateCloseProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val holderId: String = "unavailable",
    val nativeResult: NpuTrueEngineHolderNativeResult? = null,
) {
    val hasResult: Boolean
        get() = nativeResult != null || status != "idle" || reason != "not_run"

    val values: Map<String, String>
        get() = parseNpuTrueEngineHolderKeyValueText(nativeResult?.resultText.orEmpty())
}

internal interface NpuTrueEngineHolderCreateCloseProbeRunner {
    suspend fun run(): NpuTrueEngineHolderCreateCloseProbeState
}

internal fun npuTrueEngineHolderCreateCloseProbeExecutionBlockReason(): String =
    if (npuTrueEngineHolderCreateCloseProbeExecutionAvailable()) {
        "unavailable"
    } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
        !BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED
    ) {
        NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_CRASH_DISABLED_REASON
    } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        !BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED
    ) {
        NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_DISABLED_REASON
    } else {
        NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON
    }

internal fun npuTrueEngineHolderCreateCloseProbeVariantName(): String =
    BuildConfig.CURRENT_FLAVOR + BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercaseChar() }

internal fun blockedNpuTrueEngineHolderCreateCloseNativeResult(): NpuTrueEngineHolderNativeResult =
    NpuTrueEngineHolderNativeResult(
        nativeReturn = "blocked",
        resultText = """
            selected_native_probe_mode=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NATIVE_PROBE_MODE
            true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}
            isolated_flavor_available=${BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR}
            isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}
            isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}
            true_engine_create_close_probe_startup_safe=true
            native_call_deferred_until_button_click=true
            startup_native_call_blocked=true
            probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}
            probe_execution_block_reason=${npuTrueEngineHolderCreateCloseProbeExecutionBlockReason()}
            argument_validation_passed=false
            run_count_validation_skipped_for_create_close_only=unavailable
            persistent_custom_jni_status=blocked
            model_assets_create_reached=false
            model_assets_create_returned=false
            model_assets_create_succeeded=false
            engine_settings_create_reached=false
            engine_settings_create_returned=false
            engine_settings_create_succeeded=false
            engine_create_reached=false
            engine_create_returned=false
            engine_create_succeeded=false
            engine_close_reached=false
            engine_close_success=false
            session_create_reached=false
            session_create_count=0
            prefill_reached=false
            decode_reached=false
            decode_count=0
            generate_count=0
            npu_decode_called=false
            qnn_decode_called=false
            true_engine_persistent_reuse=false
            engine_reuse_observed=unavailable
            recommended_next_step=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_SAFE_RECOMMENDED_NEXT_STEP
        """.trimIndent(),
    )

internal fun npuTrueEngineHeldRunOnceProbeExecutionAvailable(): Boolean =
    BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED

internal fun npuTrueEngineHeldRunOnceProbeExecutionBlockReason(): String =
    if (npuTrueEngineHeldRunOnceProbeExecutionAvailable()) {
        "unavailable"
    } else {
        NPU_TRUE_ENGINE_HELD_RUN_ONCE_BLOCK_REASON
    }

internal data class NpuTrueEngineHeldRunOnceProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val holderId: String = "unavailable",
    val nativeResult: NpuTrueEngineHolderNativeResult? = null,
) {
    val hasResult: Boolean
        get() = nativeResult != null || status != "idle" || reason != "not_run"

    val values: Map<String, String>
        get() = parseNpuTrueEngineHolderKeyValueText(nativeResult?.resultText.orEmpty())
}

internal interface NpuTrueEngineHeldRunOnceProbeRunner {
    suspend fun run(): NpuTrueEngineHeldRunOnceProbeState
}

internal fun npuTrueEngineEntrypointProbeExecutionAvailable(): Boolean =
    BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED

internal fun npuTrueEngineEntrypointProbeExecutionBlockReason(): String =
    if (npuTrueEngineEntrypointProbeExecutionAvailable()) {
        "unavailable"
    } else {
        NPU_TRUE_ENGINE_ENTRYPOINT_BLOCK_REASON
    }

internal data class NpuTrueEngineEntrypointProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val nativeResult: NpuTrueEngineHolderNativeResult? = null,
) {
    val hasResult: Boolean
        get() = nativeResult != null || status != "idle" || reason != "not_run"

    val values: Map<String, String>
        get() = parseNpuTrueEngineHolderKeyValueText(nativeResult?.resultText.orEmpty())
}

internal interface NpuTrueEngineEntrypointProbeRunner {
    suspend fun run(): NpuTrueEngineEntrypointProbeState
}

internal fun npuTrueEngineModelAssetsProbeExecutionAvailable(): Boolean =
    BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
        BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED

internal fun npuTrueEngineModelAssetsProbeExecutionBlockReason(): String =
    if (npuTrueEngineModelAssetsProbeExecutionAvailable()) {
        "unavailable"
    } else {
        NPU_TRUE_ENGINE_MODEL_ASSETS_BLOCK_REASON
    }

internal data class NpuTrueEngineModelAssetsProbeState(
    val status: String = "idle",
    val reason: String = "not_run",
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val modelPathOrReason: String = "unavailable",
    val nativeResult: NpuTrueEngineHolderNativeResult? = null,
) {
    val hasResult: Boolean
        get() = nativeResult != null || status != "idle" || reason != "not_run"

    val values: Map<String, String>
        get() = parseNpuTrueEngineHolderKeyValueText(nativeResult?.resultText.orEmpty())
}

internal fun normalizeNpuTrueEngineModelAssetsProbeState(
    state: NpuTrueEngineModelAssetsProbeState,
): NpuTrueEngineModelAssetsProbeState =
    if (modelAssetsOnlyCompleted(state.values, state.nativeResult)) {
        state.copy(
            status = "completed",
            reason = "model_assets_only_completed",
        )
    } else {
        state
    }

internal interface NpuTrueEngineModelAssetsProbeRunner {
    suspend fun run(): NpuTrueEngineModelAssetsProbeState
}


internal fun createNpuTrueEngineHolderCreateCloseProbeRunner(
    context: Context,
): NpuTrueEngineHolderCreateCloseProbeRunner? =
    runCatching {
        Class.forName(NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuTrueEngineHolderCreateCloseProbeRunner
    }.getOrNull()


internal fun createNpuTrueEngineHeldRunOnceProbeRunner(
    context: Context,
): NpuTrueEngineHeldRunOnceProbeRunner? =
    runCatching {
        Class.forName(NPU_TRUE_ENGINE_HELD_RUN_ONCE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuTrueEngineHeldRunOnceProbeRunner
    }.getOrNull()


internal fun createNpuTrueEngineEntrypointProbeRunner(
    context: Context,
): NpuTrueEngineEntrypointProbeRunner? =
    runCatching {
        Class.forName(NPU_TRUE_ENGINE_ENTRYPOINT_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuTrueEngineEntrypointProbeRunner
    }.getOrNull()


internal fun createNpuTrueEngineModelAssetsProbeRunner(
    context: Context,
): NpuTrueEngineModelAssetsProbeRunner? =
    runCatching {
        Class.forName(NPU_TRUE_ENGINE_MODEL_ASSETS_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuTrueEngineModelAssetsProbeRunner
    }.getOrNull()


internal fun formatNpuTrueEngineHeldRunOnceSummaryForCopy(
    state: NpuTrueEngineHeldRunOnceProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_TRUE_ENGINE_HELD_RUN_ONCE_NO_RESULT\n" +
            "test_name=$NPU_TRUE_ENGINE_HELD_RUN_ONCE_TEST_NAME\n" +
            "probe_status=idle\n" +
            "probe_reason=not_run\n" +
            "true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}\n" +
            "selected_native_probe_mode=$NPU_TRUE_ENGINE_HELD_RUN_ONCE_NATIVE_PROBE_MODE\n" +
            "held_engine_run_once_probe_available=${npuTrueEngineHeldRunOnceProbeExecutionAvailable()}\n" +
            "probe_execution_block_reason=${npuTrueEngineHeldRunOnceProbeExecutionBlockReason()}\n" +
            "startup_native_call_blocked=true\n" +
            "native_call_deferred_until_button_click=true\n" +
            "engine_create_count=0\n" +
            "session_create_count=0\n" +
            "decode_count=0\n" +
            "generate_count=0\n" +
            "true_engine_persistent_reuse=false\n" +
            "persistent_multi_turn_possible=false\n" +
            "engine_reuse_observed=unavailable"
    }
    val values = state.values
    val engineCreateCount = values["engine_create_count"]
        ?: values.countFromReachedFlag("engine_create_reached").takeIf { it != "unavailable" }
        ?: "0"
    val engineCloseCount = values["engine_close_count"]
        ?: values.countFromReachedFlag("engine_close_reached").takeIf { it != "unavailable" }
        ?: "0"
    val sessionCreateCount = values["session_create_count"]
        ?: values.countFromReachedFlag("session_create_reached").takeIf { it != "unavailable" }
        ?: "0"
    val decodeCount = values["decode_count"]
        ?: values["decode_attempt_count"]
        ?: values.countFromReachedFlag("decode_reached").takeIf { it != "unavailable" }
        ?: "0"
    val generateCount = values["generate_count"] ?: decodeCount
    val runDecodeReached = values.boolText("run_decode_reached", values.boolText("decode_reached", "false"))
    val closeSucceeded = values.boolText("engine_close_success", "false")
    val backendEvidence = values["npu_backend_evidence"]
        ?: values["backend_evidence"]
        ?: "unavailable"
    val throwableClass = state.nativeResult?.throwableClass ?: "unavailable"
    val fatal = throwableClass != "unavailable" || state.status == "failed" || closeSucceeded != "true"
    val engineReuseObserved = if (
        engineCreateCount == "1" && decodeCount == "1" && engineCloseCount == "1" && closeSucceeded == "true"
    ) {
        "single_run"
    } else {
        "unavailable"
    }
    return buildString {
        appendLine("[DEV診断: NPU true engine held run once summary]")
        appendLine("test_name=$NPU_TRUE_ENGINE_HELD_RUN_ONCE_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("probe_reason=${state.reason}")
        appendLine("selected_native_probe_mode=${values["selected_native_probe_mode"] ?: NPU_TRUE_ENGINE_HELD_RUN_ONCE_NATIVE_PROBE_MODE}")
        appendLine("true_engine_probe_flavor=${values["true_engine_probe_flavor"] ?: npuTrueEngineHolderCreateCloseProbeVariantName()}")
        appendLine("held_engine_run_once_probe_available=${npuTrueEngineHeldRunOnceProbeExecutionAvailable()}")
        appendLine("probe_execution_block_reason=${npuTrueEngineHeldRunOnceProbeExecutionBlockReason()}")
        appendLine("startup_native_call_blocked=true")
        appendLine("native_call_deferred_until_button_click=true")
        appendLine("argument_validation_passed=${values.boolText("argument_validation_passed")}")
        appendLine("model_assets_create_called=${values.boolText("model_assets_create_reached")}")
        appendLine("model_assets_create_succeeded=${values.boolText("model_assets_create_succeeded", values.boolText("model_assets_create_returned", "false"))}")
        appendLine("engine_settings_create_called=${values.boolText("engine_settings_create_reached")}")
        appendLine("engine_settings_create_succeeded=${values.boolText("engine_settings_create_succeeded", values.boolText("engine_settings_create_returned", "false"))}")
        appendLine("engine_create_called=${values.boolText("engine_create_reached")}")
        appendLine("engine_create_succeeded=${values.boolText("engine_create_succeeded", values.boolText("engine_create_returned", "false"))}")
        appendLine("engine_create_count=$engineCreateCount")
        appendLine("engine_holder_open_during_decode=${values.boolText("engine_holder_open_during_decode", "unavailable")}")
        appendLine("session_create_reached=${values.boolText("session_create_reached")}")
        appendLine("session_create_count=$sessionCreateCount")
        appendLine("prefill_reached=${values.boolText("prefill_reached")}")
        appendLine("decode_reached=${values.boolText("decode_reached")}")
        appendLine("decode_count=$decodeCount")
        appendLine("generate_count=$generateCount")
        appendLine("run_decode_reached=$runDecodeReached")
        appendLine("raw_output=${values["raw_output"] ?: "unavailable"}")
        appendLine("sanitized_output=${values["sanitized_output"] ?: "unavailable"}")
        appendLine("backend_evidence=$backendEvidence")
        appendLine("fallback_used=${values["fallback_used"] ?: "unavailable"}")
        appendLine("timeout=${values["timeout"] ?: "unavailable"}")
        appendLine("fresh_crash=${values["fresh_crash"] ?: "unavailable"}")
        appendLine("engine_close_count=$engineCloseCount")
        appendLine("engine_close_succeeded=$closeSucceeded")
        appendLine("engine_fatal_latch=$fatal")
        appendLine("restart_app_recommended=$fatal")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("persistent_multi_turn_possible=false")
        appendLine("engine_reuse_observed=$engineReuseObserved")
        appendLine("recommended_next_step=$NPU_TRUE_ENGINE_HELD_RUN_ONCE_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuTrueEngineHeldRunOnceFullDumpForCopy(
    state: NpuTrueEngineHeldRunOnceProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU true engine held run once full dump]")
    appendLine("test_name=$NPU_TRUE_ENGINE_HELD_RUN_ONCE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("holder_id=${state.holderId}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    val result = state.nativeResult
    if (result == null) {
        appendLine(NPU_TRUE_ENGINE_HELD_RUN_ONCE_NO_RESULT)
        appendLine()
        appendLine(formatNpuTrueEngineHeldRunOnceSummaryForCopy(state))
        return@buildString
    }
    appendLine("native_return=${result.nativeReturn}")
    appendLine("throwable_class=${result.throwableClass}")
    appendLine("throwable_message=${result.throwableMessage}")
    appendLine("native_result_begin")
    appendLine(result.resultText.ifBlank { "unavailable" })
    appendLine("native_result_end")
    appendLine("native_diag_begin")
    appendLine(result.diagText.ifBlank { "unavailable" })
    appendLine("native_diag_end")
    appendLine()
    appendLine(formatNpuTrueEngineHeldRunOnceSummaryForCopy(state))
}.trimEnd()

internal fun formatNpuTrueEngineEntrypointSummaryForCopy(
    state: NpuTrueEngineEntrypointProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_TRUE_ENGINE_ENTRYPOINT_NO_RESULT\n" +
            "test_name=$NPU_TRUE_ENGINE_ENTRYPOINT_TEST_NAME\n" +
            "probe_status=idle\n" +
            "probe_reason=not_run\n" +
            "true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}\n" +
            "selected_native_probe_mode=$NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE\n" +
            "entrypoint_only_probe_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}\n" +
            "entrypoint_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED}\n" +
            "isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}\n" +
            "isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}\n" +
            "probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}\n" +
            "entrypoint_only_probe_execution_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}\n" +
            "startup_native_call_blocked=true\n" +
            "native_call_deferred_until_button_click=true\n" +
            "native_entrypoint_reached=false\n" +
            "model_assets_create_reached=false\n" +
            "engine_settings_create_reached=false\n" +
            "engine_create_reached=false\n" +
            "session_create_count=0\n" +
            "decode_count=0\n" +
            "generate_count=0\n" +
            "restart_app_recommended=false\n" +
            "true_engine_persistent_reuse=false\n" +
            "engine_reuse_observed=unavailable"
    }
    val values = state.values
    val nativeEntrypointReached = when {
        values.boolText("native_entrypoint_reached") == "true" -> "true"
        values["last_native_stage"] == "entrypoint" -> "true"
        values["hypothesis_result"] == "entrypoint_only_success" -> "true"
        else -> values.boolText("native_entrypoint_reached", "false")
    }
    val modelAssetsReached = values.boolText("model_assets_create_reached", "false")
    val settingsReached = values.boolText("engine_settings_create_reached", "false")
    val engineCreateReached = values.boolText("engine_create_reached", "false")
    val sessionCreateCount = values["session_create_count"]
        ?: values.countFromReachedFlag("session_create_reached").takeIf { it != "unavailable" }
        ?: "0"
    val decodeCount = values["decode_count"]
        ?: values["decode_attempt_count"]
        ?: values.countFromReachedFlag("decode_reached").takeIf { it != "unavailable" }
        ?: "0"
    val generateCount = values["generate_count"] ?: "0"
    val throwableClass = state.nativeResult?.throwableClass ?: "unavailable"
    val fatal = throwableClass != "unavailable" || state.status == "failed"
    return buildString {
        appendLine("[DEV診断: NPU true engine entrypoint summary]")
        appendLine("test_name=$NPU_TRUE_ENGINE_ENTRYPOINT_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("probe_reason=${state.reason}")
        appendLine("true_engine_probe_flavor=${values["true_engine_probe_flavor"] ?: npuTrueEngineHolderCreateCloseProbeVariantName()}")
        appendLine("selected_native_probe_mode=${values["selected_native_probe_mode"] ?: NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE}")
        appendLine("entrypoint_only_probe_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}")
        appendLine("entrypoint_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED}")
        appendLine("isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}")
        appendLine("isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}")
        appendLine("probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}")
        appendLine("entrypoint_only_probe_execution_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}")
        appendLine("startup_native_call_blocked=true")
        appendLine("native_call_deferred_until_button_click=true")
        appendLine("native_entrypoint_reached=$nativeEntrypointReached")
        appendLine("last_native_stage=${values["last_native_stage"] ?: "unavailable"}")
        appendLine("hypothesis_result=${values["hypothesis_result"] ?: "unavailable"}")
        appendLine("model_assets_create_reached=$modelAssetsReached")
        appendLine("engine_settings_create_reached=$settingsReached")
        appendLine("engine_create_reached=$engineCreateReached")
        appendLine("session_create_count=$sessionCreateCount")
        appendLine("prefill_reached=${values.boolText("prefill_reached", "false")}")
        appendLine("decode_count=$decodeCount")
        appendLine("generate_count=$generateCount")
        appendLine("npu_decode_called=false")
        appendLine("qnn_decode_called=false")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("restart_app_recommended=$fatal")
        appendLine("recommended_next_step=$NPU_TRUE_ENGINE_ENTRYPOINT_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuTrueEngineEntrypointFullDumpForCopy(
    state: NpuTrueEngineEntrypointProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU true engine entrypoint full dump]")
    appendLine("test_name=$NPU_TRUE_ENGINE_ENTRYPOINT_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    val result = state.nativeResult
    if (result == null) {
        appendLine(NPU_TRUE_ENGINE_ENTRYPOINT_NO_RESULT)
        appendLine()
        appendLine(formatNpuTrueEngineEntrypointSummaryForCopy(state))
        return@buildString
    }
    appendLine("native_return=${result.nativeReturn}")
    appendLine("throwable_class=${result.throwableClass}")
    appendLine("throwable_message=${result.throwableMessage}")
    appendLine("native_result_begin")
    appendLine(result.resultText.ifBlank { "unavailable" })
    appendLine("native_result_end")
    appendLine("native_diag_begin")
    appendLine(result.diagText.ifBlank { "unavailable" })
    appendLine("native_diag_end")
    appendLine()
    appendLine(formatNpuTrueEngineEntrypointSummaryForCopy(state))
}.trimEnd()



internal fun formatNpuTrueEngineModelAssetsSummaryForCopy(
    state: NpuTrueEngineModelAssetsProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_TRUE_ENGINE_MODEL_ASSETS_NO_RESULT\n" +
            "test_name=$NPU_TRUE_ENGINE_MODEL_ASSETS_TEST_NAME\n" +
            "probe_status=idle\n" +
            "probe_reason=not_run\n" +
            "true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}\n" +
            "selected_native_probe_mode=$NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE\n" +
            "model_assets_only_probe_available=${npuTrueEngineModelAssetsProbeExecutionAvailable()}\n" +
            "model_assets_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED}\n" +
            "isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}\n" +
            "isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}\n" +
            "probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}\n" +
            "model_assets_only_probe_execution_available=${npuTrueEngineModelAssetsProbeExecutionAvailable()}\n" +
            "startup_native_call_blocked=true\n" +
            "native_call_deferred_until_button_click=true\n" +
            "native_entrypoint_reached=false\n" +
            "model_assets_create_reached=false\n" +
            "model_assets_create_returned=false\n" +
            "model_assets_create_succeeded=false\n" +
            "engine_settings_create_reached=false\n" +
            "engine_create_reached=false\n" +
            "session_create_count=0\n" +
            "decode_count=0\n" +
            "generate_count=0\n" +
            "restart_app_recommended=false\n" +
            "true_engine_persistent_reuse=false\n" +
            "engine_reuse_observed=unavailable"
    }
    val values = state.values
    val displayStatus = modelAssetsDisplayStatus(state)
    val displayReason = modelAssetsDisplayReason(state)
    val selectedNativeProbeMode = modelAssetsNativeProbeMode(values) ?: NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE
    val hypothesisResult = values["hypothesis_result"] ?: values["persistent_custom_jni_hypothesis_result"] ?: "unavailable"
    val nativeEntrypointReached = when {
        values.boolText("native_entrypoint_reached") == "true" -> "true"
        values["last_native_stage"]?.startsWith("model_assets") == true -> "true"
        values["hypothesis_result"] == "model_assets_only_success" -> "true"
        values["persistent_custom_jni_hypothesis_result"] == "model_assets_only_success" -> "true"
        else -> values.boolText("native_entrypoint_reached", "false")
    }
    val modelAssetsReached = values.boolText("model_assets_create_reached", "false")
    val modelAssetsReturned = values.boolText("model_assets_create_returned", "false")
    val modelAssetsSucceeded = values.boolText("model_assets_create_succeeded", modelAssetsReturned)
    val settingsReached = values.boolText("engine_settings_create_reached", "false")
    val engineCreateReached = values.boolText("engine_create_reached", "false")
    val sessionCreateCount = values["session_create_count"]
        ?: values.countFromReachedFlag("session_create_reached").takeIf { it != "unavailable" }
        ?: "0"
    val decodeCount = values["decode_count"]
        ?: values["decode_attempt_count"]
        ?: values.countFromReachedFlag("decode_reached").takeIf { it != "unavailable" }
        ?: "0"
    val generateCount = values["generate_count"] ?: "0"
    val throwableClass = state.nativeResult?.throwableClass ?: "unavailable"
    val nativeFatal = throwableClass != "unavailable" ||
        values.boolText("native_fatal_latch", "false") == "true" ||
        values.boolText("restart_app_recommended", "false") == "true"
    return buildString {
        appendLine("[DEV診断: NPU true engine model assets summary]")
        appendLine("test_name=$NPU_TRUE_ENGINE_MODEL_ASSETS_TEST_NAME")
        appendLine("probe_status=$displayStatus")
        appendLine("probe_reason=$displayReason")
        appendLine("true_engine_probe_flavor=${values["true_engine_probe_flavor"] ?: npuTrueEngineHolderCreateCloseProbeVariantName()}")
        appendLine("selected_native_probe_mode=$selectedNativeProbeMode")
        appendLine("model_assets_only_probe_available=${npuTrueEngineModelAssetsProbeExecutionAvailable()}")
        appendLine("model_assets_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED}")
        appendLine("isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}")
        appendLine("isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}")
        appendLine("probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}")
        appendLine("model_assets_only_probe_execution_available=${npuTrueEngineModelAssetsProbeExecutionAvailable()}")
        appendLine("startup_native_call_blocked=true")
        appendLine("native_call_deferred_until_button_click=true")
        appendLine("native_entrypoint_reached=$nativeEntrypointReached")
        appendLine("last_native_stage=${values["last_native_stage"] ?: "unavailable"}")
        appendLine("hypothesis_result=$hypothesisResult")
        appendLine("model_assets_create_reached=$modelAssetsReached")
        appendLine("model_assets_create_returned=$modelAssetsReturned")
        appendLine("model_assets_create_succeeded=$modelAssetsSucceeded")
        appendLine("engine_settings_create_reached=$settingsReached")
        appendLine("engine_create_reached=$engineCreateReached")
        appendLine("session_create_count=$sessionCreateCount")
        appendLine("prefill_reached=${values.boolText("prefill_reached", "false")}")
        appendLine("decode_count=$decodeCount")
        appendLine("generate_count=$generateCount")
        appendLine("npu_decode_called=false")
        appendLine("qnn_decode_called=false")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("restart_app_recommended=$nativeFatal")
        appendLine("recommended_next_step=$NPU_TRUE_ENGINE_MODEL_ASSETS_RECOMMENDED_NEXT_STEP")
    }.trimEnd()
}

internal fun formatNpuTrueEngineModelAssetsFullDumpForCopy(
    state: NpuTrueEngineModelAssetsProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU true engine model assets full dump]")
    appendLine("test_name=$NPU_TRUE_ENGINE_MODEL_ASSETS_TEST_NAME")
    appendLine("probe_status=${modelAssetsDisplayStatus(state)}")
    appendLine("probe_reason=${modelAssetsDisplayReason(state)}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    val result = state.nativeResult
    if (result == null) {
        appendLine(NPU_TRUE_ENGINE_MODEL_ASSETS_NO_RESULT)
        appendLine()
        appendLine(formatNpuTrueEngineModelAssetsSummaryForCopy(state))
        return@buildString
    }
    appendLine("native_return=${result.nativeReturn}")
    appendLine("throwable_class=${result.throwableClass}")
    appendLine("throwable_message=${result.throwableMessage}")
    appendLine("native_result_begin")
    appendLine(result.resultText.ifBlank { "unavailable" })
    appendLine("native_result_end")
    appendLine("native_diag_begin")
    appendLine(result.diagText.ifBlank { "unavailable" })
    appendLine("native_diag_end")
    appendLine()
    appendLine(formatNpuTrueEngineModelAssetsSummaryForCopy(state))
}.trimEnd()


internal fun formatNpuTrueEngineHolderCreateCloseSummaryForCopy(
    state: NpuTrueEngineHolderCreateCloseProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NO_RESULT\n" +
            "test_name=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_TEST_NAME\n" +
            "true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}\n" +
            "isolated_flavor_available=${BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR}\n" +
            "isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}\n" +
            "isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}\n" +
            "true_engine_create_close_probe_startup_safe=true\n" +
            "native_call_deferred_until_button_click=true\n" +
            "startup_native_call_blocked=true\n" +
            "probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}\n" +
            "probe_execution_block_reason=${npuTrueEngineHolderCreateCloseProbeExecutionBlockReason()}"
    }
    val values = state.values
    val selectedNativeProbeMode = values["selected_native_probe_mode"] ?: "unavailable"
    val trueEngineProbeFlavor = values["true_engine_probe_flavor"]
        ?: npuTrueEngineHolderCreateCloseProbeVariantName()
    val isolatedFlavorAvailable = values.boolText(
        "isolated_flavor_available",
        BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR.toString(),
    )
    val isolatedNativePayloadStaged = values.boolText(
        "isolated_native_payload_staged",
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED.toString(),
    )
    val isolatedNativeExecutionEnabled = values.boolText(
        "isolated_native_execution_enabled",
        BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED.toString(),
    )
    val startupSafe = values.boolText("true_engine_create_close_probe_startup_safe", "true")
    val nativeCallDeferred = values.boolText("native_call_deferred_until_button_click", "true")
    val startupNativeCallBlocked = values.boolText("startup_native_call_blocked", "true")
    val probeExecutionAvailable = values.boolText(
        key = "probe_execution_available",
        fallback = npuTrueEngineHolderCreateCloseProbeExecutionAvailable().toString(),
    )
    val probeExecutionBlockReason = values["probe_execution_block_reason"]
        ?: if (state.status == "blocked") {
            npuTrueEngineHolderCreateCloseProbeExecutionBlockReason()
        } else {
            "unavailable"
        }
    val argumentValidationPassed = values.boolText("argument_validation_passed")
    val runCountValidationSkipped = values.boolText("run_count_validation_skipped_for_create_close_only")
    val modelAssetsCalled = values.boolText("model_assets_create_reached")
    val modelAssetsReturned = values.boolText("model_assets_create_returned")
    val modelAssetsSucceeded = values.boolText("model_assets_create_succeeded", modelAssetsReturned)
    val settingsCalled = values.boolText("engine_settings_create_reached")
    val settingsReturned = values.boolText("engine_settings_create_returned")
    val settingsSucceeded = values.boolText("engine_settings_create_succeeded", settingsReturned)
    val engineCreateCalled = values.boolText("engine_create_reached")
    val engineCreateReturned = values.boolText("engine_create_returned")
    val engineCreateSucceeded = values.boolText("engine_create_succeeded", engineCreateReturned)
    val closeSucceeded = values.boolText("engine_close_success")
    val sessionCreateCount = values["session_create_count"] ?: values.countFromReachedFlag("session_create_reached")
    val decodeCount = values["decode_count"] ?: values["decode_attempt_count"] ?: values.countFromReachedFlag("decode_reached")
    val generateCount = values["generate_count"] ?: "0"
    val firstFailureStage = values["first_failure_stage"] ?: "unavailable"
    val firstFailureReason = values["first_failure_reason"] ?: "unavailable"
    val firstFailureExceptionClass = values["first_failure_exception_class"] ?: "unavailable"
    val firstFailureExceptionMessage = values["first_failure_exception_message"] ?: "unavailable"
    val throwableClass = state.nativeResult?.throwableClass ?: "unavailable"
    val fatalLatch = throwableClass != "unavailable" ||
        state.status == "failed" ||
        values["persistent_custom_jni_status"] == "stopped"
    val recommendedNextStep = values["recommended_next_step"]
        ?: if (state.status == "blocked") {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_SAFE_RECOMMENDED_NEXT_STEP
        } else {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RECOMMENDED_NEXT_STEP
        }
    val createClosePossible = modelAssetsSucceeded == "true" &&
        settingsSucceeded == "true" &&
        engineCreateSucceeded == "true" &&
        closeSucceeded == "true" &&
        sessionCreateCount == "0" &&
        decodeCount == "0" &&
        generateCount == "0" &&
        !fatalLatch
    return buildString {
        appendLine("[DEV診断: NPU true engine holder create close summary]")
        appendLine("test_name=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_TEST_NAME")
        appendLine("probe_status=${state.status}")
        appendLine("probe_reason=${state.reason}")
        appendLine("selected_native_probe_mode=$selectedNativeProbeMode")
        appendLine("true_engine_probe_flavor=$trueEngineProbeFlavor")
        appendLine("isolated_flavor_available=$isolatedFlavorAvailable")
        appendLine("isolated_native_payload_staged=$isolatedNativePayloadStaged")
        appendLine("isolated_native_execution_enabled=$isolatedNativeExecutionEnabled")
        appendLine("true_engine_create_close_probe_startup_safe=$startupSafe")
        appendLine("native_call_deferred_until_button_click=$nativeCallDeferred")
        appendLine("startup_native_call_blocked=$startupNativeCallBlocked")
        appendLine("probe_execution_available=$probeExecutionAvailable")
        appendLine("probe_execution_block_reason=$probeExecutionBlockReason")
        appendLine("argument_validation_passed=$argumentValidationPassed")
        appendLine("run_count_validation_skipped_for_create_close_only=$runCountValidationSkipped")
        appendLine("holder_create_requested=${state.nativeResult != null}")
        appendLine("holder_create_called=$engineCreateCalled")
        appendLine("holder_create_succeeded=$engineCreateSucceeded")
        appendLine("model_assets_create_called=$modelAssetsCalled")
        appendLine("model_assets_create_returned=$modelAssetsReturned")
        appendLine("model_assets_create_succeeded=$modelAssetsSucceeded")
        appendLine("engine_settings_create_called=$settingsCalled")
        appendLine("engine_settings_create_returned=$settingsReturned")
        appendLine("engine_settings_create_succeeded=$settingsSucceeded")
        appendLine("engine_factory_create_called=$engineCreateCalled")
        appendLine("engine_create_called=$engineCreateCalled")
        appendLine("engine_create_returned=$engineCreateReturned")
        appendLine("engine_create_succeeded=$engineCreateSucceeded")
        appendLine("engine_holder_open=false")
        appendLine("engine_holder_id=${state.holderId}")
        appendLine("engine_create_count=${values["engine_create_count"] ?: "unavailable"}")
        appendLine("engine_close_count=${if (values.boolText("engine_close_reached") == "true") "1" else "0"}")
        appendLine("engine_close_succeeded=$closeSucceeded")
        appendLine("engine_double_close_safe=true")
        appendLine("engine_fatal_latch=$fatalLatch")
        appendLine("engine_fatal_reason=${if (fatalLatch) state.reason else "unavailable"}")
        appendLine("first_failure_stage=$firstFailureStage")
        appendLine("first_failure_reason=$firstFailureReason")
        appendLine("first_failure_exception_class=$firstFailureExceptionClass")
        appendLine("first_failure_exception_message=$firstFailureExceptionMessage")
        appendLine("session_create_reached=${values.boolText("session_create_reached")}")
        appendLine("session_create_count=$sessionCreateCount")
        appendLine("prefill_reached=${values.boolText("prefill_reached")}")
        appendLine("decode_reached=${values.boolText("decode_reached")}")
        appendLine("decode_count=$decodeCount")
        appendLine("generate_count=$generateCount")
        appendLine("npu_decode_called=false")
        appendLine("qnn_decode_called=false")
        appendLine("true_engine_persistent_reuse_possible=$createClosePossible")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("restart_app_recommended=$fatalLatch")
        appendLine("recommended_next_step=$recommendedNextStep")
    }.trimEnd()
}

internal fun formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(
    state: NpuTrueEngineHolderCreateCloseProbeState,
): String = buildString {
    appendLine("[DEV診断: NPU true engine holder create close full dump]")
    appendLine("test_name=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_TEST_NAME")
    appendLine("probe_status=${state.status}")
    appendLine("probe_reason=${state.reason}")
    appendLine("model_path_or_reason=${state.modelPathOrReason}")
    appendLine("holder_id=${state.holderId}")
    appendLine("started_at_elapsed_realtime_ms=${state.startedAtElapsedRealtimeMs ?: "unavailable"}")
    appendLine("finished_at_elapsed_realtime_ms=${state.finishedAtElapsedRealtimeMs ?: "unavailable"}")
    val result = state.nativeResult
    if (result == null) {
        appendLine(NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NO_RESULT)
        appendLine()
        appendLine(formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state))
        return@buildString
    }
    appendLine("native_return=${result.nativeReturn}")
    appendLine("throwable_class=${result.throwableClass}")
    appendLine("throwable_message=${result.throwableMessage}")
    appendLine("native_result_begin")
    appendLine(result.resultText.ifBlank { "unavailable" })
    appendLine("native_result_end")
    appendLine("native_diag_begin")
    appendLine(result.diagText.ifBlank { "unavailable" })
    appendLine("native_diag_end")
    appendLine()
    appendLine(formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state))
}.trimEnd()


private fun modelAssetsDisplayStatus(state: NpuTrueEngineModelAssetsProbeState): String =
    normalizeNpuTrueEngineModelAssetsProbeState(state).status

private fun modelAssetsDisplayReason(state: NpuTrueEngineModelAssetsProbeState): String =
    normalizeNpuTrueEngineModelAssetsProbeState(state).reason

private fun modelAssetsNativeProbeMode(values: Map<String, String>): String? =
    values["selected_native_probe_mode"] ?: values["native_probe_mode"]

private fun modelAssetsEffectiveProbeMode(values: Map<String, String>): String =
    modelAssetsNativeProbeMode(values) ?: NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE

private fun modelAssetsNativeReturnCompleted(
    values: Map<String, String>,
    result: NpuTrueEngineHolderNativeResult?,
): Boolean =
    result?.nativeReturn == "completed" ||
        values["native_return"] == "completed" ||
        values["persistent_custom_jni_status"] == "completed"

private fun modelAssetsHypothesisSucceeded(values: Map<String, String>): Boolean =
    values["hypothesis_result"] == "model_assets_only_success" ||
        values["persistent_custom_jni_hypothesis_result"] == "model_assets_only_success"

private fun modelAssetsOnlyCompleted(
    values: Map<String, String>,
    result: NpuTrueEngineHolderNativeResult?,
): Boolean =
    modelAssetsEffectiveProbeMode(values) == NPU_TRUE_ENGINE_MODEL_ASSETS_NATIVE_PROBE_MODE &&
        modelAssetsNativeReturnCompleted(values, result) &&
        modelAssetsHypothesisSucceeded(values) &&
        values.boolText("model_assets_create_reached", "false") == "true" &&
        values.boolText("model_assets_create_returned", "false") == "true" &&
        values.boolText("model_assets_create_succeeded", "false") == "true" &&
        values.boolText("engine_settings_create_reached", "false") == "false" &&
        values.boolText("engine_create_reached", "false") == "false" &&
        (values["session_create_count"] ?: "0") == "0" &&
        (values["decode_count"] ?: values["decode_attempt_count"] ?: "0") == "0" &&
        (values["generate_count"] ?: "0") == "0"

private fun parseNpuTrueEngineHolderKeyValueText(text: String): Map<String, String> =
    linkedMapOf<String, String>().apply {
        text.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val separator = line.indexOf('=')
                if (separator > 0) {
                    put(line.substring(0, separator), line.substring(separator + 1))
                }
            }
    }

private fun Map<String, String>.boolText(key: String): String =
    this[key]?.takeIf { it == "true" || it == "false" } ?: "unavailable"

private fun Map<String, String>.boolText(key: String, fallback: String): String =
    this[key]?.takeIf { it == "true" || it == "false" } ?: fallback

private fun Map<String, String>.countFromReachedFlag(key: String): String =
    when (this[key]) {
        "true" -> "1"
        "false" -> "0"
        else -> "unavailable"
    }
