package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import java.util.Locale

internal enum class StatsUiValueSource {
    MEASURED,
    SEMI_MEASURED,
    TOKENIZER_BASED,
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
    val resolvedInputTokens: Int? = null,
    val resolvedOutputTokens: Int? = null,
    val resolvedTotalTokens: Int? = null,
    val resolvedTokensPerSecond: Double? = null,
    val resolvedTokenSourceLabel: String = "未取得",
    val resolvedSpeedSourceLabel: String = "未取得",
    val resolvedLamiTtftMs: Long? = null,
    val resolvedBackendTtftMs: Long? = null,
    val resolvedLamiTokensPerSecond: Double? = null,
    val resolvedLamiPerceivedTokensPerSecond: Double? = null,
    val resolvedBackendTokensPerSecond: Double? = null,
    val resolvedPrimarySpeedValue: Double? = null,
    val resolvedPrimarySpeedSourceLabel: String = "未取得",
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
    StatsUiValueSource.SEMI_MEASURED -> "SEMI_MEASURED"
    StatsUiValueSource.TOKENIZER_BASED -> "TOKENIZER_BASED"
    StatsUiValueSource.DERIVED -> "DERIVED"
    StatsUiValueSource.ESTIMATED -> "ESTIMATED"
    StatsUiValueSource.API_CANDIDATE_ONLY -> "API_CANDIDATE_ONLY"
    StatsUiValueSource.UNAVAILABLE -> "UNAVAILABLE"
}

internal fun StatsUiValueSource.toUiStateLabel(): String = when (this) {
    StatsUiValueSource.MEASURED,
    StatsUiValueSource.SEMI_MEASURED,
    StatsUiValueSource.TOKENIZER_BASED,
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
    // canonical definition（LiteRT）:
    // 1) 入力/出力/合計トークンは tokenizer 実測を最優先
    // 2) 合計は input + output を再構成できる場合は再構成値を優先
    // 3) 生成速度は outputTokens / generationDuration を優先し、導出不能時のみ準実測・fallback へ
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

    val generationMsForTps = (
        generationTime.rawValueLong
            ?: stats.generationDurationNs
            ?: trace.evalTimeProbe.durationNsOrNull()
    )?.div(1_000_000L)
    val totalDurationMsForLami = stats.totalDurationMs?.takeIf { it > 0L } ?: stats.generationTimeMs?.takeIf { it > 0L }
    val lamiTtftMs = stats.timeToFirstTokenMs?.takeIf { it >= 0L }
    val backendTtftMs = measuredSnapshot?.ttftMs?.takeIf { it >= 0L }
        ?: stats.backendTimeToFirstTokenMs?.takeIf { it >= 0L }
    val tokenizerOutputTokensForTps = outputTokens.rawValueInt
        ?.takeIf {
            outputTokens.source == StatsUiValueSource.MEASURED ||
                outputTokens.source == StatsUiValueSource.TOKENIZER_BASED
        }
    val tokenizerTokensPerSecond = stats.decodeDurationMs
        ?.takeIf { it > 0L }
        ?.let { decodeDurationMs ->
            buildLocalTokensPerSecondOrNull(
                outputTokens = tokenizerOutputTokensForTps,
                generationTimeMs = decodeDurationMs,
            )
        }
    val useTokenizerRecount = stats.tokenCountMode == "tokenizer_recount" ||
        stats.tokenCountMode == "mediapipe_tokenizer_recount"
    val assistantUpdateCountForTps = trace.assistantUpdateCount.takeIf { it > 0 }
    val assistantUpdateBasedTokensPerSecond = if (useTokenizerRecount) {
        null
    } else {
        generationMsForTps?.let { generationTimeMs ->
            buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
                assistantUpdateCount = assistantUpdateCountForTps,
                generationTimeMs = generationTimeMs,
            )
        }
    }
    val outputTokensForTps = outputTokens.rawValueInt
    val fallbackTokensPerSecond = generationMsForTps?.let {
        buildLocalTokensPerSecondOrNull(outputTokens = outputTokensForTps, generationTimeMs = it)
    }
    val lamiGenerationDurationMs = when {
        totalDurationMsForLami != null && lamiTtftMs != null -> (totalDurationMsForLami - lamiTtftMs).coerceAtLeast(1L)
        else -> generationMsForTps
    }
    val lamiTokensPerSecond = lamiGenerationDurationMs?.let {
        buildLocalTokensPerSecondOrNull(outputTokens = outputTokensForTps, generationTimeMs = it)
    }
    val lamiPerceivedTokensPerSecond = totalDurationMsForLami?.let {
        buildLocalTokensPerSecondOrNull(outputTokens = outputTokensForTps, generationTimeMs = it)
    }
    val tokenCountIsEstimatedCodePoints =
        stats.tokenCountMode == NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS
    val backendTokensPerSecond = when {
        tokenCountIsEstimatedCodePoints -> null
        tokenizerTokensPerSecond != null -> tokenizerTokensPerSecond
        stats.tokensPerSecond != null -> stats.tokensPerSecond
        assistantUpdateBasedTokensPerSecond != null -> assistantUpdateBasedTokensPerSecond
        else -> fallbackTokensPerSecond
    }
    val estimatedCodePointTokensPerSecond = if (tokenCountIsEstimatedCodePoints) {
        stats.tokensPerSecond ?: stats.decodeDurationMs
            ?.takeIf { it > 0L }
            ?.let { decodeDurationMs ->
                buildLocalTokensPerSecondOrNull(
                    outputTokens = outputTokens.rawValueInt,
                    generationTimeMs = decodeDurationMs,
                )
            }
    } else {
        null
    }
    val tokensPerSecondValue = estimatedCodePointTokensPerSecond
        ?: lamiTokensPerSecond
        ?: lamiPerceivedTokensPerSecond
        ?: backendTokensPerSecond
    val usedAssistantUpdateBasedTps = assistantUpdateBasedTokensPerSecond != null
    val usedTokenizerBasedTps = tokenizerTokensPerSecond != null
    val tokensPerSecondSource = when {
        tokensPerSecondValue == null -> StatsUiValueSource.UNAVAILABLE
        tokenCountIsEstimatedCodePoints -> StatsUiValueSource.ESTIMATED
        lamiTokensPerSecond != null || lamiPerceivedTokensPerSecond != null -> StatsUiValueSource.MEASURED
        usedTokenizerBasedTps -> StatsUiValueSource.TOKENIZER_BASED
        usedAssistantUpdateBasedTps -> StatsUiValueSource.SEMI_MEASURED
        outputTokens.source == StatsUiValueSource.ESTIMATED -> StatsUiValueSource.ESTIMATED
        else -> StatsUiValueSource.UNAVAILABLE
    }
    val tokensPerSecond = UiStatValue(
        valueText = tokensPerSecondValue?.let { String.format(Locale.US, "%.1f token/s", it) } ?: "—",
        source = tokensPerSecondSource,
    )
    val resolvedTokenSourceLabel = when {
        tokenCountIsEstimatedCodePoints -> "推定（出力コードポイント数）"
        listOf(inputTokens, outputTokens, totalTokens).any { it.source == StatsUiValueSource.MEASURED || it.source == StatsUiValueSource.TOKENIZER_BASED } -> "Tokenizer"
        listOf(inputTokens, outputTokens, totalTokens).any { it.source == StatsUiValueSource.ESTIMATED || it.source == StatsUiValueSource.DERIVED } -> "推定"
        else -> "未取得"
    }
    val resolvedSpeedSourceLabel = when (tokensPerSecondSource) {
        StatsUiValueSource.MEASURED -> "Lami基準"
        StatsUiValueSource.TOKENIZER_BASED -> "Tokenizer"
        StatsUiValueSource.SEMI_MEASURED -> "準実測"
        StatsUiValueSource.ESTIMATED,
        StatsUiValueSource.DERIVED,
        -> "推定"
        StatsUiValueSource.API_CANDIDATE_ONLY,
        StatsUiValueSource.UNAVAILABLE,
        -> "未取得"
    }
    val backendSpeedSourceLabel = when {
        tokenCountIsEstimatedCodePoints -> "実測Decode時間 × コードポイント換算"
        usedTokenizerBasedTps -> "バックエンド基準（Decode時間）"
        stats.tokensPerSecond != null -> "バックエンド基準（Engine時間）"
        usedAssistantUpdateBasedTps -> "バックエンド基準（generation時間）"
        fallbackTokensPerSecond != null -> "バックエンド基準（fallback）"
        else -> "未取得"
    }

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
        resolvedInputTokens = inputTokens.rawValueInt,
        resolvedOutputTokens = outputTokens.rawValueInt,
        resolvedTotalTokens = totalTokens.rawValueInt,
        resolvedTokensPerSecond = tokensPerSecondValue,
        resolvedTokenSourceLabel = resolvedTokenSourceLabel,
        resolvedSpeedSourceLabel = "$resolvedSpeedSourceLabel / $backendSpeedSourceLabel",
        resolvedLamiTtftMs = lamiTtftMs,
        resolvedBackendTtftMs = backendTtftMs,
        resolvedLamiTokensPerSecond = lamiTokensPerSecond,
        resolvedLamiPerceivedTokensPerSecond = lamiPerceivedTokensPerSecond,
        resolvedBackendTokensPerSecond = backendTokensPerSecond,
        resolvedPrimarySpeedValue = tokensPerSecondValue,
        resolvedPrimarySpeedSourceLabel = when (tokensPerSecondSource) {
            StatsUiValueSource.ESTIMATED -> "推定"
            StatsUiValueSource.TOKENIZER_BASED -> "Tokenizer"
            StatsUiValueSource.SEMI_MEASURED -> "準実測"
            StatsUiValueSource.MEASURED -> "Lami基準"
            else -> "未取得"
        },
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

internal fun buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
    assistantUpdateCount: Int?,
    generationTimeMs: Long,
): Double? {
    if (assistantUpdateCount == null || assistantUpdateCount <= 0 || generationTimeMs <= 0L) return null
    val tokensPerSecond = assistantUpdateCount * 1000.0 / generationTimeMs
    return tokensPerSecond.takeIf { it.isFinite() }
}

internal fun formatProbeDurationForUi(durationNs: Long?): String {
    val safeDurationNs = durationNs ?: return "—"
    if (safeDurationNs < 0L) return "—"
    val seconds = safeDurationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}
