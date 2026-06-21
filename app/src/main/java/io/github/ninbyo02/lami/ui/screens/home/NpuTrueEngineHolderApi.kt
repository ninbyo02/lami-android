package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

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
internal const val NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RECOMMENDED_NEXT_STEP =
    "review_true_engine_create_close_device_result_then_implement_held_engine_run_once"

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

internal fun createNpuTrueEngineHolderCreateCloseProbeRunner(
    context: Context,
): NpuTrueEngineHolderCreateCloseProbeRunner? =
    runCatching {
        Class.forName(NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuTrueEngineHolderCreateCloseProbeRunner
    }.getOrNull()

internal fun formatNpuTrueEngineHolderCreateCloseSummaryForCopy(
    state: NpuTrueEngineHolderCreateCloseProbeState,
): String {
    if (!state.hasResult) {
        return "$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NO_RESULT\n" +
            "test_name=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_TEST_NAME"
    }
    val values = state.values
    val modelAssetsCalled = values.boolText("model_assets_create_reached")
    val modelAssetsSucceeded = values.boolText("model_assets_create_returned")
    val settingsCalled = values.boolText("engine_settings_create_reached")
    val settingsSucceeded = values.boolText("engine_settings_create_returned")
    val engineCreateCalled = values.boolText("engine_create_reached")
    val engineCreateSucceeded = values.boolText("engine_create_returned")
    val closeSucceeded = values.boolText("engine_close_success")
    val sessionCreateCount = values.countFromReachedFlag("session_create_reached")
    val decodeCount = values["decode_attempt_count"] ?: values.countFromReachedFlag("decode_reached")
    val generateCount = "0"
    val throwableClass = state.nativeResult?.throwableClass ?: "unavailable"
    val fatalLatch = throwableClass != "unavailable" ||
        state.status == "failed" ||
        values["persistent_custom_jni_status"] == "stopped"
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
        appendLine("holder_create_requested=${state.nativeResult != null}")
        appendLine("holder_create_called=$engineCreateCalled")
        appendLine("holder_create_succeeded=$engineCreateSucceeded")
        appendLine("model_assets_create_called=$modelAssetsCalled")
        appendLine("model_assets_create_succeeded=$modelAssetsSucceeded")
        appendLine("engine_settings_create_called=$settingsCalled")
        appendLine("engine_settings_create_succeeded=$settingsSucceeded")
        appendLine("engine_factory_create_called=$engineCreateCalled")
        appendLine("engine_create_called=$engineCreateCalled")
        appendLine("engine_create_succeeded=$engineCreateSucceeded")
        appendLine("engine_holder_open=false")
        appendLine("engine_holder_id=${state.holderId}")
        appendLine("engine_create_count=${values["engine_create_count"] ?: "unavailable"}")
        appendLine("engine_close_count=${if (values.boolText("engine_close_reached") == "true") "1" else "0"}")
        appendLine("engine_close_succeeded=$closeSucceeded")
        appendLine("engine_double_close_safe=true")
        appendLine("engine_fatal_latch=$fatalLatch")
        appendLine("engine_fatal_reason=${if (fatalLatch) state.reason else "unavailable"}")
        appendLine("session_create_count=$sessionCreateCount")
        appendLine("decode_count=$decodeCount")
        appendLine("generate_count=$generateCount")
        appendLine("npu_decode_called=false")
        appendLine("qnn_decode_called=false")
        appendLine("true_engine_persistent_reuse_possible=$createClosePossible")
        appendLine("true_engine_persistent_reuse=false")
        appendLine("engine_reuse_observed=unavailable")
        appendLine("restart_app_recommended=$fatalLatch")
        appendLine("recommended_next_step=$NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RECOMMENDED_NEXT_STEP")
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

private fun Map<String, String>.countFromReachedFlag(key: String): String =
    when (this[key]) {
        "true" -> "1"
        "false" -> "0"
        else -> "unavailable"
    }
