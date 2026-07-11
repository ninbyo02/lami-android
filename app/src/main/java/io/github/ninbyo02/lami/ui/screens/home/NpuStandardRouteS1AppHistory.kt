package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import io.github.ninbyo02.lami.ui.screens.settings.LocalNpuRuntimeHistorySnapshot
import io.github.ninbyo02.lami.ui.screens.settings.toLocalBackendRuntimeEvidence

internal object NpuStandardRouteS1AppHistory {
    private const val PREFS_NAME = "npu_s1_normal_chat_history"
    private const val KEY_SUCCESSFUL_REQUEST_COUNT = "successful_request_count"
    private const val KEY_ENGINE_CREATE_FAILURE_COUNT = "engine_create_failure_count"
    private const val KEY_LAST_SUCCESS_FINISHED_AT = "last_successful_npu_s1_request_finished_at_elapsed_realtime_ms"
    private const val KEY_LAST_ENGINE_CREATE_FAILURE_AT = "last_engine_create_failure_at_elapsed_realtime_ms"

    fun recordStarted(
        context: Context,
        prompt: String,
        selectedModelFile: String?,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousSuccessCount = prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0)
        prefs.edit()
            .putLong("last_npu_s1_request_started_at_elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .putString("last_npu_s1_prompt", prompt)
            .putString("last_npu_s1_final_prompt_tail", NpuStandardRouteS1Contract.finalPromptTail(prompt))
            .putString("last_npu_s1_prompt_profile", NpuStandardRouteS1Contract.PROMPT_WRAPPER_USED)
            .putString("last_npu_s1_model_path", selectedModelFile.orEmpty().ifBlank { "unknown" })
            .putString("last_npu_s1_stage", "request_started")
            .putString("last_npu_s1_status", "running")
            .putString("last_npu_s1_reason", "running")
            .putString("last_npu_s1_exception_class", "unavailable")
            .putString("last_npu_s1_exception_message", "unavailable")
            .putInt("last_npu_s1_previous_successful_request_count", previousSuccessCount)
            .apply()
    }

    fun recordFinished(
        context: Context,
        result: NpuStandardRouteS1Result,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val successCount = prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0)
        val finishedAt = SystemClock.elapsedRealtime()
        val nextSuccessCount = if (result.successCriteriaMet) {
            successCount + 1
        } else {
            successCount
        }
        val engineCreateFailed = isNpuStandardRouteS1EngineCreateFailed(result)
        val lastSuccessFinishedAt = prefs.getLong(KEY_LAST_SUCCESS_FINISHED_AT, -1L)
        val failureAfterLastSuccessElapsedMs = if (!result.successCriteriaMet && lastSuccessFinishedAt >= 0L) {
            (finishedAt - lastSuccessFinishedAt).coerceAtLeast(0L)
        } else {
            -1L
        }
        val editor = prefs.edit()
            .putLong("last_npu_s1_request_finished_at_elapsed_realtime_ms", finishedAt)
            .putString("last_npu_s1_stage", npuStandardRouteS1FailureStage(result))
            .putString("last_npu_s1_status", result.status)
            .putString("last_npu_s1_reason", result.reason)
            .putString("last_npu_s1_exception_class", npuStandardRouteS1FailureExceptionClass(result))
            .putString("last_npu_s1_exception_message", npuStandardRouteS1FailureExceptionMessage(result))
            .putString("npu_s1_failure_kind", npuStandardRouteS1FailureKind(result))
            .putString("npu_s1_failure_layer", npuStandardRouteS1FailureLayer(result))
            .putString("npu_s1_failure_recovery_hint", npuStandardRouteS1FailureRecoveryHint(result))
            .putString("last_npu_s1_backend_evidence", result.npuBackendEvidence)
            .putString("last_npu_s1_run_decode_reached", result.runDecodeReached.toString())
            .putString("last_npu_s1_native_stage", result.nativeDiagnostics.nativeStage)
            .putString("last_npu_s1_native_stage_history", result.nativeDiagnostics.nativeStageHistory)
            .putString("last_npu_s1_native_call_reached", result.nativeDiagnostics.nativeCallReached)
            .putString("last_npu_s1_native_call_returned", result.nativeDiagnostics.nativeCallReturned)
            .putString("last_npu_s1_native_decode_started", result.nativeDiagnostics.nativeDecodeStarted)
            .putString("last_npu_s1_native_decode_finished", result.nativeDiagnostics.nativeDecodeFinished)
            .putString("last_npu_s1_success_criteria_met", result.successCriteriaMet.toString())
            .putInt(KEY_SUCCESSFUL_REQUEST_COUNT, nextSuccessCount)
        if (result.successCriteriaMet) {
            editor.putString("last_successful_npu_s1_prompt", result.inputPrompt)
                .putLong(KEY_LAST_SUCCESS_FINISHED_AT, finishedAt)
        } else {
            editor.putString("last_failed_npu_s1_prompt", result.inputPrompt)
        }
        if (!result.successCriteriaMet) {
            editor.putInt("failure_after_successful_npu_s1_request_count", successCount)
                .putLong("failure_after_last_success_elapsed_ms", failureAfterLastSuccessElapsedMs)
                .putBoolean("last_failure_was_engine_create_failed", engineCreateFailed)
                .putString("native_crash_risk_hint", npuStandardRouteS1NativeCrashRiskHint(result))
            if (engineCreateFailed) {
                editor.putInt(
                    KEY_ENGINE_CREATE_FAILURE_COUNT,
                    prefs.getInt(KEY_ENGINE_CREATE_FAILURE_COUNT, 0) + 1,
                )
                    .putLong(KEY_LAST_ENGINE_CREATE_FAILURE_AT, finishedAt)
            }
        }
        editor.apply()
    }

    fun recordException(
        context: Context,
        prompt: String,
        throwable: Throwable,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val finishedAt = SystemClock.elapsedRealtime()
        val engineCreateFailed = throwable.javaClass.simpleName == "LiteRtLmJniException" &&
            throwable.message.orEmpty().contains("engine-create-failed", ignoreCase = true)
        val lastSuccessFinishedAt = prefs.getLong(KEY_LAST_SUCCESS_FINISHED_AT, -1L)
        val failureAfterLastSuccessElapsedMs = if (lastSuccessFinishedAt >= 0L) {
            (finishedAt - lastSuccessFinishedAt).coerceAtLeast(0L)
        } else {
            -1L
        }
        val editor = prefs.edit()
            .putLong("last_npu_s1_request_finished_at_elapsed_realtime_ms", finishedAt)
            .putString("last_npu_s1_prompt", prompt)
            .putString("last_failed_npu_s1_prompt", prompt)
            .putString("last_npu_s1_stage", "kotlin_exception")
            .putString("last_npu_s1_status", "exception")
            .putString("last_npu_s1_reason", throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName })
            .putString("last_npu_s1_exception_class", throwable.javaClass.simpleName)
            .putString("last_npu_s1_exception_message", throwable.message.orEmpty().ifBlank { "unavailable" })
            .putString(
                "npu_s1_failure_kind",
                if (engineCreateFailed) NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED else "unavailable",
            )
            .putString(
                "npu_s1_failure_layer",
                if (engineCreateFailed) "litert_npu_compiled_model_executor" else "unavailable",
            )
            .putString(
                "npu_s1_failure_recovery_hint",
                if (engineCreateFailed) "recreate_app_or_wait_before_retry" else "unavailable",
            )
            .putInt("failure_after_successful_npu_s1_request_count", prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0))
            .putLong("failure_after_last_success_elapsed_ms", failureAfterLastSuccessElapsedMs)
            .putBoolean("last_failure_was_engine_create_failed", engineCreateFailed)
            .putString(
                "native_crash_risk_hint",
                if (engineCreateFailed) {
                    "engine_create_failed_near_litert_compiled_model_dispatch_delegate_check_tombstone_dropbox"
                } else {
                    "unavailable"
                },
            )
        if (engineCreateFailed) {
            editor.putInt(
                KEY_ENGINE_CREATE_FAILURE_COUNT,
                prefs.getInt(KEY_ENGINE_CREATE_FAILURE_COUNT, 0) + 1,
            )
                .putLong(KEY_LAST_ENGINE_CREATE_FAILURE_AT, finishedAt)
        }
        editor.apply()
    }

    fun runtimeEvidence(
        context: Context,
        currentNpuModelPath: String?,
    ): LocalBackendRuntimeEvidence {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LocalNpuRuntimeHistorySnapshot(
            latestModelPath = prefs.getString("last_npu_s1_model_path", "unavailable").orEmpty(),
            latestStatus = prefs.getString("last_npu_s1_status", "unavailable").orEmpty(),
            latestReason = prefs.getString("last_npu_s1_reason", "unavailable").orEmpty(),
            latestStage = prefs.getString("last_npu_s1_stage", "unavailable").orEmpty(),
            latestBackendEvidence = prefs.getString("last_npu_s1_backend_evidence", "unavailable").orEmpty(),
            latestRunDecodeReached = prefs.getString("last_npu_s1_run_decode_reached", "unavailable").orEmpty(),
            latestNativeCallReturned = prefs.getString("last_npu_s1_native_call_returned", "unavailable").orEmpty(),
            latestNativeDecodeFinished = prefs.getString("last_npu_s1_native_decode_finished", "unavailable").orEmpty(),
            latestSuccessCriteriaMet = prefs.getString("last_npu_s1_success_criteria_met", "unavailable").orEmpty(),
            successfulRequestCount = prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0),
        ).toLocalBackendRuntimeEvidence(currentNpuModelPath)
    }

    fun formatForDev(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return listOf(
            "[DEV診断: NPU S1 normal chat app history]",
            "last_npu_s1_request_started_at_elapsed_realtime_ms=${prefs.getLong("last_npu_s1_request_started_at_elapsed_realtime_ms", -1L).toUnavailableIfNegative()}",
            "last_npu_s1_request_finished_at_elapsed_realtime_ms=${prefs.getLong("last_npu_s1_request_finished_at_elapsed_realtime_ms", -1L).toUnavailableIfNegative()}",
            "last_npu_s1_prompt=${prefs.copyValue("last_npu_s1_prompt")}",
            "last_npu_s1_final_prompt_tail=${prefs.copyValue("last_npu_s1_final_prompt_tail")}",
            "last_npu_s1_prompt_profile=${prefs.getString("last_npu_s1_prompt_profile", "unavailable")}",
            "last_npu_s1_model_path=${prefs.copyValue("last_npu_s1_model_path")}",
            "last_npu_s1_stage=${prefs.getString("last_npu_s1_stage", "unavailable")}",
            "last_npu_s1_status=${prefs.getString("last_npu_s1_status", "unavailable")}",
            "last_npu_s1_reason=${prefs.getString("last_npu_s1_reason", "unavailable")}",
            "last_npu_s1_exception_class=${prefs.getString("last_npu_s1_exception_class", "unavailable")}",
            "last_npu_s1_exception_message=${prefs.copyValue("last_npu_s1_exception_message")}",
            "npu_s1_failure_kind=${prefs.getString("npu_s1_failure_kind", "unavailable")}",
            "npu_s1_failure_layer=${prefs.getString("npu_s1_failure_layer", "unavailable")}",
            "npu_s1_failure_recovery_hint=${prefs.getString("npu_s1_failure_recovery_hint", "unavailable")}",
            "last_npu_s1_backend_evidence=${prefs.getString("last_npu_s1_backend_evidence", "unavailable")}",
            "last_npu_s1_run_decode_reached=${prefs.getString("last_npu_s1_run_decode_reached", "unavailable")}",
            "last_npu_s1_native_stage=${prefs.getString("last_npu_s1_native_stage", "unavailable")}",
            "last_npu_s1_native_stage_history=${prefs.getString("last_npu_s1_native_stage_history", "unavailable")}",
            "last_npu_s1_native_call_reached=${prefs.getString("last_npu_s1_native_call_reached", "unavailable")}",
            "last_npu_s1_native_call_returned=${prefs.getString("last_npu_s1_native_call_returned", "unavailable")}",
            "last_npu_s1_native_decode_started=${prefs.getString("last_npu_s1_native_decode_started", "unavailable")}",
            "last_npu_s1_native_decode_finished=${prefs.getString("last_npu_s1_native_decode_finished", "unavailable")}",
            "last_npu_s1_success_criteria_met=${prefs.getString("last_npu_s1_success_criteria_met", "unavailable")}",
            "last_successful_npu_s1_prompt=${prefs.copyValue("last_successful_npu_s1_prompt")}",
            "last_failed_npu_s1_prompt=${prefs.copyValue("last_failed_npu_s1_prompt")}",
            "last_npu_s1_previous_successful_request_count=${prefs.getInt("last_npu_s1_previous_successful_request_count", 0)}",
            "successful_npu_s1_request_count=${prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0)}",
            "engine_create_failure_count=${prefs.getInt(KEY_ENGINE_CREATE_FAILURE_COUNT, 0)}",
            "last_engine_create_failure_at_elapsed_realtime_ms=${prefs.getLong(KEY_LAST_ENGINE_CREATE_FAILURE_AT, -1L).toUnavailableIfNegative()}",
            "failure_after_successful_npu_s1_request_count=${prefs.getInt("failure_after_successful_npu_s1_request_count", 0)}",
            "failure_after_last_success_elapsed_ms=${prefs.getLong("failure_after_last_success_elapsed_ms", -1L).toUnavailableIfNegative()}",
            "last_failure_was_engine_create_failed=${prefs.getBoolean("last_failure_was_engine_create_failed", false)}",
            "native_crash_risk_hint=${prefs.getString("native_crash_risk_hint", "unavailable")}",
        ).joinToString("\n")
    }

    private fun Long.toUnavailableIfNegative(): String =
        if (this < 0L) "unavailable" else toString()

    private fun android.content.SharedPreferences.copyValue(key: String): String =
        npuStandardRouteS1EscapeCopyValue(getString(key, "unavailable").orEmpty())
}
