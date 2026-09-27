package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LocalTokenRecountRequest(
    val modelPath: String?,
    val prompt: String,
    val response: String,
    val trace: LocalInferenceTrace,
    val allowStandaloneGpu: Boolean = false,
)

/** Uses the caller's UI scope; never owns a generation engine or an independent job. */
internal class PostResponseTokenStatsUpdater(
    private val coroutineScope: CoroutineScope,
    private val postTerminalAssistantMetadataUpdater: PostTerminalAssistantMetadataUpdater,
    private val recount: suspend (LocalTokenRecountRequest) -> LocalInferenceTrace,
    private val metadataDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onStatsUpdated: (Int, InferenceStats) -> Unit,
) {
    fun scheduleLocalTokenizerStatsUpdate(
        assistantId: Int,
        chatId: Int,
        response: String,
        prompt: String,
        trace: LocalInferenceTrace,
        modelPath: String?,
        generationTimeMs: Long,
        sourceSummary: String,
    ) {
        if (assistantId <= 0) return
        val measured = trace.measuredTokenSnapshot
        if (measured?.inputTokens != null && measured.outputTokens != null) return
        coroutineScope.launch {
            val recounted = recount(
                LocalTokenRecountRequest(
                    modelPath = modelPath,
                    prompt = prompt,
                    response = response,
                    trace = trace,
                    allowStandaloneGpu = trace.requestedPreferredBackend == "GPU" && trace.appliedPreferredBackend == "GPU",
                ),
            )
            val snapshot = recounted.measuredTokenSnapshot ?: return@launch
            val stats = InferenceStatsFactory.fromLocalTrace(
                trace = recounted,
                generationTimeMs = generationTimeMs,
                responseCharCount = response.length,
                responseText = response,
                fallbackTimeToFirstTokenMs = generationTimeMs,
            ) ?: return@launch
            val summary = mergePostTerminalTokenCountDiagnostics(sourceSummary, snapshot)
            val finalStats = stats.copy(localSourceSummary = summary)
            val update = withContext(metadataDispatcher) {
                postTerminalAssistantMetadataUpdater.update(
                    messageId = assistantId,
                    expectedChatId = chatId,
                    expectedMessage = response,
                    patch = PostTerminalAssistantMetadataPatch.fromInferenceStats(
                        stats = finalStats,
                        localSourceSummary = summary,
                    ),
                )
            }
            if (update.accepted) onStatsUpdated(assistantId, finalStats)
        }
    }

    fun scheduleNpuFallbackTokenizerStatsUpdate(
        assistantId: Int,
        chatId: Int,
        persistedResponse: String,
        prompt: String,
        response: String,
        successfulBackend: String?,
        trace: LocalInferenceTrace,
        modelPath: String?,
    ) {
        if (assistantId <= 0 || successfulBackend !in setOf("GPU", "CPU")) return
        coroutineScope.launch {
            val recountedTrace = recount(
                LocalTokenRecountRequest(
                    modelPath = modelPath,
                    prompt = prompt,
                    response = response,
                    trace = trace,
                ),
            )
            val updatedPersistence = buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = successfulBackend,
                response = response,
                trace = recountedTrace,
            ) ?: return@launch
            val updatedStats = updatedPersistence.inferenceStats
            if (updatedStats.inputTokens == null &&
                updatedStats.outputTokens == null &&
                updatedStats.totalTokens == null
            ) return@launch
            val metadataUpdate = withContext(metadataDispatcher) {
                postTerminalAssistantMetadataUpdater.update(
                    messageId = assistantId,
                    expectedChatId = chatId,
                    expectedMessage = persistedResponse,
                    patch = PostTerminalAssistantMetadataPatch.fromInferenceStats(
                        stats = updatedStats,
                        localSourceSummary = updatedPersistence.localSourceSummary,
                    ),
                )
            }
            if (metadataUpdate.accepted) {
                onStatsUpdated(assistantId, updatedStats)
            }
        }
    }
}
