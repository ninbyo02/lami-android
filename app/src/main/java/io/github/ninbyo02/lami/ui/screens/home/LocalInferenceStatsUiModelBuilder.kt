package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import java.util.Locale

internal enum class StatsUiValueSource {
    MEASURED,
    DERIVED,
    ESTIMATED,
    API_CANDIDATE_ONLY,
    UNAVAILABLE,
}

internal data class UiStatValue(
    val valueText: String,
    val source: StatsUiValueSource,
    val rawValueLong: Long? = null,
    val rawValueInt: Int? = null,
)

internal data class UiTokenStats(
    val inputTokens: UiStatValue,
    val outputTokens: UiStatValue,
    val totalTokens: UiStatValue,
)

internal data class LocalInferenceStatsUiModel(
    val firstToken: UiStatValue,
    val promptEvalTime: UiStatValue,
    val generationTime: UiStatValue,
    val totalTime: UiStatValue,
    val tokens: UiTokenStats,
    val tokensPerSecond: UiStatValue,
    val modelLoadTime: UiStatValue,
    val imageInput: UiStatValue,
    val finishReasonText: String,
    val sourceLabel: String,
)

internal fun StatsValueSource.toUiSource(): StatsUiValueSource = when (this) {
    StatsValueSource.MEASURED -> StatsUiValueSource.MEASURED
    StatsValueSource.DERIVED -> StatsUiValueSource.DERIVED
    StatsValueSource.API_CANDIDATE_ONLY -> StatsUiValueSource.API_CANDIDATE_ONLY
    StatsValueSource.UNAVAILABLE -> StatsUiValueSource.UNAVAILABLE
}

internal fun LocalInferenceTokenMetricSource.toUiSource(): StatsUiValueSource = when (this) {
    LocalInferenceTokenMetricSource.MEASURED -> StatsUiValueSource.MEASURED
    LocalInferenceTokenMetricSource.DERIVED -> StatsUiValueSource.DERIVED
    LocalInferenceTokenMetricSource.ESTIMATED -> StatsUiValueSource.ESTIMATED
    LocalInferenceTokenMetricSource.API_CANDIDATE_ONLY -> StatsUiValueSource.API_CANDIDATE_ONLY
    LocalInferenceTokenMetricSource.UNAVAILABLE -> StatsUiValueSource.UNAVAILABLE
}

internal fun StatsUiValueSource.toDevLabel(): String = when (this) {
    StatsUiValueSource.MEASURED -> "MEASURED"
    StatsUiValueSource.DERIVED -> "DERIVED"
    StatsUiValueSource.ESTIMATED -> "ESTIMATED"
    StatsUiValueSource.API_CANDIDATE_ONLY -> "API_CANDIDATE_ONLY"
    StatsUiValueSource.UNAVAILABLE -> "UNAVAILABLE"
}

internal fun StatsUiValueSource.toUiStateLabel(): String = when (this) {
    StatsUiValueSource.MEASURED,
    StatsUiValueSource.DERIVED,
    -> "取得済み"
    StatsUiValueSource.ESTIMATED -> "推定"
    StatsUiValueSource.API_CANDIDATE_ONLY -> "候補のみ"
    StatsUiValueSource.UNAVAILABLE -> "未取得"
}

internal fun buildLocalInferenceStatsUiModel(
    resolved: LocalInferenceResolvedStats,
    stats: InferenceStats,
    trace: LocalInferenceTrace,
    measuredSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    assistantText: String? = null,
    promptText: String? = null,
    selectedAssistantResponseSource: String?,
): LocalInferenceStatsUiModel {
    fun buildUiStatValueFromResolvedLong(valueNs: Long?, source: StatsUiValueSource): UiStatValue = UiStatValue(
        valueText = formatProbeDurationForUi(valueNs),
        source = source,
        rawValueLong = valueNs,
    )
    fun buildUiStatValueFromResolvedInt(value: Int?, source: StatsUiValueSource): UiStatValue = UiStatValue(
        valueText = value?.toString() ?: "—",
        source = source,
        rawValueInt = value,
    )

    val tokenMetrics = extractLocalInferenceTokenMetrics(
        trace = trace,
        resolved = resolved,
        stats = stats,
        measuredSnapshot = measuredSnapshot,
        assistantText = assistantText,
        promptText = promptText,
    )
    val inputTokens = buildUiStatValueFromResolvedInt(
        value = tokenMetrics.inputTokens.value,
        source = tokenMetrics.inputTokens.source.toUiSource(),
    )
    val outputTokens = buildUiStatValueFromResolvedInt(
        value = tokenMetrics.outputTokens.value,
        source = tokenMetrics.outputTokens.source.toUiSource(),
    )
    val totalTokens = buildUiStatValueFromResolvedInt(
        value = tokenMetrics.totalTokens.value,
        source = tokenMetrics.totalTokens.source.toUiSource(),
    )

    val generationTime = buildUiStatValueFromResolvedLong(
        valueNs = resolved.generationDurationNs.value ?: stats.generationDurationNs,
        source = resolved.generationDurationNs.source.toUiSource(),
    )

    val outputTokensForTps = outputTokens.rawValueInt
    val generationMsForTps = (
        generationTime.rawValueLong
            ?: stats.generationDurationNs
            ?: trace.evalTimeProbe.durationNsOrNull()
    )?.div(1_000_000L)
    val tokensPerSecondValue = generationMsForTps?.let {
        buildLocalTokensPerSecondOrNull(outputTokens = outputTokensForTps, generationTimeMs = it)
    }
    val tokensPerSecondSource = when {
        tokensPerSecondValue == null -> StatsUiValueSource.UNAVAILABLE
        outputTokens.source == StatsUiValueSource.MEASURED -> StatsUiValueSource.DERIVED
        outputTokens.source == StatsUiValueSource.DERIVED -> StatsUiValueSource.DERIVED
        outputTokens.source == StatsUiValueSource.ESTIMATED -> StatsUiValueSource.ESTIMATED
        else -> StatsUiValueSource.UNAVAILABLE
    }
    val tokensPerSecond = UiStatValue(
        valueText = tokensPerSecondValue?.let { String.format(Locale.US, "%.1f token/s", it) } ?: "—",
        source = tokensPerSecondSource,
    )

    return LocalInferenceStatsUiModel(
        firstToken = UiStatValue(
            valueText = resolved.firstTokenMs.value?.let { "${it} ms" } ?: "—",
            source = resolved.firstTokenMs.source.toUiSource(),
            rawValueLong = resolved.firstTokenMs.value,
        ),
        promptEvalTime = buildUiStatValueFromResolvedLong(
            valueNs = resolved.promptEvalDurationNs.value ?: stats.promptEvalDurationNs,
            source = resolved.promptEvalDurationNs.source.toUiSource(),
        ),
        generationTime = generationTime,
        totalTime = buildUiStatValueFromResolvedLong(
            valueNs = resolved.evalDurationNs.value ?: stats.evalDurationNs,
            source = resolved.evalDurationNs.source.toUiSource(),
        ),
        tokens = UiTokenStats(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
        ),
        tokensPerSecond = tokensPerSecond,
        modelLoadTime = buildUiStatValueFromResolvedLong(
            valueNs = stats.modelLoadDurationNs,
            source = if (stats.modelLoadDurationNs != null) StatsUiValueSource.MEASURED else StatsUiValueSource.UNAVAILABLE,
        ),
        imageInput = UiStatValue(
            valueText = formatImageInputCount(stats) ?: "—",
            source = if (formatImageInputCount(stats) != null) StatsUiValueSource.DERIVED else StatsUiValueSource.UNAVAILABLE,
        ),
        finishReasonText = formatFinishReason(stats) ?: "—",
        sourceLabel = selectedAssistantResponseSource ?: "—",
    )
}

internal fun buildLocalTokensPerSecondOrNull(
    outputTokens: Int?,
    generationTimeMs: Long,
): Double? {
    if (outputTokens == null || outputTokens < 0 || generationTimeMs <= 0L) return null
    val tokensPerSecond = outputTokens * 1000.0 / generationTimeMs
    return tokensPerSecond.takeIf { it.isFinite() }
}

internal fun formatProbeDurationForUi(durationNs: Long?): String {
    val safeDurationNs = durationNs ?: return "—"
    if (safeDurationNs < 0L) return "—"
    val seconds = safeDurationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}
