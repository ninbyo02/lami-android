package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock

internal object NpuStandardRouteS1AppHistory {
    private const val PREFS_NAME = "npu_s1_normal_chat_history"
    private const val KEY_SUCCESSFUL_REQUEST_COUNT = "successful_request_count"

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
        val nextSuccessCount = if (result.status == NpuStandardRouteS1Contract.STATUS_SUCCESS) {
            successCount + 1
        } else {
            successCount
        }
        val editor = prefs.edit()
            .putLong("last_npu_s1_request_finished_at_elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .putString("last_npu_s1_stage", npuStandardRouteS1FailureStage(result))
            .putString("last_npu_s1_status", result.status)
            .putString("last_npu_s1_reason", result.reason)
            .putString("last_npu_s1_exception_class", npuStandardRouteS1FailureExceptionClass(result))
            .putString("last_npu_s1_exception_message", npuStandardRouteS1FailureExceptionMessage(result))
            .putString("last_npu_s1_backend_evidence", result.npuBackendEvidence)
            .putString("last_npu_s1_run_decode_reached", result.runDecodeReached.toString())
            .putString("last_npu_s1_native_stage", result.nativeDiagnostics.nativeStage)
            .putString("last_npu_s1_native_stage_history", result.nativeDiagnostics.nativeStageHistory)
            .putString("last_npu_s1_native_call_reached", result.nativeDiagnostics.nativeCallReached)
            .putString("last_npu_s1_native_call_returned", result.nativeDiagnostics.nativeCallReturned)
            .putString("last_npu_s1_native_decode_started", result.nativeDiagnostics.nativeDecodeStarted)
            .putString("last_npu_s1_native_decode_finished", result.nativeDiagnostics.nativeDecodeFinished)
            .putInt(KEY_SUCCESSFUL_REQUEST_COUNT, nextSuccessCount)
        if (result.status == NpuStandardRouteS1Contract.STATUS_SUCCESS) {
            editor.putString("last_successful_npu_s1_prompt", result.inputPrompt)
        } else {
            editor.putString("last_failed_npu_s1_prompt", result.inputPrompt)
        }
        editor.apply()
    }

    fun recordException(
        context: Context,
        prompt: String,
        throwable: Throwable,
    ) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_npu_s1_request_finished_at_elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .putString("last_npu_s1_prompt", prompt)
            .putString("last_failed_npu_s1_prompt", prompt)
            .putString("last_npu_s1_stage", "kotlin_exception")
            .putString("last_npu_s1_status", "exception")
            .putString("last_npu_s1_reason", throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName })
            .putString("last_npu_s1_exception_class", throwable.javaClass.simpleName)
            .putString("last_npu_s1_exception_message", throwable.message.orEmpty().ifBlank { "unavailable" })
            .apply()
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
            "last_npu_s1_backend_evidence=${prefs.getString("last_npu_s1_backend_evidence", "unavailable")}",
            "last_npu_s1_run_decode_reached=${prefs.getString("last_npu_s1_run_decode_reached", "unavailable")}",
            "last_npu_s1_native_stage=${prefs.getString("last_npu_s1_native_stage", "unavailable")}",
            "last_npu_s1_native_stage_history=${prefs.getString("last_npu_s1_native_stage_history", "unavailable")}",
            "last_npu_s1_native_call_reached=${prefs.getString("last_npu_s1_native_call_reached", "unavailable")}",
            "last_npu_s1_native_call_returned=${prefs.getString("last_npu_s1_native_call_returned", "unavailable")}",
            "last_npu_s1_native_decode_started=${prefs.getString("last_npu_s1_native_decode_started", "unavailable")}",
            "last_npu_s1_native_decode_finished=${prefs.getString("last_npu_s1_native_decode_finished", "unavailable")}",
            "last_successful_npu_s1_prompt=${prefs.copyValue("last_successful_npu_s1_prompt")}",
            "last_failed_npu_s1_prompt=${prefs.copyValue("last_failed_npu_s1_prompt")}",
            "last_npu_s1_previous_successful_request_count=${prefs.getInt("last_npu_s1_previous_successful_request_count", 0)}",
            "successful_npu_s1_request_count=${prefs.getInt(KEY_SUCCESSFUL_REQUEST_COUNT, 0)}",
        ).joinToString("\n")
    }

    private fun Long.toUnavailableIfNegative(): String =
        if (this < 0L) "unavailable" else toString()

    private fun android.content.SharedPreferences.copyValue(key: String): String =
        npuStandardRouteS1EscapeCopyValue(getString(key, "unavailable").orEmpty())
}
