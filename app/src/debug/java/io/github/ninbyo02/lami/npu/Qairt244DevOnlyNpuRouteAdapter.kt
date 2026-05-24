package io.github.ninbyo02.lami.npu

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmoke
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class Qairt244DevOnlyNpuRouteAdapter(
    context: Context,
) : DevOnlyNpuRouteAdapter {
    private val appContext = context.applicationContext
    private val resultFile: File = appContext.filesDir.resolve(RESULT_FILE_NAME)
    private val nativeDiagFile: File = appContext.filesDir.resolve(NATIVE_DIAG_FILE_NAME)
    private val modelResolutionFile: File = appContext.filesDir.resolve(MODEL_RESOLUTION_FILE_NAME)
    private val runGuardFile: File = appContext.filesDir.resolve(RUN_GUARD_FILE_NAME)

    override suspend fun runOnce(
        prompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult = runRoute(
        requestedPrompt = prompt,
        maxOutputTokens = maxOutputTokens,
        timeoutMs = timeoutMs,
        promptSource = PROMPT_SOURCE_CHAT_SCREEN,
        validation = NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt),
        allowMaxOutputTokenRange = false,
        expectedModelBasename = REQUIRED_MODEL_BASENAME,
    )

    suspend fun runInternalIntentOnce(
        prompt: String,
        expectedModelBasename: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult = runRoute(
        requestedPrompt = prompt,
        maxOutputTokens = maxOutputTokens,
        timeoutMs = timeoutMs,
        promptSource = PROMPT_SOURCE_INTERNAL_INTENT,
        validation = NpuDiagnosticPromptValidator.validateUtf8InternalIntent(prompt),
        allowMaxOutputTokenRange = true,
        expectedModelBasename = expectedModelBasename,
    )

    private suspend fun runRoute(
        requestedPrompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
        promptSource: String,
        validation: NpuDiagnosticPromptValidator.Result,
        allowMaxOutputTokenRange: Boolean,
        expectedModelBasename: String,
    ): DevOnlyNpuRouteResult {
        check(BuildConfig.CURRENT_FLAVOR in allowedDebugFlavors) {
            "QAIRT hidden-experimental NPU route adapter is debug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
        }

        if (expectedModelBasename != REQUIRED_MODEL_BASENAME) {
            return blockedResult(
                prompt = requestedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "expected_model_basename_mismatch",
            )
        }
        val maxOutputTokensValid = if (allowMaxOutputTokenRange) {
            maxOutputTokens in 1..DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS
        } else {
            maxOutputTokens == DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS
        }
        if (!maxOutputTokensValid) {
            return blockedResult(
                prompt = requestedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "invalid_max_output_tokens",
            )
        }

        if (!validation.isValid) {
            appendRouteMarker(
                "state=invalid_prompt reason=${validation.reasonCode} prompt_source=$promptSource " +
                    "prompt_validation_mode=${validation.promptValidationMode} engine_initialize=false run_decode=false",
            )
            appendInvalidPromptResult(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = validation.normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validation = validation,
            )
            return blockedResult(
                prompt = requestedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "invalid_prompt:${validation.reasonCode}",
            )
        }

        val normalizedPrompt = validation.normalizedPrompt
        if (!runGuardFile.createNewFile()) {
            appendRouteMarker(
                "state=duplicate_run_blocked actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt " +
                    "prompt_source=$promptSource prompt_validation_mode=${validation.promptValidationMode} " +
                    "max_output_tokens=$maxOutputTokens engine_initialize=false run_decode=false db=false tts=false markdown=false stream=false",
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "duplicate_run_blocked",
            )
        }
        runGuardFile.writeText("created_at_ms=${System.currentTimeMillis()}\n")

        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        writeModelResolution(modelResolution)
        appendRouteMarker(
            "state=model_resolution reason=${modelResolution.reasonCode} " +
                "resolved_model_path=${modelResolution.path ?: "-"} candidate_count=${modelResolution.candidates.size}",
        )
        if (!modelResolution.resolved) {
            appendRouteMarker(
                "state=failure reason=${modelResolution.reasonCode} engine_initialize=false run_decode=false " +
                    "db=false tts=false markdown=false stream=false",
            )
            appendModelFailureResult(
                prompt = normalizedPrompt,
                requestedPrompt = requestedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validationMode = validation.promptValidationMode,
                resolution = modelResolution,
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = modelResolution.reasonCode,
            )
        }

        val resolvedModelPath = checkNotNull(modelResolution.path)
        val requiredModelInfo = Qairt244ModelPathResolver.requiredSm8750ModelInfo(resolvedModelPath)
        if (!requiredModelInfo.required) {
            appendRouteMarker(
                "state=failure stop_reason=model_file_not_required_sm8750 resolved_model_path=$resolvedModelPath " +
                    "resolved_model_basename=${requiredModelInfo.resolvedModelBasename} " +
                    "canonical_model_basename=${requiredModelInfo.canonicalModelBasename} " +
                    "timestamp_prefix_stripped=${requiredModelInfo.timestampPrefixStripped} " +
                    "required_sm8750_model_path=false " +
                    "engine_initialize=false run_decode=false db=false tts=false markdown=false stream=false",
            )
            appendRequiredModelFailureResult(
                prompt = normalizedPrompt,
                requestedPrompt = requestedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validationMode = validation.promptValidationMode,
                resolution = modelResolution,
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "model_file_not_required_sm8750",
            )
        }

        val runId = "chat-real-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        appendRouteMarker(
            "runId=$runId state=started actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt " +
                "requested_prompt=$requestedPrompt prompt_source=$promptSource " +
                "prompt_validation_mode=${validation.promptValidationMode} max_output_tokens=$maxOutputTokens " +
                "resolved_model_path=${modelResolution.path}",
        )

        val start = SystemClock.elapsedRealtime()
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    Qairt244ShortMultitokenSmoke.runEditablePrompt(
                        context = appContext,
                        modelPath = resolvedModelPath,
                        runId = runId,
                        prompt = normalizedPrompt,
                        maxOutputTokens = maxOutputTokens,
                        promptValidationMode = validation.promptValidationMode,
                    )
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val valuesBeforeMetadata = parseResultFile()
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validationMode = validation.promptValidationMode,
                timeout = false,
                freshCrash = false,
                values = valuesBeforeMetadata,
                resolution = modelResolution,
            )
            val values = parseResultFile()
            val success = values["result"] == "success"
            val output = values["output"]
            val rawNativeOutput = output.orEmpty()
            appendOutputDiagnostics(
                rawNativeOutput = rawNativeOutput,
                adapterOutput = output.orEmpty(),
                values = values,
                promptSource = promptSource,
            )
            appendRouteMarker(
                "runId=$runId state=${if (success) "success" else "failure"} elapsed_ms=$elapsed " +
                    "result=${values["result"] ?: "unknown"} output=${output ?: "-"} db=false tts=false markdown=false stream=false",
            )
            DevOnlyNpuRouteResult(
                success = success,
                output = output,
                reasonCode = if (success) "success" else "native_result:${values["result"] ?: "unknown"}",
                elapsedMs = values["elapsed_ms"]?.toLongOrNull() ?: elapsed,
                decodeElapsedMs = values["decode_elapsed_ms"]?.toLongOrNull(),
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = values["npu_backend_evidence"] ?: nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = false,
            )
        } catch (timeout: TimeoutCancellationException) {
            val elapsed = SystemClock.elapsedRealtime() - start
            appendRouteMarker(
                "runId=$runId state=timeout elapsed_ms=$elapsed timeout_ms=$timeoutMs db=false tts=false markdown=false stream=false",
            )
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validationMode = validation.promptValidationMode,
                timeout = true,
                freshCrash = false,
                values = parseResultFile(),
                resolution = modelResolution,
            )
            DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "timeout",
                elapsedMs = elapsed,
                decodeElapsedMs = null,
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = true,
            )
        } catch (throwable: Throwable) {
            val elapsed = SystemClock.elapsedRealtime() - start
            appendRouteMarker(
                "runId=$runId state=failure elapsed_ms=$elapsed class=${throwable.javaClass.name} " +
                    "message=${throwable.message ?: "-"} db=false tts=false markdown=false stream=false",
            )
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validationMode = validation.promptValidationMode,
                timeout = false,
                freshCrash = false,
                values = parseResultFile(),
                resolution = modelResolution,
            )
            DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "adapter_failure:${throwable.javaClass.simpleName}",
                elapsedMs = elapsed,
                decodeElapsedMs = null,
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = false,
            )
        }
    }

    private fun blockedResult(
        prompt: String,
        maxOutputTokens: Int,
        reasonCode: String,
    ): DevOnlyNpuRouteResult =
        DevOnlyNpuRouteResult(
            success = false,
            output = null,
            reasonCode = reasonCode,
            elapsedMs = null,
            decodeElapsedMs = null,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            backendEvidence = null,
            artifactPath = resultFile.absolutePath,
            freshCrash = false,
            timeout = false,
        )

    private fun parseResultFile(): Map<String, String> =
        parseNativeResultFile().values

    private fun parseNativeResultFile(): Qairt244NativeResultParser.ParsedResult {
        if (!resultFile.isFile) {
            return Qairt244NativeResultParser.ParsedResult(values = emptyMap(), output = "")
        }
        return Qairt244NativeResultParser.parse(resultFile.readText())
    }

    private fun appendOutputDiagnostics(
        rawNativeOutput: String,
        adapterOutput: String,
        values: Map<String, String>,
        promptSource: String,
    ) {
        resultFile.appendText(
            listOf(
                "route_type=${routeType(promptSource)}",
                "raw_native_output=${escapeValue(rawNativeOutput)}",
                "raw_native_output_length=${rawNativeOutput.length}",
                "adapter_output=${escapeValue(adapterOutput)}",
                "adapter_output_length=${adapterOutput.length}",
                "finish_reason=${values["finish_reason"].orEmpty().ifBlank { "not_exposed_by_lower_level_entrypoint" }}",
                "stop_reason=${values["stop_reason"].orEmpty()}",
                "output_token_count=${values["output_token_count"].orEmpty().ifBlank { "unavailable" }}",
                "markdown_mode=non_streaming_direct_insert",
                "repair_applied=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun escapeValue(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun nativeBackendEvidence(): String? {
        if (!nativeDiagFile.isFile) return null
        val text = nativeDiagFile.readText()
        val hasQnnHtp = text.contains("QNN", ignoreCase = true) &&
            text.contains("HTP", ignoreCase = true)
        val hasFastRpc = text.contains("FastRPC", ignoreCase = true) ||
            text.contains("transport run [status = 0]", ignoreCase = true)
        val hasV79 = text.contains("V79", ignoreCase = true) ||
            text.contains("QNN stub", ignoreCase = true)
        return if (hasQnnHtp && hasFastRpc && hasV79) {
            "QNN_HTP_V79_FastRPC_native_diag"
        } else {
            null
        }
    }

    private fun appendRouteMarker(message: String) {
        resultFile.appendText("$ROUTE_MARKER $message\n")
    }

    private fun appendInvalidPromptResult(
        requestedPrompt: String,
        normalizedPrompt: String,
        maxOutputTokens: Int,
        promptSource: String,
        validation: NpuDiagnosticPromptValidator.Result,
    ) {
        resultFile.writeText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=invalid_prompt:${validation.reasonCode}",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$normalizedPrompt",
                "normalized_prompt=$normalizedPrompt",
                "prompt_source=$promptSource",
                "prompt_validation_mode=${validation.promptValidationMode}",
                "max_output_tokens=$maxOutputTokens",
                "fallback_used=false",
                "timeout=false",
                "fresh_crash=false",
                "engine_initialize=no",
                "run_decode=no",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "selected_path_npu_saved=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun appendModelFailureResult(
        prompt: String,
        requestedPrompt: String,
        maxOutputTokens: Int,
        promptSource: String,
        validationMode: String,
        resolution: Qairt244ModelPathResolver.Resolution,
    ) {
        resultFile.appendText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=${resolution.reasonCode}",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$prompt",
                "normalized_prompt=$prompt",
                "prompt_source=$promptSource",
                "prompt_validation_mode=$validationMode",
                "max_output_tokens=$maxOutputTokens",
                "resolved_model_path=${resolution.path ?: ""}",
                "resolved_model_basename=${resolution.modelInfo?.resolvedModelBasename ?: ""}",
                "canonical_model_basename=${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}",
                "timestamp_prefix_stripped=${resolution.modelInfo?.timestampPrefixStripped ?: false}",
                "required_sm8750_model_path=${resolution.modelInfo?.required ?: false}",
                "stop_reason=${resolution.reasonCode}",
                "checked_model_path=${resolution.checkedPath ?: ""}",
                "model_candidate_count=${resolution.candidates.size}",
                "checked_exists=${resolution.checkedExists ?: ""}",
                "checked_can_read=${resolution.checkedCanRead ?: ""}",
                "checked_length=${resolution.checkedLength ?: ""}",
                "fallback_used=false",
                "timeout=false",
                "fresh_crash=false",
                "engine_initialize=no",
                "run_decode=no",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "selected_path_npu_saved=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun appendRequiredModelFailureResult(
        prompt: String,
        requestedPrompt: String,
        maxOutputTokens: Int,
        promptSource: String,
        validationMode: String,
        resolution: Qairt244ModelPathResolver.Resolution,
    ) {
        resultFile.appendText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=model_file_not_required_sm8750",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$prompt",
                "normalized_prompt=$prompt",
                "prompt_source=$promptSource",
                "prompt_validation_mode=$validationMode",
                "max_output_tokens=$maxOutputTokens",
                "resolved_model_path=${resolution.path ?: ""}",
                "resolved_model_basename=${resolution.modelInfo?.resolvedModelBasename ?: ""}",
                "canonical_model_basename=${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}",
                "timestamp_prefix_stripped=${resolution.modelInfo?.timestampPrefixStripped ?: false}",
                "required_sm8750_model_path=${resolution.modelInfo?.required ?: false}",
                "stop_reason=model_file_not_required_sm8750",
                "checked_model_path=${resolution.checkedPath ?: ""}",
                "model_candidate_count=${resolution.candidates.size}",
                "checked_exists=${resolution.checkedExists ?: ""}",
                "checked_can_read=${resolution.checkedCanRead ?: ""}",
                "checked_length=${resolution.checkedLength ?: ""}",
                "fallback_used=false",
                "timeout=false",
                "fresh_crash=false",
                "engine_initialize=no",
                "run_decode=no",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "selected_path_npu_saved=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun appendRouteResultMetadata(
        requestedPrompt: String,
        normalizedPrompt: String,
        maxOutputTokens: Int,
        promptSource: String,
        validationMode: String,
        timeout: Boolean,
        freshCrash: Boolean,
        values: Map<String, String>,
        resolution: Qairt244ModelPathResolver.Resolution,
    ) {
        val runDecodeReached = values["decode_elapsed_ms"]?.isNotBlank() == true ||
            values["run_decode"]?.contains("RunDecode") == true
        val modelInfo = resolution.modelInfo
        resultFile.appendText(
            listOf(
                "selected_route=${selectedRoute(promptSource)}",
                "resolved_model_basename=${modelInfo?.resolvedModelBasename ?: ""}",
                "canonical_model_basename=${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}",
                "timestamp_prefix_stripped=${modelInfo?.timestampPrefixStripped ?: false}",
                "required_sm8750_model_path=${modelInfo?.required ?: false}",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$normalizedPrompt",
                "normalized_prompt=$normalizedPrompt",
                "prompt_source=$promptSource",
                "prompt_validation_mode=$validationMode",
                "native_prompt_validation_mode=${values["native_prompt_validation_mode"] ?: validationMode}",
                "utf8_allowed=${values["utf8_allowed"] ?: (validationMode != NpuDiagnosticPromptValidator.ASCII_DIAGNOSTIC_MODE).toString()}",
                "max_output_tokens=$maxOutputTokens",
                "run_decode_reached=$runDecodeReached",
                "fallback_used=false",
                "timeout=$timeout",
                "fresh_crash=$freshCrash",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "selected_path_npu_saved=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun writeModelResolution(resolution: Qairt244ModelPathResolver.Resolution) {
        val modelInfo = resolution.modelInfo
        val resolvedModelBasename = modelInfo?.resolvedModelBasename.orEmpty()
        val requiredSm8750ModelPath = modelInfo?.required ?: false
        val stopReason = when {
            !resolution.resolved -> resolution.reasonCode
            !requiredSm8750ModelPath -> "model_file_not_required_sm8750"
            else -> ""
        }
        modelResolutionFile.writeText(
            buildString {
                appendLine("reasonCode=${resolution.reasonCode}")
                appendLine("resolved=${resolution.resolved}")
                appendLine("resolved_model_path=${resolution.path ?: ""}")
                appendLine("resolved_model_basename=$resolvedModelBasename")
                appendLine("canonical_model_basename=${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
                appendLine("timestamp_prefix_stripped=${modelInfo?.timestampPrefixStripped ?: false}")
                appendLine("checked_model_path=${resolution.checkedPath ?: ""}")
                appendLine("candidate_count=${resolution.candidates.size}")
                appendLine("checked_exists=${resolution.checkedExists ?: ""}")
                appendLine("checked_can_read=${resolution.checkedCanRead ?: ""}")
                appendLine("checked_length=${resolution.checkedLength ?: ""}")
                resolution.candidates.forEachIndexed { index, candidate ->
                    appendLine("candidate_$index=$candidate")
                }
                appendLine("required_sm8750_model_path=$requiredSm8750ModelPath")
                appendLine("stop_reason=$stopReason")
                appendLine("saved_to_settings=false")
            },
        )
    }

    companion object {
        const val ROUTE_MARKER = "qairt244_chat_screen_real_npu_adapter_v1"
        const val PROMPT_SOURCE_CHAT_SCREEN = "chat_screen"
        const val PROMPT_SOURCE_INTERNAL_INTENT = "internal_intent"
        const val REQUIRED_MODEL_BASENAME = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
        private const val NATIVE_DIAG_FILE_NAME = "qairt244_native_diag.txt"
        private const val MODEL_RESOLUTION_FILE_NAME = "qairt244_chat_screen_model_path_resolution.txt"
        private const val RUN_GUARD_FILE_NAME = "qairt244_chat_screen_real_npu_once_guard.txt"
        private val allowedDebugFlavors = setOf("standard", "customBuildExperiment")

        fun selectedRoute(promptSource: String): String =
            if (promptSource == PROMPT_SOURCE_CHAT_SCREEN) {
                "qairt244_sm8750_hidden_npu"
            } else {
                "qairt244_sm8750_dev_npu"
            }

        fun routeType(promptSource: String): String =
            if (promptSource == PROMPT_SOURCE_CHAT_SCREEN) {
                "standard_hidden_chat_screen"
            } else {
                "internal_intent"
            }
    }
}
