package io.github.ninbyo02.lami.npu

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuEngineLogcatDiagnostics
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_ADAPTER_START
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_ADAPTER_SUCCESS
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_AFTER_NATIVE_CALL
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_BEFORE_NATIVE_CALL
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_NATIVE_CALL
import io.github.ninbyo02.lami.ui.screens.home.NPU_S1_NATIVE_STAGE_NATIVE_RESULT_PARSE
import io.github.ninbyo02.lami.ui.screens.home.NpuS1LogcatDiagnostics
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmoke
import io.github.ninbyo02.lami.ui.screens.home.buildNpuNativeLinkFailureDiagnostics
import io.github.ninbyo02.lami.ui.screens.home.captureLocalMemorySnapshot
import io.github.ninbyo02.lami.ui.screens.home.npuNativeLinkFailureDiagnosticsLines
import io.github.ninbyo02.lami.ui.screens.home.npuNativeLinkFailureReason
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class Qairt244DevOnlyNpuRouteAdapter(
    context: Context,
    private val promptTemplateMode: HiddenQairt244PromptTemplateMode = HiddenQairt244PromptTemplateMode.RAW,
    private val maxOutputTokenRangeLimit: Int = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
    private val unsafeDevBypassPromptLengthGate: Boolean = false,
    private val terminalTraceRunId: String? = null,
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
        validation = promptLengthGateBypassedValidation(
            validation = if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
                NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(prompt)
            } else {
                NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt)
            },
        ),
        allowMaxOutputTokenRange = maxOutputTokenRangeLimit != DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        expectedModelBasename = REQUIRED_MODEL_BASENAME,
        templateMode = if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            HiddenQairt244PromptTemplateMode.RAW
        } else {
            promptTemplateMode
        },
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
        templateMode = HiddenQairt244PromptTemplateMode.RAW,
    )

    suspend fun runDevOnlyConversationOnce(
        prompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult = runRoute(
        requestedPrompt = prompt,
        maxOutputTokens = maxOutputTokens,
        timeoutMs = timeoutMs,
        promptSource = PROMPT_SOURCE_DEV_ONLY_CONVERSATION,
        validation = promptLengthGateBypassedValidation(
            validation = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt),
        ),
        allowMaxOutputTokenRange = true,
        expectedModelBasename = REQUIRED_MODEL_BASENAME,
        templateMode = HiddenQairt244PromptTemplateMode.RAW,
    )

    suspend fun runDevOnlyPromptTemplateExperimentOnce(
        prompt: String,
        templateMode: HiddenQairt244PromptTemplateMode,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult = runRoute(
        requestedPrompt = prompt,
        maxOutputTokens = maxOutputTokens,
        timeoutMs = timeoutMs,
        promptSource = PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX,
        validation = promptLengthGateBypassedValidation(
            validation = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(prompt),
        ),
        allowMaxOutputTokenRange = true,
        expectedModelBasename = REQUIRED_MODEL_BASENAME,
        templateMode = templateMode,
    )

    private suspend fun runRoute(
        requestedPrompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
        promptSource: String,
        validation: NpuDiagnosticPromptValidator.Result,
        allowMaxOutputTokenRange: Boolean,
        expectedModelBasename: String,
        templateMode: HiddenQairt244PromptTemplateMode,
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
            maxOutputTokens in 1..maxOutputTokenRangeLimit
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
        val promptTemplate = PromptTemplateExperiment.apply(
            normalizedPrompt = normalizedPrompt,
            requestedMode = templateMode,
            promptSource = promptSource,
        )
        val shouldValidateFinalInput = (
            promptSource == PROMPT_SOURCE_CHAT_SCREEN &&
                !BuildConfig.CUSTOM_BUILD_EXPERIMENT
            ) || promptSource == PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX
        val finalInputValidation = if (shouldValidateFinalInput) {
            promptLengthGateBypassedValidation(
                NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(promptTemplate.finalModelInput),
            )
        } else {
            validation
        }
        if (!finalInputValidation.isValid) {
            appendRouteMarker(
                "state=invalid_prompt reason=${finalInputValidation.reasonCode} prompt_source=$promptSource " +
                    "prompt_validation_mode=${finalInputValidation.promptValidationMode} " +
                    "template_mode=${promptTemplate.mode.storageValue} " +
                    "prompt_input_code_points=${finalInputValidation.promptInputCodePoints} " +
                    "prompt_input_code_point_limit=${finalInputValidation.promptInputCodePointLimit} " +
                    "prompt_input_limit_mode=${finalInputValidation.promptInputLimitMode} " +
                    "engine_initialize=false run_decode=false",
            )
            appendInvalidPromptResult(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validation = finalInputValidation,
                promptTemplate = promptTemplate,
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "invalid_prompt:${finalInputValidation.reasonCode}",
            )
        }
        val usesSharedOnceGuard = usesSharedOnceGuard(promptSource)
        if (usesSharedOnceGuard && !runGuardFile.createNewFile()) {
            appendRouteMarker(
                "state=duplicate_run_blocked actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt " +
                    "prompt_source=$promptSource prompt_validation_mode=${finalInputValidation.promptValidationMode} " +
                    "template_mode=${promptTemplate.mode.storageValue} " +
                    "max_output_tokens=$maxOutputTokens engine_initialize=false run_decode=false db=false tts=false markdown=false stream=false",
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "duplicate_run_blocked",
            )
        }
        if (usesSharedOnceGuard) {
            runGuardFile.writeText("created_at_ms=${System.currentTimeMillis()}\n")
        }

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
                validation = finalInputValidation,
                resolution = modelResolution,
                promptTemplate = promptTemplate,
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
                validation = finalInputValidation,
                resolution = modelResolution,
                promptTemplate = promptTemplate,
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
                "turn_stop_compare_marker=qairt244_turn_stop_compare_v1 " +
                "requested_prompt=$requestedPrompt prompt_source=$promptSource " +
                "template_mode=${promptTemplate.mode.storageValue} final_model_input_length=${promptTemplate.finalModelInput.length} " +
                "prompt_validation_mode=${finalInputValidation.promptValidationMode} " +
                "prompt_input_code_points=${finalInputValidation.promptInputCodePoints} " +
                "prompt_input_code_point_limit=${finalInputValidation.promptInputCodePointLimit} " +
                "prompt_input_limit_mode=${finalInputValidation.promptInputLimitMode} " +
                "max_output_tokens=$maxOutputTokens " +
                "resolved_model_path=${modelResolution.path}",
        )

        val stageHistory = mutableListOf(NPU_S1_NATIVE_STAGE_ADAPTER_START)
        var nativeCallStartedAtElapsedRealtimeMs: Long? = null
        var nativeCallFinishedAtElapsedRealtimeMs: Long? = null
        var nativeCallReturned = false
        val start = SystemClock.elapsedRealtime()
        NpuEngineLogcatDiagnostics.i(
            event = "s1_engine_create_start",
            route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
            probeName = "npu_s1_adapter",
            modelPath = resolvedModelPath,
            backendRequested = "NPU",
            maxOutputTokens = maxOutputTokens,
            memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_engine_create_start"),
            detail = "run_id=$runId prompt_source=$promptSource prompt_length=${promptTemplate.finalModelInput.length}",
        )
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    NpuEngineLogcatDiagnostics.i(
                        event = "s1_decode_start",
                        route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                        probeName = "npu_s1_adapter",
                        modelPath = resolvedModelPath,
                        backendRequested = "NPU",
                        maxOutputTokens = maxOutputTokens,
                        memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_decode_start"),
                        detail = "run_id=$runId prompt_source=$promptSource prompt_length=${promptTemplate.finalModelInput.length}",
                    )
                    traceTerminal(DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN)
                    stageHistory += NPU_S1_NATIVE_STAGE_BEFORE_NATIVE_CALL
                    nativeCallStartedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    stageHistory += NPU_S1_NATIVE_STAGE_NATIVE_CALL
                    val nativeResult = Qairt244ShortMultitokenSmoke.runEditablePrompt(
                        context = appContext,
                        modelPath = resolvedModelPath,
                        runId = runId,
                        prompt = promptTemplate.finalModelInput,
                        maxOutputTokens = maxOutputTokens,
                        promptValidationMode = finalInputValidation.promptValidationMode,
                        unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
                    )
                    nativeCallFinishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    nativeCallReturned = true
                    stageHistory += NPU_S1_NATIVE_STAGE_AFTER_NATIVE_CALL
                    traceTerminal(DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN)
                    NpuEngineLogcatDiagnostics.i(
                        event = "s1_engine_create_success",
                        route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                        probeName = "npu_s1_adapter",
                        modelPath = resolvedModelPath,
                        backendRequested = "NPU",
                        maxOutputTokens = maxOutputTokens,
                        detail = "run_id=$runId native_result_returned=true",
                    )
                    nativeResult
                }
            }
            traceRunDecodeMarkerIfSeen()
            val elapsed = SystemClock.elapsedRealtime() - start
            val valuesBeforeMetadata = parseResultFile()
            stageHistory += NPU_S1_NATIVE_STAGE_NATIVE_RESULT_PARSE
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validation = finalInputValidation,
                timeout = false,
                freshCrash = false,
                values = valuesBeforeMetadata,
                resolution = modelResolution,
                promptTemplate = promptTemplate,
            )
            val values = parseResultFile()
            val nativeSuccess = values["result"] == "success"
            val output = values["output"]
            val rawNativeOutput = output.orEmpty()
            val sanitizerResult = Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawNativeOutput,
                prompt = normalizedPrompt,
            )
            val sanitizedOutput = sanitizerResult.sanitizedOutput
            val success = nativeSuccess && sanitizedOutput.isNotEmpty()
            val reasonCode = when {
                success -> "success"
                nativeSuccess -> "empty_after_sanitize"
                else -> "native_result:${values["result"] ?: "unknown"}"
            }
            NpuEngineLogcatDiagnostics.i(
                event = if (success) "s1_decode_success" else "s1_decode_failure",
                route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                probeName = "npu_s1_adapter",
                modelPath = resolvedModelPath,
                backendRequested = "NPU",
                maxOutputTokens = maxOutputTokens,
                memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_decode_finished"),
                detail = "run_id=$runId status=${if (success) "success" else "failure"} reason=$reasonCode run_decode_reached=true fallback_used=false timeout=false fresh_crash=false total_ms=${values["elapsed_ms"] ?: elapsed} decode_ms=${values["decode_elapsed_ms"] ?: "unavailable"}",
            )
            appendOutputDiagnostics(
                rawNativeOutput = rawNativeOutput,
                adapterOutput = sanitizedOutput,
                sanitizerResult = sanitizerResult,
                values = values,
                promptSource = promptSource,
            )
            stageHistory += if (success) {
                NPU_S1_NATIVE_STAGE_ADAPTER_SUCCESS
            } else {
                NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
            }
            appendNativeStageDiagnostics(
                runId = runId,
                stage = stageHistory.last(),
                stageHistory = stageHistory,
                values = values,
                nativeCallStartedAtElapsedRealtimeMs = nativeCallStartedAtElapsedRealtimeMs,
                nativeCallFinishedAtElapsedRealtimeMs = nativeCallFinishedAtElapsedRealtimeMs,
                nativeCallReturned = nativeCallReturned,
                throwable = null,
                errorStage = "unavailable",
                errorSource = "unavailable",
            )
            appendRouteMarker(
                "runId=$runId state=${if (success) "success" else "failure"} elapsed_ms=$elapsed " +
                    "result=${if (success) "success" else reasonCode} output=${sanitizedOutput.ifBlank { "-" }} " +
                    "sanitizer_applied=${sanitizerResult.sanitizerApplied} " +
                    "removed_template_token_count=${sanitizerResult.removedTemplateTokenCount} " +
                    "removed_prompt_echo=${sanitizerResult.removedPromptEcho} " +
                    "db=false tts=false markdown=false stream=false",
            )
            DevOnlyNpuRouteResult(
                success = success,
                output = sanitizedOutput.ifEmpty { null },
                reasonCode = reasonCode,
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
            traceRunDecodeMarkerIfSeen()
            val elapsed = SystemClock.elapsedRealtime() - start
            nativeCallFinishedAtElapsedRealtimeMs = nativeCallFinishedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
            stageHistory += NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
            NpuEngineLogcatDiagnostics.w(
                event = "s1_decode_failure",
                route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                probeName = "npu_s1_adapter",
                modelPath = resolvedModelPath,
                backendRequested = "NPU",
                maxOutputTokens = maxOutputTokens,
                memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_decode_timeout"),
                detail = "run_id=$runId status=failure reason=timeout run_decode_reached=true fallback_used=false timeout=true fresh_crash=false total_ms=$elapsed decode_ms=unavailable",
            )
            appendRouteMarker(
                "runId=$runId state=timeout elapsed_ms=$elapsed timeout_ms=$timeoutMs db=false tts=false markdown=false stream=false",
            )
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validation = finalInputValidation,
                timeout = true,
                freshCrash = false,
                values = parseResultFile(),
                resolution = modelResolution,
                promptTemplate = promptTemplate,
            )
            appendNativeStageDiagnostics(
                runId = runId,
                stage = NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE,
                stageHistory = stageHistory,
                values = parseResultFile(),
                nativeCallStartedAtElapsedRealtimeMs = nativeCallStartedAtElapsedRealtimeMs,
                nativeCallFinishedAtElapsedRealtimeMs = nativeCallFinishedAtElapsedRealtimeMs,
                nativeCallReturned = nativeCallReturned,
                throwable = timeout,
                errorStage = if (nativeCallStartedAtElapsedRealtimeMs != null && !nativeCallReturned) {
                    NPU_S1_NATIVE_STAGE_NATIVE_CALL
                } else {
                    NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
                },
                errorSource = "throwable",
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
            traceRunDecodeMarkerIfSeen()
            val elapsed = SystemClock.elapsedRealtime() - start
            val reasonCode = npuNativeLinkFailureReason(throwable)
            nativeCallFinishedAtElapsedRealtimeMs = nativeCallFinishedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
            stageHistory += NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
            NpuEngineLogcatDiagnostics.e(
                event = "s1_engine_create_failure",
                route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                throwable = throwable,
                probeName = "npu_s1_adapter",
                modelPath = resolvedModelPath,
                backendRequested = "NPU",
                maxOutputTokens = maxOutputTokens,
                memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_engine_create_failure"),
                detail = "run_id=$runId reason=$reasonCode prompt_length=${promptTemplate.finalModelInput.length}",
            )
            NpuEngineLogcatDiagnostics.e(
                event = "s1_decode_failure",
                route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                throwable = throwable,
                probeName = "npu_s1_adapter",
                modelPath = resolvedModelPath,
                backendRequested = "NPU",
                maxOutputTokens = maxOutputTokens,
                memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_decode_failure"),
                detail = "run_id=$runId status=failure reason=$reasonCode run_decode_reached=false fallback_used=false timeout=false fresh_crash=false total_ms=$elapsed decode_ms=unavailable",
            )
            NpuEngineLogcatDiagnostics.e(
                event = "s1_adapter_failure",
                route = "Qairt244DevOnlyNpuRouteAdapter.runRoute",
                throwable = throwable,
                probeName = "npu_s1_adapter",
                modelPath = resolvedModelPath,
                backendRequested = "NPU",
                maxOutputTokens = maxOutputTokens,
                memorySnapshot = captureLocalMemorySnapshot(appContext, "s1_adapter_failure"),
                detail = "run_id=$runId reason=$reasonCode",
            )
            appendRouteMarker(
                "runId=$runId state=failure elapsed_ms=$elapsed class=${throwable.javaClass.name} " +
                    "message=${throwable.message ?: "-"} db=false tts=false markdown=false stream=false",
            )
            NpuS1LogcatDiagnostics.logAdapterFailure(
                reason = reasonCode,
                throwable = throwable,
                memorySnapshot = captureLocalMemorySnapshot(
                    context = appContext,
                    stage = "npu_s1_adapter_failure",
                ),
                promptLength = requestedPrompt.length,
                effectiveMaxOutputTokens = maxOutputTokens,
            )
            appendRouteResultMetadata(
                requestedPrompt = requestedPrompt,
                normalizedPrompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                promptSource = promptSource,
                validation = finalInputValidation,
                timeout = false,
                freshCrash = false,
                values = parseResultFile(),
                resolution = modelResolution,
                promptTemplate = promptTemplate,
            )
            appendNativeStageDiagnostics(
                runId = runId,
                stage = NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE,
                stageHistory = stageHistory,
                values = parseResultFile(),
                nativeCallStartedAtElapsedRealtimeMs = nativeCallStartedAtElapsedRealtimeMs,
                nativeCallFinishedAtElapsedRealtimeMs = nativeCallFinishedAtElapsedRealtimeMs,
                nativeCallReturned = nativeCallReturned,
                throwable = throwable,
                errorStage = if (nativeCallStartedAtElapsedRealtimeMs != null && !nativeCallReturned) {
                    NPU_S1_NATIVE_STAGE_NATIVE_CALL
                } else {
                    NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE
                },
                errorSource = "throwable",
            )
            DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = reasonCode,
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
        sanitizerResult: Qairt244NpuOutputSanitizer.Result,
        values: Map<String, String>,
        promptSource: String,
    ) {
        val outputDiagnostics = Qairt244OutputUnicodeDiagnostics.toEscapedLines(
            fields = Qairt244OutputUnicodeDiagnostics.buildFields(
                output = rawNativeOutput,
                values = values,
            ),
            escapeValue = ::escapeValue,
        )
        resultFile.appendText(
            listOf(
                "route_type=${routeType(promptSource)}",
                "raw_native_output=${escapeValue(rawNativeOutput)}",
                "raw_native_output_length=${rawNativeOutput.length}",
                "raw_output=${escapeValue(sanitizerResult.rawOutput)}",
                "raw_output_length=${sanitizerResult.rawOutput.length}",
                "sanitized_output=${escapeValue(sanitizerResult.sanitizedOutput)}",
                "sanitized_output_length=${sanitizerResult.sanitizedOutput.length}",
                "sanitizer_applied=${sanitizerResult.sanitizerApplied}",
                "removed_template_token_count=${sanitizerResult.removedTemplateTokenCount}",
                "removed_prompt_echo=${sanitizerResult.removedPromptEcho}",
                "code_block_detected=${sanitizerResult.codeBlockDetected}",
                "code_fence_completed=${sanitizerResult.codeFenceCompleted}",
                "adapter_output=${escapeValue(adapterOutput)}",
                "adapter_output_length=${adapterOutput.length}",
            ).plus(outputDiagnostics).plus(
                listOf(
                    "markdown_mode=non_streaming_direct_insert",
                    "repair_applied=false",
                ),
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun appendNativeStageDiagnostics(
        runId: String,
        stage: String,
        stageHistory: List<String>,
        values: Map<String, String>,
        nativeCallStartedAtElapsedRealtimeMs: Long?,
        nativeCallFinishedAtElapsedRealtimeMs: Long?,
        nativeCallReturned: Boolean,
        throwable: Throwable?,
        errorStage: String,
        errorSource: String,
    ) {
        val resultTail = tailFileText(resultFile)
        val diagTail = tailFileText(nativeDiagFile)
        val nativeCallDurationMs = if (
            nativeCallStartedAtElapsedRealtimeMs != null &&
            nativeCallFinishedAtElapsedRealtimeMs != null
        ) {
            (nativeCallFinishedAtElapsedRealtimeMs - nativeCallStartedAtElapsedRealtimeMs).toString()
        } else {
            "unavailable"
        }
        val decodeReached = values["decode_elapsed_ms"]?.isNotBlank() == true ||
            values["run_decode"]?.contains("RunDecode") == true ||
            diagTail.contains("RunDecode", ignoreCase = true)
        val decodeFinished = values["decode_elapsed_ms"]?.isNotBlank() == true ||
            (values["result"] == "success" && decodeReached)
        val cleanupReached = values["cleanup_elapsed_ms"]?.isNotBlank() == true ||
            diagTail.contains("cleanup", ignoreCase = true)
        val sessionDestroyReached = diagTail.contains("session_destroy", ignoreCase = true) ||
            diagTail.contains("destroy", ignoreCase = true) ||
            diagTail.contains("engine_ptr.reset", ignoreCase = true) ||
            diagTail.contains("session_ptr.reset", ignoreCase = true)
        val nativeLinkDiagnostics = throwable?.let {
            buildNpuNativeLinkFailureDiagnostics(
                throwable = it,
                javaLibraryPath = System.getProperty("java.library.path"),
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            )
        }
        resultFile.appendText(
            listOf(
                "native_run_id=$runId",
                "native_stage=$stage",
                "native_stage_history=${stageHistory.joinToString(">")}",
                "native_call_started_at_elapsed_realtime_ms=${nativeCallStartedAtElapsedRealtimeMs?.toString() ?: "unavailable"}",
                "native_call_finished_at_elapsed_realtime_ms=${nativeCallFinishedAtElapsedRealtimeMs?.toString() ?: "unavailable"}",
                "native_call_duration_ms=$nativeCallDurationMs",
                "native_call_reached=${nativeCallStartedAtElapsedRealtimeMs != null}",
                "native_call_returned=$nativeCallReturned",
                "native_decode_started=${if (decodeReached) "true" else "unavailable"}",
                "native_decode_finished=${if (decodeFinished) "true" else "unavailable"}",
                "native_cleanup_started=${if (cleanupReached) "true" else "unavailable"}",
                "native_cleanup_finished=${if (values["cleanup_elapsed_ms"]?.isNotBlank() == true) "true" else "unavailable"}",
                "native_cleanup_reached=${if (cleanupReached) "true" else "unavailable"}",
                "native_session_destroy_started=${if (sessionDestroyReached) "true" else "unavailable"}",
                "native_session_destroy_finished=${if (sessionDestroyReached) "true" else "unavailable"}",
                "native_session_destroy_reached=${if (sessionDestroyReached) "true" else "unavailable"}",
                "native_result_available=${resultTail.isNotBlank()}",
                "native_result_tail=${escapeValue(resultTail)}",
                "native_diag_available=${diagTail.isNotBlank()}",
                "native_diag_tail=${escapeValue(diagTail)}",
                "native_error_class=${throwable?.javaClass?.simpleName ?: "unavailable"}",
                "native_error_message=${escapeValue(throwable?.message ?: "unavailable")}",
                "native_error_stage=$errorStage",
                "native_error_source=$errorSource",
            ).plus(
                nativeLinkDiagnostics?.let(::npuNativeLinkFailureDiagnosticsLines)
                    ?: npuNativeLinkFailureDiagnosticsLines(
                        buildNpuNativeLinkFailureDiagnostics(
                            throwable = RuntimeException("no native link failure"),
                            javaLibraryPath = System.getProperty("java.library.path"),
                            supportedAbis = Build.SUPPORTED_ABIS.toList(),
                        ),
                    ),
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun tailFileText(file: File): String {
        if (!file.isFile) return ""
        val text = runCatching { file.readText() }.getOrDefault("")
        return text.takeLast(NATIVE_STAGE_TAIL_LIMIT_CHARS)
    }

    private fun promptFormattingDiagnostics(
        requestedPrompt: String,
        normalizedPrompt: String,
        promptSource: String,
        promptTemplate: PromptTemplateExperiment.Result = PromptTemplateExperiment.apply(
            normalizedPrompt = normalizedPrompt,
            requestedMode = HiddenQairt244PromptTemplateMode.RAW,
            promptSource = promptSource,
        ),
    ): List<String> = listOf(
        "raw_user_prompt=${escapeValue(requestedPrompt)}",
        "normalized_prompt=${escapeValue(normalizedPrompt)}",
        "final_model_input=${escapeValue(promptTemplate.finalModelInput)}",
        "final_model_input_length=${promptTemplate.finalModelInput.length}",
        "final_model_input_code_points=${promptTemplate.finalModelInput.codePointCount(0, promptTemplate.finalModelInput.length)}",
        "conversation_history_count=0",
        "system_prompt_used=none",
        "chat_template_used=${promptTemplate.chatTemplateUsed}",
        "template_mode=${promptTemplate.mode.storageValue}",
        "template_prefix_length=${promptTemplate.prefix.length}",
        "template_suffix_length=${promptTemplate.suffix.length}",
        "prompt_source=$promptSource",
        "prompt_formatting_mode=${promptTemplate.promptFormattingMode}",
    )

    private fun promptInputDiagnostics(
        validation: NpuDiagnosticPromptValidator.Result,
        values: Map<String, String> = emptyMap(),
    ): List<String> = listOf(
        "prompt_input_code_points=${values["prompt_input_code_points"] ?: validation.promptInputCodePoints}",
        "prompt_input_code_point_limit=${values["prompt_input_code_point_limit"] ?: validation.promptInputCodePointLimit}",
        "prompt_input_limit_mode=${values["prompt_input_limit_mode"] ?: validation.promptInputLimitMode}",
    )

    private fun promptLengthGateBypassedValidation(
        validation: NpuDiagnosticPromptValidator.Result,
    ): NpuDiagnosticPromptValidator.Result {
        if (!unsafeDevBypassPromptLengthGate || !isHiddenPromptLengthGateBlock(validation)) return validation
        return validation.copy(isValid = true)
    }

    private fun isHiddenPromptLengthGateBlock(validation: NpuDiagnosticPromptValidator.Result): Boolean =
        validation.reasonCode == "too_long" &&
            validation.promptInputCodePointLimit == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH &&
            validation.promptInputLimitMode == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE

    private fun promptLengthGateDiagnostics(validation: NpuDiagnosticPromptValidator.Result): List<String> {
        val wouldBlock = isHiddenPromptLengthGateBlock(validation)
        return listOf(
            "unsafe_dev_bypass_prompt_length_gate_requested=$unsafeDevBypassPromptLengthGate",
            "unsafe_dev_bypass_prompt_length_gate_effective=$unsafeDevBypassPromptLengthGate",
            "prompt_length_gate_limit=${NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH}",
            "prompt_length_gate_would_block=$wouldBlock",
            "prompt_length_gate_bypassed=${unsafeDevBypassPromptLengthGate && wouldBlock}",
            "adapter_prompt_length_gate_would_block=$wouldBlock",
            "adapter_prompt_length_gate_bypassed=${unsafeDevBypassPromptLengthGate && wouldBlock}",
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

    private fun traceTerminal(marker: DevOnlyNpuTerminalTraceMarker) {
        val runId = terminalTraceRunId ?: return
        DevOnlyNpuTerminalTrace.append(
            context = appContext,
            runId = runId,
            marker = marker,
        )
    }

    private fun traceRunDecodeMarkerIfSeen() {
        if (!nativeDiagFile.isFile) return
        val text = nativeDiagFile.readText()
        val markerSeen = text.contains("before RunDecode SetMaxOutputTokens") ||
            text.contains("SetMaxOutputTokens(512)") ||
            text.contains("RunDecode")
        if (markerSeen) {
            traceTerminal(DevOnlyNpuTerminalTraceMarker.BEFORE_RUN_DECODE_MARKER_SEEN)
        }
    }

    private fun appendInvalidPromptResult(
        requestedPrompt: String,
        normalizedPrompt: String,
        maxOutputTokens: Int,
        promptSource: String,
        validation: NpuDiagnosticPromptValidator.Result,
        promptTemplate: PromptTemplateExperiment.Result = PromptTemplateExperiment.apply(
            normalizedPrompt = normalizedPrompt,
            requestedMode = HiddenQairt244PromptTemplateMode.RAW,
            promptSource = promptSource,
        ),
    ) {
        resultFile.writeText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=invalid_prompt:${validation.reasonCode}",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$normalizedPrompt",
                *promptFormattingDiagnostics(
                    requestedPrompt = requestedPrompt,
                    normalizedPrompt = normalizedPrompt,
                    promptSource = promptSource,
                    promptTemplate = promptTemplate,
                ).toTypedArray(),
                "prompt_validation_mode=${validation.promptValidationMode}",
                *promptInputDiagnostics(validation).toTypedArray(),
                *promptLengthGateDiagnostics(validation).toTypedArray(),
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
        validation: NpuDiagnosticPromptValidator.Result,
        resolution: Qairt244ModelPathResolver.Resolution,
        promptTemplate: PromptTemplateExperiment.Result,
    ) {
        resultFile.appendText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=${resolution.reasonCode}",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$prompt",
                *promptFormattingDiagnostics(
                    requestedPrompt = requestedPrompt,
                    normalizedPrompt = prompt,
                    promptSource = promptSource,
                    promptTemplate = promptTemplate,
                ).toTypedArray(),
                "prompt_validation_mode=${validation.promptValidationMode}",
                *promptInputDiagnostics(validation).toTypedArray(),
                *promptLengthGateDiagnostics(validation).toTypedArray(),
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
        validation: NpuDiagnosticPromptValidator.Result,
        resolution: Qairt244ModelPathResolver.Resolution,
        promptTemplate: PromptTemplateExperiment.Result,
    ) {
        resultFile.appendText(
            listOf(
                "marker=$ROUTE_MARKER",
                "selected_route=${selectedRoute(promptSource)}",
                "result=failure",
                "reasonCode=model_file_not_required_sm8750",
                "requested_prompt=$requestedPrompt",
                "actual_prompt=$prompt",
                *promptFormattingDiagnostics(
                    requestedPrompt = requestedPrompt,
                    normalizedPrompt = prompt,
                    promptSource = promptSource,
                    promptTemplate = promptTemplate,
                ).toTypedArray(),
                "prompt_validation_mode=${validation.promptValidationMode}",
                *promptInputDiagnostics(validation).toTypedArray(),
                *promptLengthGateDiagnostics(validation).toTypedArray(),
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
        validation: NpuDiagnosticPromptValidator.Result,
        timeout: Boolean,
        freshCrash: Boolean,
        values: Map<String, String>,
        resolution: Qairt244ModelPathResolver.Resolution,
        promptTemplate: PromptTemplateExperiment.Result,
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
                *promptFormattingDiagnostics(
                    requestedPrompt = requestedPrompt,
                    normalizedPrompt = normalizedPrompt,
                    promptSource = promptSource,
                    promptTemplate = promptTemplate,
                ).toTypedArray(),
                "prompt_validation_mode=${validation.promptValidationMode}",
                *promptInputDiagnostics(validation, values).toTypedArray(),
                *promptLengthGateDiagnostics(validation).toTypedArray(),
                "native_prompt_validation_mode=${values["native_prompt_validation_mode"] ?: validation.promptValidationMode}",
                "native_prompt_input_code_point_limit=${values["native_prompt_input_code_point_limit"].orEmpty()}",
                "native_prompt_input_limit_mode=${values["native_prompt_input_limit_mode"].orEmpty()}",
                "utf8_allowed=${values["utf8_allowed"] ?: (validation.promptValidationMode != NpuDiagnosticPromptValidator.ASCII_DIAGNOSTIC_MODE).toString()}",
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

    private object PromptTemplateExperiment {
        data class Result(
            val mode: HiddenQairt244PromptTemplateMode,
            val prefix: String,
            val suffix: String,
            val finalModelInput: String,
            val chatTemplateUsed: String,
            val promptFormattingMode: String,
        )

        fun apply(
            normalizedPrompt: String,
            requestedMode: HiddenQairt244PromptTemplateMode,
            promptSource: String,
        ): Result {
            val effectiveMode = if (
                promptSource == PROMPT_SOURCE_CHAT_SCREEN ||
                promptSource == PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX
            ) {
                requestedMode
            } else {
                HiddenQairt244PromptTemplateMode.RAW
            }
            val (prefix, suffix) = when (effectiveMode) {
                HiddenQairt244PromptTemplateMode.RAW -> "" to ""
                HiddenQairt244PromptTemplateMode.SIMPLE_JA_CHAT ->
                    "あなたは親切なAIアシスタントです。\nユーザー: " to "\nアシスタント:"
                HiddenQairt244PromptTemplateMode.GEMMA_IT_LIKE ->
                    "<start_of_turn>user\n" to "\n<end_of_turn>\n<start_of_turn>model"
            }
            return Result(
                mode = effectiveMode,
                prefix = prefix,
                suffix = suffix,
                finalModelInput = "$prefix$normalizedPrompt$suffix",
                chatTemplateUsed = effectiveMode.storageValue,
                promptFormattingMode = if (effectiveMode == HiddenQairt244PromptTemplateMode.RAW) {
                    "raw_normalized_prompt"
                } else {
                    "hidden_prompt_template_experiment"
                },
            )
        }
    }

    companion object {
        const val ROUTE_MARKER = "qairt244_chat_screen_real_npu_adapter_v1"
        const val PROMPT_SOURCE_CHAT_SCREEN = "chat_screen"
        const val PROMPT_SOURCE_INTERNAL_INTENT = "internal_intent"
        const val PROMPT_SOURCE_DEV_ONLY_CONVERSATION = "dev_only_conversation"
        const val PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX = "dev_only_prompt_template_matrix"
        const val REQUIRED_MODEL_BASENAME = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
        private const val NATIVE_STAGE_TAIL_LIMIT_CHARS = 800
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
            } else if (promptSource == PROMPT_SOURCE_DEV_ONLY_CONVERSATION) {
                "dev_only_one_turn_conversation"
            } else if (promptSource == PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX) {
                "dev_only_prompt_template_matrix"
            } else {
                "internal_intent"
            }

        fun usesSharedOnceGuard(promptSource: String): Boolean =
            promptSource != PROMPT_SOURCE_DEV_ONLY_CONVERSATION &&
                promptSource != PROMPT_SOURCE_DEV_ONLY_PROMPT_TEMPLATE_MATRIX
    }
}
