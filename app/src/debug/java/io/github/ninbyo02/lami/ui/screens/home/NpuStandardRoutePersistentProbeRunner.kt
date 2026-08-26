package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer

internal object NpuStandardRoutePersistentProbeRunner {
    private const val NATIVE_PROBE_MODE_STANDARD_ROUTE_REUSE_ONCE = "standard_route_reuse_once"
    private const val HOLDER_KEY_PREFIX = "standard_route_reuse_once_v1"

    fun run(
        context: Context,
        request: DevOnlyNpuOneTurnConversationRequest,
    ): DevOnlyNpuOneTurnConversationDisplay {
        val appContext = context.applicationContext
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        if (modelPath.isBlank()) {
            return failureDisplay(
                request = request,
                reason = "model_resolution_failed:${modelResolution.reasonCode}",
                nativeErrorMessage = modelResolution.reasonCode,
            )
        }

        val finalPrompt = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )
        val startedAt = SystemClock.elapsedRealtime()
        val nativeResult = Qairt244ShortMultitokenSmoke.runPersistentProbe(
            context = appContext,
            modelPath = modelPath,
            runId = "standard_route_reuse_once_${startedAt}",
            prompt = finalPrompt,
            maxOutputTokens = request.maxOutputTokens,
            runCount = 1,
            holderKey = listOf(
                HOLDER_KEY_PREFIX,
                appContext.packageName,
                modelPath,
                appContext.applicationInfo.nativeLibraryDir,
                appContext.cacheDir.absolutePath,
                request.maxOutputTokens.toString(),
            ).joinToString(separator = "|"),
            nativeProbeMode = NATIVE_PROBE_MODE_STANDARD_ROUTE_REUSE_ONCE,
            promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
            unsafeDevBypassPromptLengthGate = true,
        )
        val values = parseKeyValues(nativeResult.resultText)
        val rawOutput = unflatten(values["raw_output"].orEmpty())
        val sanitizer = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = rawOutput,
            prompt = request.userPrompt,
        )
        val sanitizedOutput = Qairt244NpuOutputSanitizer
            .normalizeJapaneseInternalSpaces(sanitizer.sanitizedOutput)
            .trim()
        val nativeStatus = values["persistent_custom_jni_status"].orEmpty()
        val nativeHypothesis = values["persistent_custom_jni_hypothesis_result"].orEmpty()
        val nativeBackendEvidence = values["backend_evidence"].orEmpty()
        val contractBackendEvidence = normalizeNpuBackendEvidence(nativeBackendEvidence)
        val throwableUnavailable = nativeResult.throwableClass == "unavailable"
        val decodeReached = (values["decode_reached"] == "true") ||
            ((values["decode_count"]?.toIntOrNull() ?: 0) > 0) ||
            ((values["decode_success_count"]?.toIntOrNull() ?: 0) > 0)
        val success = throwableUnavailable &&
            nativeStatus == "completed" &&
            nativeHypothesis == "standard_route_reuse_once_success" &&
            decodeReached &&
            sanitizedOutput.isNotBlank()
        val reason = if (success) {
            "success"
        } else {
            values["first_failure_reason"]
                ?: nativeResult.throwableMessage.takeIf { it != "unavailable" }
                ?: values["reason"]
                ?: "standard_route_reuse_once_failure"
        }
        val finishedAt = SystemClock.elapsedRealtime()
        return DevOnlyNpuOneTurnConversationDisplay(
            text = buildString {
                appendLine("NPU STANDARD ROUTE S1 PERSISTENT")
                appendLine("status=${if (success) "success" else "failure"}")
                appendLine("reason=$reason")
                appendLine("native_probe_mode=$NATIVE_PROBE_MODE_STANDARD_ROUTE_REUSE_ONCE")
                appendLine("engine_create_count=${values["engine_create_count"] ?: "unavailable"}")
                appendLine("holder_generation=${values["holder_generation"] ?: "unavailable"}")
                appendLine("holder_reused_count=${values["holder_reused_count"] ?: "unavailable"}")
                appendLine("decode_count=${values["decode_count"] ?: values["decode_success_count"] ?: "unavailable"}")
                appendLine("engine_holder_open_after_run=${values["engine_holder_open_during_decode"] ?: "unavailable"}")
                appendLine("npu_backend_evidence=$contractBackendEvidence")
                appendLine("native_backend_evidence=$nativeBackendEvidence")
                appendLine("raw_output=$rawOutput")
                appendLine("sanitized_output=$sanitizedOutput")
            }.trimEnd(),
            output = sanitizedOutput,
            status = if (success) NpuStandardRouteS1Contract.STATUS_SUCCESS else "failure",
            reason = reason,
            nativeReached = true,
            decodeReached = decodeReached,
            npuEvidence = contractBackendEvidence,
            fallback = false,
            freshCrash = false,
            timeout = false,
            requestedMaxOutputTokens = request.maxOutputTokens,
            effectiveMaxOutputTokens = request.maxOutputTokens,
            nativeMaxOutputTokensLimit = NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT.toString(),
            rawLen = rawOutput.length,
            sanitizedLen = sanitizedOutput.length,
            quality = values["quality_classification"].takeUnless { it.isNullOrBlank() || it == "unavailable" }
                ?: if (success) "natural_japanese" else "standard_route_reuse_once_failure",
            controlCharSummary = "unavailable",
            rawOutputFirst200Chars = rawOutput.take(200),
            rawOutputLast200Chars = rawOutput.takeLast(200),
            rawUnicodeSummary = "unavailable",
            sanitizerApplied = sanitizer.sanitizerApplied.toString(),
            removedTemplateTokenCount = sanitizer.removedTemplateTokenCount.toString(),
            removedPromptEcho = sanitizer.removedPromptEcho.toString(),
            replacementCharCount = "unavailable",
            outputContainsControlChars = "unavailable",
            rawOutput = rawOutput,
            stopReason = values["stop_reason"].orEmpty(),
            finishReason = values["finish_reason"].orEmpty(),
            eosDetected = values["eos_detected"].orEmpty(),
            outputTokenCount = values["output_token_count"].orEmpty(),
            promptTokenCount = values["prompt_token_count"].orEmpty(),
            nativeDiagnostics = NpuS1NativeStageDiagnostics(
                nativeRunId = "standard_route_reuse_once_${startedAt}",
                nativeStage = if (success) NPU_S1_NATIVE_STAGE_ADAPTER_SUCCESS else NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE,
                nativeStageHistory = "provider_start>adapter_start>persistent_holder_run_once>${if (success) "adapter_success" else "adapter_failure"}",
                nativeCallStartedAtElapsedRealtimeMs = startedAt.toString(),
                nativeCallFinishedAtElapsedRealtimeMs = finishedAt.toString(),
                nativeCallDurationMs = (finishedAt - startedAt).toString(),
                nativeCallReached = "true",
                nativeCallReturned = throwableUnavailable.toString(),
                nativeDecodeStarted = decodeReached.toString(),
                nativeDecodeFinished = (success || decodeReached).toString(),
                nativeCleanupStarted = success.toString(),
                nativeCleanupFinished = success.toString(),
                nativeCleanupReached = success.toString(),
                nativeResultAvailable = nativeResult.resultText.isNotBlank().toString(),
                nativeResultTail = flatten(nativeResult.resultText.takeLast(1200)),
                nativeDiagAvailable = nativeResult.diagText.isNotBlank().toString(),
                nativeDiagTail = flatten(nativeResult.diagText.takeLast(1200)),
                nativeErrorClass = if (success) "unavailable" else nativeResult.throwableClass,
                nativeErrorMessage = if (success) "unavailable" else reason,
                nativeErrorStage = if (success) "unavailable" else values["first_failure_stage"].orEmpty().ifBlank { "persistent_holder_run_once" },
                nativeErrorSource = if (success) "unavailable" else "persistent_probe",
                nativeLinkFailureDetected = "false",
                nativeLinkFailureLibrary = "unavailable",
                nativeLoadOrder = "litertlm_jni>lami_npu_persistent_holder_stub",
                javaLibraryPath = System.getProperty("java.library.path") ?: "unavailable",
                supportedAbis = android.os.Build.SUPPORTED_ABIS?.joinToString(",") ?: "unavailable",
            ),
        )
    }

    private fun failureDisplay(
        request: DevOnlyNpuOneTurnConversationRequest,
        reason: String,
        nativeErrorMessage: String,
    ): DevOnlyNpuOneTurnConversationDisplay = DevOnlyNpuOneTurnConversationDisplay(
        text = "NPU STANDARD ROUTE S1 PERSISTENT\nstatus=failure\nreason=$reason",
        output = "",
        status = "failure",
        reason = reason,
        nativeReached = false,
        decodeReached = false,
        npuEvidence = "unavailable",
        fallback = false,
        freshCrash = false,
        timeout = false,
        requestedMaxOutputTokens = request.maxOutputTokens,
        effectiveMaxOutputTokens = request.maxOutputTokens,
        nativeMaxOutputTokensLimit = NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT.toString(),
        rawLen = 0,
        sanitizedLen = 0,
        quality = "failure",
        controlCharSummary = "unavailable",
        rawOutputFirst200Chars = "",
        rawOutputLast200Chars = "",
        rawUnicodeSummary = "unavailable",
        sanitizerApplied = "false",
        removedTemplateTokenCount = "0",
        removedPromptEcho = "false",
        replacementCharCount = "unavailable",
        outputContainsControlChars = "unavailable",
        nativeDiagnostics = NpuS1NativeStageDiagnostics(
            nativeStage = NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE,
            nativeStageHistory = "provider_start>adapter_failure",
            nativeErrorClass = "unavailable",
            nativeErrorMessage = nativeErrorMessage,
            nativeErrorStage = "model_resolution",
            nativeErrorSource = "kotlin",
            nativeLinkFailureDetected = "false",
        ),
    )

    internal fun normalizeNpuBackendEvidence(nativeEvidence: String): String =
        if (listOf("QNN", "HTP", "FastRPC").all { marker ->
                nativeEvidence.contains(marker, ignoreCase = true)
            }
        ) {
            NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE
        } else {
            nativeEvidence.ifBlank { "unavailable" }
        }

    private fun parseKeyValues(text: String): Map<String, String> = text
        .lineSequence()
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }
        .toMap()

    private fun unflatten(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\r", "\r")

    private fun flatten(value: String): String = value
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
