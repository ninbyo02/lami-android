package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.util.formatModelName

internal fun buildInferenceStatsFullCopyText(
    stats: InferenceStats,
    displayMode: InferenceStatsDisplayMode,
    sections: List<InferenceStatsSectionUi>,
    detailSections: List<InferenceStatsSectionUi>,
    memoryRecoveryCheckState: MemoryRecoveryCheckState? = null,
    npuS1RepeatedRunState: NpuS1RepeatedRunState? = null,
    npuNonStreamingRepeatedStabilityState: NpuNonStreamingRepeatedStabilityState? = null,
    npuS1PersistentEngineState: NpuS1PersistentEngineProbeState? = null,
    npuPersistentHolderCreateCloseState: NpuPersistentHolderCreateCloseProbeState? = null,
    npuTrueEngineHolderCreateCloseState: NpuTrueEngineHolderCreateCloseProbeState? = null,
    npuPersistentHolderRunOnceState: NpuPersistentHolderRunOnceProbeState? = null,
    npuPersistentHolderTwoTurnState: NpuPersistentHolderTwoTurnProbeState? = null,
    npuPersistentHolderFiveTurnState: NpuPersistentHolderFiveTurnProbeState? = null,
    npuPersistentHolderTenTurnState: NpuPersistentHolderTenTurnProbeState? = null,
    npuS1PersistentCustomJniState: NpuS1PersistentCustomJniProbeState? = null,
): String {
    return buildString {
        appendLine("推論統計")
        appendLine()
        appendLine("[モデル情報]")
        appendLine("使用モデル: ${formatModelName(stats) ?: "—"}")
        appendLine()

        sections.forEachIndexed { index, section ->
            appendSectionAsPlainText(
                sectionTitle = section.title,
                items = section.items,
            )
            if (index != sections.lastIndex) appendLine()
        }

        if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
            appendLine()
            appendLine("[推論時間内訳]")
            val breakdown = buildInferenceTimeBreakdown(stats)
            if (breakdown == null) {
                appendLine("—")
            } else {
                breakdown.segments.forEach { segment ->
                    appendLine("${segment.label}: ${segment.durationText} / ${segment.percent}%")
                }
            }
            appendLine()
            appendLine("[コンテキスト使用量]")
            when (val usage = buildContextUsageUi(stats)) {
                null -> appendLine("—")
                is ContextUsageUi.WithMax -> appendLine("${usage.used} / ${usage.max} tokens (${usage.percent}%)")
                is ContextUsageUi.Loading -> {
                    appendLine("使用トークン ${usage.used}")
                    appendLine("上限取得中…")
                }

                is ContextUsageUi.WithoutMax -> {
                    appendLine("使用トークン ${usage.used}")
                    appendLine("上限未取得")
                }
            }
        }

        if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
            appendLine()
            appendLine("[追加情報]")
            if (detailSections.isEmpty()) {
                appendLine("—")
            } else {
                detailSections.forEachIndexed { index, section ->
                    appendSectionAsPlainText(
                        sectionTitle = section.title,
                        items = section.items,
                    )
                    if (index != detailSections.lastIndex) appendLine()
                }
            }
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && memoryRecoveryCheckState != null) {
            appendLine()
            appendLine(formatMemoryRecoveryCheckForDev(memoryRecoveryCheckState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuS1RepeatedRunState != null) {
            appendLine()
            appendLine(formatNpuS1RepeatedRunDiagnosticsForDev(npuS1RepeatedRunState))
        }
        if (
            displayMode == InferenceStatsDisplayMode.DEVELOPER &&
            npuNonStreamingRepeatedStabilityState != null
        ) {
            appendLine()
            appendLine(
                buildNpuNonStreamingRepeatedStabilityFullDumpCopyText(
                    npuNonStreamingRepeatedStabilityState,
                ),
            )
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuS1PersistentEngineState != null) {
            appendLine()
            appendLine(formatNpuS1PersistentEngineDiagnosticsForDev(npuS1PersistentEngineState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuPersistentHolderCreateCloseState != null) {
            appendLine()
            appendLine(formatNpuPersistentHolderCreateCloseFullDumpForCopy(npuPersistentHolderCreateCloseState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuTrueEngineHolderCreateCloseState != null) {
            appendLine()
            appendLine(formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(npuTrueEngineHolderCreateCloseState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuPersistentHolderRunOnceState != null) {
            appendLine()
            appendLine(formatNpuPersistentHolderRunOnceFullDumpForCopy(npuPersistentHolderRunOnceState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuPersistentHolderTwoTurnState != null) {
            appendLine()
            appendLine(formatNpuPersistentHolderTwoTurnFullDumpForCopy(npuPersistentHolderTwoTurnState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuPersistentHolderFiveTurnState != null) {
            appendLine()
            appendLine(formatNpuPersistentHolderFiveTurnFullDumpForCopy(npuPersistentHolderFiveTurnState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuPersistentHolderTenTurnState != null) {
            appendLine()
            appendLine(formatNpuPersistentHolderTenTurnFullDumpForCopy(npuPersistentHolderTenTurnState))
        }
        if (displayMode == InferenceStatsDisplayMode.DEVELOPER && npuS1PersistentCustomJniState != null) {
            appendLine()
            appendLine(formatNpuS1PersistentCustomJniDiagnosticsForDev(npuS1PersistentCustomJniState))
        }

    }.trimEnd()
}

private fun StringBuilder.appendSectionAsPlainText(
    sectionTitle: String,
    items: List<InferenceStatItemUi>,
) {
    appendLine("[$sectionTitle]")
    if (items.isEmpty()) {
        appendLine("—")
        return
    }
    items.forEach { item ->
        appendLine("${item.label}: ${item.value}")
    }
}
