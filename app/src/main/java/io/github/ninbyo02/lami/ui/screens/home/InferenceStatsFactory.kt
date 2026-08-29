package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

internal object InferenceStatsFactory {
    internal const val DETERMINISTIC_SAFE_GREETING_MODEL_LABEL = "固定応答（NPU失敗）"

    fun fromLocalTrace(
        trace: LocalInferenceTrace,
        generationTimeMs: Long,
        responseCharCount: Int,
        responseText: String? = null,
        fallbackTimeToFirstTokenMs: Long? = null,
    ): InferenceStats? {
        val resolvedStats = resolveLocalInferenceStats(trace)
        val measuredSnapshot = trace.measuredTokenSnapshot
        val existingOutputTokens = resolvedStats.outputTokens.value
        val existingTotalTokens = resolvedStats.totalTokens.value
        val existingTimeToFirstTokenMs = resolvedStats.firstTokenMs.value
        val existingGenerationDurationNs = resolvedStats.generationDurationNs.value
        val totalInferenceDurationNs = resolvedStats.evalDurationNs.value
        val wallClockLoadDurationNs = trace.wallClockLoadDurationNs?.takeIf { it >= 0L }
        val existingLoadDurationNs =
            wallClockLoadDurationNs ?: trace.loadTimeProbe.longValueOrNull()?.takeIf { it >= 0L }
        val timeToFirstTokenMs = existingTimeToFirstTokenMs ?: fallbackTimeToFirstTokenMs
        val fallbackGenerationDurationNs = generationOnlyMsOrNull(
            generationTimeMs = generationTimeMs,
            timeToFirstTokenMs = timeToFirstTokenMs,
        )?.times(1_000_000L)
        val fallbackPromptEvalNs =
            if (resolvedStats.promptEvalDurationNs.value != null) {
                resolvedStats.promptEvalDurationNs.value
            } else {
                val evalNs = totalInferenceDurationNs
                val genNs = fallbackGenerationDurationNs
                if (evalNs != null && genNs != null) {
                    (evalNs - genNs).takeIf { it > 0L }
                } else {
                    null
                }
            }
        val inputTokens = measuredSnapshot?.inputTokens ?: trace.sessionPromptTokens
        val outputTokens = measuredSnapshot?.outputTokens ?: existingOutputTokens ?: trace.sessionResponseTokens
        val totalTokens = measuredSnapshot?.totalTokens ?: existingTotalTokens ?: trace.sessionTotalTokens
        val tokensPerSecond = measuredSnapshot?.tokensPerSecond
            ?: buildLocalTokensPerSecondOrNull(
                outputTokens = outputTokens,
                generationTimeMs = generationTimeMs,
            )
        val modelName = trace.modelNameProbe.stringValueOrNull()
            ?: trace.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
        val finishReason = finishReasonOrNull(
            existingFinishReason = trace.finishReasonProbe.stringValueOrNull(),
            responseText = responseText,
        )
        val hasStats = modelName != null ||
            finishReason != null ||
            inputTokens != null ||
            outputTokens != null ||
            totalTokens != null
        if (!hasStats) return null
        return InferenceStats(
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            tokensPerSecond = tokensPerSecond,
            charsPerSecond = measuredSnapshot?.charsPerSecond,
            tokenCountMode = measuredSnapshot?.tokenCountMode,
            notes = measuredSnapshot?.notes,
            completionTokens = outputTokens,
            finishReason = finishReason,
            generationTimeMs = generationTimeMs,
            decodeDurationMs = measuredSnapshot?.decodeDurationMs,
            totalDurationMs = measuredSnapshot?.totalDurationMs,
            generationDurationNs = existingGenerationDurationNs ?: fallbackGenerationDurationNs,
            evalDurationNs = totalInferenceDurationNs,
            modelLoadDurationNs = existingLoadDurationNs,
            promptEvalDurationNs = fallbackPromptEvalNs,
            timeToFirstTokenMs = measuredSnapshot?.ttftMs ?: timeToFirstTokenMs,
            responseCharCount = responseCharCount,
        )
    }

    fun fromNpuStandardRoute(
        result: NpuStandardRouteS1Result,
        localSourceSummary: String,
        assistantText: String,
    ): InferenceStats {
        val timing = result.timing
        val rejectedAttemptOutput = result.rawOutput
            .trim()
            .ifBlank { result.sanitizedOutput.trim() }
        val estimatedOutputTokens = rejectedAttemptOutput
            .takeIf { it.isNotBlank() }
            ?.let { output -> output.codePointCount(0, output.length).coerceAtLeast(1) }
        val outputTokens = timing.outputTokens ?: estimatedOutputTokens
        val inputTokens = result.inputPrompt
            .takeIf { it.isNotBlank() }
            ?.let { prompt -> prompt.codePointCount(0, prompt.length).coerceAtLeast(1) }
        val totalTokens = when {
            inputTokens != null && outputTokens != null -> inputTokens + outputTokens
            else -> outputTokens
        }
        val modelName = result.selectedModelName
            .takeIf { it.isNotBlank() }
            ?: result.selectedModelFile.takeIf { it.isNotBlank() }
            ?: "NPU プレビュー"
        val generationTimeMs = timing.decodeMs ?: timing.totalMs
        val tokensPerSecond = timing.tokensPerSecond ?: run {
            val measuredOutputTokens = outputTokens ?: return@run null
            val measuredGenerationMs = generationTimeMs?.takeIf { it > 0L } ?: return@run null
            measuredOutputTokens.toDouble() / (measuredGenerationMs.toDouble() / 1_000.0)
        }
        val tokenCountMode = when {
            timing.outputTokens != null -> timing.tokenCountMode
            estimatedOutputTokens != null -> NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS
            else -> timing.tokenCountMode
        }
        val generationDurationNs = generationTimeMs
            ?.takeIf { it > 0L }
            ?.times(1_000_000L)
        return InferenceStats(
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            tokensPerSecond = tokensPerSecond,
            tokenCountMode = tokenCountMode,
            completionTokens = outputTokens,
            finishReason = result.reason.takeIf { it.isNotBlank() } ?: "stop",
            generationTimeMs = generationTimeMs,
            decodeDurationMs = timing.decodeMs,
            totalDurationMs = timing.totalMs,
            generationDurationNs = generationDurationNs,
            evalDurationNs = timing.totalMs?.takeIf { it > 0L }?.times(1_000_000L),
            localSourceSummary = localSourceSummary,
            timeToFirstTokenMs = timing.ttftMs,
            responseCharCount = assistantText.length,
        )
    }

    fun safeGreetingSourceSummary(
        result: NpuStandardRouteS1Result,
        existingSummary: String,
    ): String = buildString {
        existingSummary.trim().takeIf { it.isNotBlank() }?.let(::appendLine)
        appendLine("fallback=${NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING}")
        appendLine("fallback_display_source=deterministic_safe_greeting")
        appendLine("inference_metrics_source=rejected_npu_attempt")
        appendLine("npu_attempt_status=${result.status}")
        appendLine("npu_attempt_reason=${result.reason}")
        appendLine("npu_attempt_quality_candidate_status=${result.outputQualityCandidateStatus}")
        append("npu_attempt_quality_candidate_reason=${result.outputQualityCandidateReason}")
    }.trim()

    fun safeGreetingFallback(
        result: NpuStandardRouteS1Result,
        localSourceSummary: String,
        assistantText: String,
    ): InferenceStats = fromNpuStandardRoute(
        result = result,
        localSourceSummary = localSourceSummary,
        assistantText = assistantText,
    ).copy(
        modelName = DETERMINISTIC_SAFE_GREETING_MODEL_LABEL,
        model = DETERMINISTIC_SAFE_GREETING_MODEL_LABEL,
        modelLabel = DETERMINISTIC_SAFE_GREETING_MODEL_LABEL,
        finishReason = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
        notes = listOf(
            "display_source=deterministic_safe_greeting",
            "metrics_source=rejected_npu_attempt",
            "npu_attempt_status=${result.status}",
            "npu_attempt_reason=${result.reason}",
            "npu_attempt_quality_candidate_status=${result.outputQualityCandidateStatus}",
            "npu_attempt_quality_candidate_reason=${result.outputQualityCandidateReason}",
        ).joinToString("; "),
    )

    private fun generationOnlyMsOrNull(
        generationTimeMs: Long,
        timeToFirstTokenMs: Long?,
    ): Long? {
        val firstTokenMs = timeToFirstTokenMs ?: return null
        if (generationTimeMs <= 0L || firstTokenMs < 0L) return null
        return (generationTimeMs - firstTokenMs).coerceAtLeast(0L)
    }

    private fun finishReasonOrNull(
        existingFinishReason: String?,
        responseText: String?,
    ): String? {
        val normalizedExisting = existingFinishReason?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedExisting != null) return normalizedExisting
        return if (responseText.isNullOrBlank()) null else "stop"
    }
}
