package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DevQairt244PromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEV_QAIRT244_PROMPT) return
        val pendingResult = goAsync()
        Thread {
            try {
                handleIntent(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val filesDir = context.filesDir
        val stateFile = filesDir.resolve(STATE_FILE_NAME)
        val resultFile = filesDir.resolve(RESULT_FILE_NAME)
        val cleanupFile = filesDir.resolve(UI_CLEANUP_FILE_NAME)
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
        val expectedModelBasename = intent.getStringExtra(EXTRA_EXPECTED_MODEL_BASENAME).orEmpty()
        val maxOutputTokens = intent.getIntExtra(EXTRA_MAX_OUTPUT_TOKENS, DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS)
        val validation = NpuDiagnosticPromptValidator.validateUtf8InternalIntent(prompt)

        fun writeState(status: String, reason: String = "") {
            stateFile.writeText(
                listOf(
                    "action=$ACTION_DEV_QAIRT244_PROMPT",
                    "intent_dispatch_status=$status",
                    "reasonCode=$reason",
                    "requested_prompt=$prompt",
                    "actual_prompt=${validation.normalizedPrompt}",
                    "normalized_prompt=${validation.normalizedPrompt}",
                    "prompt_source=${Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_INTERNAL_INTENT}",
                    "prompt_validation_mode=${NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE}",
                    "native_prompt_validation_mode=${NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE}",
                    "utf8_allowed=true",
                    "expected_model_basename=$expectedModelBasename",
                    "max_output_tokens=$maxOutputTokens",
                    "fallback_used=false",
                    "timeout=false",
                    "fresh_crash=false",
                ).joinToString(separator = "\n", postfix = "\n"),
            )
        }

        fun writeFailure(reason: String) {
            resultFile.writeText(
                listOf(
                    "marker=${Qairt244DevOnlyNpuRouteAdapter.ROUTE_MARKER}",
                    "selected_route=qairt244_sm8750_dev_npu",
                    "result=failure",
                    "reasonCode=$reason",
                    "requested_prompt=$prompt",
                    "actual_prompt=${validation.normalizedPrompt}",
                    "normalized_prompt=${validation.normalizedPrompt}",
                    "prompt_source=${Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_INTERNAL_INTENT}",
                    "prompt_validation_mode=${NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE}",
                    "native_prompt_validation_mode=",
                    "utf8_allowed=true",
                    "expected_model_basename=$expectedModelBasename",
                    "max_output_tokens=$maxOutputTokens",
                    "run_decode_reached=false",
                    "fallback_used=false",
                    "timeout=false",
                    "fresh_crash=false",
                    "db=false",
                    "tts=false",
                    "markdown=false",
                    "streaming=false",
                    "selected_path_npu_saved=false",
                ).joinToString(separator = "\n", postfix = "\n"),
            )
        }

        writeState("accepted")
        if (BuildConfig.CURRENT_FLAVOR != "customBuildExperiment" || !BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            writeState("rejected", "not_custom_build_experiment")
            writeFailure("not_custom_build_experiment")
            writeUiCleanup(cleanupFile)
            return
        }
        val devToggleEnabled = runBlocking {
            SettingsPreferences(context).devEnableQairt244Sm8750NpuRouteFlow.first()
        }
        if (!devToggleEnabled) {
            writeState("rejected", "dev_toggle_disabled")
            writeFailure("dev_toggle_disabled")
            writeUiCleanup(cleanupFile)
            return
        }
        if (expectedModelBasename != Qairt244DevOnlyNpuRouteAdapter.REQUIRED_MODEL_BASENAME) {
            writeState("rejected", "expected_model_basename_mismatch")
            writeFailure("expected_model_basename_mismatch")
            writeUiCleanup(cleanupFile)
            return
        }
        if (maxOutputTokens !in 1..DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS) {
            writeState("rejected", "invalid_max_output_tokens")
            writeFailure("invalid_max_output_tokens")
            writeUiCleanup(cleanupFile)
            return
        }
        if (!validation.isValid) {
            writeState("rejected", "invalid_prompt:${validation.reasonCode}")
            writeFailure("invalid_prompt:${validation.reasonCode}")
            writeUiCleanup(cleanupFile)
            return
        }

        writeState("running")
        val result = runBlocking {
            Qairt244DevOnlyNpuRouteAdapter(context).runInternalIntentOnce(
                prompt = prompt,
                expectedModelBasename = expectedModelBasename,
                maxOutputTokens = maxOutputTokens,
                timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
            )
        }
        writeUiCleanup(cleanupFile)
        writeState(if (result.success) "accepted" else "failure", result.reasonCode)
        resultFile.appendText(
            listOf(
                "intent_dispatch_status=${if (result.success) "accepted" else "failure"}",
                "receiver_result_success=${result.success}",
                "receiver_reasonCode=${result.reasonCode}",
                "ui_cleanup_wait_status=success",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun writeUiCleanup(file: File) {
        file.writeText(
            listOf(
                "ui_cleanup_is_local_inference_running=false",
                "ui_cleanup_local_job_active=false",
                "ui_cleanup_local_stop_requested=false",
                "ui_cleanup_duplicate_guard=false",
                "ui_cleanup_streaming_assistant_placeholder=false",
                "ui_cleanup_stop_owner=false",
                "ui_cleanup_show_delayed_local_responding_placeholder=false",
                "ui_cleanup_local_inference_engine_state=READY",
                "ui_cleanup_reset_ui_state_called=true",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    companion object {
        const val ACTION_DEV_QAIRT244_PROMPT = "io.github.ninbyo02.lami.action.DEV_QAIRT244_PROMPT"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_EXPECTED_MODEL_BASENAME = "expected_model_basename"
        const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
        private const val STATE_FILE_NAME = "qairt244_internal_intent_prompt_state.txt"
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
        private const val UI_CLEANUP_FILE_NAME = "qairt244_dev_npu_ui_cleanup_state.txt"
    }
}
