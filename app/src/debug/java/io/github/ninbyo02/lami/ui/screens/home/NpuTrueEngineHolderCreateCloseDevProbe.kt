package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuTrueEngineHolderCreateCloseDevProbe(
    context: Context,
) : NpuTrueEngineHolderCreateCloseProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuTrueEngineHolderCreateCloseProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val holderId = "true-engine-holder-create-close-dev"
        if (!npuTrueEngineHolderCreateCloseProbeExecutionAvailable()) {
            return@withContext NpuTrueEngineHolderCreateCloseProbeState(
                status = "blocked",
                reason = npuTrueEngineHolderCreateCloseProbeExecutionBlockReason(),
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = "not_resolved_startup_safe_block",
                holderId = holderId,
                nativeResult = blockedNpuTrueEngineHolderCreateCloseNativeResult(),
            )
        }

        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        if (modelPath.isBlank()) {
            return@withContext NpuTrueEngineHolderCreateCloseProbeState(
                status = "failed",
                reason = "model_resolution_failed:${modelResolution.reasonCode}",
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelResolution.reasonCode,
                holderId = holderId,
                nativeResult = trueEngineHolderCreateCloseNotStartedNativeResult(
                    reason = "model_resolution_failed:${modelResolution.reasonCode}",
                ),
            )
        }

        val nativeResult = Qairt244ShortMultitokenSmoke.runTrueEngineHolderCreateCloseProbe(
            context = appContext,
            modelPath = modelPath,
            runId = "npu_true_engine_create_close_${SystemClock.elapsedRealtime()}",
            maxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
            holderKey = listOf(
                BuildConfig.CURRENT_FLAVOR,
                modelPath,
                appContext.applicationInfo.nativeLibraryDir,
                appContext.cacheDir.absolutePath,
                "create_close_only",
            ).joinToString(separator = "|"),
        )
        val values = parseTrueEngineCreateCloseKeyValueText(nativeResult.resultText)
        val throwableRaised = nativeResult.throwableClass != "unavailable"
        val nativeStatus = values["persistent_custom_jni_status"]
        val completed = !throwableRaised &&
            nativeStatus == "completed" &&
            values["engine_create_succeeded"] == "true" &&
            values["engine_close_success"] == "true" &&
            values["session_create_count"] == "0" &&
            (values["decode_count"] ?: values["decode_attempt_count"] ?: "0") == "0" &&
            (values["generate_count"] ?: "0") == "0"
        val reason = when {
            completed -> "create_close_only_completed"
            throwableRaised -> nativeResult.throwableMessage
                .takeIf { it != "unavailable" }
                ?: nativeResult.throwableClass
            nativeStatus != null -> nativeStatus
            else -> "native_result_unavailable"
        }
        NpuTrueEngineHolderCreateCloseProbeState(
            status = if (completed) "completed" else "failed",
            reason = reason,
            startedAtElapsedRealtimeMs = startedAt,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            modelPathOrReason = modelPath,
            holderId = holderId,
            nativeResult = nativeResult,
        )
    }
}

internal class NpuTrueEngineEntrypointDevProbe(
    context: Context,
) : NpuTrueEngineEntrypointProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuTrueEngineEntrypointProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        if (!npuTrueEngineEntrypointProbeExecutionAvailable()) {
            return@withContext NpuTrueEngineEntrypointProbeState(
                status = "blocked",
                reason = npuTrueEngineEntrypointProbeExecutionBlockReason(),
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = "not_resolved_startup_safe_block",
                nativeResult = trueEngineEntrypointBlockedNativeResult(),
            )
        }

        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        if (modelPath.isBlank()) {
            return@withContext NpuTrueEngineEntrypointProbeState(
                status = "failed",
                reason = "model_resolution_failed:${modelResolution.reasonCode}",
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelResolution.reasonCode,
                nativeResult = trueEngineEntrypointNotStartedNativeResult(
                    reason = "model_resolution_failed:${modelResolution.reasonCode}",
                ),
            )
        }

        val persistentResult = Qairt244ShortMultitokenSmoke.runPersistentProbe(
            context = appContext,
            modelPath = modelPath,
            runId = "npu_true_engine_entrypoint_${SystemClock.elapsedRealtime()}",
            prompt = "こんにちは",
            maxOutputTokens = 1,
            runCount = 1,
            holderKey = listOf(
                BuildConfig.CURRENT_FLAVOR,
                modelPath,
                appContext.applicationInfo.nativeLibraryDir,
                appContext.cacheDir.absolutePath,
                "entrypoint_only",
            ).joinToString(separator = "|"),
            nativeProbeMode = NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE,
            promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
        )
        val nativeResult = NpuTrueEngineHolderNativeResult(
            nativeReturn = persistentResult.nativeReturn,
            resultText = persistentResult.resultText,
            diagText = persistentResult.diagText,
            throwableClass = persistentResult.throwableClass,
            throwableMessage = persistentResult.throwableMessage,
        )
        val values = parseTrueEngineCreateCloseKeyValueText(nativeResult.resultText)
        val throwableRaised = nativeResult.throwableClass != "unavailable"
        val entrypointReached = values["native_entrypoint_reached"] == "true" ||
            values["last_native_stage"] == "entrypoint" ||
            values["hypothesis_result"] == "entrypoint_only_success"
        val completed = !throwableRaised &&
            (nativeResult.nativeReturn == "completed" || values["persistent_custom_jni_status"] == "completed") &&
            values["selected_native_probe_mode"] == NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE &&
            entrypointReached &&
            values["model_assets_create_reached"] != "true" &&
            values["engine_settings_create_reached"] != "true" &&
            values["engine_create_reached"] != "true" &&
            (values["session_create_count"] ?: "0") == "0" &&
            (values["decode_count"] ?: values["decode_attempt_count"] ?: "0") == "0" &&
            (values["generate_count"] ?: "0") == "0"
        val reason = when {
            completed -> "entrypoint_only_completed"
            throwableRaised -> nativeResult.throwableMessage
                .takeIf { it != "unavailable" }
                ?: nativeResult.throwableClass
            values["first_failure_reason"] != null -> values["first_failure_reason"] ?: "native_failure"
            else -> "entrypoint_only_result_unavailable"
        }
        NpuTrueEngineEntrypointProbeState(
            status = if (completed) "completed" else "failed",
            reason = reason,
            startedAtElapsedRealtimeMs = startedAt,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            modelPathOrReason = modelPath,
            nativeResult = nativeResult,
        )
    }
}

private fun trueEngineEntrypointBlockedNativeResult(): NpuTrueEngineHolderNativeResult =
    NpuTrueEngineHolderNativeResult(
        nativeReturn = "blocked",
        resultText = """
            selected_native_probe_mode=$NPU_TRUE_ENGINE_ENTRYPOINT_NATIVE_PROBE_MODE
            true_engine_probe_flavor=${npuTrueEngineHolderCreateCloseProbeVariantName()}
            entrypoint_only_probe_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}
            entrypoint_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED}
            isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}
            isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}
            probe_execution_available=${npuTrueEngineHolderCreateCloseProbeExecutionAvailable()}
            entrypoint_only_probe_execution_available=${npuTrueEngineEntrypointProbeExecutionAvailable()}
            startup_native_call_blocked=true
            native_call_deferred_until_button_click=true
            native_entrypoint_reached=false
            model_assets_create_reached=false
            engine_settings_create_reached=false
            engine_create_reached=false
            session_create_count=0
            prefill_reached=false
            decode_count=0
            generate_count=0
            true_engine_persistent_reuse=false
            engine_reuse_observed=unavailable
            recommended_next_step=$NPU_TRUE_ENGINE_ENTRYPOINT_RECOMMENDED_NEXT_STEP
        """.trimIndent(),
    )

private fun trueEngineEntrypointNotStartedNativeResult(reason: String): NpuTrueEngineHolderNativeResult =
    trueEngineEntrypointBlockedNativeResult().copy(
        nativeReturn = "not_started",
        throwableMessage = reason,
    )


private fun trueEngineHolderCreateCloseNotStartedNativeResult(reason: String): NpuTrueEngineHolderNativeResult =
    NpuTrueEngineHolderNativeResult(
        nativeReturn = "not_started",
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
            probe_execution_block_reason=unavailable
            argument_validation_passed=false
            run_count_validation_skipped_for_create_close_only=unavailable
            persistent_custom_jni_status=not_started
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
            recommended_next_step=provide_model_then_retry_true_engine_create_close_only
        """.trimIndent(),
        throwableClass = "unavailable",
        throwableMessage = reason,
    )

private fun parseTrueEngineCreateCloseKeyValueText(text: String): Map<String, String> =
    text.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator > 0) {
                line.substring(0, separator) to line.substring(separator + 1)
            } else {
                null
            }
        }
        .toMap()
