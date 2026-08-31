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

internal class NpuPersistentHolderFiveTurnDevProbe(
    context: Context,
) : NpuPersistentHolderFiveTurnProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuPersistentHolderFiveTurnProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path ?: modelResolution.reasonCode
        val api = NativeStubNpuPersistentHolderApi
        val turns = mutableListOf<NpuPersistentHolderTwoTurnRecord>()
        var createResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null
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
                for ((turnIndex, prompt) in prompts.withIndex()) {
                    val turn = runTurn(
                        api = api,
                        holderId = holderId,
                        turnIndex = turnIndex + 1,
                        prompt = prompt,
                    )
                    turns += turn
                    if (turn.decodeResult?.status != "success") break
                }
            }
            closeResult = api.closeHolder(
                NpuPersistentHolderCloseRequest(
                    holderId = holderId,
                    reason = "dev_ui_five_turn_probe_close",
                ),
            )
            diagnosticsAfterClose = api.getDiagnostics(holderId)
            NpuPersistentHolderFiveTurnProbeState(
                status = "completed",
                reason = resolveReason(turns, createResult, closeResult),
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                turns = turns.toList(),
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
            )
        } catch (throwable: Throwable) {
            if (closeResult == null && holderId != "unavailable") {
                closeResult = runCatching {
                    api.closeHolder(
                        NpuPersistentHolderCloseRequest(
                            holderId = holderId,
                            reason = "dev_ui_five_turn_probe_close_after_throwable",
                        ),
                    )
                }.getOrNull()
                diagnosticsAfterClose = runCatching { api.getDiagnostics(holderId) }.getOrNull()
            }
            NpuPersistentHolderFiveTurnProbeState(
                status = "failed",
                reason = throwable.message ?: "holder_five_turn_probe_failed",
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                turns = turns.toList(),
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
                throwableClass = throwable.javaClass.name,
                throwableMessage = throwable.message ?: "unavailable",
            )
        }
    }

    private suspend fun runTurn(
        api: NpuPersistentHolderApi,
        holderId: String,
        turnIndex: Int,
        prompt: String,
    ): NpuPersistentHolderTwoTurnRecord {
        val runResult = api.runOnce(
            NpuPersistentHolderRunRequest(
                holderId = holderId,
                prompt = prompt,
                maxOutputTokens = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
            ),
        )
        val decodeResult = if (runResult.diagnostics.runOnceSupported) {
            runExistingOneShotDecode(prompt)
        } else {
            null
        }
        return NpuPersistentHolderTwoTurnRecord(
            turnIndex = turnIndex,
            prompt = prompt,
            runResult = runResult,
            decodeResult = decodeResult,
        )
    }

    private suspend fun runExistingOneShotDecode(prompt: String): NpuPersistentHolderRunOnceDecodeResult =
        withContext(Dispatchers.IO) {
            val display = DevOnlyNpuOneTurnConversationEntry(appContext).run(
                DevOnlyNpuOneTurnConversationRequest(
                    userPrompt = prompt,
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

    private fun resolveReason(
        turns: List<NpuPersistentHolderTwoTurnRecord>,
        createResult: NpuPersistentHolderApiResult?,
        closeResult: NpuPersistentHolderApiResult?,
    ): String {
        val firstFailure = turns.firstOrNull { it.decodeResult?.status != "success" }
        return firstFailure?.decodeResult?.reason
            ?: closeResult?.reason
            ?: createResult?.reason
            ?: "unavailable"
    }

    private fun Map<String, String>.valueOrUnavailable(key: String): String =
        this[key].orEmpty().ifBlank { "unavailable" }

    private companion object {
        val prompts = listOf(
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_1,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_2,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_3,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_4,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_5,
        )
    }
}
