package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuS1PersistentCustomJniDevProbe(
    context: Context,
) : NpuS1PersistentCustomJniProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(
        mode: NpuS1PersistentCustomJniProbeMode,
        onUpdate: (NpuS1PersistentCustomJniProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentCustomJniProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val cacheDir = appContext.cacheDir.absolutePath
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        val modelFile = modelPath.takeIf { it.isNotBlank() }?.let(::File)
        val modelFileSize = modelFile?.takeIf { it.exists() }?.length()?.toString() ?: "unavailable"
        val modelLastModified = modelFile?.takeIf { it.exists() }?.lastModified()?.toString() ?: "unavailable"
        val holderKey = NpuS1PersistentCustomJniHolderKey(
            modelPath = modelPath.ifBlank { modelResolution.reasonCode },
            modelFileLastModified = modelLastModified,
            modelFileSize = modelFileSize,
            backend = NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND,
            cacheDir = cacheDir,
            maxTokenBudget = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS.toString(),
            engineConfigVersion = NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION,
        )
        var state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_RUNNING,
            runCountRequested = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
            runCountCompletedOverride = 0,
            startedAtElapsedRealtimeMs = startedAt,
            engineCreateCount = "0",
            engineCloseReached = "false",
            engineCloseSuccess = "unavailable",
            holderKey = holderKey,
            holderGeneration = "unavailable",
            holderReusedCount = "0",
            holderInvalidated = "false",
            holderKeyMismatchDetected = "false",
            holderKeyMismatchReason = "unavailable",
            nativeHolderEntrypointAvailable = "false",
            selectedNativeProbeMode = mode.wireValue,
            modelPath = holderKey.modelPath,
            modelFileSize = modelFileSize,
            modelFileLastModified = modelLastModified,
            backendEvidence = "custom_jni_persistent_holder_probe_requested",
            persistentCustomJniHypothesisResult = "starting",
        )
        fun update(next: NpuS1PersistentCustomJniProbeState) {
            state = next
            onUpdate(next)
        }
        update(state)

        if (isCancelled()) {
            return@withContext state.copy(
                persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_CANCELLED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                persistentCustomJniHypothesisResult = "cancelled",
            ).also(::update)
        }

        if (modelPath.isBlank()) {
            return@withContext state.copy(
                persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                holderInvalidated = "true",
                firstFailureStage = "model_resolve",
                firstFailureReason = "model_resolution_failed:${modelResolution.reasonCode}",
                firstFailureExceptionClass = "unavailable",
                firstFailureDiagTail = "persistent_custom_jni_holder_not_started model_resolution=${modelResolution.reasonCode}",
                backendEvidence = "custom_jni_persistent_holder_model_unavailable",
                persistentCustomJniHypothesisResult = "model_resolution_failed",
            ).also(::update)
        }

        val runId = "npu_s1_persistent_custom_jni_${SystemClock.elapsedRealtime()}"
        val nativeResult = Qairt244ShortMultitokenSmoke.runPersistentProbe(
            context = appContext,
            modelPath = modelPath,
            runId = runId,
            prompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
            maxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
            runCount = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
            holderKey = holderKey.stableText(),
            nativeProbeMode = mode.wireValue,
            promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
            unsafeDevBypassPromptLengthGate = true,
        )
        val parsedState = parsePersistentCustomJniProbeResult(
            result = nativeResult,
            fallbackState = state,
            holderKey = holderKey,
        )
        parsedState.copy(finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime())
            .also(::update)
    }
}

private fun parsePersistentCustomJniProbeResult(
    result: Qairt244PersistentProbeResult,
    fallbackState: NpuS1PersistentCustomJniProbeState,
    holderKey: NpuS1PersistentCustomJniHolderKey,
): NpuS1PersistentCustomJniProbeState {
    if (result.resultText.isBlank()) {
        val entrypointMissing = result.throwableClass.endsWith("UnsatisfiedLinkError") ||
            result.throwableClass == UnsatisfiedLinkError::class.java.name
        return fallbackState.copy(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            nativeHolderEntrypointAvailable = if (entrypointMissing) "false" else "unavailable",
            holderInvalidated = "true",
            firstFailureStage = if (entrypointMissing) "native_holder_entrypoint" else "native_call",
            firstFailureReason = if (entrypointMissing) {
                "native_persistent_holder_entrypoint_not_available"
            } else {
                "native_persistent_probe_failed:${result.throwableClass}"
            },
            firstFailureExceptionClass = result.throwableClass.substringAfterLast('.'),
            firstFailureDiagTail = result.throwableMessage,
            backendEvidence = if (entrypointMissing) {
                "custom_jni_persistent_holder_entrypoint_missing"
            } else {
                "custom_jni_persistent_holder_native_call_failed_without_result"
            },
            persistentCustomJniHypothesisResult = if (entrypointMissing) {
                "native_holder_entrypoint_not_available"
            } else {
                "native_persistent_probe_failed_without_result"
            },
        )
    }

    val parsed = parsePersistentCustomJniKeyValueLines(result.resultText)
    val summary = parsed.summary
    val records = parsed.records.map { values ->
        val rawOutput = values["raw_output"].orEmpty()
        val sanitized = values["sanitized_output"]
            ?.takeIf { it.isNotBlank() }
            ?: Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawOutput,
                prompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
            ).sanitizedOutput
        NpuS1PersistentCustomJniRunRecord(
            runIndex = values["run_index"]?.toIntOrNull() ?: 0,
            status = values["status"].orEmpty().ifBlank { "unavailable" },
            reason = values["reason"].orEmpty().ifBlank { "unavailable" },
            sessionCreated = values["session_created"].orUnavailable(),
            sessionClosed = values["session_closed"].orUnavailable(),
            prefillStarted = values["prefill_started"].orUnavailable(),
            prefillFinished = values["prefill_finished"].orUnavailable(),
            decodeStarted = values["decode_started"].orUnavailable(),
            decodeFinished = values["decode_finished"].orUnavailable(),
            rawOutput = rawOutput,
            sanitizedOutput = sanitized,
            qualityClassification = values["quality_classification"].orUnavailable(),
            totalMs = values["total_ms"].toNullableNonNegativeLong(),
            prefillMs = values["prefill_ms"].toNullableNonNegativeLong(),
            decodeMs = values["decode_ms"].toNullableNonNegativeLong(),
            cleanupMs = values["cleanup_ms"].toNullableNonNegativeLong(),
            failureStage = values["failure_stage"].orUnavailable(),
            failureExceptionClass = values["failure_exception_class"].orUnavailable(),
            failureExceptionMessage = values["failure_exception_message"].orUnavailable(),
            nativeDiagTail = values["native_diag_tail"]
                ?: result.diagText.lineSequence().lastOrNull().orUnavailable(),
        )
    }
    return fallbackState.copy(
        persistentCustomJniStatus = summary["persistent_custom_jni_status"].orUnavailable(),
        runCountCompletedOverride = summary["run_count_completed"]?.toIntOrNull(),
        engineCreateCount = summary["engine_create_count"].orUnavailable(),
        decodeAttemptCount = summary["decode_attempt_count"].orUnavailable(),
        decodeSuccessCount = summary["decode_success_count"].orUnavailable(),
        engineCloseReached = summary["engine_close_reached"].orUnavailable(),
        engineCloseSuccess = summary["engine_close_success"].orUnavailable(),
        holderKey = holderKey,
        holderGeneration = summary["holder_generation"].orUnavailable(),
        holderReusedCount = summary["holder_reused_count"].orUnavailable(),
        holderInvalidated = summary["holder_invalidated"].orUnavailable(),
        holderKeyMismatchDetected = summary["holder_key_mismatch_detected"].orUnavailable(),
        holderKeyMismatchReason = summary["holder_key_mismatch_reason"].orUnavailable(),
        nativeHolderEntrypointAvailable = summary["native_holder_entrypoint_available"].orUnavailable(),
        selectedNativeProbeMode = summary["selected_native_probe_mode"].orUnavailable(),
        lastNativeStage = summary["last_native_stage"].orUnavailable(),
        nativeEntrypointReached = summary["native_entrypoint_reached"].orUnavailable(),
        modelAssetsCreateReached = summary["model_assets_create_reached"].orUnavailable(),
        modelAssetsCreateReturned = summary["model_assets_create_returned"].orUnavailable(),
        engineSettingsCreateReached = summary["engine_settings_create_reached"].orUnavailable(),
        engineSettingsCreateReturned = summary["engine_settings_create_returned"].orUnavailable(),
        engineCreateReached = summary["engine_create_reached"].orUnavailable(),
        engineCreateReturned = summary["engine_create_returned"].orUnavailable(),
        sessionCreateReached = summary["session_create_reached"].orUnavailable(),
        prefillReached = summary["prefill_reached"].orUnavailable(),
        decodeReached = summary["decode_reached"].orUnavailable(),
        nativeDiagFlushCount = summary["native_diag_flush_count"].orUnavailable(),
        nativeResultFlushCount = summary["native_result_flush_count"].orUnavailable(),
        firstFailureRunIndex = summary["first_failure_run_index"]?.toIntOrNull(),
        firstFailureStage = summary["first_failure_stage"].orUnavailable(),
        firstFailureReason = summary["first_failure_reason"].orUnavailable(),
        firstFailureExceptionClass = summary["first_failure_exception_class"].orUnavailable(),
        firstFailureDiagTail = summary["first_failure_diag_tail"]
            ?.takeIf { it != "unavailable" }
            ?: result.diagText.lineSequence().lastOrNull().orUnavailable(),
        backendEvidence = summary["backend_evidence"].orUnavailable(),
        persistentCustomJniHypothesisResult = summary["persistent_custom_jni_hypothesis_result"].orUnavailable(),
        records = records,
    )
}

private data class ParsedPersistentCustomJniProbeResult(
    val summary: Map<String, String>,
    val records: List<Map<String, String>>,
)

private fun parsePersistentCustomJniKeyValueLines(text: String): ParsedPersistentCustomJniProbeResult {
    val summary = linkedMapOf<String, String>()
    val records = mutableListOf<Map<String, String>>()
    var currentRecord: MutableMap<String, String>? = null
    var inDetails = false
    text.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key == "details_begin") {
                inDetails = true
                return@forEach
            }
            if (inDetails || key == "run_index") {
                if (key == "run_index") {
                    currentRecord?.let { records += it.toMap() }
                    currentRecord = linkedMapOf()
                    inDetails = true
                }
                currentRecord?.put(key, value)
            } else {
                summary[key] = value
            }
        }
    currentRecord?.let { records += it.toMap() }
    return ParsedPersistentCustomJniProbeResult(summary = summary, records = records)
}

private fun String?.orUnavailable(): String = this?.takeIf { it.isNotBlank() } ?: "unavailable"

private fun String?.toNullableNonNegativeLong(): Long? =
    this?.toLongOrNull()?.takeIf { it >= 0L }
