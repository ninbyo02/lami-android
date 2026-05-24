package io.github.ninbyo02.lami.npu

import android.content.Context
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object DevOnlyNpuChatScreenBlockedBranch {
    private val chatScreenRunInProgress = AtomicBoolean(false)
    private const val DEV_SELECTED_ROUTE = "qairt244_sm8750_dev_npu"
    private const val HIDDEN_SELECTED_ROUTE = "qairt244_sm8750_hidden_npu"

    @JvmStatic
    fun run(prompt: String): String {
        val validation = NpuDiagnosticPromptValidator.validateAsciiDiagnostic(prompt)
        val normalizedPrompt = validation.normalizedPrompt.ifBlank { prompt }
        val result = runBlocking {
            DevOnlyNpuRoutePlanner(
                adapter = BlockedDevOnlyNpuRouteAdapter(),
            ).runIfAllowed(
                gateInput = DevOnlyNpuRouteGateInput(
                    customBuildExperiment = true,
                    allowEditablePromptPreview = true,
                    allowGuardedNpuRun = true,
                    allowEditablePromptExecution = true,
                    devCheckboxChecked = true,
                    validatorValid = validation.isValid,
                    nativeEditablePromptSupported = true,
                    running = false,
                    maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                ),
                prompt = normalizedPrompt,
                maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
            )
        }
        val displayModel = DevOnlyNpuRouteDisplayModelMapper.from(result)
        val transientState = DevOnlyNpuTransientPresenter.present(displayModel)
        return listOf(
            "DEV NPU blocked",
            "status=${transientState.status}",
            "reason=${transientState.reasonCode}",
            "db=${transientState.shouldPersistToDb}",
            "tts=${transientState.shouldSpeakTts}",
            "markdown=${transientState.shouldRenderMarkdown}",
            "stream=${transientState.shouldStream}",
        ).joinToString(" ")
    }

    @JvmStatic
    fun run(context: Context, prompt: String): String {
        val appContext = context.applicationContext
        val validation = NpuDiagnosticPromptValidator.validateAsciiDiagnostic(prompt)
        val normalizedPrompt = validation.normalizedPrompt.ifBlank { prompt }
        val result = runBlocking {
            try {
                DevOnlyNpuRoutePlanner(
                    adapter = Qairt244DevOnlyNpuRouteAdapter(appContext),
                ).runIfAllowed(
                    gateInput = DevOnlyNpuRouteGateInput(
                        customBuildExperiment = true,
                        allowEditablePromptPreview = true,
                        allowGuardedNpuRun = true,
                        allowEditablePromptExecution = true,
                        devCheckboxChecked = true,
                        validatorValid = validation.isValid,
                        nativeEditablePromptSupported = true,
                        running = false,
                        maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                    ),
                    prompt = normalizedPrompt,
                    maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                    timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
                )
            } finally {
                if (io.github.ninbyo02.lami.BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
                    val preferences = SettingsPreferences(appContext)
                    preferences.saveDevEnableQairt244Sm8750NpuRoute(false)
                    preferences.saveDevEnableNpuChatScreenRoute(false)
                }
            }
        }
        val displayModel = DevOnlyNpuRouteDisplayModelMapper.from(result)
        val transientState = DevOnlyNpuTransientPresenter.present(displayModel)
        return listOf(
            "DEV NPU route",
            "status=${transientState.status}",
            "reason=${transientState.reasonCode}",
            "output=${transientState.outputPreview ?: "-"}",
            "db=${transientState.shouldPersistToDb}",
            "tts=${transientState.shouldSpeakTts}",
            "markdown=${transientState.shouldRenderMarkdown}",
            "stream=${transientState.shouldStream}",
        ).joinToString(" ")
    }

    @JvmStatic
    fun runForChatScreen(context: Context, prompt: String): String {
        val appContext = context.applicationContext
        val templateMode = runBlocking {
            SettingsPreferences(appContext).hiddenQairt244PromptTemplateModeFlow.first()
        }
        return runForChatScreen(context = appContext, prompt = prompt, templateMode = templateMode.storageValue)
    }

    @JvmStatic
    fun runForChatScreen(context: Context, prompt: String, templateMode: String): String {
        val resolvedTemplateMode = HiddenQairt244PromptTemplateMode.fromStorage(templateMode)
        if (!chatScreenRunInProgress.compareAndSet(false, true)) {
            val promptTemplate = buildPromptTemplate(prompt = prompt, mode = resolvedTemplateMode)
            return listOf(
                "selected_route=$HIDDEN_SELECTED_ROUTE",
                "success=false",
                "status=ERROR",
                "reasonCode=duplicate_run_blocked",
                "failure_stage=preflight",
                "stop_reason=duplicate_run_blocked",
                "assistant_message=${escapeValue("実験的NPU route failed: duplicate_run_blocked")}",
                "output=",
                "prompt=${escapeValue(prompt)}",
                "raw_user_prompt=${escapeValue(prompt)}",
                "normalized_prompt=${escapeValue(prompt)}",
                "final_model_input=${escapeValue(promptTemplate.finalModelInput)}",
                "final_model_input_length=${promptTemplate.finalModelInput.length}",
                "conversation_history_count=0",
                "system_prompt_used=none",
                "chat_template_used=${promptTemplate.mode.storageValue}",
                "template_mode=${promptTemplate.mode.storageValue}",
                "template_prefix_length=${promptTemplate.prefix.length}",
                "template_suffix_length=${promptTemplate.suffix.length}",
                "prompt_source=${Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_CHAT_SCREEN}",
                "prompt_validation_mode=${NpuDiagnosticPromptValidator.UTF8_HIDDEN_EXPERIMENTAL_MODE}",
                "prompt_formatting_mode=${promptTemplate.promptFormattingMode}",
                "max_output_tokens=${DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS}",
                "native_max_output_tokens_limit=${DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS}",
                "resolved_model_basename=",
                "required_sm8750_model_path=false",
                "npu_backend=",
                "npu_backend_evidence=",
                "run_decode_reached=false",
                "decode_elapsed_ms=",
                "elapsed_ms=",
                "artifact_path=",
                "fallback_used=false",
                "ui_cleanup_status=not_started",
                "normal_ui_route_connected=true",
                "conversation_created=no",
                "generate_response=no",
                "selected_path_npu_normal_route=no",
                "timeout=false",
                "fresh_crash=false",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
            ).joinToString("\n")
        }
        return try {
            runForChatScreenGuarded(context, prompt, resolvedTemplateMode)
        } finally {
            chatScreenRunInProgress.set(false)
        }
    }

    private fun runForChatScreenGuarded(
        context: Context,
        prompt: String,
        templateMode: HiddenQairt244PromptTemplateMode,
    ): String {
        val appContext = context.applicationContext
        val validation = NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt)
        val normalizedPrompt = validation.normalizedPrompt.ifBlank { prompt }
        File(appContext.filesDir, "qairt244_chat_screen_real_npu_once_guard.txt").delete()
        val result = runBlocking {
            DevOnlyNpuRoutePlanner(
                adapter = Qairt244DevOnlyNpuRouteAdapter(
                    context = appContext,
                    promptTemplateMode = templateMode,
                ),
            ).runIfAllowed(
                gateInput = DevOnlyNpuRouteGateInput(
                    customBuildExperiment = true,
                    allowEditablePromptPreview = true,
                    allowGuardedNpuRun = true,
                    allowEditablePromptExecution = true,
                    devCheckboxChecked = true,
                    validatorValid = validation.isValid,
                    nativeEditablePromptSupported = true,
                    running = false,
                    maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                ),
                prompt = normalizedPrompt,
                maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
            )
        }
        val modelResolution = readKeyValueFile(File(appContext.filesDir, "qairt244_chat_screen_model_path_resolution.txt"))
        val nativeResult = readKeyValueFile(File(appContext.filesDir, "qairt244_short_multitoken_smoke_result.txt"))
        val resolvedModelBasename = modelResolution["resolved_model_basename"].orEmpty()
            .ifBlank { modelResolution["resolved_model_path"]?.substringAfterLast('/').orEmpty() }
        val requiredSm8750ModelPath = modelResolution["required_sm8750_model_path"].orEmpty()
            .ifBlank { "false" }
        val stopReason = modelResolution["stop_reason"].orEmpty()
            .ifBlank { nativeResult["stop_reason"].orEmpty() }
            .ifBlank { if (result.success) "" else result.reasonCode }
        val failureStage = when {
            result.success -> ""
            result.timeout -> "timeout"
            result.reasonCode == "duplicate_run_blocked" -> "preflight"
            result.reasonCode.startsWith("invalid_prompt") -> "prompt_validation"
            result.reasonCode.startsWith("gate_blocked") -> "route_gate"
            result.reasonCode.startsWith("model_file") -> "model_resolution"
            stopReason.startsWith("model_file") -> "model_resolution"
            result.reasonCode.startsWith("adapter_failure") -> "adapter_execution"
            result.decodeElapsedMs == null -> "engine_or_decode"
            else -> "native_result"
        }
        val npuBackend = nativeResult["npu_backend"].orEmpty()
            .ifBlank { if (result.success) "NPU" else "" }
        val backendEvidence = result.backendEvidence.orEmpty()
            .ifBlank { nativeResult["npu_backend_evidence"].orEmpty() }
        val rawNativeOutput = unescapeValue(nativeResult["raw_native_output"].orEmpty())
        val adapterOutput = unescapeValue(nativeResult["adapter_output"].orEmpty())
            .ifBlank { result.output.orEmpty() }
        val rawUserPrompt = nativeResult["raw_user_prompt"].orEmpty()
            .let(::unescapeValue)
            .ifBlank { prompt }
        val finalModelInput = nativeResult["final_model_input"].orEmpty()
            .let(::unescapeValue)
            .ifBlank { result.prompt }
        val finalModelInputLength = nativeResult["final_model_input_length"].orEmpty()
            .ifBlank { finalModelInput.length.toString() }
        val resultTemplateMode = nativeResult["template_mode"].orEmpty()
            .ifBlank { templateMode.storageValue }
        val promptTemplate = buildPromptTemplate(prompt = result.prompt, mode = templateMode)
        val templatePrefixLength = nativeResult["template_prefix_length"].orEmpty()
            .ifBlank { promptTemplate.prefix.length.toString() }
        val templateSuffixLength = nativeResult["template_suffix_length"].orEmpty()
            .ifBlank { promptTemplate.suffix.length.toString() }
        val nativeMaxOutputTokensLimit = nativeResult["native_max_output_tokens_limit"].orEmpty()
            .ifBlank { DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS.toString() }
        val outputDiagnosticsValues = nativeResult + mapOf(
            "raw_native_output" to rawNativeOutput,
            "adapter_output" to adapterOutput,
            "output" to result.output.orEmpty(),
            "finish_reason" to nativeResult["finish_reason"].orEmpty()
                .ifBlank { if (result.success) "success_no_finish_reason_exposed" else "" },
            "stop_reason" to stopReason,
        )
        val outputDiagnostics = Qairt244OutputUnicodeDiagnostics.toEscapedLines(
            fields = Qairt244OutputUnicodeDiagnostics.buildFieldsFromExistingValues(outputDiagnosticsValues),
            escapeValue = ::escapeValue,
        )
        val runDecodeReached = nativeResult["run_decode"].orEmpty().contains("RunDecode") ||
            result.decodeElapsedMs != null
        val assistantMessage = if (result.success) {
            result.output.orEmpty()
        } else {
            "実験的NPU route failed: ${result.reasonCode}"
        }
        val selectedRoute = if (io.github.ninbyo02.lami.BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            DEV_SELECTED_ROUTE
        } else {
            HIDDEN_SELECTED_ROUTE
        }
        return listOf(
            "selected_route=$selectedRoute",
            "success=${result.success}",
            "status=${if (result.success) "SUCCESS" else "ERROR"}",
            "reasonCode=${escapeValue(result.reasonCode)}",
            "failure_stage=${escapeValue(failureStage)}",
            "assistant_message=${escapeValue(assistantMessage)}",
            "output=${escapeValue(result.output.orEmpty())}",
            "raw_native_output=${escapeValue(rawNativeOutput)}",
            "raw_native_output_length=${rawNativeOutput.length}",
            "adapter_output=${escapeValue(adapterOutput)}",
            "adapter_output_length=${adapterOutput.length}",
            "displayed_assistant_text=${escapeValue(assistantMessage)}",
            "displayed_assistant_text_length=${assistantMessage.length}",
            "prompt=${escapeValue(result.prompt)}",
            "raw_user_prompt=${escapeValue(rawUserPrompt)}",
            "normalized_prompt=${escapeValue(result.prompt)}",
            "final_model_input=${escapeValue(finalModelInput)}",
            "final_model_input_length=$finalModelInputLength",
            "conversation_history_count=${nativeResult["conversation_history_count"].orEmpty().ifBlank { "0" }}",
            "system_prompt_used=${escapeValue(nativeResult["system_prompt_used"].orEmpty().ifBlank { "none" })}",
            "chat_template_used=${escapeValue(nativeResult["chat_template_used"].orEmpty().ifBlank { resultTemplateMode })}",
            "template_mode=$resultTemplateMode",
            "template_prefix_length=$templatePrefixLength",
            "template_suffix_length=$templateSuffixLength",
            "prompt_source=${Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_CHAT_SCREEN}",
            "prompt_validation_mode=${validation.promptValidationMode}",
            "prompt_formatting_mode=${nativeResult["prompt_formatting_mode"].orEmpty().ifBlank { "raw_normalized_prompt" }}",
            "route_type=standard_hidden_chat_screen",
            "max_output_tokens=${result.maxOutputTokens}",
            "native_max_output_tokens_limit=${escapeValue(nativeMaxOutputTokensLimit)}",
        ).plus(outputDiagnostics).plus(
            listOf(
                "canonical_model_basename=${escapeValue(Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME)}",
                "timestamp_prefix_stripped=${escapeValue(modelResolution["timestamp_prefix_stripped"].orEmpty())}",
                "resolved_model_basename=${escapeValue(resolvedModelBasename)}",
                "required_sm8750_model_path=$requiredSm8750ModelPath",
                "npu_backend=${escapeValue(npuBackend)}",
                "npu_backend_evidence=${escapeValue(backendEvidence)}",
                "run_decode_reached=$runDecodeReached",
                "decode_elapsed_ms=${result.decodeElapsedMs ?: ""}",
                "elapsed_ms=${result.elapsedMs ?: ""}",
                "artifact_path=${escapeValue(result.artifactPath.orEmpty())}",
                "fallback_used=false",
                "ui_cleanup_status=scheduled",
                "normal_ui_route_connected=true",
                "conversation_created=no",
                "generate_response=no",
                "selected_path_npu_normal_route=no",
                "timeout=${result.timeout}",
                "fresh_crash=${result.freshCrash}",
                "db=false",
                "tts=false",
                "markdown=false",
                "markdown_mode=non_streaming_direct_insert",
                "repair_applied=false",
                "streaming=false",
            ),
        ).joinToString("\n")
    }

    private fun readKeyValueFile(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return file.readLines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
    }

    private fun escapeValue(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unescapeValue(value: String): String =
        value.replace("\\n", "\n").replace("\\\\", "\\")

    private data class PromptTemplate(
        val mode: HiddenQairt244PromptTemplateMode,
        val prefix: String,
        val suffix: String,
        val finalModelInput: String,
        val promptFormattingMode: String,
    )

    private fun buildPromptTemplate(
        prompt: String,
        mode: HiddenQairt244PromptTemplateMode,
    ): PromptTemplate {
        val (prefix, suffix) = when (mode) {
            HiddenQairt244PromptTemplateMode.RAW -> "" to ""
            HiddenQairt244PromptTemplateMode.SIMPLE_JA_CHAT ->
                "あなたは親切なAIアシスタントです。\nユーザー: " to "\nアシスタント:"
            HiddenQairt244PromptTemplateMode.GEMMA_IT_LIKE ->
                "<start_of_turn>user\n" to "\n<end_of_turn>\n<start_of_turn>model"
        }
        return PromptTemplate(
            mode = mode,
            prefix = prefix,
            suffix = suffix,
            finalModelInput = "$prefix$prompt$suffix",
            promptFormattingMode = if (mode == HiddenQairt244PromptTemplateMode.RAW) {
                "raw_normalized_prompt"
            } else {
                "hidden_prompt_template_experiment"
            },
        )
    }

}
