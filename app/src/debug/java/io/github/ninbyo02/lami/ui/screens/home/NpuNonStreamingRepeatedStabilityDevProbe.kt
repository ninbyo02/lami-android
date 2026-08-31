package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.Qairt244NativeResultParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuNonStreamingRepeatedStabilityDevProbe(
    context: Context,
) : NpuNonStreamingRepeatedStabilityProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(
        onUpdate: (NpuNonStreamingRepeatedStabilityState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuNonStreamingRepeatedStabilityState = withContext(Dispatchers.Default) {
        val startedAtMs = System.currentTimeMillis()
        val records = mutableListOf<NpuNonStreamingRepeatedStabilityRecord>()
        var state = NpuNonStreamingRepeatedStabilityState(
            status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_RUNNING,
            reason = "running",
            startedAtMs = startedAtMs,
            runCountRequested = NPU_NON_STREAMING_REPEATED_STABILITY_PROMPTS.size,
            selectedBackend = NPU_S1_BACKEND_NPU,
            requestedBackend = NPU_S1_BACKEND_NPU,
            effectiveBackend = NPU_S1_BACKEND_UNAVAILABLE,
            backendEvidence = NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE,
            routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S5,
        )
        onUpdate(state)
        try {
            for ((promptIndex, prompt) in NPU_NON_STREAMING_REPEATED_STABILITY_PROMPTS.withIndex()) {
                if (isCancelled()) {
                    return@withContext state.copy(
                        status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_CANCELLED,
                        reason = "cancelled",
                        finishedAtMs = System.currentTimeMillis(),
                        records = records.toList(),
                        stopped = true,
                        stopReason = "cancelled",
                    )
                }
                val record = runOne(promptIndex + 1, prompt)
                records += record
                state = state.copy(
                    status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_RUNNING,
                    reason = record.reason,
                    effectiveBackend = if (record.backendEvidence.isNotBlank() && record.backendEvidence != "-") {
                        NPU_S1_BACKEND_NPU
                    } else {
                        state.effectiveBackend
                    },
                    backendEvidence = record.backendEvidence.takeIf { it.isNotBlank() && it != "-" }
                        ?: state.backendEvidence,
                    records = records.toList(),
                )
                onUpdate(state)
                val stopReason = nonStreamingRepeatStopReason(record)
                if (stopReason != null) {
                    return@withContext state.copy(
                        status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_STOPPED,
                        reason = record.reason,
                        finishedAtMs = System.currentTimeMillis(),
                        records = records.toList(),
                        stopped = true,
                        stopReason = stopReason,
                    )
                }
            }
            state.copy(
                status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_COMPLETED,
                reason = records.lastOrNull()?.reason ?: "completed",
                finishedAtMs = System.currentTimeMillis(),
                records = records.toList(),
            )
        } catch (throwable: Throwable) {
            state.copy(
                status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_FAILED,
                reason = throwable.message ?: "non_streaming_repeat_failed",
                finishedAtMs = System.currentTimeMillis(),
                records = records.toList(),
                stopped = true,
                stopReason = "throwable",
                throwableClass = throwable.javaClass.name,
                throwableMessage = throwable.message ?: "unavailable",
            )
        }
    }

    private suspend fun runOne(
        runIndex: Int,
        prompt: String,
    ): NpuNonStreamingRepeatedStabilityRecord = withContext(Dispatchers.IO) {
        val startedAtElapsed = SystemClock.elapsedRealtime()
        val display = DevOnlyNpuOneTurnConversationEntry(appContext).run(
            DevOnlyNpuOneTurnConversationRequest(
                userPrompt = prompt,
                unsafeDevBypassPromptLengthGate = true,
                maxOutputTokens = NPU_NON_STREAMING_REPEATED_STABILITY_MAX_OUTPUT_TOKENS,
                promptTailVariant = DevOnlyNpuOneTurnConversationContract.DEFAULT_PROMPT_TAIL_VARIANT,
                timeoutMs = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
            ),
        )
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtElapsed
        val values = readNativeValues()
        NpuNonStreamingRepeatedStabilityRecord(
            runIndex = runIndex,
            prompt = prompt,
            status = display.status,
            reason = display.reason,
            runDecodeReached = display.decodeReached,
            backendEvidence = display.npuEvidence.ifBlank { values.valueOrUnavailable("npu_backend_evidence") },
            qualityClassification = display.quality.ifBlank { values.valueOrUnavailable("quality_classification") },
            fallbackUsed = display.fallback,
            timeout = display.timeout,
            freshCrash = display.freshCrash,
            totalMs = values["elapsed_ms"]?.toLongOrNull() ?: elapsedMs,
            decodeMs = values["decode_elapsed_ms"]?.toLongOrNull(),
            rawOutput = display.rawOutput.ifBlank { values.valueOrUnavailable("raw_output") },
            sanitizedOutput = display.output.ifBlank { values.valueOrUnavailable("sanitized_output") },
            nativeStage = display.nativeDiagnostics.nativeStage,
            nativeStageHistory = display.nativeDiagnostics.nativeStageHistory,
            nativeErrorStage = display.nativeDiagnostics.nativeErrorStage,
            nativeErrorClass = display.nativeDiagnostics.nativeErrorClass,
            nativeDiagTail = display.nativeDiagnostics.nativeDiagTail,
        )
    }

    private fun readNativeValues(): Map<String, String> {
        val resultFile = appContext.filesDir.resolve("qairt244_short_multitoken_smoke_result.txt")
        return if (resultFile.isFile) {
            Qairt244NativeResultParser.parse(resultFile.readText()).values
        } else {
            emptyMap()
        }
    }

    private fun nonStreamingRepeatStopReason(record: NpuNonStreamingRepeatedStabilityRecord): String? =
        when {
            record.fallbackUsed -> "fallback_detected"
            record.timeout -> "timeout"
            record.freshCrash -> "fresh_crash_detected"
            !record.runDecodeReached -> "run_decode_reached_false"
            record.status != "success" -> record.reason
            else -> null
        }

    private fun Map<String, String>.valueOrUnavailable(key: String): String =
        this[key].orEmpty().ifBlank { "unavailable" }
}
