package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

internal fun LocalStatsCandidateProbe.stringValueOrNull(): String? {
    if (availability == LocalStatsAvailability.NOT_FOUND) return null
    return valueSummary
}

internal fun createLocalInferenceStatsUiModel(
    trace: LocalInferenceTrace,
    stats: InferenceStats,
    assistantText: String? = null,
    promptText: String? = null,
): LocalInferenceStatsUiModel {
    return buildLocalInferenceStatsUiModel(
        trace = trace,
        resolved = resolveLocalInferenceStats(trace),
        stats = stats,
        measuredSnapshot = trace.measuredTokenSnapshot,
        assistantText = assistantText,
        promptText = promptText,
        selectedAssistantResponseSource = trace.selectedAssistantResponseSource,
    )
}
