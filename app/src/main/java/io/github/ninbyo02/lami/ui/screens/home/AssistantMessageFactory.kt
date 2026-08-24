package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.ui.model.InferenceStats

internal fun createAssistantMessage(
    chatId: Int,
    response: String,
    latestInferenceStats: InferenceStats? = null,
    localSourceSummary: String? = null,
    imageInputCount: Int? = null,
    generationTimeMs: Long? = null,
): Message {
    val outputTokens = latestInferenceStats?.outputTokens ?: latestInferenceStats?.completionTokens
    val inputTokens = latestInferenceStats?.inputTokens
    val persistedTotalTokens = latestInferenceStats?.totalTokens
        ?: if (inputTokens != null && outputTokens != null) inputTokens + outputTokens else null
    return Message(
        message = response,
        chatId = chatId,
        isSendbyMe = false,
        completionTokens = outputTokens,
        generationTimeMs = generationTimeMs
            ?: latestInferenceStats?.generationTimeMs
            ?: latestInferenceStats?.inferenceTimeSec?.times(1000.0)?.toLong(),
        generationDurationNs = latestInferenceStats?.generationDurationNs,
        evalDurationNs = latestInferenceStats?.evalDurationNs,
        loadDurationNs = latestInferenceStats?.modelLoadDurationNs,
        promptEvalDurationNs = latestInferenceStats?.promptEvalDurationNs,
        modelName = latestInferenceStats?.modelName ?: latestInferenceStats?.model,
        inputTokens = inputTokens,
        totalTokens = persistedTotalTokens,
        tokensPerSecond = latestInferenceStats?.tokensPerSecond,
        charsPerSecond = latestInferenceStats?.charsPerSecond,
        tokenCountMode = latestInferenceStats?.tokenCountMode,
        inferenceNotes = latestInferenceStats?.notes,
        inferenceTimeSec = latestInferenceStats?.inferenceTimeSec,
        decodeDurationMs = latestInferenceStats?.decodeDurationMs,
        totalDurationMs = latestInferenceStats?.totalDurationMs,
        finishReason = latestInferenceStats?.finishReason,
        localSourceSummary = localSourceSummary,
        timeToFirstTokenMs = latestInferenceStats?.timeToFirstTokenMs,
        imageInputCount = imageInputCount ?: latestInferenceStats?.imageInputCount,
    )
}
