package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import io.github.ninbyo02.lami.ui.util.formatInferenceTime
import io.github.ninbyo02.lami.ui.util.formatModelLoadDuration
import io.github.ninbyo02.lami.ui.util.formatOutputTokens
import io.github.ninbyo02.lami.ui.util.formatPromptEvalDuration
import io.github.ninbyo02.lami.ui.util.formatTimeToFirstToken
import io.github.ninbyo02.lami.ui.util.formatTokenPerSec
import io.github.ninbyo02.lami.ui.util.formatTotalTokens
import java.util.Locale

internal fun buildInferenceSummarySections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
    assistantText: String? = null,
    promptText: String? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
): List<InferenceStatsSectionUi> {
    val isLocalMinimal = isLocalMinimalInferenceStats(stats)
    val localStatsUiModel = localTraceForDev?.let {
        createLocalInferenceStatsUiModel(
            trace = it,
            stats = stats,
            assistantText = assistantText,
            promptText = promptText,
        )
    }
    val localSourceSummaryText = stats.localSourceSummary
        ?.takeIf { it.isNotBlank() }
        ?: localTraceForDev?.let { buildLocalSourceSummaryText(trace = it, stats = stats) }
    val summaryItems = if (isLocalMinimal) {
        buildList {
            add(InferenceStatItemUi(label = "応答時間", value = formatInferenceTime(stats) ?: "—"))
            add(InferenceStatItemUi(label = "応答文字数", value = stats.responseCharCount?.toString() ?: "—"))
            if (localSourceSummaryText != null) {
                add(InferenceStatItemUi(label = "採用元", value = localSourceSummaryText))
            }
        }
    } else {
        buildList {
            add(InferenceStatItemUi(label = "初回受信まで（端末基準）", value = formatTimeToFirstToken(stats) ?: "—"))
            add(InferenceStatItemUi(label = "全体完了まで（統計基準）", value = formatInferenceTime(stats) ?: "—"))
            add(
                InferenceStatItemUi(
                    label = "生成速度",
                    value = if (localTraceForDev == null) {
                        // Ollama 主表示は実測 token/sec を優先して表示する。
                        formatTokenPerSec(stats)?.removePrefix("⚡")?.trim() ?: "—"
                    } else {
                        formatRegularTokensPerSecondValue(
                            statValue = localStatsUiModel?.tokensPerSecond,
                            fallbackValue = formatTokenPerSec(stats)?.removePrefix("⚡")?.trim(),
                        )
                    },
                    emphasizeValue = true,
                )
            )
            add(InferenceStatItemUi(label = "完了理由", value = formatFinishReason(stats) ?: "—"))

            if (localSourceSummaryText != null) {
                add(InferenceStatItemUi(label = "採用元", value = localSourceSummaryText))
            }
        }
    }
    val summarySection = InferenceStatsSectionUi(
        title = "概要",
        items = summaryItems,
    )
    val localInventorySection = buildLocalInventorySectionForDev(
        isLocalMinimal = isLocalMinimal,
        trace = localTraceForDev,
        stats = stats,
        enableDevLlmSessionAsyncPoc = enableDevLlmSessionAsyncPoc,
    )
    return listOfNotNull(summarySection, localInventorySection)
}

internal fun buildInferenceDetailSections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
    assistantText: String? = null,
    promptText: String? = null,
    devHeldStateText: String? = null,
    devCloseLifecycleText: String? = null,
    devDebugText: String? = null,
    measuredTokenSnapshotSummary: String? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
): List<InferenceStatsSectionUi> {
    val hasRealGenerationDuration = stats.generationDurationNs?.let { it > 0L } == true
    val localStatsUiModel = localTraceForDev?.let {
        createLocalInferenceStatsUiModel(
            trace = it,
            stats = stats,
            assistantText = assistantText,
            promptText = promptText,
        )
    }
    val measuredTokensPerSecondText = formatTokenPerSec(stats)?.removePrefix("⚡")?.trim()
    val perceivedTokensPerSecondText = buildPerceivedTokensPerSecondText(stats)
    val showOllamaPerceivedTokensPerSecond = localTraceForDev == null
    val perceivedTokensPerSecondSourceText = if (showOllamaPerceivedTokensPerSecond && perceivedTokensPerSecondText != null) {
        "semi-measured:assistantUpdateCount / generationTimeMs"
    } else {
        null
    }

    val devDiagnosticsUiModel = buildLocalInferenceDevDiagnosticsUiModel(
        devHeldStateText = devHeldStateText,
        devCloseLifecycleText = devCloseLifecycleText,
        devDebugText = devDebugText,
    )
    val devSectionItems = buildList {
        devHeldStateText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Held Engine State", value = it))
        }
        devCloseLifecycleText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Close Lifecycle", value = it))
        }
        devDebugText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Failure / Debug", value = it))
        }
        perceivedTokensPerSecondSourceText?.let {
            add(InferenceStatItemUi(label = "体感生成速度source", value = it))
        }
    }
    val devDiagnosticSummarySection = buildDevDiagnosticSummarySection(
        stats = stats,
        trace = localTraceForDev,
        devHeldStateText = devHeldStateText,
        devCloseLifecycleText = devCloseLifecycleText,
        devDebugText = devDebugText,
        devDiagnosticsUiModel = devDiagnosticsUiModel,
    )

    val tokenizerRecountSnapshot = localTraceForDev?.measuredTokenSnapshot
    val tokenizerSucceeded = tokenizerRecountSnapshot?.let { snapshot ->
        (snapshot.tokenCountMode == "tokenizer_recount" ||
            snapshot.tokenCountMode == "mediapipe_tokenizer_recount") &&
            snapshot.inputTokens != null &&
            snapshot.outputTokens != null
    } == true
    val inputTokenLabel = if (localTraceForDev != null) {
        buildTokenizerTokenLabel(
            baseLabel = "入力トークン数",
            tokenizerSucceeded = tokenizerSucceeded,
            statValue = localStatsUiModel?.tokens?.inputTokens,
            fallbackValue = stats.inputTokens?.toString(),
        )
    } else {
        "入力トークン"
    }
    val outputTokenLabel = if (localTraceForDev != null) {
        buildTokenizerTokenLabel(
            baseLabel = "出力トークン数",
            tokenizerSucceeded = tokenizerSucceeded,
            statValue = localStatsUiModel?.tokens?.outputTokens,
            fallbackValue = formatOutputTokens(stats),
        )
    } else {
        "生成トークン"
    }
    val totalTokenLabel = if (localTraceForDev != null) {
        buildTokenizerTokenLabel(
            baseLabel = "合計トークン",
            tokenizerSucceeded = tokenizerSucceeded,
            statValue = localStatsUiModel?.tokens?.totalTokens,
            fallbackValue = formatTotalTokens(stats),
        )
    } else {
        "合計トークン"
    }
    val tokenizerDiagnosticsItems = buildTokenizerDiagnosticsItems(
        stats = stats,
        trace = localTraceForDev,
        tokenizerSucceeded = tokenizerSucceeded,
    )

    return listOfNotNull(
        devDiagnosticSummarySection,
        InferenceStatsSectionUi(
            title = "トークン",
            items = buildList {
                add(
                    InferenceStatItemUi(
                        label = inputTokenLabel,
                        value = formatRegularTokenValue(
                            statValue = localStatsUiModel?.tokens?.inputTokens,
                            fallbackValue = stats.inputTokens?.toString(),
                            tokenizerSucceeded = tokenizerSucceeded,
                        ),
                    ),
                )
                add(
                    InferenceStatItemUi(
                        label = outputTokenLabel,
                        value = formatRegularTokenValue(
                            statValue = localStatsUiModel?.tokens?.outputTokens,
                            fallbackValue = formatOutputTokens(stats),
                            tokenizerSucceeded = tokenizerSucceeded,
                        ),
                    ),
                )
                add(
                    InferenceStatItemUi(
                        label = totalTokenLabel,
                        value = formatRegularTokenValue(
                            statValue = localStatsUiModel?.tokens?.totalTokens,
                            fallbackValue = formatTotalTokens(stats),
                            tokenizerSucceeded = tokenizerSucceeded,
                        ),
                    ),
                )
                add(
                    InferenceStatItemUi(
                        label = "トークン取得元",
                        value = localStatsUiModel?.resolvedTokenSourceLabel ?: resolveOllamaTokenSourceLabel(stats),
                    ),
                )
                if (localTraceForDev != null) {
                    localStatsUiModel?.tokensPerSecond?.let {
                        add(
                            InferenceStatItemUi(
                                label = "実測生成速度",
                                value = formatRegularTokensPerSecondValue(
                                    statValue = it,
                                    fallbackValue = stats.tokensPerSecond?.let { tokenPerSec ->
                                        String.format(Locale.US, "%.1f token/s", tokenPerSec)
                                    },
                                ),
                            ),
                        )
                    }
                    add(
                        InferenceStatItemUi(
                            label = "速度取得元",
                            value = localStatsUiModel.resolvedSpeedSourceLabel,
                        ),
                    )
                    stats.timeToFirstTokenMs?.let {
                        add(InferenceStatItemUi(label = "TTFT", value = formatMillisToCompactText(it)))
                    }
                    stats.decodeDurationMs?.let {
                        add(InferenceStatItemUi(label = "Decode時間", value = formatMillisToCompactText(it)))
                    }
                    stats.totalDurationMs?.let {
                        add(InferenceStatItemUi(label = "総応答時間", value = formatMillisToCompactText(it)))
                    }
                }
                if (showOllamaPerceivedTokensPerSecond) {
                    measuredTokensPerSecondText?.let {
                        add(InferenceStatItemUi(label = "実測生成速度", value = it))
                    }
                    perceivedTokensPerSecondText?.let {
                        add(InferenceStatItemUi(label = "体感生成速度", value = it))
                    }
                    add(
                        InferenceStatItemUi(
                            label = "速度取得元",
                            value = resolveOllamaSpeedSourceLabel(stats = stats, hasPerceived = perceivedTokensPerSecondText != null),
                        ),
                    )
                }
                localTraceForDev?.measuredTokenSnapshot?.lastPrefillTokenCount?.takeIf { it >= 0 }?.let {
                    add(
                        InferenceStatItemUi(
                            label = "直近 Prefill Token",
                            value = it.toString(),
                        ),
                    )
                }
                localTraceForDev?.measuredTokenSnapshot?.lastDecodeTokenCount?.takeIf { it >= 0 }?.let {
                    add(
                        InferenceStatItemUi(
                            label = "直近 Decode Token",
                            value = it.toString(),
                        ),
                    )
                }
                addAll(tokenizerDiagnosticsItems)
            },
        ),
        InferenceStatsSectionUi(
            title = "バックエンド時間詳細",
            items = listOfNotNull(
                InferenceStatItemUi(
                    label = "モデルロード時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.modelLoadTime?.valueText ?: formatModelLoadDuration(stats),
                        state = localStatsUiModel?.modelLoadTime?.source?.toUiStateLabel()
                            ?: if (stats.modelLoadDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "入力評価時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.promptEvalTime?.valueText ?: formatPromptEvalDuration(stats),
                        state = localStatsUiModel?.promptEvalTime?.source?.toUiStateLabel()
                            ?: if (stats.promptEvalDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "生成時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.generationTime?.valueText
                            ?: if (hasRealGenerationDuration) formatProbeDurationForUi(stats.generationDurationNs) else null,
                        state = localStatsUiModel?.generationTime?.source?.toUiStateLabel()
                            ?: if (hasRealGenerationDuration) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "推論時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.totalTime?.valueText ?: formatProbeDurationForUi(stats.evalDurationNs),
                        state = localStatsUiModel?.totalTime?.source?.toUiStateLabel()
                            ?: if (stats.evalDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
            ),
        ),
        InferenceStatsSectionUi(
            title = "補足",
            items = buildList {
                add(InferenceStatItemUi(label = "画像入力", value = formatImageInputCount(stats) ?: "—"))
                if (localTraceForDev != null && !stats.notes.isNullOrBlank()) {
                    add(InferenceStatItemUi(label = "注記", value = stats.notes))
                }
                if (localTraceForDev != null && enableDevLlmSessionAsyncPoc) {
                    add(InferenceStatItemUi(label = "evalTime", value = localTraceForDev.evalTimeProbe.availability.name))
                    add(InferenceStatItemUi(label = "evalTimeSignature", value = localTraceForDev.evalTimeProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawEvalTime", value = localTraceForDev.evalTimeProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "outputTokens", value = localTraceForDev.outputTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "outputTokensSignature", value = localTraceForDev.outputTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawOutputTokens", value = localTraceForDev.outputTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "estimatedTokens", value = localTraceForDev.estimatedTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "estimatedTokensSignature", value = localTraceForDev.estimatedTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawEstimatedTokens", value = localTraceForDev.estimatedTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "firstToken", value = localTraceForDev.firstTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "firstTokenSignature", value = localTraceForDev.firstTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawFirstToken", value = localTraceForDev.firstTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "assistantUpdateCount", value = localTraceForDev.assistantUpdateCount.toString()))
                    add(InferenceStatItemUi(label = "firstNonEmptyAssistantChunkSeen", value = localTraceForDev.firstNonEmptyAssistantChunkSeen.toString()))
                    add(InferenceStatItemUi(label = "assistantStreamedToUi", value = localTraceForDev.assistantStreamedToUi.toString()))
                    add(InferenceStatItemUi(label = "realPartialReceived", value = localTraceForDev.realPartialReceived.toString()))
                    add(InferenceStatItemUi(label = "realPartialChunkCount", value = localTraceForDev.realPartialChunkCount.toString()))
                    add(InferenceStatItemUi(label = "officialFlowAttempted", value = localTraceForDev.officialFlowAttempted.toString()))
                    add(InferenceStatItemUi(label = "officialFlowUsed", value = localTraceForDev.officialFlowUsed.toString()))
                    add(InferenceStatItemUi(label = "officialFlowFallbackReason", value = localTraceForDev.officialFlowFallbackReason ?: "—"))
                    add(InferenceStatItemUi(label = "officialConversationApiAvailable", value = localTraceForDev.officialConversationApiAvailable?.toString() ?: "—"))
                    add(InferenceStatItemUi(label = "officialFlowChunkCount", value = localTraceForDev.officialFlowChunkCount.toString()))
                }
            },
        ),
        InferenceStatsSectionUi(
            title = "DEV診断",
            items = devSectionItems,
        ).takeIf { it.items.isNotEmpty() },
    )
}

private fun isLocalMinimalInferenceStats(stats: InferenceStats): Boolean {
    return stats.generationTimeMs != null &&
        stats.evalDurationNs == null &&
        stats.outputTokens == null &&
        stats.completionTokens == null &&
        stats.finishReason == null
}

internal fun buildLocalSourceSummaryText(
    trace: LocalInferenceTrace,
    stats: InferenceStats,
): String? {
    val sourceByLabel = resolveLocalSourceItemsForDev(trace = trace, stats = stats)
        .associate { it.label to shortenLocalSourceLabelForSummary(it.value) }

    val summaryParts = listOfNotNull(
        sourceByLabel["modelNameSource"]?.let { "model:$it" },
        sourceByLabel["finishReasonSource"]?.let { "finish:$it" },
        sourceByLabel["outputTokenSource"]?.let { "out:$it" },
        sourceByLabel["evalDurationSource"]?.let { "total:$it" },
        sourceByLabel["tokensPerSecondSource"]?.let { "tps:$it" },
    )

    return summaryParts.takeIf { it.isNotEmpty() }?.joinToString(separator = " / ")
}

private fun shortenLocalSourceLabelForSummary(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "unavailable") return null
    return when {
        raw == "probe" || raw.startsWith("probe-") -> "probe"
        raw == "session" || raw.startsWith("session-") -> "session"
        raw == "trace-local-display-name" -> "trace"
        raw == "trace-finishReason-fallback" -> "fallback"
        raw == "derived-from-total-minus-first" -> "fallback"
        raw == "fallback-generationTimeMs-minus-ttft" -> "fallback"
        raw == "derived-from-eval-minus-generation" -> "fallback"
        raw == "derived-from-total-minus-generation" -> "fallback"
        raw == "self-trace-completed-minus-first" -> "trace"
        raw == "self-trace-completed-minus-start" -> "trace"
        raw == "wall-clock-total-inference" -> "trace"
        raw == "probe-eval-as-total-fallback" -> "fallback"
        raw == "fallback-generationTimeMs" -> "fallback"
        raw == "derived-from-output-and-generationTimeMs" -> "fallback"
        else -> null
    }
}

private fun resolveLocalSourceItemsForDev(
    trace: LocalInferenceTrace,
    stats: InferenceStats,
): List<InferenceStatItemUi> {
    val resolved = resolveLocalInferenceStats(trace)
    val statsUiModel = createLocalInferenceStatsUiModel(trace = trace, stats = stats)
    fun formatResolvedSource(
        source: StatsValueSource,
        detail: String,
    ): String {
        val base = when (source) {
            StatsValueSource.MEASURED -> "measured"
            StatsValueSource.DERIVED -> "derived"
            StatsValueSource.API_CANDIDATE_ONLY -> LocalStatsAvailability.API_CANDIDATE_ONLY.name
            StatsValueSource.UNAVAILABLE -> "unavailable"
        }
        return if (source == StatsValueSource.MEASURED || source == StatsValueSource.DERIVED) {
            "$base:$detail"
        } else {
            base
        }
    }
    val modelNameSource = when {
        trace.modelNameProbe.stringValueOrNull() != null -> "probe"
        !trace.localModelDisplayName.isNullOrBlank() -> "trace-local-display-name"
        else -> "unavailable"
    }
    val finishReasonSource = when {
        trace.finishReasonProbe.stringValueOrNull() != null -> "probe"
        !stats.finishReason.isNullOrBlank() -> "trace-finishReason-fallback"
        else -> "unavailable"
    }
    val outputTokenSource = statsUiModel.tokens.outputTokens.source.toDevLabel().lowercase(Locale.ROOT)
    val totalTokenSource = statsUiModel.tokens.totalTokens.source.toDevLabel().lowercase(Locale.ROOT)
    val firstTokenSource = when (resolved.firstTokenMs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.firstTokenMs.source, "probe-first-token")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.firstTokenMs.source, "self-trace-first-response")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.firstTokenMs.source, "")
        StatsValueSource.UNAVAILABLE ->
            if (stats.timeToFirstTokenMs != null) "fallback-generationTimeMs" else "unavailable"
    }
    val generationDurationSource = when (resolved.generationDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.generationDurationNs.source, "probe-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.generationDurationNs.source, "self-trace-completed-minus-first")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.generationDurationNs.source, "")
        StatsValueSource.UNAVAILABLE ->
            if (stats.generationDurationNs != null) "fallback-generationTimeMs-minus-ttft" else "unavailable"
    }
    val evalDurationSource = when (resolved.evalDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.evalDurationNs.source, "probe-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.evalDurationNs.source, "self-trace-completed-minus-start")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.evalDurationNs.source, "")
        StatsValueSource.UNAVAILABLE -> "unavailable"
    }
    val promptEvalDurationSource = when (resolved.promptEvalDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.promptEvalDurationNs.source, "probe-prompt-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.promptEvalDurationNs.source, "derived-from-total-minus-generation")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.promptEvalDurationNs.source, "")
        StatsValueSource.UNAVAILABLE -> "unavailable"
    }
    val tokensPerSecondSource = when (statsUiModel.tokensPerSecond.source) {
        StatsUiValueSource.SEMI_MEASURED -> "semi-measured:assistantUpdateCount / generationTimeMs"
        else -> statsUiModel.tokensPerSecond.source.toDevLabel().lowercase(Locale.ROOT)
    }
    return listOf(
        InferenceStatItemUi(label = "modelNameSource", value = modelNameSource),
        InferenceStatItemUi(label = "finishReasonSource", value = finishReasonSource),
        InferenceStatItemUi(label = "outputTokenSource", value = outputTokenSource),
        InferenceStatItemUi(label = "totalTokenSource", value = totalTokenSource),
        InferenceStatItemUi(label = "firstTokenSource", value = firstTokenSource),
        InferenceStatItemUi(label = "generationDurationSource", value = generationDurationSource),
        InferenceStatItemUi(label = "evalDurationSource", value = evalDurationSource),
        InferenceStatItemUi(label = "promptEvalDurationSource", value = promptEvalDurationSource),
        InferenceStatItemUi(label = "tokensPerSecondSource", value = tokensPerSecondSource),
    )
}

private fun buildLocalInventorySectionForDev(
    isLocalMinimal: Boolean,
    trace: LocalInferenceTrace?,
    stats: InferenceStats,
    enableDevLlmSessionAsyncPoc: Boolean,
): InferenceStatsSectionUi? {
    if (!isLocalMinimal || trace == null) return null
    val statsUiModel = createLocalInferenceStatsUiModel(trace = trace, stats = stats)
    val rawProbeComparisonItems = listOf(
        InferenceStatItemUi(
            label = "inputTokens(raw probe)",
            value = trace.sessionPromptTokens?.toString() ?: "—",
        ),
        InferenceStatItemUi(
            label = "outputTokens(raw probe)",
            value = trace.outputTokenProbe.valueSummary ?: "—",
        ),
        InferenceStatItemUi(
            label = "totalTokens(raw probe / estimated probe)",
            value = trace.estimatedTokenProbe.valueSummary ?: "—",
        ),
        InferenceStatItemUi(
            label = "inputTokens(adopted UI)",
            value = withProbeStateLabel(
                value = statsUiModel.tokens.inputTokens.valueText,
                state = statsUiModel.tokens.inputTokens.source.toDevLabel(),
            ),
        ),
        InferenceStatItemUi(
            label = "outputTokens(adopted UI)",
            value = withProbeStateLabel(
                value = statsUiModel.tokens.outputTokens.valueText,
                state = statsUiModel.tokens.outputTokens.source.toDevLabel(),
            ),
        ),
        InferenceStatItemUi(
            label = "totalTokens(adopted UI)",
            value = withProbeStateLabel(
                value = statsUiModel.tokens.totalTokens.valueText,
                state = statsUiModel.tokens.totalTokens.source.toDevLabel(),
            ),
        ),
        InferenceStatItemUi(label = "rawOutputTokens", value = trace.outputTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawEstimatedTokens", value = trace.estimatedTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawLoadTime", value = trace.loadTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawPromptEvalTime", value = trace.promptEvalTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawEvalTime", value = trace.evalTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawFirstToken", value = trace.firstTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "parsedLoadTime", value = trace.loadTimeProbe.longValueOrNull()?.toString() ?: "—"),
        InferenceStatItemUi(label = "parsedPromptEvalTime", value = trace.promptEvalTimeProbe.longValueOrNull()?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionPromptTokens", value = trace.sessionPromptTokens?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionResponseTokens", value = trace.sessionResponseTokens?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionTotalTokens", value = trace.sessionTotalTokens?.toString() ?: "—"),
    )
    val fallbackSourceItems = resolveLocalSourceItemsForDev(
        trace = trace,
        stats = stats,
    )
    val sessionAsyncPocDetailItems = if (enableDevLlmSessionAsyncPoc) {
        listOf(
            InferenceStatItemUi(label = "sessionAsyncPocAttempted", value = trace.sessionAsyncPocAttempted.toString()),
            InferenceStatItemUi(label = "sessionAsyncPocCreate", value = trace.sessionAsyncPocCreateSucceeded.toString()),
            InferenceStatItemUi(label = "sessionAsyncPocMethod", value = trace.sessionAsyncPocMethodSignature ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocFutureClass", value = trace.sessionAsyncPocFutureClassName ?: "—"),
            InferenceStatItemUi(
                label = "sessionAsyncPocResponseLength",
                value = trace.sessionAsyncPocResponseLength?.toString() ?: "—",
            ),
            InferenceStatItemUi(label = "sessionAsyncPocResponseHead", value = trace.sessionAsyncPocResponseHead ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocClose", value = trace.sessionAsyncPocCloseSucceeded?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorStage", value = trace.sessionAsyncPocErrorStage ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorClass", value = trace.sessionAsyncPocErrorClassName ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorMessage", value = trace.sessionAsyncPocErrorMessage ?: "—"),
        )
    } else {
        emptyList()
    }
    return InferenceStatsSectionUi(
        title = "LOCAL棚卸し（開発用）",
        items = listOf(
            InferenceStatItemUi(label = "modelName", value = trace.modelNameProbe.availability.name),
            InferenceStatItemUi(label = "finishReason", value = trace.finishReasonProbe.availability.name),
            InferenceStatItemUi(label = "outputTokens", value = statsUiModel.tokens.outputTokens.source.toDevLabel()),
            InferenceStatItemUi(label = "outputTokensSignature", value = trace.outputTokenProbe.signature ?: "—"),
            InferenceStatItemUi(label = "estimatedTokens", value = statsUiModel.tokens.totalTokens.source.toDevLabel()),
            InferenceStatItemUi(label = "estimatedTokensSignature", value = trace.estimatedTokenProbe.signature ?: "—"),
            InferenceStatItemUi(label = "loadTime", value = trace.loadTimeProbe.availability.name),
            InferenceStatItemUi(label = "loadTimeSignature", value = trace.loadTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "promptEvalTime", value = trace.promptEvalTimeProbe.availability.name),
            InferenceStatItemUi(label = "promptEvalTimeSignature", value = trace.promptEvalTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "evalTime", value = statsUiModel.totalTime.source.toDevLabel()),
            InferenceStatItemUi(label = "evalTimeSignature", value = trace.evalTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "firstToken", value = statsUiModel.firstToken.source.toDevLabel()),
            InferenceStatItemUi(label = "firstTokenSignature", value = trace.firstTokenProbe.signature ?: "—"),
            InferenceStatItemUi(
                label = "streamingCandidate",
                value = trace.streamingCandidateDetected?.toString() ?: "—",
            ),
            InferenceStatItemUi(
                label = "streamingCandidateDetected",
                value = trace.streamingCandidateDetected?.toString() ?: "—",
            ),
            InferenceStatItemUi(
                label = "asyncApi",
                value = if (trace.asyncApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "asyncSignature", value = trace.asyncApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "listenerApi",
                value = if (trace.listenerApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "listenerSignature", value = trace.listenerApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionApi",
                value = if (trace.sessionApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionSignature", value = trace.sessionApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionGenerateApi",
                value = if (trace.sessionGenerateSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionGenerateSignature", value = trace.sessionGenerateSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionAsyncApi",
                value = if (trace.sessionAsyncSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionAsyncSignature", value = trace.sessionAsyncSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionStreamingApi",
                value = if (trace.sessionStreamingSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionStreamingSignature", value = trace.sessionStreamingSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionTokenApi",
                value = if (trace.sessionTokenSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionTokenSignature", value = trace.sessionTokenSignature ?: "—"),
        ) + rawProbeComparisonItems + fallbackSourceItems + listOf(
            InferenceStatItemUi(label = "sessionPromptTokens", value = trace.sessionPromptTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionResponseTokens", value = trace.sessionResponseTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionTotalTokens", value = trace.sessionTotalTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionTokenProbeErrorStage", value = trace.sessionTokenProbeErrorStage ?: "—"),
            InferenceStatItemUi(label = "sessionTokenProbeErrorClass", value = trace.sessionTokenProbeErrorClassName ?: "—"),
            InferenceStatItemUi(
                label = "sessionListenerApi",
                value = if (trace.sessionListenerSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionListenerSignature", value = trace.sessionListenerSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionLifecycleApi",
                value = if (trace.sessionLifecycleSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionLifecycleSignature", value = trace.sessionLifecycleSignature ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocEnabled", value = enableDevLlmSessionAsyncPoc.toString()),
        ) + sessionAsyncPocDetailItems + listOf(
            InferenceStatItemUi(label = "assistantResponseSource", value = trace.selectedAssistantResponseSource ?: "—"),
            InferenceStatItemUi(label = "selectedAssistantResponseHead", value = trace.selectedAssistantResponseHead ?: "—"),
            InferenceStatItemUi(label = "oneShotResponseHead", value = trace.oneShotResponseHead ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocCandidateHead", value = trace.sessionAsyncPocSelectedCandidateHead ?: "—"),
            InferenceStatItemUi(label = "generateMethod", value = trace.generateMethodSignature ?: "—"),
            InferenceStatItemUi(label = "createPath", value = trace.createMethodSignature ?: "—"),
            InferenceStatItemUi(label = "optionsBuildPath", value = trace.optionsBuildPath ?: "—"),
        ),
    )
}


private fun buildPerceivedTokensPerSecondText(stats: InferenceStats): String? {
    val assistantUpdateCount = stats.assistantUpdateCount?.takeIf { it > 0 } ?: return null
    val generationTimeMs = stats.generationTimeMs?.takeIf { it > 0L } ?: return null
    val perceivedTokensPerSecond = assistantUpdateCount * 1000.0 / generationTimeMs
    return String.format(Locale.US, "%.1f token/s", perceivedTokensPerSecond)
}

private fun formatRegularTokenValue(
    statValue: UiStatValue?,
    fallbackValue: String?,
    tokenizerSucceeded: Boolean,
): String {
    if (statValue == null) {
        return fallbackValue?.let { "${it}（推定）" } ?: "—（未取得）"
    }
    val numericValue = statValue.rawValueInt?.toString() ?: return "—（未取得）"
    return when (statValue.source) {
        StatsUiValueSource.MEASURED,
        StatsUiValueSource.TOKENIZER_BASED,
        -> if (tokenizerSucceeded) "${numericValue}（Tokenizer）" else "${numericValue}（推定）"
        StatsUiValueSource.SEMI_MEASURED -> "${numericValue}（準実測）"
        StatsUiValueSource.DERIVED -> "${numericValue}（推定）"
        StatsUiValueSource.ESTIMATED -> "${numericValue}（推定）"
        StatsUiValueSource.API_CANDIDATE_ONLY,
        StatsUiValueSource.UNAVAILABLE,
        -> "—（未取得）"
    }
}

private fun formatRegularTokensPerSecondValue(statValue: UiStatValue?, fallbackValue: String?): String {
    if (statValue == null) return fallbackValue?.let { "${it}（推定）" } ?: "—"
    val valueText = statValue.valueText.takeIf { it.isNotBlank() } ?: return "—"
    return when (statValue.source) {
        StatsUiValueSource.DERIVED,
        StatsUiValueSource.MEASURED,
        -> "${valueText}（推定）"
        StatsUiValueSource.TOKENIZER_BASED -> "${valueText}（Tokenizer）"
        StatsUiValueSource.SEMI_MEASURED -> "${valueText}（準実測）"
        StatsUiValueSource.ESTIMATED -> "${valueText}（推定）"
        StatsUiValueSource.API_CANDIDATE_ONLY,
        StatsUiValueSource.UNAVAILABLE,
        -> "—"
    }
}

private fun resolveOllamaTokenSourceLabel(stats: InferenceStats): String {
    return if (stats.inputTokens != null || stats.outputTokens != null || stats.completionTokens != null || stats.totalTokens != null) {
        "Ollama"
    } else {
        "未取得"
    }
}

private fun resolveOllamaSpeedSourceLabel(stats: InferenceStats, hasPerceived: Boolean): String {
    return when {
        stats.tokensPerSecond != null -> "Ollama"
        hasPerceived -> "準実測"
        (stats.outputTokens ?: stats.completionTokens) != null &&
            (stats.generationDurationNs ?: stats.generationTimeMs) != null -> "推定"
        else -> "未取得"
    }
}

private fun buildTokenizerTokenLabel(
    baseLabel: String,
    tokenizerSucceeded: Boolean,
    statValue: UiStatValue?,
    fallbackValue: String?,
): String {
    if (tokenizerSucceeded) return "${baseLabel}（Tokenizer基準）"
    val hasValue = statValue?.rawValueInt != null || !fallbackValue.isNullOrBlank()
    return if (hasValue) {
        "${baseLabel}（推定）"
    } else {
        "${baseLabel}（未取得）"
    }
}

private fun buildTokenizerDiagnosticsItems(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
    tokenizerSucceeded: Boolean,
): List<InferenceStatItemUi> {
    if (trace == null) return emptyList()
    val measuredSnapshot = trace.measuredTokenSnapshot
    val sourceTraceSummary = measuredSnapshot?.tokenizerSourceTraceSummary.orEmpty()
    val tokenizerRecountStatus = if (tokenizerSucceeded) "成功" else "未取得"
    val mediaPipeStatus = measuredSnapshot?.mediaPipeTokenizerStatus
        ?.takeIf { it.isNotBlank() }
        ?.toUiStatusForMediaPipeTokenizer()
        ?: "未実行"
    val mediaPipeSummary = measuredSnapshot?.mediaPipeTokenizerSummary.orEmpty()
    val mediaPipeSessionCreateStatus = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe session create")
        ?.toUiStatusForMediaPipeSessionCreate()
        ?: "未取得"
    val mediaPipeModelPathSource = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path source") ?: "未取得"
    val mediaPipeModelPath = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path") ?: "未取得"
    val mediaPipeModelPathExists = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path exists") ?: "未取得"
    val mediaPipeModelPathIsFile = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path isFile") ?: "未取得"
    val mediaPipeModelPathReadable = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path readable") ?: "未取得"
    val mediaPipeModelPathStatus = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe model path status") ?: "未取得"
    val mediaPipeSizeInTokensStatus = mediaPipeSummary.extractTokenizerSourceValue("MediaPipe sizeInTokens")
        ?.toUiStatusForFoundOrNotFound()
        ?: "未取得"
    val mediaPipePromptTokens = measuredSnapshot?.mediaPipeInputTokens?.toString() ?: "—"
    val mediaPipeResponseTokens = measuredSnapshot?.mediaPipeOutputTokens?.toString() ?: "—"
    val mediaPipeTotalTokens = measuredSnapshot?.mediaPipeTotalTokens?.toString() ?: "—"
    val createSessionStatus = sourceTraceSummary.extractTokenizerSourceValue("engine-createSession status")
        ?.toUiStatusForCreateSession()
        ?: "未実行"
    val createdSessionSizeInTokensStatus = sourceTraceSummary.extractTokenizerSourceValue("created-session sizeInTokens")
        .toUiStatusForFoundOrNotFound()
    val existingSessionSizeInTokensStatus = sourceTraceSummary.extractTokenizerSourceValue("existing-session sizeInTokens")
        .toUiStatusForFoundOrNotFound()
    val conversationTokenizerStatus = sourceTraceSummary.extractTokenizerSourceValue("conversation-tokenizer path")
        .toUiStatusForConversationTokenizerPath()
    return listOf(
        InferenceStatItemUi(label = "Tokenizer再計数", value = tokenizerRecountStatus),
        InferenceStatItemUi(label = "MediaPipe tokenizer", value = mediaPipeStatus),
        InferenceStatItemUi(label = "MediaPipe model path source", value = mediaPipeModelPathSource),
        InferenceStatItemUi(label = "MediaPipe model path", value = mediaPipeModelPath),
        InferenceStatItemUi(label = "MediaPipe model path exists", value = mediaPipeModelPathExists),
        InferenceStatItemUi(label = "MediaPipe model path isFile", value = mediaPipeModelPathIsFile),
        InferenceStatItemUi(label = "MediaPipe model path readable", value = mediaPipeModelPathReadable),
        InferenceStatItemUi(label = "MediaPipe model path status", value = mediaPipeModelPathStatus),
        InferenceStatItemUi(label = "MediaPipe session create", value = mediaPipeSessionCreateStatus),
        InferenceStatItemUi(label = "MediaPipe sizeInTokens", value = mediaPipeSizeInTokensStatus),
        InferenceStatItemUi(label = "MediaPipe prompt tokens", value = mediaPipePromptTokens),
        InferenceStatItemUi(label = "MediaPipe response tokens", value = mediaPipeResponseTokens),
        InferenceStatItemUi(label = "MediaPipe total tokens", value = mediaPipeTotalTokens),
        InferenceStatItemUi(label = "createSession", value = createSessionStatus),
        InferenceStatItemUi(label = "created-session sizeInTokens", value = createdSessionSizeInTokensStatus),
        InferenceStatItemUi(label = "existing-session sizeInTokens", value = existingSessionSizeInTokensStatus),
        InferenceStatItemUi(label = "conversation tokenizer", value = conversationTokenizerStatus),
    )
}

private fun String.extractTokenizerSourceValue(key: String): String? {
    if (isBlank()) return null
    return lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun String?.toUiStatusForCreateSession(): String {
    val normalized = this?.trim().orEmpty()
    return when {
        normalized == "engine-createSession-success" -> "成功"
        normalized.endsWith("method-not-found") -> "未発見"
        normalized.endsWith("not-attempted") -> "未実行"
        normalized.endsWith("failed") -> "失敗"
        normalized.isBlank() -> "未実行"
        else -> normalized
    }
}

private fun String?.toUiStatusForFoundOrNotFound(): String {
    val normalized = this?.trim().orEmpty()
    return when (normalized) {
        "found" -> "成功"
        "not-found" -> "未発見"
        "" -> "未取得"
        else -> normalized
    }
}

private fun String?.toUiStatusForConversationTokenizerPath(): String {
    val normalized = this?.trim().orEmpty()
    return when {
        normalized.isBlank() -> "未取得"
        normalized == "none" -> "未発見"
        else -> "成功"
    }
}

private fun String.toUiStatusForMediaPipeTokenizer(): String {
    val normalized = trim()
    return when {
        normalized == "success" -> "成功"
        normalized.startsWith("failed") -> "失敗"
        normalized.startsWith("unavailable") -> "未対応"
        normalized.isBlank() -> "未実行"
        else -> normalized
    }
}

private fun String.toUiStatusForMediaPipeSessionCreate(): String {
    val normalized = trim()
    return when (normalized) {
        "success" -> "成功"
        "failed" -> "失敗"
        "unavailable" -> "未対応"
        else -> normalized
    }
}

private fun buildDevDiagnosticSummarySection(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
    devHeldStateText: String?,
    devCloseLifecycleText: String?,
    devDebugText: String?,
    devDiagnosticsUiModel: LocalInferenceDevDiagnosticsUiModel,
): InferenceStatsSectionUi? {
    if (
        trace == null &&
        stats.modelName.isNullOrBlank() &&
        devHeldStateText.isNullOrBlank() &&
        devCloseLifecycleText.isNullOrBlank() &&
        devDebugText.isNullOrBlank()
    ) {
        return null
    }
    val items = listOf(
        InferenceStatItemUi(label = "実行経路", value = resolveDevSummaryExecutionPath(stats, trace)),
        InferenceStatItemUi(label = "使用モデル", value = resolveDevSummaryModelName(stats, trace)),
        InferenceStatItemUi(label = "モデル解決", value = resolveDevSummaryModelResolution(stats, trace)),
        InferenceStatItemUi(label = "held engine再利用", value = devDiagnosticsUiModel.heldEngineReuseSummary),
        InferenceStatItemUi(label = "held engine状態", value = devDiagnosticsUiModel.heldEngineStateSummary),
        InferenceStatItemUi(label = "close結果", value = devDiagnosticsUiModel.closeStatusSummary),
        InferenceStatItemUi(label = "失敗要約", value = devDiagnosticsUiModel.failureSummary),
    )
    if (items.all { it.value == "—" || it.value == "不明" }) return null
    return InferenceStatsSectionUi(
        title = "DEV診断サマリー",
        items = items,
    )
}

private fun resolveDevSummaryExecutionPath(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    val source = trace?.selectedAssistantResponseSource?.trim()?.takeIf { it.isNotBlank() }
    if (source != null) return source
    val localSourceSummary = stats.localSourceSummary?.trim()?.takeIf { it.isNotBlank() }
    if (localSourceSummary != null) return localSourceSummary
    return "unknown"
}

private fun resolveDevSummaryModelName(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    return stats.modelName?.trim()?.takeIf { it.isNotBlank() }
        ?: trace?.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
        ?: "—"
}

private fun resolveDevSummaryModelResolution(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    val modelName = stats.modelName?.trim()?.takeIf { it.isNotBlank() }
    val traceModel = trace?.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
    return when {
        modelName != null && (traceModel == null || modelName == traceModel) -> "設定モデル使用"
        modelName == null && traceModel != null -> "設定モデル使用"
        else -> "不明"
    }
}

private fun formatMillisToCompactText(valueMs: Long): String {
    val safeMs = valueMs.coerceAtLeast(0L)
    return if (safeMs >= 1000L) {
        String.format(Locale.US, "%.1f s", safeMs / 1000.0)
    } else {
        "$safeMs ms"
    }
}
