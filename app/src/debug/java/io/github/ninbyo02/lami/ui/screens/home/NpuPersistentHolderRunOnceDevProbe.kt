package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import io.github.ninbyo02.lami.npu.Qairt244NativeResultParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuPersistentHolderRunOnceDevProbe(
    context: Context,
) : NpuPersistentHolderRunOnceProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuPersistentHolderRunOnceProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path ?: modelResolution.reasonCode
        val api = NativeStubNpuPersistentHolderApi
        var createResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null
        var runResult: NpuPersistentHolderApiResult? = null
        var decodeResult: NpuPersistentHolderRunOnceDecodeResult? = null
        var closeResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null
        var holderId = "unavailable"
        try {
            createResult = api.createHolder(
                NpuPersistentHolderCreateRequest(
                    modelPath = modelPath,
                    nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                    cacheDir = appContext.cacheDir.absolutePath,
                    maxTokens = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
                ),
            )
            holderId = createResult.holderId.ifBlank { "unavailable" }
            diagnosticsAfterCreate = api.getDiagnostics(holderId)
            if (createResult.diagnostics.holderCreateSucceeded && diagnosticsAfterCreate.holderOpen) {
                val gateResult = api.runOnce(
                    NpuPersistentHolderRunRequest(
                        holderId = holderId,
                        prompt = NPU_PERSISTENT_HOLDER_RUN_ONCE_PROMPT,
                        maxOutputTokens = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
                    ),
                )
                runResult = gateResult
                if (gateResult.diagnostics.runOnceSupported) {
                    decodeResult = runExistingOneShotDecode()
                }
            }
            closeResult = api.closeHolder(
                NpuPersistentHolderCloseRequest(
                    holderId = holderId,
                    reason = "dev_ui_run_once_probe_close",
                ),
            )
            diagnosticsAfterClose = api.getDiagnostics(holderId)
            val reason = decodeResult?.reason
                ?: runResult?.reason
                ?: createResult.reason
            NpuPersistentHolderRunOnceProbeState(
                status = "completed",
                reason = reason,
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                runResult = runResult,
                decodeResult = decodeResult,
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
            )
        } catch (throwable: Throwable) {
            if (closeResult == null && holderId != "unavailable") {
                closeResult = runCatching {
                    api.closeHolder(
                        NpuPersistentHolderCloseRequest(
                            holderId = holderId,
                            reason = "dev_ui_run_once_probe_close_after_throwable",
                        ),
                    )
                }.getOrNull()
                diagnosticsAfterClose = runCatching { api.getDiagnostics(holderId) }.getOrNull()
            }
            NpuPersistentHolderRunOnceProbeState(
                status = "failed",
                reason = throwable.message ?: "holder_run_once_probe_failed",
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                runResult = runResult,
                decodeResult = decodeResult,
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
                throwableClass = throwable.javaClass.name,
                throwableMessage = throwable.message ?: "unavailable",
            )
        }
    }

    private suspend fun runExistingOneShotDecode(): NpuPersistentHolderRunOnceDecodeResult =
        withContext(Dispatchers.IO) {
            val display = DevOnlyNpuOneTurnConversationEntry(appContext).run(
                DevOnlyNpuOneTurnConversationRequest(
                    userPrompt = NPU_PERSISTENT_HOLDER_RUN_ONCE_PROMPT,
                    unsafeDevBypassPromptLengthGate = true,
                    maxOutputTokens = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
                    promptTailVariant = DevOnlyNpuOneTurnConversationContract.DEFAULT_PROMPT_TAIL_VARIANT,
                    timeoutMs = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
                ),
            )
            val resultFile = appContext.filesDir.resolve("qairt244_short_multitoken_smoke_result.txt")
            val values = if (resultFile.isFile) {
                Qairt244NativeResultParser.parse(resultFile.readText()).values
            } else {
                emptyMap()
            }
            NpuPersistentHolderRunOnceDecodeResult(
                status = display.status,
                reason = display.reason,
                runDecodeReached = display.decodeReached.toString(),
                rawOutput = display.rawOutput.ifBlank { values.valueOrUnavailable("raw_output") },
                sanitizedOutput = display.output.ifBlank { values.valueOrUnavailable("sanitized_output") },
                qualityClassification = display.quality.ifBlank { values.valueOrUnavailable("quality_classification") },
                backendEvidence = display.npuEvidence.ifBlank { values.valueOrUnavailable("npu_backend_evidence") },
                fallbackUsed = display.fallback.toString(),
                timeout = display.timeout.toString(),
                freshCrash = display.freshCrash.toString(),
                totalMs = values.valueOrUnavailable("elapsed_ms"),
                decodeMs = values.valueOrUnavailable("decode_elapsed_ms"),
                outputTokens = display.outputTokenCount.ifBlank { values.valueOrUnavailable("output_token_count") },
                tokensPerSecond = values.valueOrUnavailable("tokens_per_second"),
                finishReason = display.finishReason.ifBlank { values.valueOrUnavailable("finish_reason") },
                stopReason = display.stopReason.ifBlank { values.valueOrUnavailable("stop_reason") },
                eosDetected = display.eosDetected.ifBlank { values.valueOrUnavailable("eos_detected") },
                fullText = display.text,
            )
        }

    private fun Map<String, String>.valueOrUnavailable(key: String): String =
        this[key].orEmpty().ifBlank { "unavailable" }
}
