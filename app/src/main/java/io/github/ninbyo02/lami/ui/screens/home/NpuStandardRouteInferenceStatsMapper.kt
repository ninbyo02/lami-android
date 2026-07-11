package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

/** Maps the completed standard NPU route onto the shared GPU/CPU stats contract. */
internal fun NpuStandardRouteS1Result.toSharedInferenceStats(
    finalAssistantText: String,
): InferenceStats {
    val normalizedText = finalAssistantText.trim()
    val outputTokens = timing.outputTokens
    val totalDurationMs = timing.totalMs
    return InferenceStats(
        modelName = selectedModelName.ifBlank {
            selectedModelFile.substringAfterLast('/').ifBlank { null }
        },
        outputTokens = outputTokens,
        totalTokens = outputTokens,
        tokensPerSecond = timing.tokensPerSecond,
        tokenCountMode = timing.tokenCountMode.takeUnless {
            it == NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE
        },
        notes = buildString {
            append("backend=NPU")
            if (npuBackendEvidence.isNotBlank()) {
                append("; evidence=")
                append(npuBackendEvidence)
            }
        },
        inferenceTimeSec = totalDurationMs?.div(1000.0),
        generationTimeMs = timing.decodeMs,
        decodeDurationMs = timing.decodeMs,
        totalDurationMs = totalDurationMs,
        finishReason = reason,
        localSourceSummary = displayText,
        timeToFirstTokenMs = timing.ttftMs,
        completionTokens = outputTokens,
        responseCharCount = normalizedText.length,
    )
}
