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
        trace = localTraceForDev,
    )
    val executionInference = inferExecutionTarget(
        officialFlowUsed = localTraceForDev?.officialFlowUsed,
        fallbackReason = localTraceForDev?.officialFlowFallbackReason,
        requestedPreferredBackend = localTraceForDev?.requestedPreferredBackend ?: preferredBackendDryRunSetting.name,
        appliedPreferredBackend = localTraceForDev?.appliedPreferredBackend,
        preferredBackendApplyResult = localTraceForDev?.preferredBackendApplyResult,
        gpuRenderer = acceleratorProbeSnapshot?.gpuRenderer,
        nnapiAvailable = acceleratorProbeSnapshot?.nnapiAvailable == true,
        nnapiDevices = acceleratorProbeSnapshot?.nnapiDevices.orEmpty(),
        androidSdk = acceleratorProbeSnapshot?.androidSdk,
        delegateSwitchingSupportedHint = acceleratorProbeSnapshot?.delegateSwitchingSupportedHint,
        qnnNpuAttempted = acceleratorProbeSnapshot?.qnnNpuAttempted == true,
        qnnNpuAvailable = acceleratorProbeSnapshot?.qnnNpuAvailable,
        qnnNpuSelectedPath = acceleratorProbeSnapshot?.qnnNpuSelectedPath,
        qnnNpuFallbackPath = acceleratorProbeSnapshot?.qnnNpuFallbackPath,
        npuReadinessSummary = acceleratorProbeSnapshot?.npuReadinessSummary,
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
            add(InferenceStatItemUi(label = "NPU probe hint", value = probe.npuProbeHint?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "NPU status", value = "probe-only (not applied)"))
            add(InferenceStatItemUi(label = "NPU apply status", value = "disabled (forced GPU fallback)"))
            add(InferenceStatItemUi(label = "NPU note", value = "NPU backend candidate detected via reflection. Currently disabled for safety; GPU fallback is used for actual inference."))
            add(InferenceStatItemUi(label = "NPU delegate candidates", value = probe.npuDelegateCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "NPU backend candidates", value = probe.npuBackendCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Backend NPU probe hint", value = probe.backendNpuProbeHint?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Backend NPU class candidates", value = probe.backendNpuClassCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Backend NPU method candidates", value = probe.backendNpuMethodCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Backend NPU constructor signatures", value = probe.backendNpuConstructorSignatures.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Backend NPU nativeLibraryDir required", value = probe.backendNpuNativeLibraryDirRequired?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "NPU stage probe", value = "probe-only"))
            add(InferenceStatItemUi(label = "NPU constructor available", value = probe.npuConstructorAvailable.toString()))
            add(InferenceStatItemUi(label = "NPU string constructor available", value = probe.npuStringConstructorAvailable.toString()))
            add(InferenceStatItemUi(label = "NPU nativeLibraryDir candidate", value = probe.npuNativeLibraryDirCandidate?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "NPU stage probe result", value = probe.npuStageProbeResult?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "NPU stage probe error", value = probe.npuStageProbeError?.takeIf { it.isNotBlank() } ?: "—"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU SoC", value = listOfNotNull(probe.npuSocManufacturer, probe.npuSocModel).joinToString(" / ").ifBlank { "unknown" }))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU official vendor", value = probe.npuOfficialVendor?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU SoC support", value = probe.npuOfficialSocSupport?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU model requirement", value = probe.npuModelRequirement?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU runtime libs", value = probe.npuRuntimeLibraryRequirement?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU dispatch lib", value = probe.npuDispatchLibraryRequirement?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU CLI proof", value = probe.npuCliProofRequirement?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU nativeLibraryDir", value = probe.npuNativeLibraryDir?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU packaged libs", value = probe.npuPackagedLibraryCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU runtime lib status", value = probe.npuVendorRuntimeLibraryStatus?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU dispatch lib status", value = probe.npuDispatchLibraryStatus?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Lami LiteRT-LM NPU readiness", value = formatLamiNpuReadiness(probe)))
            formatLamiBlockedReason(probe)?.let { add(InferenceStatItemUi(label = "Blocked reason", value = it)) }
            add(InferenceStatItemUi(label = "QNN/NPU要求", value = probe.qnnNpuAttemptRequested?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU試行", value = if (probe.qnnNpuAttempted) "yes" else "no"))
            add(InferenceStatItemUi(label = "Lami runtime QNN availability", value = formatLamiRuntimeQnnAvailability(probe)))
            add(InferenceStatItemUi(label = "QNN/NPU selectedPath", value = probe.qnnNpuSelectedPath?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU fallbackPath", value = probe.qnnNpuFallbackPath?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU stage", value = probe.qnnNpuAttemptStage?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU errorClass", value = probe.qnnNpuAttemptErrorClass?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU errorMessage", value = probe.qnnNpuAttemptErrorMessage?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU evidence", value = probe.qnnNpuAttemptEvidence.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(" / ") ?: "none/unknown"))
            val qnnDetected = probe.qnnDelegateCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ")
            add(InferenceStatItemUi(label = "QNN candidates", value = qnnDetected ?: "none/unknown"))
            add(InferenceStatItemUi(label = "QNN status", value = if (qnnDetected == null) "not-detected" else "candidate-detected"))
            val nnapiDelegateDetected = probe.nnapiDelegateCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ")
            add(InferenceStatItemUi(label = "NNAPI delegate candidates", value = nnapiDelegateDetected ?: "none/unknown"))
            add(InferenceStatItemUi(label = "NNAPI delegate status", value = if (nnapiDelegateDetected == null) "not-detected" else "candidate-detected"))
            val resolvedRequestedPreferredBackend = localTraceForDev?.requestedPreferredBackend ?: preferredBackendDryRunSetting.name
            val resolvedAppliedPreferredBackend = localTraceForDev?.appliedPreferredBackend ?: "not-applied"
            val resolvedPreferredBackendApplyResult = localTraceForDev?.preferredBackendApplyResult ?: when (preferredBackendDryRunSetting) {
                PreferredBackendDryRunSetting.DEFAULT -> "skipped-default"
                else -> "not-supported"
            }
            add(InferenceStatItemUi(label = "Requested preferredBackend", value = resolvedRequestedPreferredBackend))
            add(InferenceStatItemUi(label = "Applied backend", value = formatAppliedBackendDisplay(resolvedAppliedPreferredBackend, resolvedPreferredBackendApplyResult)))
            add(InferenceStatItemUi(label = "PreferredBackend apply result", value = resolvedPreferredBackendApplyResult))
            if (resolvedRequestedPreferredBackend == PreferredBackendDryRunSetting.NPU.name && resolvedAppliedPreferredBackend == "GPU") {
                add(InferenceStatItemUi(label = "Effective backend note", value = "NPU requested but GPU used for stability"))
            }
            add(InferenceStatItemUi(label = "PreferredBackend EngineConfig applied", value = localTraceForDev?.preferredBackendHookReached?.toString() ?: "false"))
            add(InferenceStatItemUi(label = "PreferredBackend hook source", value = localTraceForDev?.preferredBackendHookSource?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend apply error", value = localTraceForDev?.preferredBackendApplyError ?: "—"))
            add(InferenceStatItemUi(label = "PreferredBackend builder class", value = localTraceForDev?.preferredBackendApplyBuilderClass?.ifBlank { "none/unknown" } ?: "none/unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend method candidates", value = localTraceForDev?.preferredBackendApplyMethodCandidates?.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend backend enum candidates", value = localTraceForDev?.preferredBackendApplyBackendEnumCandidates?.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Held engine create path", value = localTraceForDev?.heldEngineCreatePath?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder instance hash", value = localTraceForDev?.holderInstanceHash?.toString() ?: "-1"))
            add(InferenceStatItemUi(label = "Held engine hash", value = localTraceForDev?.heldEngineHash?.toString() ?: "-1"))
            add(InferenceStatItemUi(label = "Holder app foreground", value = localTraceForDev?.holderAppInForeground?.toString() ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder last acquire action", value = localTraceForDev?.holderLastAcquireAction ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder last lifecycle event", value = localTraceForDev?.holderLastLifecycleEventReason ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder last lifecycle decision", value = localTraceForDev?.holderLastLifecycleDecisionAction ?: "unknown"))
            add(InferenceStatItemUi(label = "Held recreate request count", value = localTraceForDev?.heldEngineRecreateRequestCount?.toString() ?: "0"))
            add(InferenceStatItemUi(label = "Held present at run start", value = localTraceForDev?.heldEngineWasPresentAtRunStart?.toString() ?: "false"))
            add(InferenceStatItemUi(label = "Held created during run", value = localTraceForDev?.heldEngineCreatedDuringRun?.toString() ?: "false"))
            add(InferenceStatItemUi(label = "Holder last recreate result", value = localTraceForDev?.holderLastRecreateResult ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder last recreate reason", value = localTraceForDev?.holderLastRecreateReason ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder held before recreate", value = localTraceForDev?.holderHasHeldEngineBeforeRecreate?.toString() ?: "unknown"))
            add(InferenceStatItemUi(label = "Holder held after recreate", value = localTraceForDev?.holderHasHeldEngineAfterRecreate?.toString() ?: "unknown"))
            add(InferenceStatItemUi(label = "Held last create source", value = localTraceForDev?.lastHeldEngineCreateSource ?: "unknown"))
            add(InferenceStatItemUi(label = "Held last create reason", value = localTraceForDev?.lastHeldEngineCreateReason ?: "unknown"))
            add(InferenceStatItemUi(label = "Held last create requested preferredBackend", value = localTraceForDev?.lastHeldEngineCreateRequestedPreferredBackend ?: "unknown"))
            add(InferenceStatItemUi(label = "Held last create elapsed", value = localTraceForDev?.lastHeldEngineCreateAtElapsedMs?.toString() ?: "unknown"))
            add(InferenceStatItemUi(label = "Held last create stack hint", value = localTraceForDev?.lastHeldEngineCreateStackHint ?: "unknown"))
            add(InferenceStatItemUi(label = "LlmInference create method", value = localTraceForDev?.llmInferenceCreateMethod?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Options builder source", value = localTraceForDev?.optionsBuilderSource?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend hook eligible", value = localTraceForDev?.preferredBackendHookEligible?.toString() ?: "false"))
            add(InferenceStatItemUi(label = "PreferredBackend hook missing reason", value = localTraceForDev?.preferredBackendHookMissingReason?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "PreferredBackend EngineConfig request setting", value = preferredBackendDryRunSetting.name))
            val resolverRequestedPreferredBackend = localTraceForDev?.requestedPreferredBackend ?: preferredBackendDryRunSetting.name
            val preferredBackendRecreateRequired = resolvePreferredBackendEngineRecreateDiagnostic(
                trace = localTraceForDev,
                preferredBackendDryRunSetting = preferredBackendDryRunSetting,
            )
            if (preferredBackendRecreateRequired?.first == true) {
                add(InferenceStatItemUi(label = "PreferredBackend requires engine recreate", value = "true"))
                preferredBackendRecreateRequired.second?.let {
                    add(InferenceStatItemUi(label = "PreferredBackend recreate reason", value = it.ifBlank { "unknown" }))
                }
            }
            localTraceForDev?.preferredBackendApplyNotSupportedReason?.takeIf { it.isNotBlank() }?.let {
                add(InferenceStatItemUi(label = "PreferredBackend not-supported reason", value = it))
            }
            add(InferenceStatItemUi(label = "Delegate class candidates", value = probe.delegateClassCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            probe.delegateBackendEnumProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate backend enum probe error", value = it)) }
            probe.delegatePreferredBackendSignatureProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate preferredBackend signature error", value = it)) }
            probe.delegateProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Delegate Probe Error", value = it)) }
            probe.npuProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "NPU probe error", value = it)) }
            probe.backendNpuProbeError?.takeIf { it.isNotBlank() }?.let { add(InferenceStatItemUi(label = "Backend NPU probe error", value = it)) }
            add(InferenceStatItemUi(label = "実行経路推定", value = "${executionInference.target} / ${executionInference.confidence}"))
            val executionReason = preferredBackendRecreateRequired?.second?.let { recreateReason ->
                "${executionInference.reason}; ${recreateReason}"
            } ?: executionInference.reason
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
        executionInference = executionInference,
        preferredBackendDryRunSetting = preferredBackendDryRunSetting,
        acceleratorProbeSnapshot = acceleratorProbeSnapshot,
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
        acceleratorProbeSnapshot
            ?.takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER && hasExternalQairtDiagnostics(it) }
            ?.let { probe ->
                InferenceStatsSectionUi(
                    title = "DEV診断: External QAIRT",
                    items = buildList {
                        add(InferenceStatItemUi(label = "External QAIRT stage", value = formatExternalQairtStageStatus(probe.externalQairtStageStatus)))
                        add(InferenceStatItemUi(label = "qnn-net-run", value = probe.externalQairtQnnNetRunStatus))
                        add(InferenceStatItemUi(label = "qnn-platform-validator", value = probe.externalQairtQnnPlatformValidatorStatus))
                        add(InferenceStatItemUi(label = "QNN SDK version", value = probe.externalQairtQnnSdkVersion))
                        add(InferenceStatItemUi(label = "External QNN GPU", value = formatExternalQairtPassStatus(probe.externalQairtGpuBackendStatus)))
                        add(InferenceStatItemUi(label = "QNN DSP core", value = probe.externalQairtDspCore))
                        add(InferenceStatItemUi(label = "External QNN DSP/HTP", value = formatExternalQairtPassStatus(probe.externalQairtDspBackendStatus)))
                        add(InferenceStatItemUi(label = "QAIRT stage path", value = probe.externalQairtStagePath))
                        probe.externalQairtNote?.takeIf { it.isNotBlank() }?.let {
                            add(InferenceStatItemUi(label = "QAIRT stage note", value = it))
                        }
                    },
                )
            },
        acceleratorProbeSnapshot
            ?.takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER }
            ?.let { probe ->
                InferenceStatsSectionUi(
                    title = "DEV診断: LiteRT-LM NPU Readiness",
                    items = buildLiteRtLmNpuReadinessItems(
                        probe = probe,
                        selectedModel = resolveLiteRtLmReadinessSelectedModelName(stats, localTraceForDev),
                    ),
                )
            },
        acceleratorProbeSnapshot
            ?.takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER && hasQnnDelegateProbeDiagnostics(it) }
            ?.let { probe ->
                InferenceStatsSectionUi(
                    title = "DEV診断: QNN Probe",
                    items = buildQnnDelegateProbeItems(probe),
                )
            },
        localTraceForDev?.localFailureDiagnosticsText
            ?.takeIf { displayMode == InferenceStatsDisplayMode.DEVELOPER && it.isNotBlank() }
            ?.let { diagnostics ->
                InferenceStatsSectionUi(
                    title = "DEV診断: Qualcomm Model Failure",
                    items = listOf(
                        InferenceStatItemUi(
                            label = "Qualcomm model failure diagnostics",
                            value = diagnostics,
                        ),
                    ),
                )
            },
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
                localTraceForDev?.let { trace ->
                    add(InferenceStatItemUi(label = "streamedCharsPerSecond", value = formatCharsPerSecond(trace.streamedCharsPerSecond)))
                    add(InferenceStatItemUi(label = "appendBatchSizeAvg", value = formatChars(trace.appendBatchSizeAvg)))
                    add(InferenceStatItemUi(label = "appendEventsPerSecond", value = formatEventsPerSecond(trace.appendEventsPerSecond)))
                    add(InferenceStatItemUi(label = "officialChunkCount", value = trace.officialChunkCount.toString()))
                    add(InferenceStatItemUi(label = "officialChunkIntervalAvgMs", value = formatMillis(trace.officialChunkIntervalAvgMs)))
                    add(InferenceStatItemUi(label = "officialChunkIntervalMaxMs", value = formatMillis(trace.officialChunkIntervalMaxMs)))
                    add(InferenceStatItemUi(label = "officialChunkIntervalMinMs", value = formatMillis(trace.officialChunkIntervalMinMs)))
                    add(InferenceStatItemUi(label = "officialChunkFirstToLastMs", value = formatMillis(trace.officialChunkFirstToLastMs)))
                    add(InferenceStatItemUi(label = "officialChunkCharsAvg", value = formatChars(trace.officialChunkCharsAvg)))
                    add(InferenceStatItemUi(label = "officialChunkCharsMax", value = trace.officialChunkCharsMax?.let { "$it chars" } ?: "—"))
                    add(InferenceStatItemUi(label = "officialChunkCharsMin", value = trace.officialChunkCharsMin?.let { "$it chars" } ?: "—"))
                    add(InferenceStatItemUi(label = "officialChunkEventsPerSecond", value = formatEventsPerSecond(trace.officialChunkEventsPerSecond)))
                    add(InferenceStatItemUi(label = "officialChunkCharsPerSecond", value = formatCharsPerSecond(trace.officialChunkCharsPerSecond)))
                    add(InferenceStatItemUi(label = "officialChunkEmptyCount", value = trace.officialChunkEmptyCount.toString()))
                    add(InferenceStatItemUi(label = "officialChunkNonEmptyCount", value = trace.officialChunkNonEmptyCount.toString()))
                    add(InferenceStatItemUi(label = "Streaming bottleneck hint", value = resolveStreamingBottleneckHint(trace)))
                    add(InferenceStatItemUi(label = "composeRecomposeEstimate", value = trace.composeRecomposeEstimate?.toString() ?: "—"))
                    add(InferenceStatItemUi(label = "markdownRepairCount", value = trace.markdownRepairCount?.toString() ?: "—"))
                    add(InferenceStatItemUi(label = "uiAppendDebounceMs", value = trace.uiAppendDebounceMs?.let { "${it} ms" } ?: "—"))
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

private fun formatCharsPerSecond(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f chars/s", it) } ?: "—"

private fun formatEventsPerSecond(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f events/s", it) } ?: "—"

private fun formatChars(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f chars", it) } ?: "—"

private fun formatMillis(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f ms", it) } ?: "—"

private fun formatMillis(value: Long?): String =
    value?.takeIf { it >= 0L }?.let { "$it ms" } ?: "—"

private fun resolveStreamingBottleneckHint(trace: LocalInferenceTrace): String {
    val officialIntervalMaxMs = trace.officialChunkIntervalMaxMs ?: 0L
    val officialChunkEventsPerSecond = trace.officialChunkEventsPerSecond ?: 0.0
    val appendEventsPerSecond = trace.appendEventsPerSecond ?: 0.0
    val markdownRepairCount = trace.markdownRepairCount ?: 0
    val composeRecomposeEstimate = trace.composeRecomposeEstimate ?: 0

    return when {
        trace.officialChunkCount > 0 &&
            trace.officialChunkCount <= trace.assistantUpdateCount &&
            officialIntervalMaxMs >= 750L -> "official-chunk-sparse"
        appendEventsPerSecond > 0.0 &&
            officialChunkEventsPerSecond >= appendEventsPerSecond * 2.0 &&
            appendEventsPerSecond < 8.0 -> "ui-append-sparse"
        markdownRepairCount > 0 -> "markdown-repair-heavy"
        composeRecomposeEstimate >= 120 -> "compose-recompose-heavy"
        else -> "unknown"
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
    executionInference: ExecutionTargetInference,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
    acceleratorProbeSnapshot: AcceleratorProbeSnapshot?,
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
    val requestedPreferredBackend = trace?.requestedPreferredBackend ?: preferredBackendDryRunSetting.name
    val appliedPreferredBackend = trace?.appliedPreferredBackend ?: "not-applied"
    val preferredBackendApplyResult = trace?.preferredBackendApplyResult ?: when (preferredBackendDryRunSetting) {
        PreferredBackendDryRunSetting.DEFAULT -> "skipped-default"
        else -> "not-supported-or-not-reached"
    }
    val items = buildList {
        add(InferenceStatItemUi(label = "実行経路", value = resolveDevSummaryExecutionPath(stats, trace)))
        add(InferenceStatItemUi(label = "使用モデル", value = resolveDevSummaryModelName(stats, trace)))
        add(InferenceStatItemUi(label = "モデル解決", value = resolveDevSummaryModelResolution(stats, trace)))
        add(InferenceStatItemUi(label = "held engine再利用", value = devDiagnosticsUiModel.heldEngineReuseSummary))
        add(InferenceStatItemUi(label = "held engine状態", value = devDiagnosticsUiModel.heldEngineStateSummary))
        add(InferenceStatItemUi(label = "推定実行先", value = "${executionInference.target} / ${executionInference.confidence}"))
        add(InferenceStatItemUi(label = "Requested preferredBackend", value = requestedPreferredBackend))
        add(InferenceStatItemUi(label = "Applied backend", value = formatAppliedBackendDisplay(appliedPreferredBackend, preferredBackendApplyResult)))
        add(InferenceStatItemUi(label = "PreferredBackend apply result", value = preferredBackendApplyResult))
        add(InferenceStatItemUi(label = "PreferredBackend hook", value = trace?.preferredBackendHookReached?.toString() ?: "false"))
        add(InferenceStatItemUi(label = "PreferredBackend hook source", value = trace?.preferredBackendHookSource?.ifBlank { "unknown" } ?: "unknown"))
        trace?.let {
            add(InferenceStatItemUi(label = "Streaming bottleneck hint", value = resolveStreamingBottleneckHint(it)))
        }
        trace?.preferredBackendHookMissingReason
            ?.takeIf { trace.preferredBackendHookReached != true && it.isNotBlank() }
            ?.let {
            add(InferenceStatItemUi(label = "PreferredBackend hook missing", value = it))
        }
        trace?.preferredBackendApplyError?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "PreferredBackend apply error", value = it))
        }
        acceleratorProbeSnapshot?.let { probe ->
            add(InferenceStatItemUi(label = "QNN/NPU要求", value = probe.qnnNpuAttemptRequested?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU試行", value = if (probe.qnnNpuAttempted) "yes" else "no"))
            add(InferenceStatItemUi(label = "Lami runtime QNN availability", value = formatLamiRuntimeQnnAvailability(probe)))
            add(InferenceStatItemUi(label = "QNN/NPU selectedPath", value = probe.qnnNpuSelectedPath?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU fallbackPath", value = probe.qnnNpuFallbackPath?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU stage", value = probe.qnnNpuAttemptStage?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "QNN/NPU errorClass", value = probe.qnnNpuAttemptErrorClass?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU errorMessage", value = probe.qnnNpuAttemptErrorMessage?.ifBlank { "—" } ?: "—"))
            add(InferenceStatItemUi(label = "QNN/NPU evidence", value = probe.qnnNpuAttemptEvidence.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(" / ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU runtime lib status", value = probe.npuVendorRuntimeLibraryStatus?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "LiteRT-LM NPU dispatch lib status", value = probe.npuDispatchLibraryStatus?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Lami LiteRT-LM NPU readiness", value = formatLamiNpuReadiness(probe)))
            formatLamiBlockedReason(probe)?.let { add(InferenceStatItemUi(label = "Blocked reason", value = it)) }
            add(InferenceStatItemUi(label = "Backend NPU probe hint", value = probe.backendNpuProbeHint?.ifBlank { "unknown" } ?: "unknown"))
            add(InferenceStatItemUi(label = "Backend NPU class candidates", value = probe.backendNpuClassCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
            add(InferenceStatItemUi(label = "Backend NPU method candidates", value = probe.backendNpuMethodCandidates.takeIf { it.isNotEmpty() }?.take(10)?.joinToString(", ") ?: "none/unknown"))
        }
        add(InferenceStatItemUi(label = "close結果", value = devDiagnosticsUiModel.closeStatusSummary))
        add(InferenceStatItemUi(label = "Tokenizer再計数", value = resolveDevSummaryTokenizerRecountStatus(trace)))
        add(InferenceStatItemUi(label = "MediaPipe tokenizer", value = resolveDevSummaryMediaPipeTokenizerStatus(trace)))
        add(InferenceStatItemUi(label = "失敗要約", value = devDiagnosticsUiModel.failureSummary))
    }
    if (items.all { it.value == "—" || it.value == "不明" }) return null
    return InferenceStatsSectionUi(
        title = "DEV診断サマリー",
        items = items,
    )
}

private fun resolvePreferredBackendEngineRecreateDiagnostic(
    trace: LocalInferenceTrace?,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
): Pair<Boolean, String?>? {
    if (trace == null) return null
    if (trace.preferredBackendRequiresEngineRecreate == true) {
        return true to trace.preferredBackendEngineRecreateReason
    }
    val requested = trace.requestedPreferredBackend ?: preferredBackendDryRunSetting.name
    val requiresRecreate = requested != PreferredBackendDryRunSetting.DEFAULT.name &&
        trace.heldEngineCreatePath == "holder-existing-engine" &&
        trace.preferredBackendHookReached != true &&
        trace.preferredBackendHookMissingReason == "holder-existing-engine"
    if (!requiresRecreate) {
        return false to null
    }
    return true to "requested preferredBackend requires a new held engine; current run reused existing engine"
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

private fun hasExternalQairtDiagnostics(probe: AcceleratorProbeSnapshot): Boolean {
    return listOf(
        probe.externalQairtStageStatus,
        probe.externalQairtQnnNetRunStatus,
        probe.externalQairtQnnPlatformValidatorStatus,
        probe.externalQairtQnnSdkVersion,
        probe.externalQairtGpuBackendStatus,
        probe.externalQairtDspCore,
        probe.externalQairtDspBackendStatus,
        probe.externalQairtStagePath,
        probe.externalQairtNote,
    ).any { !it.isNullOrBlank() && it != "unknown" }
}

private fun hasQnnDelegateProbeDiagnostics(probe: AcceleratorProbeSnapshot): Boolean {
    return probe.qnnDelegateProbeIsSm8750Likely != null ||
        probe.qnnDelegateProbeClassFound != null ||
        probe.qnnDelegateProbeCreated != null ||
        probe.qnnDelegateProbeHtpBackendRequested != null ||
        probe.qnnDelegateProbeSocHints.isNotEmpty() ||
        !probe.qnnDelegateProbeNativeLibraryDir.isNullOrBlank() ||
        !probe.qnnDelegateProbeErrorClass.isNullOrBlank() ||
        !probe.qnnDelegateProbeErrorMessage.isNullOrBlank()
}

private fun buildQnnDelegateProbeItems(probe: AcceleratorProbeSnapshot): List<InferenceStatItemUi> {
    val errorText = listOfNotNull(
        probe.qnnDelegateProbeErrorClass?.takeIf { it.isNotBlank() },
        probe.qnnDelegateProbeErrorMessage?.takeIf { it.isNotBlank() },
    ).joinToString(": ").ifBlank { "—" }
    return listOf(
        InferenceStatItemUi(
            label = "SoC判定",
            value = if (probe.qnnDelegateProbeIsSm8750Likely == true) "SM8750 likely" else "unknown",
        ),
        InferenceStatItemUi(
            label = "SoC hints",
            value = probe.qnnDelegateProbeSocHints.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "none/unknown",
        ),
        InferenceStatItemUi(
            label = "QNN class",
            value = when (probe.qnnDelegateProbeClassFound) {
                true -> "found"
                false -> "not found"
                null -> "unknown"
            },
        ),
        InferenceStatItemUi(
            label = "HTP backend",
            value = when (probe.qnnDelegateProbeHtpBackendRequested) {
                true -> "requested"
                false -> "not requested"
                null -> "unknown"
            },
        ),
        InferenceStatItemUi(
            label = "QNN delegate",
            value = when (probe.qnnDelegateProbeCreated) {
                true -> "created"
                false -> "failed"
                null -> "unknown"
            },
        ),
        InferenceStatItemUi(
            label = "nativeLibraryDir",
            value = probe.qnnDelegateProbeNativeLibraryDir?.ifBlank { "unknown" } ?: "unknown",
        ),
        InferenceStatItemUi(
            label = "QNN class probe note",
            value = if (probe.qnnDelegateProbeClassFound == false) {
                "TFLite QnnDelegate class was not found. LiteRT-LM NPU path is checked separately via Backend.NPU."
            } else {
                "TFLite QnnDelegate class probe is separate from LiteRT-LM Backend.NPU readiness."
            },
        ),
        InferenceStatItemUi(label = "error", value = errorText),
    )
}

private fun buildLiteRtLmNpuReadinessItems(
    probe: AcceleratorProbeSnapshot,
    selectedModel: String,
): List<InferenceStatItemUi> {
    val modelKind = classifyLiteRtLmModelKind(selectedModel)
    val modelCompatibilityHint = classifyLiteRtLmModelNpuCompatibilityHint(selectedModel)
    val modelNpuBlocker = formatLiteRtLmModelNpuBlocker(modelCompatibilityHint, selectedModel)
    val dispatchStatus = formatDispatchApiStatus(probe.npuDispatchLibraryStatus, probe.npuDispatchApiCandidates)
    val readiness = computeLiteRtLmNpuReadiness(
        probe = probe,
        dispatchStatus = dispatchStatus,
        modelCompatibilityHint = modelCompatibilityHint,
    )
    return listOf(
        InferenceStatItemUi(label = "selected model", value = selectedModel.ifBlank { "unknown" }),
        InferenceStatItemUi(label = "model kind", value = modelKind),
        InferenceStatItemUi(label = "model npu compatibility hint", value = modelCompatibilityHint),
        InferenceStatItemUi(label = "model npu blocker", value = modelNpuBlocker ?: "none"),
        InferenceStatItemUi(label = "model requirement", value = formatLiteRtLmModelRequirement(modelKind)),
        InferenceStatItemUi(label = "SoC", value = listOfNotNull(probe.npuSocManufacturer, probe.npuSocModel).joinToString(" / ").ifBlank { "unknown" }),
        InferenceStatItemUi(label = "External QNN DSP core", value = probe.externalQairtDspCore.ifBlank { "unknown" }),
        InferenceStatItemUi(label = "Backend.NPU available", value = (probe.backendNpuClassCandidates.isNotEmpty() || probe.npuConstructorAvailable).toString()),
        InferenceStatItemUi(label = "Backend.NPU(String)", value = if (probe.npuStringConstructorAvailable) "available" else "missing"),
        InferenceStatItemUi(label = "Backend.NPU(String) available", value = probe.npuStringConstructorAvailable.toString()),
        InferenceStatItemUi(label = "nativeLibraryDir", value = probe.npuNativeLibraryDir?.ifBlank { "unknown" } ?: "unknown"),
        InferenceStatItemUi(label = "nativeLibraryDir exists", value = probe.npuNativeLibraryDirExists?.toString() ?: "unknown"),
        InferenceStatItemUi(label = "nativeLibraryDir status", value = formatNativeLibraryDirStatus(probe)),
        InferenceStatItemUi(label = "dispatch API candidates", value = probe.npuDispatchApiCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"),
        InferenceStatItemUi(label = "dispatch API exact match", value = probe.npuDispatchApiExactMatch?.toString() ?: "unknown"),
        InferenceStatItemUi(label = "dispatch API status", value = dispatchStatus),
        InferenceStatItemUi(label = "dispatch API selected candidate", value = probe.npuDispatchApiSelectedCandidate?.ifBlank { "none" } ?: "none"),
        InferenceStatItemUi(label = "dispatch API search dir", value = probe.npuDispatchApiSearchDir?.ifBlank { "unknown" } ?: "unknown"),
        InferenceStatItemUi(label = "dispatch API search error", value = probe.npuDispatchApiSearchError?.ifBlank { "none" } ?: "none"),
        InferenceStatItemUi(label = "dispatch API .so", value = if (dispatchStatus.startsWith("found-")) "found" else dispatchStatus),
        InferenceStatItemUi(label = "QNN runtime libs", value = if (probe.npuQnnRuntimeCandidates.isNotEmpty()) "found" else "missing"),
        InferenceStatItemUi(label = "QNN runtime candidates", value = probe.npuQnnRuntimeCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"),
        InferenceStatItemUi(label = "HTP skel/stub", value = if (probe.npuHtpSkelStubCandidates.isNotEmpty()) "found" else "missing"),
        InferenceStatItemUi(label = "HTP V79 skel/stub", value = if (probe.npuV79SkelStubCandidates.isNotEmpty()) "found" else "missing"),
        InferenceStatItemUi(label = "HTP skel/stub candidates", value = probe.npuHtpSkelStubCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"),
        InferenceStatItemUi(label = "V79 skel/stub candidates", value = probe.npuV79SkelStubCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"),
        InferenceStatItemUi(label = "readiness", value = readiness),
        InferenceStatItemUi(label = "selected path", value = formatLiteRtLmNpuSelectedPath(readiness)),
        InferenceStatItemUi(label = "NPU apply status", value = "disabled / blocked"),
        InferenceStatItemUi(label = "next action", value = formatLiteRtLmNpuNextAction(readiness)),
    )
}

private fun classifyLiteRtLmModelKind(selectedModel: String): String {
    val lower = selectedModel.substringAfterLast('/').lowercase(Locale.ROOT)
    return when {
        "qualcomm" in lower && "sm8750" in lower -> "qualcomm-sm8750-litertlm"
        listOf("qualcomm", "qcs", "qnn", "htp").any { it in lower } -> "qualcomm-litertlm"
        "litertlm" in lower -> "generic-litertlm"
        else -> "unknown"
    }
}

private fun classifyLiteRtLmModelNpuCompatibilityHint(selectedModel: String): String {
    val lower = selectedModel.substringAfterLast('/').lowercase(Locale.ROOT)
    return when {
        listOf("qualcomm", "sm8750", "qcs", "qnn", "htp").any { it in lower } -> "qualcomm-soc-specific-candidate"
        "litertlm" in lower -> "generic-gpu-compatible"
        else -> "unknown"
    }
}

private fun formatLiteRtLmModelNpuBlocker(
    modelCompatibilityHint: String,
    selectedModel: String,
): String? {
    if (modelCompatibilityHint == "qualcomm-soc-specific-candidate") return null
    val lower = selectedModel.substringAfterLast('/').lowercase(Locale.ROOT)
    return if ("sm8750" in lower || selectedModel.isBlank()) {
        "requires-soc-specific-qualcomm-litertlm-for-sm8750"
    } else {
        "requires-soc-specific-qualcomm-litertlm"
    }
}

private fun formatLiteRtLmModelRequirement(modelKind: String): String {
    return when (modelKind) {
        "qualcomm-sm8750-litertlm" -> "requires-soc-specific-qualcomm-litertlm-for-sm8750"
        "qualcomm-litertlm" -> "requires-soc-specific-qualcomm-litertlm"
        "generic-litertlm" -> "generic-litertlm-gpu-compatible"
        else -> "unknown"
    }
}

private fun formatDispatchApiStatus(
    dispatchLibraryStatus: String?,
    dispatchApiCandidates: List<String>,
): String {
    return when {
        dispatchLibraryStatus == "found-exact-libLiteRtDispatch_Qualcomm-so" -> dispatchLibraryStatus
        dispatchLibraryStatus == "found-dispatch-candidate" -> dispatchLibraryStatus
        dispatchApiCandidates.any { it.equals("libLiteRtDispatch_Qualcomm.so", ignoreCase = true) } -> "found-exact-libLiteRtDispatch_Qualcomm-so"
        dispatchApiCandidates.isNotEmpty() -> "found-dispatch-candidate"
        dispatchLibraryStatus == "missing-dispatch-api-so-candidate" -> "missing"
        dispatchLibraryStatus == "candidate-detected" -> "found-dispatch-candidate"
        dispatchLibraryStatus == "native-library-dir-missing" -> dispatchLibraryStatus
        dispatchLibraryStatus == "native-library-dir-empty" -> dispatchLibraryStatus
        dispatchLibraryStatus == "missing" -> dispatchLibraryStatus
        dispatchLibraryStatus == "unknown-error" -> dispatchLibraryStatus
        dispatchLibraryStatus.isNullOrBlank() -> "unknown"
        dispatchLibraryStatus.startsWith("error-") -> "unknown"
        else -> dispatchLibraryStatus
    }
}

private fun computeLiteRtLmNpuReadiness(
    probe: AcceleratorProbeSnapshot,
    dispatchStatus: String,
    modelCompatibilityHint: String,
): String {
    val runtimeReady = probe.npuQnnRuntimeCandidates.isNotEmpty() ||
        probe.npuVendorRuntimeLibraryStatus?.startsWith("candidate-detected") == true
    val htpSkelStubReady = probe.npuHtpSkelStubCandidates.isNotEmpty()
    return when {
        !probe.npuStringConstructorAvailable -> "blocked-backend-npu-string-missing"
        probe.npuNativeLibraryDir.isNullOrBlank() || probe.npuNativeLibraryDirExists == false -> "blocked-native-library-dir-missing"
        !runtimeReady -> "blocked-qnn-runtime-missing"
        !htpSkelStubReady -> "blocked-htp-skel-stub-missing"
        dispatchStatus == "missing" || dispatchStatus == "native-library-dir-empty" -> "blocked-dispatch-api-so-missing"
        dispatchStatus == "native-library-dir-missing" -> "blocked-native-library-dir-missing"
        dispatchStatus == "unknown-error" -> "npu-unknown"
        modelCompatibilityHint != "qualcomm-soc-specific-candidate" -> "blocked-requires-soc-specific-qualcomm-litertlm"
        dispatchStatus.startsWith("found-") -> "ready-but-disabled-cli-proof-required"
        else -> "npu-unknown"
    }
}

private fun formatLiteRtLmNpuSelectedPath(readiness: String): String {
    return when (readiness) {
        "ready-for-manual-npu-enable" -> "npu-probe-only"
        "ready-but-disabled-cli-proof-required" -> "gpu"
        else -> if (readiness.startsWith("blocked-")) "gpu" else "blocked"
    }
}

private fun formatNativeLibraryDirStatus(probe: AcceleratorProbeSnapshot): String {
    return when {
        probe.npuNativeLibraryDir.isNullOrBlank() -> "missing"
        probe.npuNativeLibraryDirExists == true -> "exists"
        probe.npuNativeLibraryDirExists == false -> "missing"
        else -> "unknown"
    }
}

private fun formatLiteRtLmNpuNextAction(readiness: String): String {
    return when (readiness) {
        "blocked-dispatch-api-so-missing" ->
            "add compatible Qualcomm LiteRT dispatch API .so and verify with litert_lm_main --backend=npu before enabling app NPU"
        "ready-but-disabled-cli-proof-required" ->
            "verify with litert_lm_main --backend=npu before enabling app NPU"
        "ready-for-manual-npu-enable" ->
            "manual NPU enable can be considered after explicit app-side guard review"
        else ->
            "keep GPU fallback; resolve listed NPU prerequisite before any Backend.NPU app enable"
    }
}

private fun resolveLiteRtLmReadinessSelectedModelName(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    return trace?.mediaPipeProbeModelPath?.trim()?.takeIf { it.isNotBlank() }
        ?: stats.modelName?.trim()?.takeIf { it.isNotBlank() }
        ?: trace?.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
        ?: "—"
}

private fun formatExternalQairtStageStatus(status: String?): String {
    return when (status?.trim()) {
        null, "" -> "unknown"
        "present", "available", "passed" -> "passed"
        else -> status
    }
}

private fun formatExternalQairtPassStatus(status: String?): String {
    return when (status?.trim()) {
        null, "" -> "unknown"
        "available", "present", "passed" -> "passed"
        else -> status
    }
}

private fun formatAppliedBackendDisplay(appliedBackend: String, preferredBackendApplyResult: String): String {
    return if (appliedBackend == "GPU" && preferredBackendApplyResult.startsWith("fallback-gpu")) {
        "GPU fallback"
    } else {
        appliedBackend
    }
}

private fun formatLamiRuntimeQnnAvailability(probe: AcceleratorProbeSnapshot): String {
    return when (probe.qnnNpuAvailable?.trim()) {
        null, "" -> "unknown"
        "unsupported" -> if (formatLamiBlockedReason(probe) != null) "blocked" else "unsupported"
        else -> probe.qnnNpuAvailable
    }
}

private fun formatLamiNpuReadiness(probe: AcceleratorProbeSnapshot): String {
    val summary = probe.npuReadinessSummary?.trim()
    if (!summary.isNullOrEmpty() && !summary.startsWith("missing=")) {
        return summary
    }
    return if (formatLamiBlockedReason(probe) != null) "blocked" else summary ?: "unknown"
}

private fun formatLamiBlockedReason(probe: AcceleratorProbeSnapshot): String? {
    val reasons = linkedSetOf<String>()
    listOfNotNull(
        probe.qnnNpuAttemptErrorMessage,
        probe.npuReadinessSummary,
        probe.npuVendorRuntimeLibraryStatus,
        probe.npuDispatchLibraryStatus,
    ).forEach { source ->
        if ("qnn-runtime-libs" in source || source.startsWith("missing:libQnn")) {
            reasons += "app-packaged QNN runtime libs missing"
        }
        if ("dispatch-api-so" in source ||
            "missing-dispatch-api-so-candidate" in source ||
            source == "missing" ||
            source == "native-library-dir-empty" ||
            "blocked-dispatch-api-so-missing" in source ||
            "missing:libLiteRtDispatch.so" in source
        ) {
            reasons += "dispatch API .so missing"
        }
        if ("backend-npu-api" in source) {
            reasons += "Backend.NPU API missing"
        }
        if ("vendor-fastrpc-namespace-blocked" in source || "npu-disabled" in source) {
            reasons += "vendor FastRPC namespace blocked; GPU recommended"
        }
    }
    return reasons.takeIf { it.isNotEmpty() }?.joinToString(", ")
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
    requestedPreferredBackend: String?,
    appliedPreferredBackend: String?,
    preferredBackendApplyResult: String?,
    gpuRenderer: String?,
    nnapiAvailable: Boolean,
    nnapiDevices: List<String>,
    androidSdk: Int?,
    delegateSwitchingSupportedHint: String?,
    qnnNpuAttempted: Boolean,
    qnnNpuAvailable: String?,
    qnnNpuSelectedPath: String?,
    qnnNpuFallbackPath: String?,
    npuReadinessSummary: String?,
): ExecutionTargetInference {
    val requested = requestedPreferredBackend?.trim()?.uppercase(Locale.ROOT).orEmpty()
    val applied = appliedPreferredBackend?.trim()?.uppercase(Locale.ROOT).orEmpty()
    val applyResult = preferredBackendApplyResult?.trim().orEmpty()
    val applyResultLower = applyResult.lowercase(Locale.ROOT)
    val requestedNpuLike = requested == "NPU" ||
        requested == "QUALCOMM_QNN_NPU" ||
        requested.contains("QNN") ||
        requested.contains("NPU")
    val qnnSelected = qnnNpuSelectedPath?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val qnnFallback = qnnNpuFallbackPath?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val qnnAvailable = qnnNpuAvailable?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val readiness = npuReadinessSummary?.trim()?.takeIf { it.isNotBlank() }

    if (applied == "NPU") {
        val confidence = if (
            qnnNpuAttempted ||
            qnnSelected.contains("npu") ||
            qnnAvailable == "available"
        ) {
            "high"
        } else {
            "medium"
        }
        val reason = buildString {
            append("preferredBackend applied NPU")
            if (applyResult.isNotBlank()) append("; applyResult=$applyResult")
            readiness?.let { append("; readiness=$it") }
        }
        return ExecutionTargetInference("qnn-npu-likely", confidence, reason)
    }

    if ((requestedNpuLike && applied == "GPU") || applyResultLower.startsWith("fallback-gpu")) {
        val reason = buildString {
            append("NPU/QNN requested but GPU applied")
            if (applyResult.isNotBlank()) append("; applyResult=$applyResult")
            if (qnnFallback.isNotBlank()) append("; qnnFallback=$qnnFallback")
        }
        return ExecutionTargetInference("gpu-fallback", "high", reason)
    }

    if (applied == "GPU") {
        val rendererNote = gpuRenderer?.takeIf { it.isNotBlank() }?.let { "; renderer=$it" }.orEmpty()
        return ExecutionTargetInference("gpu-likely", "medium", "preferredBackend applied GPU$rendererNote")
    }

    if (applied == "CPU") {
        return ExecutionTargetInference("cpu-likely", "medium", "preferredBackend applied CPU")
    }

    if (requestedNpuLike && applied == "NOT-APPLIED") {
        val reason = buildString {
            append("NPU/QNN requested but no backend was applied")
            if (applyResult.isNotBlank()) append("; applyResult=$applyResult")
            readiness?.let { append("; readiness=$it") }
        }
        return ExecutionTargetInference("unknown", "low", reason)
    }

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
