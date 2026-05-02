package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import io.github.ninbyo02.lami.ui.util.formatInferenceTime
import io.github.ninbyo02.lami.ui.util.formatModelLoadDuration
import io.github.ninbyo02.lami.ui.util.formatOutputTokens
import io.github.ninbyo02.lami.ui.util.formatPromptEvalDuration
import io.github.ninbyo02.lami.ui.util.formatTimeToFirstToken
import io.github.ninbyo02.lami.ui.util.formatTotalTokens
import java.util.Locale

private enum class InferenceBackendKind {
    LITERT,
    OLLAMA,
}

internal fun buildInferenceSummarySections(
    stats: InferenceStats,
    displayMode: InferenceStatsDisplayMode,
    localTraceForDev: LocalInferenceTrace? = null,
    assistantText: String? = null,
    promptText: String? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
    acceleratorProbeSnapshot: AcceleratorProbeSnapshot? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
): List<InferenceStatsSectionUi> {
    if (displayMode == InferenceStatsDisplayMode.SIMPLE) {
        return buildInferenceSimpleSections(
            stats = stats,
            localTraceForDev = localTraceForDev,
            assistantText = assistantText,
            promptText = promptText,
        )
    }
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
        }
    } else {
        buildList {
            add(InferenceStatItemUi(label = "初回受信まで（端末基準）", value = formatTimeToFirstToken(stats) ?: "—"))
            add(InferenceStatItemUi(label = "全体完了まで（統計基準）", value = formatInferenceTime(stats) ?: "—"))
            add(
                InferenceStatItemUi(
                    label = "生成速度",
                    value = if (localTraceForDev == null) {
                        buildBackendTokensPerSecondText(stats)
                            ?: buildLamiTokensPerSecondText(stats)
                            ?: "—"
                    } else {
                        buildBackendTokensPerSecondText(stats)
                            ?: formatRegularTokensPerSecondValue(
                                statValue = localStatsUiModel?.tokensPerSecond,
                                fallbackValue = buildLamiTokensPerSecondText(stats),
                            )
                    },
                    emphasizeValue = true,
                )
            )
            add(InferenceStatItemUi(label = "完了理由", value = formatFinishReason(stats) ?: "—"))
        }
    }
    val summarySection = InferenceStatsSectionUi(
        title = "概要",
        items = summaryItems,
    )
    val localInventorySection = if (displayMode == InferenceStatsDisplayMode.DEVELOPER) {
        buildLocalInventorySectionForDev(
            isLocalMinimal = isLocalMinimal,
            trace = localTraceForDev,
            stats = stats,
            enableDevLlmSessionAsyncPoc = enableDevLlmSessionAsyncPoc,
        )
    } else {
        null
    }
    return listOfNotNull(summarySection, localInventorySection)
}

internal fun buildInferenceDetailSections(
    stats: InferenceStats,
    displayMode: InferenceStatsDisplayMode,
    localTraceForDev: LocalInferenceTrace? = null,
    assistantText: String? = null,
    promptText: String? = null,
    devHeldStateText: String? = null,
    devCloseLifecycleText: String? = null,
    devDebugText: String? = null,
    measuredTokenSnapshotSummary: String? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
    acceleratorProbeSnapshot: AcceleratorProbeSnapshot? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
): List<InferenceStatsSectionUi> {
    if (displayMode == InferenceStatsDisplayMode.SIMPLE) return emptyList()
    val hasRealGenerationDuration = stats.generationDurationNs?.let { it > 0L } == true
    val localStatsUiModel = localTraceForDev?.let {
        createLocalInferenceStatsUiModel(
            trace = it,
            stats = stats,
            assistantText = assistantText,
            promptText = promptText,
        )
    }
    val backendTokensPerSecondText = buildBackendTokensPerSecondText(stats)
    val perceivedTokensPerSecondText = buildLamiPerceivedTokensPerSecondText(stats)
    val displayTokensPerSecondText = if (localTraceForDev == null) {
        buildLamiTokensPerSecondText(stats)
    } else {
        formatRegularTokensPerSecondValue(
            statValue = localStatsUiModel?.tokensPerSecond,
            fallbackValue = buildLamiTokensPerSecondText(stats),
        )
    }
    val showOllamaPerceivedTokensPerSecond = localTraceForDev == null
    val localSourceSummaryText = stats.localSourceSummary
        ?.takeIf { it.isNotBlank() }
        ?: localTraceForDev?.let { buildLocalSourceSummaryText(trace = it, stats = stats) }
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
    val executionInference = inferExecutionTarget(
        officialFlowUsed = localTraceForDev?.officialFlowUsed,
        fallbackReason = localTraceForDev?.officialFlowFallbackReason,
        gpuRenderer = acceleratorProbeSnapshot?.gpuRenderer,
        nnapiAvailable = acceleratorProbeSnapshot?.nnapiAvailable == true,
        nnapiDevices = acceleratorProbeSnapshot?.nnapiDevices.orEmpty(),
        androidSdk = acceleratorProbeSnapshot?.androidSdk,
        delegateSwitchingSupportedHint = acceleratorProbeSnapshot?.delegateSwitchingSupportedHint,
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
        acceleratorProbeSnapshot?.let { probe ->
            add(InferenceStatItemUi(label = "アクセラレータ候補 Device", value = listOfNotNull(probe.deviceManufacturer, probe.deviceModel, probe.deviceBoard).joinToString(" / ").ifBlank { "unknown" }))
            add(InferenceStatItemUi(label = "Android SDK", value = probe.androidSdk.toString()))
            add(InferenceStatItemUi(label = "ABI", value = probe.supportedAbis.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "unknown"))
            add(InferenceStatItemUi(label = "CPU cores", value = probe.cpuCoreCount?.toString() ?: "unknown"))
            add(InferenceStatItemUi(label = "GPU検出情報", value = listOfNotNull(probe.gpuVendor, probe.gpuRenderer, probe.gpuVersion).joinToString(" / ").ifBlank { "unknown" }))
            add(InferenceStatItemUi(label = "GPU Probe", value = probe.gpuProbeSource?.ifBlank { "unknown" } ?: "unknown"))
            probe.gpuProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "GPU Probe Error", value = it)) }
            add(InferenceStatItemUi(label = "NNAPI候補", value = if (probe.nnapiAvailable) "available" else "unavailable"))
            if (probe.nnapiDeprecatedWarning) {
                add(InferenceStatItemUi(label = "NNAPI warning", value = "deprecated on Android 15+"))
            }
            add(InferenceStatItemUi(label = "NNAPI devices", value = probe.nnapiDevices.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Source", value = probe.probeSource))
            probe.probeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Error", value = it)) }
            add(InferenceStatItemUi(label = "Delegate API Probe", value = probe.delegateProbeSource?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Delegate switching hint", value = probe.delegateSwitchingSupportedHint?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Delegate option candidates", value = probe.delegateOptionCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Delegate backend candidates", value = probe.delegateBackendCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Delegate backend enum values", value = probe.delegateBackendEnumValues.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Delegate preferredBackend signatures", value = probe.delegatePreferredBackendSignatures.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Requested preferredBackend", value = localTraceForDev?.requestedPreferredBackend ?: preferredBackendDryRunSetting.name))
            add(InferenceStatItemUi(label = "Applied preferredBackend", value = localTraceForDev?.appliedPreferredBackend ?: "not-applied"))
            add(InferenceStatItemUi(label = "PreferredBackend apply result", value = localTraceForDev?.preferredBackendApplyResult ?: if (preferredBackendDryRunSetting == PreferredBackendDryRunSetting.DEFAULT) "skipped-default" else "not-supported"))
            add(InferenceStatItemUi(label = "PreferredBackend hook reached", value = localTraceForDev?.preferredBackendHookReached?.toString() ?: "false"))
            add(InferenceStatItemUi(label = "PreferredBackend hook source", value = localTraceForDev?.preferredBackendHookSource?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend apply error", value = localTraceForDev?.preferredBackendApplyError ?: "—"))
            add(InferenceStatItemUi(label = "PreferredBackend builder class", value = localTraceForDev?.preferredBackendApplyBuilderClass?.ifBlank { "none/unknown" } ?: "none/unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend method candidates", value = localTraceForDev?.preferredBackendApplyMethodCandidates?.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend backend enum candidates", value = localTraceForDev?.preferredBackendApplyBackendEnumCandidates?.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            localTraceForDev?.preferredBackendApplyNotSupportedReason?.takeIf { it.isNotBlank() }?.let {
                add(InferenceStatItemUi(label = "PreferredBackend not-supported reason", value = it))
            }
            add(InferenceStatItemUi(label = "Delegate class candidates", value = probe.delegateClassCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            probe.delegateBackendEnumProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate backend enum probe error", value = it)) }
            probe.delegatePreferredBackendSignatureProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate preferredBackend signature error", value = it)) }
            probe.delegateProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate Probe Error", value = it)) }
            add(InferenceStatItemUi(label = "実行経路推定", value = "${executionInference.target} / ${executionInference.confidence}"))
            val executionReason = if (preferredBackendDryRunSetting != PreferredBackendDryRunSetting.DEFAULT) {
                "${executionInference.reason}; requested preferredBackend=${preferredBackendDryRunSetting.name}"
            } else {
                executionInference.reason
            }
            add(InferenceStatItemUi(label = "推定理由", value = executionReason))
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
    val detailedItems = buildList {
        if (localTraceForDev != null) {
            add(
                InferenceStatItemUi(
                    label = "速度取得元",
                    value = localStatsUiModel?.resolvedSpeedSourceLabel
                        ?: resolveBackendSpeedSourceLabel(
                            stats = stats,
                            hasPerceived = localStatsUiModel?.resolvedLamiPerceivedTokensPerSecond != null,
                            backendKind = InferenceBackendKind.LITERT,
                        ),
                ),
            )
            add(InferenceStatItemUi(label = "表示速度", value = displayTokensPerSecondText ?: "—"))
            val backendSpeedText = localStatsUiModel?.resolvedBackendTokensPerSecond?.let {
                String.format(Locale.US, "%.1f token/s", it)
            } ?: "—"
            add(InferenceStatItemUi(label = "バックエンド基準速度", value = backendSpeedText))
            localStatsUiModel?.resolvedLamiPerceivedTokensPerSecond?.let {
                add(InferenceStatItemUi(label = "体感速度", value = String.format(Locale.US, "%.1f token/s", it)))
            }
            addAll(
                buildUnifiedTtftItems(
                    lamiTtftMs = localStatsUiModel?.resolvedLamiTtftMs,
                    backendTtftMs = localStatsUiModel?.resolvedBackendTtftMs,
                ),
            )
            stats.decodeDurationMs?.let {
                add(InferenceStatItemUi(label = "Decode時間", value = formatMillisToCompactText(it)))
            }
            stats.totalDurationMs?.let {
                add(InferenceStatItemUi(label = "総応答時間", value = formatMillisToCompactText(it)))
            }
        }
        if (showOllamaPerceivedTokensPerSecond) {
            add(
                InferenceStatItemUi(
                    label = "速度取得元",
                    value = resolveBackendSpeedSourceLabel(
                        stats = stats,
                        hasPerceived = perceivedTokensPerSecondText != null,
                        backendKind = InferenceBackendKind.OLLAMA,
                    ),
                ),
            )
            add(InferenceStatItemUi(label = "表示速度", value = displayTokensPerSecondText ?: "—"))
            add(InferenceStatItemUi(label = "バックエンド基準速度", value = backendTokensPerSecondText ?: "—"))
            perceivedTokensPerSecondText?.let {
                add(InferenceStatItemUi(label = "体感速度", value = it))
            }
            addAll(
                buildUnifiedTtftItems(
                    lamiTtftMs = stats.timeToFirstTokenMs,
                    backendTtftMs = stats.timeToFirstTokenMs,
                ),
            )
        }
        localSourceSummaryText?.let {
            add(InferenceStatItemUi(label = "採用元", value = it))
        }
        add(
            InferenceStatItemUi(
                label = "モデルロード時間",
                value = withProbeStateLabel(
                    value = localStatsUiModel?.modelLoadTime?.valueText ?: formatModelLoadDuration(stats),
                    state = localStatsUiModel?.modelLoadTime?.source?.toUiStateLabel()
                        ?: if (stats.modelLoadDurationNs != null) "取得済み" else "未取得",
                ),
            ),
        )
        add(
            InferenceStatItemUi(
                label = "入力評価時間",
                value = withProbeStateLabel(
                    value = localStatsUiModel?.promptEvalTime?.valueText ?: formatPromptEvalDuration(stats),
                    state = localStatsUiModel?.promptEvalTime?.source?.toUiStateLabel()
                        ?: if (stats.promptEvalDurationNs != null) "取得済み" else "未取得",
                ),
            ),
        )
        add(
            InferenceStatItemUi(
                label = "生成時間",
                value = withProbeStateLabel(
                    value = localStatsUiModel?.generationTime?.valueText
                        ?: if (hasRealGenerationDuration) formatProbeDurationForUi(stats.generationDurationNs) else null,
                    state = localStatsUiModel?.generationTime?.source?.toUiStateLabel()
                        ?: if (hasRealGenerationDuration) "取得済み" else "未取得",
                ),
            ),
        )
        add(
            InferenceStatItemUi(
                label = "推論時間",
                value = withProbeStateLabel(
                    value = localStatsUiModel?.totalTime?.valueText ?: formatProbeDurationForUi(stats.evalDurationNs),
                    state = localStatsUiModel?.totalTime?.source?.toUiStateLabel()
                        ?: if (stats.evalDurationNs != null) "取得済み" else "未取得",
                ),
            ),
        )
    }

    return listOfNotNull(
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
            },
        ),
        InferenceStatsSectionUi(
            title = "詳細",
            items = detailedItems,
        ),
        InferenceStatsSectionUi(
            title = "補足",
            items = buildList {
                add(InferenceStatItemUi(label = "画像入力", value = formatImageInputCount(stats) ?: "—"))
                if (localTraceForDev != null && !stats.notes.isNullOrBlank()) {
                    add(InferenceStatItemUi(label = "注記", value = stats.notes))
                }
            },
        ),
        devDiagnosticSummarySection.takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER },
        InferenceStatsSectionUi(
            title = "DEV診断",
            items = buildList {
                addAll(devSectionItems)
                measuredTokenSnapshotSummary?.takeIf { it.isNotBlank() }?.let {
                    add(InferenceStatItemUi(label = "measuredTokens", value = it))
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
        ).takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER && it.items.isNotEmpty() },
    )
}

private fun buildInferenceSimpleSections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace?,
    assistantText: String?,
    promptText: String?,
): List<InferenceStatsSectionUi> {
    val localStatsUiModel = localTraceForDev?.let {
        createLocalInferenceStatsUiModel(
            trace = it,
            stats = stats,
            assistantText = assistantText,
            promptText = promptText,
        )
    }
    val generationSpeedText = if (localTraceForDev == null) {
        buildBackendTokensPerSecondText(stats)
            ?: buildLamiTokensPerSecondText(stats)
    } else {
        buildBackendTokensPerSecondText(stats)
            ?: formatRegularTokensPerSecondValue(
                statValue = localStatsUiModel?.tokensPerSecond,
                fallbackValue = buildLamiTokensPerSecondText(stats),
            )
    }
    val ttftItems = if (localTraceForDev == null) {
        buildUnifiedTtftItems(
            lamiTtftMs = stats.timeToFirstTokenMs,
            backendTtftMs = stats.timeToFirstTokenMs,
        )
    } else {
        buildUnifiedTtftItems(
            lamiTtftMs = localStatsUiModel?.resolvedLamiTtftMs,
            backendTtftMs = localStatsUiModel?.resolvedBackendTtftMs,
        )
    }
    return listOf(
        InferenceStatsSectionUi(
            title = "概要",
            items = buildList {
                add(InferenceStatItemUi(label = "応答時間", value = formatInferenceTime(stats) ?: "—"))
                add(InferenceStatItemUi(label = "生成速度", value = generationSpeedText ?: "—", emphasizeValue = true))
                addAll(ttftItems)
                add(InferenceStatItemUi(label = "使用トークン", value = formatTotalTokens(stats) ?: "—"))
                add(InferenceStatItemUi(label = "完了理由", value = formatFinishReason(stats) ?: "—"))
            },
        ),
    )
}

private fun buildUnifiedTtftItems(
    lamiTtftMs: Long?,
    backendTtftMs: Long?,
): List<InferenceStatItemUi> {
    val lamiText = lamiTtftMs?.let { formatMillisToCompactText(it) }
    val backendText = backendTtftMs?.let { formatMillisToCompactText(it) }
    if (lamiText != null && backendText != null && lamiText == backendText) {
        return listOf(InferenceStatItemUi(label = "TTFT", value = lamiText))
    }
    return listOf(
        InferenceStatItemUi(label = "Lami基準TTFT", value = lamiText ?: "—"),
        InferenceStatItemUi(label = "バックエンド基準TTFT", value = backendText ?: "—"),
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
    if (statValue == null) return fallbackValue ?: "—"
    val valueText = statValue.valueText.takeIf { it.isNotBlank() } ?: return "—"
    return when (statValue.source) {
        StatsUiValueSource.MEASURED,
        StatsUiValueSource.DERIVED,
        StatsUiValueSource.TOKENIZER_BASED,
        StatsUiValueSource.SEMI_MEASURED,
        StatsUiValueSource.ESTIMATED,
        -> valueText
        StatsUiValueSource.API_CANDIDATE_ONLY,
        StatsUiValueSource.UNAVAILABLE,
        -> "—"
    }
}

private fun resolveOllamaTokenSourceLabel(stats: InferenceStats): String {
    return if (stats.inputTokens != null || stats.outputTokens != null || stats.completionTokens != null || stats.totalTokens != null) {
        "バックエンド"
    } else {
        "未取得"
    }
}

private fun resolveBackendSpeedSourceLabel(
    stats: InferenceStats,
    hasPerceived: Boolean,
    backendKind: InferenceBackendKind,
): String {
    return when (backendKind) {
        InferenceBackendKind.LITERT -> when {
            stats.decodeDurationMs?.let { it > 0L } == true -> "Lami基準 / バックエンド基準（Decode時間）"
            stats.generationDurationNs?.let { it > 0L } == true || stats.generationTimeMs?.let { it > 0L } == true ->
                "Lami基準 / バックエンド基準（generation時間）"
            stats.tokensPerSecond != null -> "Lami基準 / バックエンド基準（Engine時間）"
            hasPerceived -> "Lami基準 / バックエンド基準（fallback）"
            else -> "未取得"
        }
        InferenceBackendKind.OLLAMA -> when {
            stats.tokensPerSecond != null -> "Lami基準 / バックエンド基準（サーバー統計）"
            hasPerceived -> "Lami基準 / バックエンド基準（fallback）"
            (stats.outputTokens ?: stats.completionTokens) != null &&
                (stats.generationDurationNs ?: stats.generationTimeMs) != null -> "推定"
            else -> "未取得"
        }
    }
}

private fun buildLamiTokensPerSecondText(stats: InferenceStats): String? {
    val outputTokens = (stats.outputTokens ?: stats.completionTokens)?.takeIf { it >= 0 } ?: return null
    val totalDurationMs = stats.totalDurationMs?.takeIf { it > 0L } ?: stats.generationTimeMs?.takeIf { it > 0L } ?: return null
    val ttftMs = stats.timeToFirstTokenMs?.takeIf { it >= 0L }
    val generationOnlyMs = if (ttftMs != null) (totalDurationMs - ttftMs).coerceAtLeast(1L) else totalDurationMs
    val value = outputTokens * 1000.0 / generationOnlyMs
    return value.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.1f token/s", it) }
}

private fun buildLamiPerceivedTokensPerSecondText(stats: InferenceStats): String? {
    val outputTokens = (stats.outputTokens ?: stats.completionTokens)?.takeIf { it >= 0 } ?: return null
    val totalDurationMs = stats.totalDurationMs?.takeIf { it > 0L } ?: stats.generationTimeMs?.takeIf { it > 0L } ?: return null
    val value = outputTokens * 1000.0 / totalDurationMs
    return value.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.1f token/s", it) }
}

private fun buildBackendTokensPerSecondText(stats: InferenceStats): String? {
    val backendValue = stats.tokensPerSecond ?: run {
        val outputTokens = (stats.outputTokens ?: stats.completionTokens)?.takeIf { it >= 0 } ?: return null
        val evalDurationNs = stats.evalDurationNs?.takeIf { it > 0L } ?: return null
        outputTokens / (evalDurationNs / 1_000_000_000.0)
    }
    return backendValue.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f token/s", it) }
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
        InferenceStatItemUi(label = "Tokenizer再計数", value = resolveDevSummaryTokenizerRecountStatus(trace)),
        InferenceStatItemUi(label = "MediaPipe tokenizer", value = resolveDevSummaryMediaPipeTokenizerStatus(trace)),
        InferenceStatItemUi(label = "失敗要約", value = devDiagnosticsUiModel.failureSummary),
    )
    if (items.all { it.value == "—" || it.value == "不明" }) return null
    return InferenceStatsSectionUi(
        title = "DEV診断サマリー",
        items = items,
    )
}

private fun resolveDevSummaryTokenizerRecountStatus(trace: LocalInferenceTrace?): String {
    val snapshot = trace?.measuredTokenSnapshot ?: return "未取得"
    val succeeded = (snapshot.tokenCountMode == "tokenizer_recount" ||
        snapshot.tokenCountMode == "mediapipe_tokenizer_recount") &&
        snapshot.inputTokens != null &&
        snapshot.outputTokens != null
    return if (succeeded) "成功" else "未取得"
}

private fun resolveDevSummaryMediaPipeTokenizerStatus(trace: LocalInferenceTrace?): String {
    val status = trace?.measuredTokenSnapshot?.mediaPipeTokenizerStatus?.trim().orEmpty()
    if (status.isBlank()) return "未実行"
    return status.toUiStatusForMediaPipeTokenizer()
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


internal data class ExecutionTargetInference(
    val target: String,
    val confidence: String,
    val reason: String,
)

internal fun inferExecutionTarget(
    officialFlowUsed: Boolean?,
    fallbackReason: String?,
    gpuRenderer: String?,
    nnapiAvailable: Boolean,
    nnapiDevices: List<String>,
    androidSdk: Int?,
    delegateSwitchingSupportedHint: String?,
): ExecutionTargetInference {
    val delegateApiCandidateDetected = delegateSwitchingSupportedHint == "delegate-api-candidate-detected" ||
        delegateSwitchingSupportedHint == "backend-enum-detected" ||
        delegateSwitchingSupportedHint == "options-candidate-detected"
    fallbackReason?.takeIf { it.isNotBlank() }?.let {
        return ExecutionTargetInference("unknown", "low", "fallback detected: $it")
    }
    if (officialFlowUsed == true) {
        val delegateNote = if (delegateApiCandidateDetected) {
            "; delegate API candidate detected"
        } else {
            ""
        }
        return ExecutionTargetInference(
            "accelerator-unknown",
            "low",
            "MediaPipe/LiteRT official flow used, delegate is not confirmed$delegateNote",
        )
    }
    if (!gpuRenderer.isNullOrBlank()) {
        return ExecutionTargetInference("gpu-possible", "low", "GPU detected, but inference delegate is not confirmed")
    }
    if (nnapiAvailable || nnapiDevices.isNotEmpty()) {
        val sdkNote = if ((androidSdk ?: 0) >= 35) " Android 15+ NNAPI is deprecated." else ""
        return ExecutionTargetInference(
            "npu-candidate",
            "low",
            "NNAPI candidate detected;$sdkNote not proof of NPU execution".trim(),
        )
    }
    return ExecutionTargetInference("cpu-likely", "low", "No accelerator signal detected")
}
