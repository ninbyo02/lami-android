package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class StandardHiddenQairt244PromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val traceRunId = intent.getStringExtra(EXTRA_RUN_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "receiver-${System.currentTimeMillis()}"
        DevOnlyNpuTerminalTrace.append(
            context = appContext,
            runId = traceRunId,
            marker = DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
        )
        val pendingResult = goAsync()
        DevOnlyNpuTerminalTrace.append(
            context = appContext,
            runId = traceRunId,
            marker = DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
        )
        Thread {
            try {
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
                )
                handle(appContext, intent, traceRunId)
            } catch (throwable: Throwable) {
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.THROWABLE_CAUGHT,
                    throwable = throwable,
                )
                throw throwable
            } finally {
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
                )
                pendingResult.finish()
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
                )
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
                )
            }
        }.start()
    }

    private fun handle(appContext: Context, intent: Intent, traceRunId: String) {
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        val promptTransport = decodePrompt(intent)
        if (!promptTransport.decodeSuccess) {
            writeState(
                stateFile = stateFile,
                prompt = "",
                developerAccessEnabled = false,
                routeEnabled = false,
                resultText = listOf(
                    "success=false",
                    "reasonCode=invalid_prompt_base64",
                    "failure_stage=receiver_preflight",
                    "assistant_message=${escapeValue("実験的NPU route failed: invalid_prompt_base64")}",
                    "output=",
                ).joinToString("\n"),
                status = "failure",
                promptTransport = promptTransport,
                unsafeDevBypassPromptLengthGate = false,
            )
            return
        }
        val prompt = promptTransport.prompt.ifBlank { "Hello" }
        val enableDeveloperAccess = intent.getBooleanExtra(EXTRA_ENABLE_DEVELOPER_ACCESS, false)
        val enableRoute = intent.getBooleanExtra(EXTRA_ENABLE_ROUTE, false)
        val shouldRun = intent.getBooleanExtra(EXTRA_RUN, true)
        val allowMaxOutputTokensCompare = intent.getBooleanExtra(EXTRA_ALLOW_MAX_OUTPUT_TOKENS_COMPARE, false)
        val unsafeDevBypassPromptLengthGate = intent.getBooleanExtra(
            EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
            false,
        )
        val requestedMaxOutputTokens = intent.getIntExtra(
            EXTRA_MAX_OUTPUT_TOKENS,
            DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        )
        val baselineMaxOutputTokens = if (
            allowMaxOutputTokensCompare &&
            requestedMaxOutputTokens in 1..DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT
        ) {
            requestedMaxOutputTokens
        } else {
            DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS
        }
        val requestedTemplateMode = (
            intent.getStringExtra(EXTRA_TEMPLATE)
                ?: intent.getStringExtra(EXTRA_TEMPLATE_MODE)
            )
            ?.let(HiddenQairt244PromptTemplateMode::fromStorage)
        val preferences = SettingsPreferences(appContext)

        val resultText = runBlocking {
            if (enableDeveloperAccess) {
                preferences.saveDeveloperAccessEnabled(true)
            }
            if (enableRoute) {
                preferences.saveDevEnableQairt244Sm8750NpuRoute(true)
            }
            if (requestedTemplateMode != null) {
                preferences.saveHiddenQairt244PromptTemplateMode(requestedTemplateMode)
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
                    promptTransport = promptTransport,
                    unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
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
                    promptTransport = promptTransport,
                    unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
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
                    promptTransport = promptTransport,
                    unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
                )
                return@runBlocking null
            }

            val templateMode = requestedTemplateMode
                ?: preferences.hiddenQairt244PromptTemplateModeFlow.first()
            DevOnlyNpuTerminalTrace.append(
                context = appContext,
                runId = traceRunId,
                marker = DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
            )
            DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(
                context = appContext,
                prompt = prompt,
                templateMode = templateMode.storageValue,
                maxOutputTokens = baselineMaxOutputTokens,
                requestedMaxOutputTokens = requestedMaxOutputTokens,
                allowMaxOutputTokensCompare = allowMaxOutputTokensCompare,
                unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
                terminalTraceRunId = traceRunId,
            ).also { result ->
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE,
                )
                writeDisplayDiagnostics(appContext, result)
                writeState(
                    stateFile = stateFile,
                    prompt = prompt,
                    developerAccessEnabled = developerAccessEnabled,
                    routeEnabled = preferences.devEnableQairt244Sm8750NpuRouteFlow.first(),
                    resultText = result,
                    status = if (result.lineSequence().any { it == "success=true" }) "success" else "failure",
                    promptTransport = promptTransport,
                    unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
                )
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE,
                )
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.BEFORE_CLEANUP,
                )
                writeRunnerCleanupState(appContext)
                DevOnlyNpuTerminalTrace.append(
                    context = appContext,
                    runId = traceRunId,
                    marker = DevOnlyNpuTerminalTraceMarker.AFTER_CLEANUP,
                )
            }
        }
        if (resultText == null) return
    }

    private fun decodePrompt(intent: Intent): PromptTransport {
        val promptBase64 = intent.getStringExtra(EXTRA_PROMPT_BASE64).orEmpty()
        if (promptBase64.isNotBlank()) {
            return try {
                PromptTransport(
                    prompt = Base64.decode(promptBase64, Base64.NO_WRAP).toString(Charsets.UTF_8),
                    transport = "base64",
                    base64Present = true,
                    decodeSuccess = true,
                )
            } catch (_: IllegalArgumentException) {
                PromptTransport(
                    prompt = "",
                    transport = "base64",
                    base64Present = true,
                    decodeSuccess = false,
                )
            }
        }
        return PromptTransport(
            prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty(),
            transport = "plain",
            base64Present = false,
            decodeSuccess = true,
        )
    }

    private fun writeState(
        stateFile: File,
        prompt: String,
        developerAccessEnabled: Boolean,
        routeEnabled: Boolean,
        resultText: String,
        status: String,
        promptTransport: PromptTransport,
        unsafeDevBypassPromptLengthGate: Boolean,
    ) {
        val resultValues = parseKeyValueText(resultText)
        val finalModelInput = resultValues["final_model_input"].orEmpty()
        val finalModelInputCodePoints = if (finalModelInput.isNotEmpty()) {
            finalModelInput.codePointCount(0, finalModelInput.length).toString()
        } else {
            ""
        }
        val promptCodePoints = prompt.codePointCount(0, prompt.length)
        val promptLengthGateLimit = NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH
        val promptLengthGateWouldBlock = promptCodePoints > promptLengthGateLimit
        stateFile.writeText(
            buildString {
                appendLine("receiver=standard_hidden_qairt244_prompt")
                appendLine("status=$status")
                appendLine("prompt=$prompt")
                appendLine("prompt_transport=${promptTransport.transport}")
                appendLine("prompt_base64_present=${promptTransport.base64Present}")
                appendLine("prompt_decode_success=${promptTransport.decodeSuccess}")
                appendLine("receiver_prompt_input_code_points=$promptCodePoints")
                appendLine("unsafe_dev_bypass_prompt_length_gate_requested=$unsafeDevBypassPromptLengthGate")
                appendLine("unsafe_dev_bypass_prompt_length_gate_effective=$unsafeDevBypassPromptLengthGate")
                appendLine("prompt_length_gate_limit=$promptLengthGateLimit")
                appendLine("prompt_length_gate_would_block=$promptLengthGateWouldBlock")
                appendLine("prompt_length_gate_bypassed=${unsafeDevBypassPromptLengthGate && promptLengthGateWouldBlock}")
                appendLine("developer_access_enabled=$developerAccessEnabled")
                appendLine("dev_enable_qairt244_sm8750_npu_route=$routeEnabled")
                appendLine("toggle_retained_after_run=$routeEnabled")
                appendLine(resultText.trim())
                appendLine("final_model_input_code_points=$finalModelInputCodePoints")
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

    private fun writeDisplayDiagnostics(appContext: Context, resultText: String) {
        val values = parseKeyValueText(resultText)
        val displayedText = values["displayed_assistant_text"].orEmpty()
            .ifBlank { values["assistant_message"].orEmpty() }
            .ifBlank { values["output"].orEmpty() }
        val outputDiagnostics = Qairt244OutputUnicodeDiagnostics.toEscapedLines(
            fields = Qairt244OutputUnicodeDiagnostics.buildFieldsFromExistingValues(values),
            escapeValue = ::escapeValue,
        )
        File(appContext.filesDir, "qairt244_standard_hidden_display_diagnostics.txt").writeText(
            listOf(
                "route_type=${values["route_type"].orEmpty().ifBlank { "standard_hidden_chat_screen" }}",
                "selected_route=${values["selected_route"].orEmpty()}",
                "assistant_message_id=receiver_runner",
                "success=${values["success"].orEmpty()}",
                "reasonCode=${values["reasonCode"].orEmpty()}",
                "raw_user_prompt=${escapeValue(values["raw_user_prompt"].orEmpty())}",
                "normalized_prompt=${escapeValue(values["normalized_prompt"].orEmpty())}",
                "final_model_input=${escapeValue(values["final_model_input"].orEmpty())}",
                "final_model_input_length=${values["final_model_input_length"].orEmpty()}",
                "conversation_history_count=${values["conversation_history_count"].orEmpty()}",
                "system_prompt_used=${escapeValue(values["system_prompt_used"].orEmpty())}",
                "chat_template_used=${escapeValue(values["chat_template_used"].orEmpty())}",
                "template_mode=${values["template_mode"].orEmpty()}",
                "template_prefix_length=${values["template_prefix_length"].orEmpty()}",
                "template_suffix_length=${values["template_suffix_length"].orEmpty()}",
                "prompt_source=${values["prompt_source"].orEmpty()}",
                "prompt_validation_mode=${values["prompt_validation_mode"].orEmpty()}",
                "prompt_input_code_points=${values["prompt_input_code_points"].orEmpty()}",
                "prompt_input_code_point_limit=${values["prompt_input_code_point_limit"].orEmpty()}",
                "prompt_input_limit_mode=${values["prompt_input_limit_mode"].orEmpty()}",
                "native_prompt_input_code_point_limit=${values["native_prompt_input_code_point_limit"].orEmpty()}",
                "native_prompt_input_limit_mode=${values["native_prompt_input_limit_mode"].orEmpty()}",
                "prompt_formatting_mode=${values["prompt_formatting_mode"].orEmpty()}",
                    "requested_max_output_tokens=${values["requested_max_output_tokens"].orEmpty()}",
                    "max_output_tokens_compare_enabled=${values["max_output_tokens_compare_enabled"].orEmpty().ifBlank { "false" }}",
                "raw_native_output=${escapeValue(values["raw_native_output"].orEmpty())}",
                "raw_native_output_length=${values["raw_native_output_length"].orEmpty()}",
                "raw_output=${escapeValue(values["raw_output"].orEmpty())}",
                "raw_output_length=${values["raw_output_length"].orEmpty()}",
                "sanitized_output=${escapeValue(values["sanitized_output"].orEmpty())}",
                "sanitized_output_length=${values["sanitized_output_length"].orEmpty()}",
                "sanitizer_applied=${values["sanitizer_applied"].orEmpty()}",
                "removed_template_token_count=${values["removed_template_token_count"].orEmpty()}",
                "removed_prompt_echo=${values["removed_prompt_echo"].orEmpty()}",
                "code_block_detected=${values["code_block_detected"].orEmpty()}",
                "code_fence_completed=${values["code_fence_completed"].orEmpty()}",
                "adapter_output=${escapeValue(values["adapter_output"].orEmpty())}",
                "adapter_output_length=${values["adapter_output_length"].orEmpty()}",
                "displayed_assistant_text=${escapeValue(displayedText)}",
                "displayed_assistant_text_length=${displayedText.length}",
            ).plus(outputDiagnostics).plus(
                listOf(
                    "max_output_tokens=${values["max_output_tokens"].orEmpty()}",
                    "decode_elapsed_ms=${values["decode_elapsed_ms"].orEmpty()}",
                    "npu_backend=${values["npu_backend"].orEmpty()}",
                    "npu_backend_evidence=${values["npu_backend_evidence"].orEmpty()}",
                    "fallback_used=${values["fallback_used"].orEmpty()}",
                    "timeout=${values["timeout"].orEmpty()}",
                    "fresh_crash=${values["fresh_crash"].orEmpty()}",
                    "markdown_mode=${values["markdown_mode"].orEmpty().ifBlank { "non_streaming_direct_insert" }}",
                    "repair_applied=${values["repair_applied"].orEmpty().ifBlank { "false" }}",
                    "streaming=${values["streaming"].orEmpty().ifBlank { "false" }}",
                ),
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun parseKeyValueText(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to unescapeValue(line.substring(index + 1))
            }
            .toMap()

    private fun unescapeValue(value: String): String =
        value.replace("\\n", "\n").replace("\\\\", "\\")

    private fun escapeValue(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_PROMPT_BASE64 = "prompt_base64"
        const val EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE = "unsafe_dev_bypass_prompt_length_gate"
        const val EXTRA_ENABLE_DEVELOPER_ACCESS = "enable_developer_access"
        const val EXTRA_ENABLE_ROUTE = "enable_route"
        const val EXTRA_RUN = "run"
        const val EXTRA_ALLOW_MAX_OUTPUT_TOKENS_COMPARE = "allow_max_output_tokens_compare"
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_TEMPLATE_MODE = "template_mode"
        const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
        const val EXTRA_RUN_ID = "run_id"
        const val STATE_FILE_NAME = "qairt244_standard_hidden_prompt_state.txt"
    }

    private data class PromptTransport(
        val prompt: String,
        val transport: String,
        val base64Present: Boolean,
        val decodeSuccess: Boolean,
    )
}
