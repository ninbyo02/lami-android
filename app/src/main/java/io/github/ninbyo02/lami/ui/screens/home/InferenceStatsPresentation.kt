package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.util.formatInferenceTime
import io.github.ninbyo02.lami.ui.util.formatTimeToFirstToken
import java.util.Locale
import kotlin.math.roundToInt

internal fun inferenceTimingNoteText(): String =
    "初回受信（Thinking対応時はThinking開始と回答本文開始を分離）は端末側、全体完了までは推論統計の完了タイミングを示します。"

internal fun shouldShowInferenceTimingNote(stats: InferenceStats): Boolean =
    formatTimeToFirstToken(stats) != null || formatInferenceTime(stats) != null


internal data class InferenceTimeSegmentUi(
    val label: String,
    val ratio: Double,
    val percent: Int,
    val durationText: String,
)

internal data class InferenceTimeBreakdownUi(
    val segments: List<InferenceTimeSegmentUi>,
)

internal fun buildInferenceTimeBreakdown(stats: InferenceStats): InferenceTimeBreakdownUi? {
    val heldOfficialBlocking = stats.localSourceSummary
        ?.contains("held-official-blocking", ignoreCase = true) == true
    val load = stats.modelLoadDurationNs?.takeIf { it >= 0L }
    val prompt = stats.promptEvalDurationNs?.takeIf { !heldOfficialBlocking && it >= 0L }
    val generation = stats.generationDurationNs?.takeIf { !heldOfficialBlocking && it > 0L }

    val knownSegmentSources = buildList {
        if (load != null) add("ロード" to load)
        if (prompt != null) add("入力" to prompt)
        if (generation != null) add("生成" to generation)
    }
    val knownTotal = knownSegmentSources.sumOf { it.second }
    val displayedTotal = if (heldOfficialBlocking) {
        stats.totalDurationMs?.takeIf { it > 0L }?.let { it * 1_000_000L }
    } else {
        stats.evalDurationNs?.takeIf { it > 0L }
            ?: stats.totalDurationMs?.takeIf { it > 0L }
            ?.let { it * 1_000_000L }
    }
    val denominator = maxOf(displayedTotal ?: 0L, knownTotal)
    if (denominator <= 0L) return null
    val unaccounted = (denominator - knownTotal).coerceAtLeast(0L)
    val segmentSources = buildList {
        addAll(knownSegmentSources)
        if (unaccounted > 0L) add("未計上" to unaccounted)
    }

    fun ratio(value: Long): Double = value.toDouble() / denominator.toDouble()
    return InferenceTimeBreakdownUi(
        segments = segmentSources.map { (label, duration) ->
            val valueRatio = ratio(duration)
            InferenceTimeSegmentUi(
                label = label,
                ratio = valueRatio,
                percent = (valueRatio * 100).roundToInt(),
                durationText = formatDurationNsAsSecondsForSheet(duration),
            )
        },
    )
}

private fun formatDurationNsAsSecondsForSheet(durationNs: Long): String {
    val seconds = durationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}

internal sealed interface ContextUsageUi {
    data class WithMax(
        val used: Int,
        val max: Int,
        val ratio: Double,
        val percent: Int,
    ) : ContextUsageUi

    data class Loading(val used: Int) : ContextUsageUi

    data class WithoutMax(val used: Int) : ContextUsageUi
}

internal fun buildContextUsageUi(stats: InferenceStats): ContextUsageUi? {
    val used = stats.totalTokens?.takeIf { it >= 0 } ?: return null
    val max = stats.contextWindow?.takeIf { it > 0 }
    if (max != null) {
        val ratio = used.toDouble() / max.toDouble()
        return ContextUsageUi.WithMax(
            used = used,
            max = max,
            ratio = ratio,
            percent = (ratio * 100).roundToInt(),
        )
    }
    return when (stats.contextWindowFetchState) {
        ContextWindowFetchState.LOADING -> ContextUsageUi.Loading(used = used)
        ContextWindowFetchState.AVAILABLE,
        ContextWindowFetchState.UNAVAILABLE,
        -> ContextUsageUi.WithoutMax(used = used)
    }
}
