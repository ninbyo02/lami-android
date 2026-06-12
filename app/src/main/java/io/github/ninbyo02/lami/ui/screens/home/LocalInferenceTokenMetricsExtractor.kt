package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import kotlin.math.ceil

private const val ASSISTANT_UPDATE_COUNT_TOKEN_FACTOR = 0.65

internal enum class LocalInferenceTokenMetricSource {
    MEASURED,
    DERIVED,
    ESTIMATED,
    API_CANDIDATE_ONLY,
    UNAVAILABLE,
}

internal data class LocalInferenceTokenMetric(
    val value: Int?,
    val source: LocalInferenceTokenMetricSource,
)

internal data class LocalInferenceTokenMetrics(
    val inputTokens: LocalInferenceTokenMetric,
    val outputTokens: LocalInferenceTokenMetric,
    val totalTokens: LocalInferenceTokenMetric,
)

internal data class LocalInferenceMeasuredTokenSnapshot(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val tokenizerRecountStatus: String? = null,
    val tokenizerSourceTraceSummary: String? = null,
    val mediaPipeTokenizerStatus: String? = null,
    val mediaPipeTokenizerSummary: String? = null,
    val mediaPipeInputTokens: Int? = null,
    val mediaPipeOutputTokens: Int? = null,
    val mediaPipeTotalTokens: Int? = null,
    val tokenCountMode: String? = null,
    val notes: String? = null,
    val tokensPerSecond: Double? = null,
    val charsPerSecond: Double? = null,
    val ttftMs: Long? = null,
    val decodeDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    val lastPrefillTokenCount: Int? = null,
    val lastDecodeTokenCount: Int? = null,
    val rawPrefillTokenCount: String? = null,
    val rawDecodeTokenCount: String? = null,
    val rawPrefillTokensPerSecond: String? = null,
    val rawDecodeTokensPerSecond: String? = null,
    val rawTimeToFirstTokenMs: String? = null,
    val rawModelInitMs: String? = null,
    val tokenizerCountStartedAtElapsedMs: Long? = null,
    val tokenizerCountFinishedAtElapsedMs: Long? = null,
    val tokenizerCountDurationMs: Long? = null,
)

internal fun extractLocalInferenceTokenMetrics(
    trace: LocalInferenceTrace,
    resolved: LocalInferenceResolvedStats,
    stats: InferenceStats,
    measuredSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    assistantText: String? = null,
    promptText: String? = null,
): LocalInferenceTokenMetrics {
    // canonical priority（LiteRT token count）:
    // 1. measuredSnapshot(MediaPipe sizeInTokens を含む tokenizer 実測)
    // 2. 既存の resolved/probe/session 値
    // 3. 推定値
    //
    // canonical priority（Ollama token count / legacy fields）:
    // 1. explicit persisted fields(inputTokens/outputTokens/completionTokens/totalTokens)
    // 2. legacy probe/session fallback
    // 3. null
    val usesOfficialApi = trace.officialFlowUsed || trace.officialConversationApiAvailable == true
    val hasEstimatedTokenProbe = trace.estimatedTokenProbe.availability != LocalStatsAvailability.NOT_FOUND

    val inputTokens = measuredSnapshot?.inputTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: stats.inputTokens
            ?.takeIf { it >= 0 }
            ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.DERIVED) }
        ?: trace.sessionPromptTokens
            ?.takeIf { it >= 0 }
            ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.DERIVED) }
        ?: estimateInputTokensFromPromptText(promptText)?.let {
            LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.ESTIMATED)
        }
        ?: LocalInferenceTokenMetric(
            value = null,
            source = resolveMissingTokenMetricSource(usesOfficialApi = usesOfficialApi),
        )

    val estimatedOutputTokens = estimateOutputTokensFromAssistantText(assistantText)
    val estimatedOutputTokensFromAssistantUpdateCount =
        estimateOutputTokensFromAssistantUpdateCount(trace.assistantUpdateCount)
    val outputTokens = measuredSnapshot?.outputTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: resolved.outputTokens.value?.let {
            LocalInferenceTokenMetric(
                value = it,
                source = resolved.outputTokens.source.toTokenMetricSource(),
            )
        }
        ?: trace.sessionResponseTokens
            ?.takeIf { it >= 0 }
            ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.DERIVED) }
        ?: estimatedOutputTokensFromAssistantUpdateCount?.let {
            LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.ESTIMATED)
        }
        ?: estimatedOutputTokens?.let {
            LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.ESTIMATED)
        }
        ?: LocalInferenceTokenMetric(
            value = null,
            source = resolveMissingTokenMetricSource(usesOfficialApi = usesOfficialApi),
        )

    val totalTokens = measuredSnapshot?.totalTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: if (inputTokens.value != null && outputTokens.value != null) {
            LocalInferenceTokenMetric(
                value = inputTokens.value + outputTokens.value,
                source = if (
                    inputTokens.source == LocalInferenceTokenMetricSource.MEASURED &&
                    outputTokens.source == LocalInferenceTokenMetricSource.MEASURED
                ) {
                    LocalInferenceTokenMetricSource.MEASURED
                } else {
                    LocalInferenceTokenMetricSource.ESTIMATED
                },
            )
        } else if (resolved.totalTokens.value != null) {
            LocalInferenceTokenMetric(
                value = resolved.totalTokens.value,
                source = if (hasEstimatedTokenProbe) {
                    LocalInferenceTokenMetricSource.ESTIMATED
                } else {
                    resolved.totalTokens.source.toTokenMetricSource()
                },
            )
        } else {
            LocalInferenceTokenMetric(
                value = null,
                source = resolveMissingTokenMetricSource(usesOfficialApi = usesOfficialApi),
            )
        }

    return LocalInferenceTokenMetrics(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
    )
}

private fun estimateOutputTokensFromAssistantText(assistantText: String?): Int? {
    if (assistantText.isNullOrBlank()) return null
    val nonBlankCharCount = assistantText.count { !it.isWhitespace() }
    if (nonBlankCharCount <= 0) return null
    return ceil(nonBlankCharCount / 2.8).toInt().coerceAtLeast(1)
}

private fun estimateOutputTokensFromAssistantUpdateCount(updateCount: Int?): Int? {
    if (updateCount == null || updateCount <= 0) return null
    return ceil(updateCount * ASSISTANT_UPDATE_COUNT_TOKEN_FACTOR).toInt().coerceAtLeast(1)
}

private fun estimateInputTokensFromPromptText(promptText: String?): Int? {
    if (promptText.isNullOrBlank()) return null
    val nonBlankCharCount = promptText.count { !it.isWhitespace() }
    if (nonBlankCharCount <= 0) return null
    return ceil(nonBlankCharCount / 2.8).toInt().coerceAtLeast(1)
}

private fun StatsValueSource.toTokenMetricSource(): LocalInferenceTokenMetricSource = when (this) {
    StatsValueSource.MEASURED -> LocalInferenceTokenMetricSource.MEASURED
    StatsValueSource.DERIVED -> LocalInferenceTokenMetricSource.DERIVED
    StatsValueSource.API_CANDIDATE_ONLY -> LocalInferenceTokenMetricSource.API_CANDIDATE_ONLY
    StatsValueSource.UNAVAILABLE -> LocalInferenceTokenMetricSource.UNAVAILABLE
}

private fun resolveMissingTokenMetricSource(usesOfficialApi: Boolean): LocalInferenceTokenMetricSource {
    return if (usesOfficialApi) {
        LocalInferenceTokenMetricSource.API_CANDIDATE_ONLY
    } else {
        LocalInferenceTokenMetricSource.UNAVAILABLE
    }
}
