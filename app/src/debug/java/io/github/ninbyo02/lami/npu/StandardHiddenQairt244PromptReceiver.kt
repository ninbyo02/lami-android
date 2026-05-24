package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class StandardHiddenQairt244PromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handle(appContext: Context, intent: Intent) {
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().ifBlank { "Hello" }
        val enableDeveloperAccess = intent.getBooleanExtra(EXTRA_ENABLE_DEVELOPER_ACCESS, false)
        val enableRoute = intent.getBooleanExtra(EXTRA_ENABLE_ROUTE, false)
        val shouldRun = intent.getBooleanExtra(EXTRA_RUN, true)
        val preferences = SettingsPreferences(appContext)

        val resultText = runBlocking {
            if (enableDeveloperAccess) {
                preferences.saveDeveloperAccessEnabled(true)
            }
            if (enableRoute) {
                preferences.saveDevEnableQairt244Sm8750NpuRoute(true)
            }

            val developerAccessEnabled = preferences.developerAccessEnabledFlow.first()
            val routeEnabled = preferences.devEnableQairt244Sm8750NpuRouteFlow.first()
            if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
                writeState(
                    stateFile = stateFile,
                    prompt = prompt,
                    developerAccessEnabled = developerAccessEnabled,
                    routeEnabled = routeEnabled,
                    resultText = "success=false\nreasonCode=wrong_variant\n",
                    status = "blocked",
                )
                return@runBlocking null
            }
            if (!developerAccessEnabled || !routeEnabled) {
                writeState(
                    stateFile = stateFile,
                    prompt = prompt,
                    developerAccessEnabled = developerAccessEnabled,
                    routeEnabled = routeEnabled,
                    resultText = "success=false\nreasonCode=hidden_gate_disabled\n",
                    status = "blocked",
                )
                return@runBlocking null
            }
            if (!shouldRun) {
                writeState(
                    stateFile = stateFile,
                    prompt = prompt,
                    developerAccessEnabled = developerAccessEnabled,
                    routeEnabled = routeEnabled,
                    resultText = "success=true\nreasonCode=gate_enabled_no_run\n",
                    status = "gate_enabled",
                )
                return@runBlocking null
            }

            DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(appContext, prompt).also { result ->
                writeRunnerCleanupState(appContext)
                writeState(
                    stateFile = stateFile,
                    prompt = prompt,
                    developerAccessEnabled = developerAccessEnabled,
                    routeEnabled = preferences.devEnableQairt244Sm8750NpuRouteFlow.first(),
                    resultText = result,
                    status = if (result.lineSequence().any { it == "success=true" }) "success" else "failure",
                )
            }
        }
        if (resultText == null) return
    }

    private fun writeState(
        stateFile: File,
        prompt: String,
        developerAccessEnabled: Boolean,
        routeEnabled: Boolean,
        resultText: String,
        status: String,
    ) {
        stateFile.writeText(
            buildString {
                appendLine("receiver=standard_hidden_qairt244_prompt")
                appendLine("status=$status")
                appendLine("prompt=$prompt")
                appendLine("developer_access_enabled=$developerAccessEnabled")
                appendLine("dev_enable_qairt244_sm8750_npu_route=$routeEnabled")
                appendLine("toggle_retained_after_run=$routeEnabled")
                appendLine(resultText.trim())
                appendLine("ui_cleanup_wait_status=success")
            },
        )
    }

    private fun writeRunnerCleanupState(appContext: Context) {
        File(appContext.filesDir, "qairt244_dev_npu_ui_cleanup_state.txt").writeText(
            listOf(
                "reason=standard-hidden-runner-finish",
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
        const val ACTION = "io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_ENABLE_DEVELOPER_ACCESS = "enable_developer_access"
        const val EXTRA_ENABLE_ROUTE = "enable_route"
        const val EXTRA_RUN = "run"
        const val STATE_FILE_NAME = "qairt244_standard_hidden_prompt_state.txt"
    }
}
