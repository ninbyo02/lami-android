package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.Locale

internal data class LocalRouteDiagnosticContext(
    val selectedModelName: String,
    val selectedModelFile: String,
    val selectedModelPath: String = selectedModelFile,
    val selectedModelSlot: String = "unknown",
    val npuPreviewModelConfigured: Boolean = false,
    val genericFallbackModelConfigured: Boolean = false,
    val preferredBackend: String,
    val npuStandardRouteMode: String,
    val effectiveNpuStandardRouteMode: String,
    val shouldEnterNpuS1: Boolean,
    val localRouteEntered: Boolean,
    val normalChatNativeRouteBlocked: Boolean,
    val blockedReason: String,
    val modelKind: String,
    val baselineRole: String,
    val genericModelCpuBaseline: Boolean,
    val nativeLibraryDir: String? = null,
)

internal data class LocalRouteDiagnosticFlags(
    val heldEngineExists: Boolean? = null,
    val heldEngineReused: Boolean? = null,
    val engineCreateStarted: Boolean? = null,
    val engineCreateFinished: Boolean? = null,
    val engineCreateDurationMs: Long? = null,
    val conversationCreateStarted: Boolean? = null,
    val conversationCreateFinished: Boolean? = null,
    val generateStarted: Boolean? = null,
    val generateStartedElapsedMs: Long? = null,
    val firstTokenReceived: Boolean? = null,
    val firstTokenElapsedMs: Long? = null,
    val failureStage: String? = null,
    val fallbackUsed: Boolean? = null,
    val staleCallbackIgnored: Boolean? = null,
    val engineConfigBuildStarted: Boolean? = null,
    val engineConfigBuildFinished: Boolean? = null,
    val engineInitializeStarted: Boolean? = null,
    val engineInitializeFinished: Boolean? = null,
    val gpuConfigDiagnostics: GpuRouteConfigDiagnostics? = null,
    val gpuPrefillProbeDiagnostics: Map<String, String> = emptyMap(),
    val cpuRouteDiagnostics: Map<String, String> = emptyMap(),
    val holderCreated: Boolean? = null,
    val holderAcquired: Boolean? = null,
    val holderReused: Boolean? = null,
    val holderInvalidated: Boolean? = null,
    val holderClosed: Boolean? = null,
    val holderTimeoutCleanup: Boolean? = null,
    val holderFailureCleanup: Boolean? = null,
    val holderProcessRestart: Boolean? = null,
    val heldEngineLifecycleHistory: String? = null,
    val heldEngineDestroyReason: String? = null,
    val heldEngineLastOwner: String? = null,
    val heldEngineLastFailureStage: String? = null,
    val heldEngineSnapshotBeforeDestroy: String? = null,
    val gpuGenerateProbeMode: String? = null,
    val gpuGenerateCallEntered: Boolean? = null,
    val gpuGenerateCallReturned: Boolean? = null,
    val gpuCallbackInvokedCount: Int? = null,
    val gpuCallbackFirstInvokedAtElapsedMs: Long? = null,
    val gpuCallbackLastInvokedAtElapsedMs: Long? = null,
    val gpuCallbackThreadName: String? = null,
    val gpuCallbackDoneTrueSeen: Boolean? = null,
    val gpuCallbackErrorSeen: Boolean? = null,
    val gpuCallbackEmptyTextCount: Int? = null,
    val gpuCallbackNonEmptyTextCount: Int? = null,
    val gpuCallbackLastTextLength: Int? = null,
    val gpuCallbackLastTextHead: String? = null,
    val gpuFirstNonEmptyTextElapsedMs: Long? = null,
    val gpuFirstTokenClassificationReason: String? = null,
    val gpuCallbackExceptionClass: String? = null,
    val gpuCallbackExceptionMessage: String? = null,
    val gpuCallbackExceptionChain: String? = null,
    val gpuCallbackExceptionStage: String? = null,
    val gpuGenerateStallInterpretation: String? = null,
    val gpuGeneratePrompt: String? = null,
    val gpuGeneratePromptLengthChars: Int? = null,
    val gpuGenerateInputTokenEstimate: String? = null,
    val gpuGenerateExceptionSeen: Boolean? = null,
    val gpuGenerateExceptionClass: String? = null,
    val gpuGenerateExceptionMessageRaw: String? = null,
    val gpuGenerateExceptionMessageSanitized: String? = null,
    val gpuGenerateExceptionStatusCode: String? = null,
    val gpuGenerateExceptionErrorFile: String? = null,
    val gpuGenerateExceptionErrorLine: String? = null,
    val gpuGenerateExceptionSummary: String? = null,
    val gpuGenerateFailedBeforeFirstToken: Boolean? = null,
    val gpuWatchdogBypassedDueToGenerateException: Boolean? = null,
    val liteRtLmErrorKind: String? = null,
    val liteRtLmErrorStatusCode: String? = null,
    val liteRtLmErrorPrimaryFile: String? = null,
    val liteRtLmErrorPrimaryLine: String? = null,
    val liteRtLmErrorSecondaryFile: String? = null,
    val liteRtLmErrorSecondaryLine: String? = null,
    val liteRtLmErrorRecoverabilityHint: String? = null,
    val cpuCompareRequested: Boolean? = null,
    val cpuCompareEnabled: Boolean? = null,
    val cpuCompareStarted: Boolean? = null,
    val cpuCompareFinished: Boolean? = null,
    val cpuCompareSkippedReason: String? = null,
    val cpuCompareFailureStage: String? = null,
    val cpuCompareEngineInitializeFinished: Boolean? = null,
    val cpuCompareConversationCreateFinished: Boolean? = null,
    val cpuCompareGenerateStarted: Boolean? = null,
    val cpuCompareCallbackInvokedCount: Int? = null,
    val cpuCompareEmptyTextCount: Int? = null,
    val cpuCompareNonEmptyTextCount: Int? = null,
    val cpuCompareFirstNonEmptyTextElapsedMs: Long? = null,
    val cpuCompareDoneTrueSeen: Boolean? = null,
    val cpuCompareExceptionClass: String? = null,
    val cpuCompareExceptionMessage: String? = null,
    val cpuCompareElapsedMs: Long? = null,
    val cpuGpuGenerateDiff: String? = null,
    val cpuCallbackAverageChunkLength: String? = null,
    val cpuCallbackMedianChunkLength: String? = null,
    val cpuCallbackP90ChunkLength: String? = null,
    val cpuCallbackP95ChunkLength: String? = null,
    val cpuCallbackOneCharChunkCount: Int? = null,
    val cpuCallbackTwoCharOrLessChunkCount: Int? = null,
    val cpuCallbackOneCharChunkRatio: String? = null,
    val cpuCallbackTwoCharOrLessRatio: String? = null,
    val cpuCallbackChunkLengthHistogram: String? = null,
    val cpuCallbackFirstChunksArtifact: String? = null,
    val cpuCallbackLastChunksArtifact: String? = null,
    val cpuCallbackQualityClassification: String? = null,
    val cpuOutputSuspiciousFragmentDetected: Boolean? = null,
    val cpuOutputSuspiciousFragmentReason: String? = null,
    val cpuOutputSourceCorruptionStage: String? = null,
    val gpuCallbackAverageChunkLength: String? = null,
    val gpuCallbackMedianChunkLength: String? = null,
    val gpuCallbackP50ChunkLength: String? = null,
    val gpuCallbackP90ChunkLength: String? = null,
    val gpuCallbackP95ChunkLength: String? = null,
    val gpuCallbackOneCharChunkCount: Int? = null,
    val gpuCallbackTwoCharOrLessChunkCount: Int? = null,
    val gpuCallbackOneCharChunkRatio: String? = null,
    val gpuCallbackTwoCharOrLessChunkRatio: String? = null,
    val gpuCallbackLongestChunkLength: Int? = null,
    val gpuCallbackShortestNonEmptyChunkLength: Int? = null,
    val gpuCallbackFirstChunksArtifact: String? = null,
    val gpuCallbackLastChunksArtifact: String? = null,
    val callbackQualityCompareResult: String? = null,
    val callbackQualityCompareReason: String? = null,
    val cpuGpuAvgChunkLengthRatio: String? = null,
    val cpuGpuTwoCharOrLessRatioDelta: String? = null,
    val cpuGpuCallbackCountDelta: String? = null,
    val cpuGpuRawTextSimilarityHint: String? = null,
    val cpuGpuSamePrompt: Boolean? = null,
    val cpuGpuSameMaxTokens: Boolean? = null,
    val cpuGpuSameSamplerConfigHint: String? = null,
    val callbackQualityClassification: String? = null,
    val callbackCorruptionEarliestStage: String? = null,
    val gpuCallbackToUiEnabled: Boolean? = null,
    val gpuCallbackTextPromotedToUi: Boolean? = null,
    val gpuCallbackPromotedTextLength: Int? = null,
    val gpuCallbackPromotedNonEmptyCount: Int? = null,
    val gpuCallbackSuccessClassification: String? = null,
    val gpuRawCallbackProbeStatus: String? = null,
    val gpuUiAppendStarted: Boolean? = null,
    val gpuUiAppendFinished: Boolean? = null,
    val gpuUiFirstVisibleTextElapsedMs: Long? = null,
    val gpuStreamingCompletionReason: String? = null,
    val gpuNormalRouteUseCallbackStreaming: Boolean? = null,
    val gpuCallbackStreamingPathSelected: Boolean? = null,
    val gpuCallbackStreamingPathReason: String? = null,
    val gpuCallbackStreamingSuccessCount: Int? = null,
    val gpuCallbackStreamingEmptyCallbackCount: Int? = null,
    val gpuCallbackStreamingNonEmptyCallbackCount: Int? = null,
    val gpuCallbackStreamingDoneTrueSeen: Boolean? = null,
    val gpuCallbackStreamingFinalTextLength: Int? = null,
    val gpuCallbackStreamingReusedHeldEngine: Boolean? = null,
    val gpuCallbackStreamingCompletionReason: String? = null,
    val gpuCallbackStreamingFailureReason: String? = null,
    val gpuOutputRawCallbackTextLength: Int? = null,
    val gpuOutputRawCallbackTextHead: String? = null,
    val gpuOutputRawCallbackTextTail: String? = null,
    val gpuOutputPromotedTextLength: Int? = null,
    val gpuOutputPromotedTextHead: String? = null,
    val gpuOutputPromotedTextTail: String? = null,
    val gpuOutputFinalAssistantTextLength: Int? = null,
    val gpuOutputFinalAssistantTextHead: String? = null,
    val gpuOutputFinalAssistantTextTail: String? = null,
    val gpuOutputCallbackChunkCount: Int? = null,
    val gpuOutputEmptyChunkCount: Int? = null,
    val gpuOutputNonEmptyChunkCount: Int? = null,
    val gpuOutputSuspiciousFragmentDetected: Boolean? = null,
    val gpuOutputSuspiciousFragmentReason: String? = null,
    val gpuOutputSuspiciousFragmentPosition: String? = null,
    val gpuOutputSuspiciousFragmentTailRatio: String? = null,
    val gpuOutputRepeatedMarkdownFragmentDetected: Boolean? = null,
    val gpuOutputMixedJapaneseFragmentDetected: Boolean? = null,
    val gpuOutputMixedLanguageFragmentDetected: Boolean? = null,
    val gpuOutputChunkJoinStrategy: String? = null,
    val gpuOutputChunkBoundarySuspected: Boolean? = null,
    val gpuOutputLastChunksSummary: String? = null,
    val gpuOutputChunkLengthHistogram: String? = null,
    val gpuOutputQualityMatrixMode: String? = null,
    val gpuOutputQualitySamplerMode: String? = null,
    val gpuOutputQualityStreamingMode: String? = null,
    val gpuOutputQualityEffectiveMaxTokens: String? = null,
    val gpuOutputQualityCollectOnlyEnabled: Boolean? = null,
    val gpuOutputQualityUiIncrementalAppendEnabled: Boolean? = null,
    val gpuOutputQualityCandidateResult: String? = null,
    val gpuOutputQualityFailureBlockReason: String? = null,
    val gpuOutputQualityRecommendation: String? = null,
    val gpuOutputActualUiAppendedTextLength: Int? = null,
    val gpuOutputActualUiAppendedTextHead: String? = null,
    val gpuOutputActualUiAppendedTextTail: String? = null,
    val gpuOutputUiAppendChangedText: Boolean? = null,
    val gpuOutputSourceCorruptionStage: String? = null,
    val gpuPerfEngineAcquireElapsedMs: Long? = null,
    val gpuPerfEngineCreateOrReuse: String? = null,
    val gpuPerfConversationCreateElapsedMs: Long? = null,
    val gpuPerfGenerateToFirstTokenMs: Long? = null,
    val gpuPerfFirstToLastCallbackMs: Long? = null,
    val gpuPerfCallbackTotalElapsedMs: Long? = null,
    val gpuPerfBackendTokensPerSecond: String? = null,
    val gpuPerfLamiVisibleTokensPerSecond: String? = null,
    val gpuPerfTokenizerCountDurationMs: Long? = null,
    val gpuPerfSlowPathDetected: Boolean? = null,
    val gpuPerfSlowPathReason: String? = null,
    val standardGpuRuntimeAlignmentCandidateEnabled: Boolean? = null,
    val standardGpuRuntimeAlignmentCandidateEligible: Boolean? = null,
    val standardGpuRuntimeAlignmentCandidateBlockReason: String? = null,
    val standardGpuRuntimeAlignmentCandidateModelSizeBytes: String? = null,
    val standardGpuRuntimeAlignmentCandidateModelIdentityHint: String? = null,
    val standardGpuRuntimeAlignmentCandidateRuntimeStack: String? = null,
    val standardGpuRuntimeAlignmentCandidateResult: String? = null,
    val gpuAlignmentHolderPresentBeforeAcquire: Boolean? = null,
    val gpuAlignmentHolderAcquireResult: String? = null,
    val gpuAlignmentHolderReused: Boolean? = null,
    val gpuAlignmentHolderCreated: Boolean? = null,
    val gpuAlignmentHolderCleared: Boolean? = null,
    val gpuAlignmentHolderClearReason: String? = null,
    val gpuAlignmentHolderCloseStarted: Boolean? = null,
    val gpuAlignmentHolderCloseFinished: Boolean? = null,
    val gpuAlignmentHolderReuseBlockReason: String? = null,
    val gpuAlignmentHolderModelPathChanged: Boolean? = null,
    val gpuAlignmentHolderBackendChanged: Boolean? = null,
    val gpuAlignmentHolderAppProcessStartMarker: String? = null,
    val gpuAlignmentTurnIndexIfAvailable: String? = null,
    val gpuAlignmentPreviousTurnSuccess: String? = null,
    val gpuAlignmentPreviousTurnFailureStage: String? = null,
    val gpuHolderLifecycleEventAfterSuccess: String? = null,
    val gpuHolderLifecycleLastActivityState: String? = null,
    val gpuHolderLifecycleLastAppVisibility: String? = null,
    val gpuHolderLifecycleClearTriggerElapsedMs: Long? = null,
    val gpuHolderLifecycleClearAfterSuccessMs: Long? = null,
    val gpuHolderLifecycleClearDuringActiveGenerate: Boolean? = null,
    val gpuHolderLifecycleClearAfterUiAppend: Boolean? = null,
    val gpuHolderLifecycleClearReasonDetail: String? = null,
    val gpuHolderLifecycleBackgroundDetectionSource: String? = null,
    val gpuHolderLifecycleOnStopDeferred: Boolean? = null,
    val gpuHolderLifecycleOnStopDeferReason: String? = null,
    val gpuHolderLifecycleClearSuppressedAfterSuccess: Boolean? = null,
    val gpuHolderLifecycleClearSuppressedReason: String? = null,
    val gpuHolderLifecycleActualBackgroundConfirmed: Boolean? = null,
    val gpuHolderLifecycleReuseExpectedNextTurn: Boolean? = null,
    val gpuPrefillProbeEnabled: Boolean? = null,
    val gpuPrefillProbeRequested: Boolean? = null,
    val gpuPrefillProbeBlocksNormalGenerate: Boolean? = null,
    val gpuPrefillProbeBlockReason: String? = null,
    val gpuPrefillProbeRequiresHeldEngine: Boolean? = null,
    val gpuPrefillProbeHeldEnginePresent: Boolean? = null,
    val gpuPrefillProbeDisableRecommendation: String? = null,
    val gpuInternalSurfaceProbeDiagnostics: Map<String, String> = emptyMap(),
)

private val GPU_ALIGNMENT_APP_PROCESS_START_MARKER: String = System.currentTimeMillis().toString()

private data class GpuAlignmentHolderDiagnostics(
    val presentBeforeAcquire: String,
    val acquireResult: String,
    val reused: String,
    val created: String,
    val cleared: String,
    val clearReason: String,
    val closeStarted: String,
    val closeFinished: String,
    val reuseBlockReason: String,
    val modelPathChanged: String,
    val backendChanged: String,
    val appProcessStartMarker: String,
    val turnIndexIfAvailable: String,
    val previousTurnSuccess: String,
    val previousTurnFailureStage: String,
)

private data class GpuOutputQualityDiagnostics(
    val rawLength: String,
    val rawHead: String,
    val rawTail: String,
    val promotedLength: String,
    val promotedHead: String,
    val promotedTail: String,
    val finalLength: String,
    val finalHead: String,
    val finalTail: String,
    val chunkCount: String,
    val emptyChunkCount: String,
    val nonEmptyChunkCount: String,
    val suspiciousDetected: String,
    val suspiciousReason: String,
    val suspiciousPosition: String,
    val suspiciousTailRatio: String,
    val repeatedMarkdownFragmentDetected: String,
    val mixedJapaneseFragmentDetected: String,
    val mixedLanguageFragmentDetected: String,
    val chunkJoinStrategy: String,
    val chunkBoundarySuspected: String,
    val lastChunksSummary: String,
    val chunkLengthHistogram: String,
    val matrixMode: String,
    val samplerMode: String,
    val streamingMode: String,
    val effectiveMaxTokens: String,
    val collectOnlyEnabled: String,
    val uiIncrementalAppendEnabled: String,
    val candidateResult: String,
    val failureBlockReason: String,
    val recommendation: String,
    val actualUiAppendedLength: String,
    val actualUiAppendedHead: String,
    val actualUiAppendedTail: String,
    val uiAppendChangedText: String,
    val sourceCorruptionStage: String,
    val callbackAverageChunkLength: String,
    val callbackMedianChunkLength: String,
    val callbackP50ChunkLength: String,
    val callbackP90ChunkLength: String,
    val callbackP95ChunkLength: String,
    val callbackOneCharChunkCount: String,
    val callbackTwoCharOrLessChunkCount: String,
    val callbackOneCharChunkRatio: String,
    val callbackTwoCharOrLessChunkRatio: String,
    val callbackLongestChunkLength: String,
    val callbackShortestNonEmptyChunkLength: String,
    val callbackFirstChunksArtifact: String,
    val callbackLastChunksArtifact: String,
    val fragmentationScore: String,
    val fragmentationPercentile: String,
    val fragmentationTailScore: String,
    val fragmentationMiddleScore: String,
    val fragmentationHeadScore: String,
    val chunkSizeDistribution: String,
    val chunkLengthSequence: String,
    val fragmentationClusterCount: String,
    val fragmentationClusterMaxLength: String,
    val fragmentationClusterAvgLength: String,
    val callbackQualityClassification: String,
    val callbackCorruptionEarliestStage: String,
    val cpuCompareRequested: String,
    val cpuCompareEnabled: String,
    val cpuCompareStarted: String,
    val cpuCompareFinished: String,
    val cpuCompareSkippedReason: String,
    val cpuCompareFailureStage: String,
    val cpuCompareElapsedMs: String,
    val cpuAverageChunkLength: String,
    val cpuMedianChunkLength: String,
    val cpuP90ChunkLength: String,
    val cpuP95ChunkLength: String,
    val cpuOneCharChunkCount: String,
    val cpuTwoCharOrLessChunkCount: String,
    val cpuOneCharChunkRatio: String,
    val gpuAverageChunkLength: String,
    val cpuCallbackCount: String,
    val cpuEmptyTextCount: String,
    val cpuNonEmptyTextCount: String,
    val gpuCallbackCount: String,
    val cpuTwoCharOrLessRatio: String,
    val cpuChunkLengthHistogram: String,
    val cpuCallbackFirstChunks: String,
    val cpuCallbackLastChunks: String,
    val cpuCallbackQualityClassification: String,
    val cpuOutputSuspiciousFragmentDetected: String,
    val cpuOutputSuspiciousFragmentReason: String,
    val cpuOutputSourceCorruptionStage: String,
    val gpuTwoCharOrLessRatio: String,
    val callbackQualityCompareResult: String,
    val callbackQualityCompareReason: String,
    val cpuGpuAvgChunkLengthRatio: String,
    val cpuGpuTwoCharOrLessRatioDelta: String,
    val cpuGpuCallbackCountDelta: String,
    val cpuGpuRawTextSimilarityHint: String,
    val cpuGpuSamePrompt: String,
    val cpuGpuSameMaxTokens: String,
    val cpuGpuSameSamplerConfigHint: String,
    val samplerRootCauseCandidate: String,
)

private data class GpuPerformanceDiagnostics(
    val engineAcquireElapsedMs: String,
    val engineCreateOrReuse: String,
    val conversationCreateElapsedMs: String,
    val generateToFirstTokenMs: String,
    val firstToLastCallbackMs: String,
    val callbackTotalElapsedMs: String,
    val backendTokensPerSecond: String,
    val lamiVisibleTokensPerSecond: String,
    val tokenizerCountDurationMs: String,
    val slowPathDetected: String,
    val slowPathReason: String,
)

private data class GpuHolderLifecycleDiagnostics(
    val eventAfterSuccess: String,
    val lastActivityState: String,
    val lastAppVisibility: String,
    val clearTriggerElapsedMs: String,
    val clearAfterSuccessMs: String,
    val clearDuringActiveGenerate: String,
    val clearAfterUiAppend: String,
    val clearReasonDetail: String,
    val backgroundDetectionSource: String,
    val onStopDeferred: String,
    val onStopDeferReason: String,
    val clearSuppressedAfterSuccess: String,
    val clearSuppressedReason: String,
    val actualBackgroundConfirmed: String,
    val reuseExpectedNextTurn: String,
)

private data class GpuPrefillProbeClarityDiagnostics(
    val enabled: String,
    val requested: String,
    val blocksNormalGenerate: String,
    val blockReason: String,
    val requiresHeldEngine: String,
    val heldEnginePresent: String,
    val disableRecommendation: String,
)

private data class RuntimeExecutorFingerprintDiagnostics(
    val executorSelectionFingerprint: String,
    val runtimeBackendFingerprint: String,
    val runtimeExecutorFingerprint: String,
    val runtimeDispatchFingerprint: String,
    val runtimeCompiledModelFingerprint: String,
    val engineConfigFingerprint: String,
    val conversationConfigFingerprint: String,
    val samplerConfigFingerprint: String,
    val edgeGalleryExecutorProbeResult: String,
    val edgeGalleryExecutorDifferenceSummary: String,
)

internal data class GpuRouteConfigDiagnostics(
    val experimentMode: String = "unavailable",
    val availableExperimentModes: String = "unavailable",
    val modelPath: String = "unavailable",
    val modelPathTail: String = "unavailable",
    val cacheDir: String = "unavailable",
    val cacheDirPresent: String = "unavailable",
    val backend: String = "unavailable",
    val visionBackend: String = "unavailable",
    val audioBackend: String = "unavailable",
    val maxTokens: String = "unavailable",
    val normalChatEngineConfigStyle: String = "unavailable",
    val recommendedNextConfigVariant: String = "unavailable",
    val samplerConfigEnabled: String = "unavailable",
    val samplerTopK: String = "unavailable",
    val samplerTopP: String = "unavailable",
    val samplerTemperature: String = "unavailable",
    val samplerAccelerationPolicy: String = "unavailable",
    val conversationConfigProfile: String = "unavailable",
    val conversationConfigSamplerPresent: String = "unavailable",
    val gpuOptionsConfigured: String = "unavailable",
    val gpuOptionsSource: String = "unavailable",
    val thinkingEnabled: String = "false",
    val speculativeDecodingEnabled: String = "false",
    val outputQualityProbeShortMaxTokensEnabled: String = "false",
    val outputQualityProbeEffectiveMaxTokens: String = "unavailable",
    val outputQualityMatrixMode: String = "unavailable",
    val outputQualitySamplerMode: String = "unavailable",
    val outputQualityStreamingMode: String = "unavailable",
    val outputQualityEffectiveMaxTokens: String = "unavailable",
    val outputQualityCollectOnlyEnabled: String = "false",
    val outputQualityUiIncrementalAppendEnabled: String = "unavailable",
)

internal data class GpuLiteRtFailureClassification(
    val executorErrorFile: String = "unavailable",
    val executorErrorLine: String = "unavailable",
    val compiledModelErrorFile: String = "unavailable",
    val compiledModelErrorLine: String = "unavailable",
    val engineInitializeInternalErrorDetected: Boolean = false,
    val compiledModelCreationFailed: Boolean = false,
    val interpretation: String = "unknown",
)

internal data class LiteRtLmErrorClassification(
    val kind: String = "unknown",
    val statusCode: String = "unavailable",
    val primaryFile: String = "unavailable",
    val primaryLine: String = "unavailable",
    val secondaryFile: String = "unavailable",
    val secondaryLine: String = "unavailable",
    val recoverabilityHint: String = "unknown",
    val summary: String = "unknown",
)

internal data class LiteRtLmBackendArtisanApiDiagnostics(
    val backendCandidates: String = "unavailable",
    val gpuArtisanAvailable: String = "unavailable",
    val cpuArtisanAvailable: String = "unavailable",
    val googleTensorArtisanAvailable: String = "unavailable",
    val engineConfigArtisanApiAvailable: String = "unavailable",
    val runtimeConfigAvailable: String = "unavailable",
    val backendConstraintApiAvailable: String = "unavailable",
    val preferredEngineTypeApiAvailable: String = "unavailable",
    val selectedModelBackendConstraintHint: String = "unavailable",
    val selectedModelArtisanHint: String = "unavailable",
    val edgeGalleryArtisanStaticEvidence: String = EDGE_GALLERY_ARTISAN_STATIC_EVIDENCE,
    val runtimeExecutorCandidates: String = "unavailable",
    val runtimeExecutorSelectionHint: String = "unavailable",
    val runtimeBackendConstraintHint: String = "unavailable",
    val runtimeCompiledModelExecutorHint: String = "unavailable",
    val runtimeGpuExecutorHint: String = "unavailable",
    val runtimeArtisanEvidence: String = "unavailable",
)

internal const val STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES = 2_588_147_712L
internal const val STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SHA256 =
    "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
internal const val STANDARD_GPU_RUNTIME_STACK_MISMATCH_HIGH_PRIORITY_CANDIDATES =
    "libLiteRt.so,liblitertlm_jni.so,libLiteRtDispatch_Qualcomm.so,libLiteRtCompilerPlugin_Qualcomm.so,libGemmaModelConstraintProvider.so"
internal const val STANDARD_GPU_RUNTIME_STACK_REQUIRED_ALIGNMENT_UNIT =
    "libLiteRt.so+liblitertlm_jni.so+libLiteRtDispatch_Qualcomm.so+libLiteRtCompilerPlugin_Qualcomm.so+libGemmaModelConstraintProvider.so"
internal const val STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256 =
    "31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24"
internal const val STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256 =
    "ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f"

internal data class StandardGpuProbeDiagnostics(
    val emit: Boolean = false,
    val expectedEdgeGalleryE2b: String = "false",
    val modelSizeBytes: String = "unavailable",
    val modelSha256Expected: String = STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SHA256,
    val modelSha256Actual: String = "device_unavailable",
    val modelIdentityHint: String = "unavailable",
    val runtimeStack: String = "unavailable",
    val callbackStreamingGate: String = "unavailable",
    val resultCandidate: String = "unknown",
    val runtimeAlignmentCandidateEnabled: String = "unavailable",
    val runtimeAlignmentCandidateEligible: String = "unavailable",
    val runtimeAlignmentCandidateBlockReason: String = "unavailable",
    val runtimeAlignmentCandidateModelSizeBytes: String = "unavailable",
    val runtimeAlignmentCandidateModelIdentityHint: String = "unavailable",
    val runtimeAlignmentCandidateRuntimeStack: String = "unavailable",
    val runtimeAlignmentCandidateResult: String = "unknown",
    val runtimeStackMismatchHighPriorityCandidates: String = "unavailable",
    val runtimeStackMismatchSummary: String = "unavailable",
    val runtimeStackRequiredAlignmentUnit: String = "unavailable",
    val runtimeStackSingleSoSwapForbidden: String = "true",
    val runtimeStackPromotionBlockedReason: String = "unavailable",
)

internal data class StandardGpuMinimalRuntimeCandidateDiagnostics(
    val emit: Boolean = false,
    val flavor: String = "false",
    val applicationId: String = "unavailable",
    val enabled: String = "false",
    val eligible: String = "false",
    val blockReason: String = "unavailable",
    val result: String = "unavailable",
    val successGate: String = "false",
    val libLiteRtSha256: String = "unavailable",
    val libLiteRtLmJniSha256: String = "unavailable",
    val dispatchPresent: String = "unavailable",
    val compilerPluginPresent: String = "unavailable",
    val constraintProviderPresent: String = "unavailable",
    val runtimeStack: String = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_RUNTIME_STACK,
    val runtimeStackSource: String = "unavailable",
    val loadedLibLiteRtSha256: String = "unavailable",
    val loadedLibLiteRtLmJniSha256: String = "unavailable",
    val interpretation: String = "unavailable",
)

internal fun buildLocalRouteDiagnosticContext(
    selectedModelName: String?,
    selectedModelFile: String?,
    selectedModelPath: String? = selectedModelFile,
    selectedModelSlot: String = "unknown",
    npuPreviewModelConfigured: Boolean = false,
    genericFallbackModelConfigured: Boolean = false,
    preferredBackend: String,
    npuStandardRouteMode: String,
    effectiveNpuStandardRouteMode: String = npuStandardRouteMode,
    shouldEnterNpuS1: Boolean,
    localRouteEntered: Boolean,
    normalChatNativeRouteBlocked: Boolean = false,
    blockedReason: String = "none",
    nativeLibraryDir: String? = null,
): LocalRouteDiagnosticContext {
    val modelName = selectedModelName?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
    val modelFile = selectedModelFile
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { path -> File(path).name.ifBlank { path } }
        ?: "unknown"
    val modelPath = selectedModelPath?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
    val modelKind = classifyLiteRtLmModelKindForBaseline(
        selectedModelFile?.trim()?.takeIf { it.isNotBlank() } ?: modelName,
    )
    val baselineRole = resolveLiteRtLmBaselineRole(
        modelKind = modelKind,
        preferredBackend = preferredBackend,
    )
    return LocalRouteDiagnosticContext(
        selectedModelName = modelName,
        selectedModelFile = modelFile,
        selectedModelPath = modelPath,
        selectedModelSlot = selectedModelSlot.ifBlank { "unknown" },
        npuPreviewModelConfigured = npuPreviewModelConfigured,
        genericFallbackModelConfigured = genericFallbackModelConfigured,
        preferredBackend = preferredBackend,
        npuStandardRouteMode = npuStandardRouteMode,
        effectiveNpuStandardRouteMode = effectiveNpuStandardRouteMode,
        shouldEnterNpuS1 = shouldEnterNpuS1,
        localRouteEntered = localRouteEntered,
        normalChatNativeRouteBlocked = normalChatNativeRouteBlocked,
        blockedReason = blockedReason,
        modelKind = modelKind,
        baselineRole = baselineRole,
        genericModelCpuBaseline = isGenericLiteRtLmCpuStableBaseline(
            modelKind = modelKind,
            baselineRole = baselineRole,
        ),
        nativeLibraryDir = nativeLibraryDir,
    )
}

internal fun buildStandardGpuMinimalRuntimeCandidateDiagnostics(
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
    loadedRuntimeNativeStack: LoadedRuntimeNativeStackDiagnostics,
): StandardGpuMinimalRuntimeCandidateDiagnostics {
    val isCandidateFlavor = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR
    val enabled = if (isCandidateFlavor) true else isStandardGpuMinimalRuntimeCandidateEnabledForDebug()
    val shouldEmit =
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (
                (BuildConfig.CURRENT_FLAVOR == "standard" && enabled) ||
                    isCandidateFlavor
                )
    if (!shouldEmit) return StandardGpuMinimalRuntimeCandidateDiagnostics()
    val eligibility = if (isCandidateFlavor) {
        resolveStandardGpuMinimalRuntimeCandidateFlavorEligibilityForDebug(
            preferredBackend = io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting.GPU,
            modelPath = context.selectedModelPath,
            callbackStreamingGateEnabled = flags.gpuNormalRouteUseCallbackStreaming == true,
            gpuGenerateProbeMode = flags.gpuGenerateProbeMode ?: GPU_GENERATE_PROBE_MODE_NORMAL,
            libLiteRtSha256 = loadedRuntimeNativeStack.libLiteRtSha256,
            libLiteRtLmJniSha256 = loadedRuntimeNativeStack.libLiteRtLmJniSha256,
            dispatchPresent = loadedRuntimeNativeStack.dispatchQualcommPresent,
            compilerPluginPresent = loadedRuntimeNativeStack.compilerPluginQualcommPresent,
            constraintProviderPresent = loadedRuntimeNativeStack.gemmaConstraintProviderPresent,
        )
    } else {
        resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
            preferredBackend = io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting.GPU,
            modelPath = context.selectedModelPath,
            callbackStreamingGateEnabled = flags.gpuNormalRouteUseCallbackStreaming == true,
            gpuGenerateProbeMode = flags.gpuGenerateProbeMode ?: GPU_GENERATE_PROBE_MODE_NORMAL,
            libLiteRtSha256 = loadedRuntimeNativeStack.libLiteRtSha256,
            libLiteRtLmJniSha256 = loadedRuntimeNativeStack.libLiteRtLmJniSha256,
            dispatchPresent = loadedRuntimeNativeStack.dispatchQualcommPresent,
            compilerPluginPresent = loadedRuntimeNativeStack.compilerPluginQualcommPresent,
            constraintProviderPresent = loadedRuntimeNativeStack.gemmaConstraintProviderPresent,
        )
    }
    val observedResult = resolveStandardGpuProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )
    val result = when {
        observedResult != "unknown" -> observedResult
        eligibility.eligible -> "unavailable"
        else -> "unavailable"
    }
    return StandardGpuMinimalRuntimeCandidateDiagnostics(
        emit = true,
        flavor = isCandidateFlavor.toString(),
        applicationId = BuildConfig.APPLICATION_ID,
        enabled = eligibility.enabled.toString(),
        eligible = eligibility.eligible.toString(),
        blockReason = eligibility.blockReason,
        result = result,
        successGate = eligibility.eligible.toString(),
        libLiteRtSha256 = loadedRuntimeNativeStack.libLiteRtSha256,
        libLiteRtLmJniSha256 = loadedRuntimeNativeStack.libLiteRtLmJniSha256,
        dispatchPresent = loadedRuntimeNativeStack.dispatchQualcommPresent,
        compilerPluginPresent = loadedRuntimeNativeStack.compilerPluginQualcommPresent,
        constraintProviderPresent = loadedRuntimeNativeStack.gemmaConstraintProviderPresent,
        runtimeStack = eligibility.runtimeStack,
        runtimeStackSource = if (isCandidateFlavor) {
            BuildConfig.DISPATCH_RUNTIME_SOURCE
        } else {
            eligibility.runtimeStack
        },
        loadedLibLiteRtSha256 = loadedRuntimeNativeStack.libLiteRtSha256,
        loadedLibLiteRtLmJniSha256 = loadedRuntimeNativeStack.libLiteRtLmJniSha256,
        interpretation = resolveStandardGpuMinimalRuntimeCandidateInterpretation(
            eligibility = eligibility,
            result = result,
            failureStage = failureStage,
            flags = flags,
        ),
    )
}

private fun resolveStandardGpuMinimalRuntimeCandidateInterpretation(
    eligibility: StandardGpuMinimalRuntimeCandidateEligibility,
    result: String,
    failureStage: String,
    flags: LocalRouteDiagnosticFlags,
): String =
    when {
        !eligibility.enabled -> "candidate_gate_disabled"
        !eligibility.eligible -> "blocked:${eligibility.blockReason}"
        result == "success" -> "minimal_runtime_core_pair_candidate_success"
        result == "failure" &&
            (
                failureStage == "gpu_generate_compiled_model_invoke_failed" ||
                    flags.liteRtLmErrorStatusCode == "13" ||
                    flags.gpuGenerateExceptionErrorLine == "735" ||
                    flags.liteRtLmErrorPrimaryLine == "735"
                ) -> "minimal_runtime_core_pair_candidate_failed_cc735"
        result == "failure" -> "minimal_runtime_core_pair_candidate_failed"
        else -> "minimal_runtime_core_pair_candidate_pending"
    }

private fun resolveStandardGpuMinimalRuntimeCandidateFlavorEligibilityForDebug(
    preferredBackend: io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting,
    modelPath: String?,
    callbackStreamingGateEnabled: Boolean,
    gpuGenerateProbeMode: String = GPU_GENERATE_PROBE_MODE_NORMAL,
    libLiteRtSha256: String = "unavailable",
    libLiteRtLmJniSha256: String = "unavailable",
    dispatchPresent: String = "unavailable",
    compilerPluginPresent: String = "unavailable",
    constraintProviderPresent: String = "unavailable",
): StandardGpuMinimalRuntimeCandidateEligibility {
    val modelFile = modelPath
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" }
        ?.let(::File)
    val sizeBytes = modelFile?.takeIf { it.isFile }?.length()
    val sizeDiagnostic = sizeBytes?.toString() ?: "unavailable"
    val pathText = listOfNotNull(modelPath, modelFile?.name)
        .joinToString(" ")
        .lowercase()
    val nameLooksLikeEdgeGalleryE2b =
        pathText.contains("gemma-4-e2b-it-edge-gallery.litertlm") ||
            pathText.contains("gemma_4_e2b_it") ||
            pathText.contains("litert-community/gemma-4-e2b-it-litert-lm") ||
            pathText.endsWith("gemma-4-e2b-it.litertlm")
    val sizeMatches = sizeBytes == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES
    val modelIdentityHint = when {
        !nameLooksLikeEdgeGalleryE2b -> "not_edge_gallery_e2b"
        sizeMatches -> "edge_gallery_e2b_expected"
        sizeBytes == null -> "edge_gallery_e2b_expected_size_unavailable"
        else -> "edge_gallery_e2b_size_mismatch"
    }
    val blockReason = when {
        !BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR ->
            "not_standard_gpu_minimal_runtime_candidate_flavor"
        preferredBackend != io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting.GPU ->
            "selected_backend_not_gpu"
        !callbackStreamingGateEnabled -> "callback_streaming_gate_disabled"
        gpuGenerateProbeMode !in STANDARD_GPU_RUNTIME_ALIGNMENT_CANDIDATE_ALLOWED_PROBE_MODES ->
            "unsupported_gpu_generate_probe_mode"
        !nameLooksLikeEdgeGalleryE2b -> "model_identity_not_edge_gallery_e2b"
        sizeBytes == null -> "model_size_unavailable"
        !sizeMatches -> "model_size_mismatch"
        libLiteRtSha256 == "unavailable" -> "liblitert_sha_unavailable"
        !libLiteRtSha256.equals(STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256, ignoreCase = true) ->
            "liblitert_sha_mismatch"
        libLiteRtLmJniSha256 == "unavailable" -> "liblitertlm_jni_sha_unavailable"
        !libLiteRtLmJniSha256.equals(STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256, ignoreCase = true) ->
            "liblitertlm_jni_sha_mismatch"
        dispatchPresent == "true" -> "dispatch_qualcomm_present"
        compilerPluginPresent == "true" -> "compiler_plugin_qualcomm_present"
        constraintProviderPresent == "true" -> "constraint_provider_present"
        else -> "none"
    }
    return StandardGpuMinimalRuntimeCandidateEligibility(
        enabled = true,
        eligible = blockReason == "none",
        blockReason = blockReason,
        modelSizeBytes = sizeDiagnostic,
        modelIdentityHint = modelIdentityHint,
        runtimeStack = "standardGpuMinimalRuntimeCandidateDebug_minimal_runtime_pair",
    )
}

internal fun buildStandardGpuProbeDiagnostics(
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): StandardGpuProbeDiagnostics {
    val selectedText = listOf(
        context.selectedModelName,
        context.selectedModelFile,
        context.selectedModelPath,
    ).joinToString(" ").lowercase()
    val modelFile = context.selectedModelPath
        .takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" }
        ?.let(::File)
    val actualSize = modelFile?.takeIf { it.isFile }?.length()
    val edgeGalleryNameHint =
        selectedText.contains("gemma-4-e2b-it-edge-gallery.litertlm") ||
            selectedText.contains("gemma_4_e2b_it") ||
            selectedText.contains("litert-community/gemma-4-e2b-it-litert-lm")
    val genericE2bNameHint = selectedText.contains("gemma-4-e2b-it.litertlm")
    val expectedEdgeGalleryE2b =
        edgeGalleryNameHint ||
            (genericE2bNameHint && actualSize == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
    val candidateEnabled = flags.standardGpuRuntimeAlignmentCandidateEnabled
        ?: isStandardGpuRuntimeAlignmentCandidateEnabledForDebug()
    val candidateBlockReason = flags.standardGpuRuntimeAlignmentCandidateBlockReason
        ?: resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
            preferredBackend = if (context.preferredBackend.equals("GPU", ignoreCase = true)) {
                io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting.GPU
            } else {
                io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting.DEFAULT
            },
            modelPath = context.selectedModelPath,
            callbackStreamingGateEnabled = flags.gpuNormalRouteUseCallbackStreaming == true,
            gpuGenerateProbeMode = flags.gpuGenerateProbeMode ?: GPU_GENERATE_PROBE_MODE_NORMAL,
        ).blockReason
    val candidateEligible = flags.standardGpuRuntimeAlignmentCandidateEligible
        ?: (candidateBlockReason == "none")
    val candidateResult = flags.standardGpuRuntimeAlignmentCandidateResult
        ?: resolveStandardGpuProbeResultCandidate(
            flags = flags,
            failureStage = failureStage,
        ).takeIf { candidateEligible || it != "unknown" }
        ?: "unknown"
    val standardGpuProbe =
        BuildConfig.CURRENT_FLAVOR == "standard" &&
            context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (expectedEdgeGalleryE2b || candidateEnabled)
    if (!standardGpuProbe) return StandardGpuProbeDiagnostics()
    val resultCandidate = resolveStandardGpuProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )
    return StandardGpuProbeDiagnostics(
        emit = true,
        expectedEdgeGalleryE2b = expectedEdgeGalleryE2b.toString(),
        modelSizeBytes = actualSize?.toString() ?: STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES.toString(),
        modelIdentityHint = when {
            expectedEdgeGalleryE2b -> "edge_gallery_e2b_expected"
            edgeGalleryNameHint || genericE2bNameHint -> "edge_gallery_e2b_unverified"
            else -> "not_edge_gallery_e2b"
        },
        runtimeStack = "standardDebug",
        callbackStreamingGate = flags.gpuNormalRouteUseCallbackStreaming?.toString() ?: "unavailable",
        resultCandidate = resultCandidate,
        runtimeAlignmentCandidateEnabled = candidateEnabled.toString(),
        runtimeAlignmentCandidateEligible = candidateEligible.toString(),
        runtimeAlignmentCandidateBlockReason = candidateBlockReason,
        runtimeAlignmentCandidateModelSizeBytes =
            flags.standardGpuRuntimeAlignmentCandidateModelSizeBytes
                ?: actualSize?.toString()
                ?: "unavailable",
        runtimeAlignmentCandidateModelIdentityHint =
            flags.standardGpuRuntimeAlignmentCandidateModelIdentityHint
                ?: when {
                    expectedEdgeGalleryE2b -> "edge_gallery_e2b_expected"
                    else -> "not_edge_gallery_e2b"
                },
        runtimeAlignmentCandidateRuntimeStack =
            flags.standardGpuRuntimeAlignmentCandidateRuntimeStack
                ?: STANDARD_GPU_RUNTIME_ALIGNMENT_CANDIDATE_RUNTIME_STACK,
        runtimeAlignmentCandidateResult = candidateResult,
        runtimeStackMismatchHighPriorityCandidates = STANDARD_GPU_RUNTIME_STACK_MISMATCH_HIGH_PRIORITY_CANDIDATES,
        runtimeStackMismatchSummary = resolveStandardGpuRuntimeStackMismatchSummary(
            candidateResult = candidateResult,
            failureStage = failureStage,
            flags = flags,
        ),
        runtimeStackRequiredAlignmentUnit = STANDARD_GPU_RUNTIME_STACK_REQUIRED_ALIGNMENT_UNIT,
        runtimeStackSingleSoSwapForbidden = "true",
        runtimeStackPromotionBlockedReason = resolveStandardGpuRuntimeStackPromotionBlockedReason(
            candidateEnabled = candidateEnabled,
            candidateEligible = candidateEligible,
            candidateResult = candidateResult,
            candidateBlockReason = candidateBlockReason,
        ),
    )
}

private fun resolveStandardGpuRuntimeStackMismatchSummary(
    candidateResult: String,
    failureStage: String,
    flags: LocalRouteDiagnosticFlags,
): String =
    when {
        candidateResult == "failure" &&
            (
                failureStage == "gpu_generate_compiled_model_invoke_failed" ||
                    flags.liteRtLmErrorStatusCode == "13" ||
                    flags.gpuGenerateExceptionErrorLine == "735" ||
                    flags.liteRtLmErrorPrimaryLine == "735"
                ) -> "runtime_stack_mismatch_suspected"
        candidateResult == "failure" -> "standard_gpu_candidate_failed_runtime_stack_review_required"
        candidateResult == "success" -> "runtime_stack_candidate_success"
        else -> "unavailable"
    }

private fun resolveStandardGpuRuntimeStackPromotionBlockedReason(
    candidateEnabled: Boolean,
    candidateEligible: Boolean,
    candidateResult: String,
    candidateBlockReason: String,
): String =
    when {
        !candidateEnabled -> "candidate_gate_disabled"
        !candidateEligible -> candidateBlockReason
        candidateResult == "failure" -> "standard_runtime_stack_not_aligned"
        candidateResult == "success" -> "dev_gate_success_requires_safety_soak"
        else -> "candidate_result_unknown"
    }

private fun resolveStandardGpuProbeResultCandidate(
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): String =
    when {
        flags.gpuCallbackStreamingPathSelected == true &&
            flags.gpuCallbackTextPromotedToUi == true &&
            flags.gpuUiAppendFinished == true &&
            flags.gpuStreamingCompletionReason == "flow_completed_non_empty_response" &&
            (failureStage == "none" || failureStage == "unavailable") -> "success"
        flags.gpuGenerateExceptionSeen == true ||
            flags.gpuCallbackExceptionClass?.takeIf { it != "none" && it != "unavailable" } != null ||
            (failureStage != "none" && failureStage != "unavailable") -> "failure"
        else -> "unknown"
    }

private fun buildGpuAlignmentHolderDiagnostics(
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): GpuAlignmentHolderDiagnostics {
    val clearReason = flags.gpuAlignmentHolderClearReason
        ?: flags.heldEngineDestroyReason
        ?: "unavailable"
    val cleared = flags.gpuAlignmentHolderCleared
        ?: flags.holderInvalidated
        ?: flags.holderClosed
    val reused = flags.gpuAlignmentHolderReused
        ?: flags.holderReused
        ?: flags.heldEngineReused
        ?: flags.gpuCallbackStreamingReusedHeldEngine
    val created = flags.gpuAlignmentHolderCreated
        ?: flags.holderCreated
        ?: when {
            flags.gpuAlignmentHolderAcquireResult == "created" -> true
            flags.gpuAlignmentHolderAcquireResult == "reused" -> false
            else -> null
        }
    val presentBeforeAcquire = flags.gpuAlignmentHolderPresentBeforeAcquire
    val modelPathChanged = flags.gpuAlignmentHolderModelPathChanged
        ?: clearReason.contains("model", ignoreCase = true).takeIf { it || clearReason != "unavailable" }
    val backendChanged = flags.gpuAlignmentHolderBackendChanged
        ?: clearReason.contains("backend", ignoreCase = true).takeIf { it || clearReason != "unavailable" }
    val previousFailureStage = flags.gpuAlignmentPreviousTurnFailureStage
        ?: flags.heldEngineLastFailureStage
        ?: "unavailable"
    val reuseBlockReason = flags.gpuAlignmentHolderReuseBlockReason
        ?: classifyGpuAlignmentHolderReuseBlockReason(
            reused = reused,
            presentBeforeAcquire = presentBeforeAcquire,
            created = created,
        cleared = cleared,
        clearReason = clearReason,
        holderFailureCleanup = flags.holderFailureCleanup,
        holderProcessRestart = flags.holderProcessRestart,
        previousFailureStage = previousFailureStage,
        failureStage = failureStage,
        onStopDeferred = flags.gpuHolderLifecycleOnStopDeferred,
        actualBackgroundConfirmed = flags.gpuHolderLifecycleActualBackgroundConfirmed,
    )
    return GpuAlignmentHolderDiagnostics(
        presentBeforeAcquire = presentBeforeAcquire.toDiagnosticValue(),
        acquireResult = flags.gpuAlignmentHolderAcquireResult ?: deriveGpuAlignmentHolderAcquireResult(
            reused = reused,
            created = created,
            holderAcquired = flags.holderAcquired,
            failureStage = failureStage,
        ),
        reused = reused.toDiagnosticValue(),
        created = created.toDiagnosticValue(),
        cleared = cleared.toDiagnosticValue(),
        clearReason = clearReason.toDiagnosticValue(),
        closeStarted = (flags.gpuAlignmentHolderCloseStarted ?: flags.holderClosed).toDiagnosticValue(),
        closeFinished = (flags.gpuAlignmentHolderCloseFinished ?: flags.holderClosed).toDiagnosticValue(),
        reuseBlockReason = reuseBlockReason,
        modelPathChanged = modelPathChanged.toDiagnosticValue(),
        backendChanged = backendChanged.toDiagnosticValue(),
        appProcessStartMarker = flags.gpuAlignmentHolderAppProcessStartMarker
            ?: GPU_ALIGNMENT_APP_PROCESS_START_MARKER,
        turnIndexIfAvailable = flags.gpuAlignmentTurnIndexIfAvailable ?: "unavailable",
        previousTurnSuccess = flags.gpuAlignmentPreviousTurnSuccess ?: "unavailable",
        previousTurnFailureStage = previousFailureStage.toDiagnosticValue(),
    )
}

private fun deriveGpuAlignmentHolderAcquireResult(
    reused: Boolean?,
    created: Boolean?,
    holderAcquired: Boolean?,
    failureStage: String,
): String =
    when {
        reused == true -> "reused"
        created == true -> "created"
        holderAcquired == true -> "acquired"
        failureStage != "none" && failureStage != "unavailable" -> "failed_or_interrupted"
        else -> "unavailable"
    }

private fun classifyGpuAlignmentHolderReuseBlockReason(
    reused: Boolean?,
    presentBeforeAcquire: Boolean?,
    created: Boolean?,
    cleared: Boolean?,
    clearReason: String,
    holderFailureCleanup: Boolean?,
    holderProcessRestart: Boolean?,
    previousFailureStage: String,
    failureStage: String,
    onStopDeferred: Boolean?,
    actualBackgroundConfirmed: Boolean?,
): String =
    when {
        reused == true -> "reuse_ok"
        onStopDeferred == true -> "transient_onstop_suppressed"
        clearReason == "app-backgrounded" && actualBackgroundConfirmed == true -> "app_backgrounded_confirmed"
        clearReason.contains("model", ignoreCase = true) -> "model_path_changed"
        clearReason.contains("backend", ignoreCase = true) -> "backend_changed"
        clearReason.contains("timeout", ignoreCase = true) -> "timeout_cleanup"
        holderFailureCleanup == true ||
            previousFailureStage !in setOf("unavailable", "none") ||
            clearReason.contains("failure", ignoreCase = true) ||
            clearReason.contains("failed", ignoreCase = true) ||
            clearReason.contains("error", ignoreCase = true) -> "failure_cleanup"
        holderProcessRestart == true && presentBeforeAcquire == false -> "process_restart"
        cleared == true && failureStage in setOf("none", "unavailable") -> "holder_cleared_after_success"
        presentBeforeAcquire == false && created == true -> "first_turn_no_previous_holder"
        presentBeforeAcquire == false -> "first_turn_no_previous_holder"
        clearReason.contains("debug_no_held_engine", ignoreCase = true) ||
            clearReason.contains("no_held_engine", ignoreCase = true) -> "explicit_debug_no_held_engine"
        else -> "unknown"
    }

private fun buildGpuOutputQualityDiagnostics(flags: LocalRouteDiagnosticFlags): GpuOutputQualityDiagnostics {
    val suspiciousReason = flags.gpuOutputSuspiciousFragmentReason
        ?: classifyGpuOutputSuspiciousFragmentReason(
            rawSample = listOfNotNull(flags.gpuOutputRawCallbackTextHead, flags.gpuOutputRawCallbackTextTail)
                .joinToString(" "),
            promotedSample = listOfNotNull(flags.gpuOutputPromotedTextHead, flags.gpuOutputPromotedTextTail)
                .joinToString(" "),
            finalSample = listOfNotNull(flags.gpuOutputFinalAssistantTextHead, flags.gpuOutputFinalAssistantTextTail)
                .joinToString(" "),
            rawLength = flags.gpuOutputRawCallbackTextLength,
            finalLength = flags.gpuOutputFinalAssistantTextLength,
            nonEmptyChunkCount = flags.gpuOutputNonEmptyChunkCount,
        )
    val suspiciousDetected = flags.gpuOutputSuspiciousFragmentDetected
        ?: (suspiciousReason != "none" && suspiciousReason != "unavailable")
    val repeatedMarkdown = flags.gpuOutputRepeatedMarkdownFragmentDetected
        ?: detectRepeatedMarkdownFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        )
    val mixedJapanese = flags.gpuOutputMixedJapaneseFragmentDetected
        ?: detectMixedJapaneseFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        )
    val mixedLanguage = flags.gpuOutputMixedLanguageFragmentDetected
        ?: (mixedJapanese || detectMixedLanguageFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        ))
    val chunkBoundarySuspected = flags.gpuOutputChunkBoundarySuspected
        ?: (suspiciousReason in setOf(
            "many_tiny_fragments",
            "tail_tiny_chunk_run",
            "tail_markdown_fragment_bias",
            "japanese_particle_or_punctuation_fragment_run",
            "repeated_markdown_or_word_pattern",
            "promoted_text_suspicious_after_stream_join",
            "final_text_only_suspicious_after_ui_or_markdown",
        ))
    val matrixMode = flags.gpuOutputQualityMatrixMode ?: flags.gpuConfigDiagnostics?.outputQualityMatrixMode ?: "unavailable"
    val samplerMode = flags.gpuOutputQualitySamplerMode ?: flags.gpuConfigDiagnostics?.outputQualitySamplerMode ?: "unavailable"
    val streamingMode = flags.gpuOutputQualityStreamingMode
        ?: flags.gpuConfigDiagnostics?.outputQualityStreamingMode
        ?: "unavailable"
    val collectOnlyEnabled = flags.gpuOutputQualityCollectOnlyEnabled
        ?: flags.gpuConfigDiagnostics?.outputQualityCollectOnlyEnabled?.toBooleanStrictOrNull()
    val uiIncrementalAppendEnabled = flags.gpuOutputQualityUiIncrementalAppendEnabled
        ?: flags.gpuConfigDiagnostics?.outputQualityUiIncrementalAppendEnabled?.toBooleanStrictOrNull()
    val candidateResult = flags.gpuOutputQualityCandidateResult
        ?: classifyGpuOutputQualityCandidateResult(
            suspiciousDetected = suspiciousDetected,
            finalLength = flags.gpuOutputFinalAssistantTextLength,
            callbackNonEmptyCount = flags.gpuOutputNonEmptyChunkCount,
        )
    val failureBlockReason = flags.gpuOutputQualityFailureBlockReason
        ?: classifyGpuOutputQualityFailureBlockReason(
            suspiciousDetected = suspiciousDetected,
            suspiciousReason = suspiciousReason,
            collectOnlyEnabled = collectOnlyEnabled == true,
            uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage,
        )
    return GpuOutputQualityDiagnostics(
        rawLength = flags.gpuOutputRawCallbackTextLength?.toString() ?: "unavailable",
        rawHead = flags.gpuOutputRawCallbackTextHead.toDiagnosticValue(),
        rawTail = flags.gpuOutputRawCallbackTextTail.toDiagnosticValue(),
        promotedLength = flags.gpuOutputPromotedTextLength?.toString() ?: "unavailable",
        promotedHead = flags.gpuOutputPromotedTextHead.toDiagnosticValue(),
        promotedTail = flags.gpuOutputPromotedTextTail.toDiagnosticValue(),
        finalLength = flags.gpuOutputFinalAssistantTextLength?.toString() ?: "unavailable",
        finalHead = flags.gpuOutputFinalAssistantTextHead.toDiagnosticValue(),
        finalTail = flags.gpuOutputFinalAssistantTextTail.toDiagnosticValue(),
        chunkCount = flags.gpuOutputCallbackChunkCount?.toString() ?: "unavailable",
        emptyChunkCount = flags.gpuOutputEmptyChunkCount?.toString() ?: "unavailable",
        nonEmptyChunkCount = flags.gpuOutputNonEmptyChunkCount?.toString() ?: "unavailable",
        suspiciousDetected = suspiciousDetected.toString(),
        suspiciousReason = suspiciousReason,
        suspiciousPosition = flags.gpuOutputSuspiciousFragmentPosition
            ?: classifyGpuOutputSuspiciousFragmentPosition(
                reason = suspiciousReason,
                headSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextHead,
                    flags.gpuOutputPromotedTextHead,
                    flags.gpuOutputFinalAssistantTextHead,
                ).joinToString(" "),
                tailSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextTail,
                    flags.gpuOutputPromotedTextTail,
                    flags.gpuOutputFinalAssistantTextTail,
                ).joinToString(" "),
            ),
        suspiciousTailRatio = flags.gpuOutputSuspiciousFragmentTailRatio
            ?: calculateGpuOutputSuspiciousTailRatio(
                tailSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextTail,
                    flags.gpuOutputPromotedTextTail,
                    flags.gpuOutputFinalAssistantTextTail,
                ).joinToString(" "),
            ),
        repeatedMarkdownFragmentDetected = repeatedMarkdown.toString(),
        mixedJapaneseFragmentDetected = mixedJapanese.toString(),
        mixedLanguageFragmentDetected = mixedLanguage.toString(),
        chunkJoinStrategy = flags.gpuOutputChunkJoinStrategy ?: "unavailable",
        chunkBoundarySuspected = chunkBoundarySuspected.toString(),
        lastChunksSummary = flags.gpuOutputLastChunksSummary.toDiagnosticValue(),
        chunkLengthHistogram = flags.gpuOutputChunkLengthHistogram.toDiagnosticValue(),
        matrixMode = matrixMode,
        samplerMode = samplerMode,
        streamingMode = streamingMode,
        effectiveMaxTokens = flags.gpuOutputQualityEffectiveMaxTokens
            ?: flags.gpuConfigDiagnostics?.outputQualityEffectiveMaxTokens
            ?: flags.gpuConfigDiagnostics?.maxTokens
            ?: "unavailable",
        collectOnlyEnabled = collectOnlyEnabled.toDiagnosticValue(),
        uiIncrementalAppendEnabled = uiIncrementalAppendEnabled.toDiagnosticValue(),
        candidateResult = candidateResult,
        failureBlockReason = failureBlockReason,
        recommendation = flags.gpuOutputQualityRecommendation
            ?: resolveGpuOutputQualityRecommendation(
                candidateResult = candidateResult,
                failureBlockReason = failureBlockReason,
                collectOnlyEnabled = collectOnlyEnabled == true,
            ),
        actualUiAppendedLength = flags.gpuOutputActualUiAppendedTextLength?.toString() ?: "unavailable",
        actualUiAppendedHead = flags.gpuOutputActualUiAppendedTextHead.toDiagnosticValue(),
        actualUiAppendedTail = flags.gpuOutputActualUiAppendedTextTail.toDiagnosticValue(),
        uiAppendChangedText = flags.gpuOutputUiAppendChangedText.toDiagnosticValue(),
        sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage
            ?: classifyGpuOutputSourceCorruptionStage(
                suspiciousReason = suspiciousReason,
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            ),
        callbackAverageChunkLength = flags.gpuCallbackAverageChunkLength ?: "unavailable",
        callbackMedianChunkLength = flags.gpuCallbackMedianChunkLength ?: "unavailable",
        callbackP50ChunkLength = flags.gpuCallbackP50ChunkLength ?: "unavailable",
        callbackP90ChunkLength = flags.gpuCallbackP90ChunkLength ?: "unavailable",
        callbackP95ChunkLength = flags.gpuCallbackP95ChunkLength ?: "unavailable",
        callbackOneCharChunkCount = flags.gpuCallbackOneCharChunkCount?.toString() ?: "unavailable",
        callbackTwoCharOrLessChunkCount = flags.gpuCallbackTwoCharOrLessChunkCount?.toString() ?: "unavailable",
        callbackOneCharChunkRatio = flags.gpuCallbackOneCharChunkRatio ?: "unavailable",
        callbackTwoCharOrLessChunkRatio = flags.gpuCallbackTwoCharOrLessChunkRatio ?: "unavailable",
        callbackLongestChunkLength = flags.gpuCallbackLongestChunkLength?.toString() ?: "unavailable",
        callbackShortestNonEmptyChunkLength = flags.gpuCallbackShortestNonEmptyChunkLength?.toString() ?: "unavailable",
        callbackFirstChunksArtifact = flags.gpuCallbackFirstChunksArtifact.toDiagnosticValue(),
        callbackLastChunksArtifact = flags.gpuCallbackLastChunksArtifact.toDiagnosticValue(),
        fragmentationScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_score"] ?: "unavailable",
        fragmentationPercentile = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_percentile"] ?: "unavailable",
        fragmentationTailScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_tail_score"] ?: "unavailable",
        fragmentationMiddleScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_middle_score"] ?: "unavailable",
        fragmentationHeadScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_head_score"] ?: "unavailable",
        chunkSizeDistribution = flags.gpuPrefillProbeDiagnostics["gpu_chunk_size_distribution"]
            ?: flags.gpuOutputChunkLengthHistogram
            ?: "unavailable",
        chunkLengthSequence = flags.gpuPrefillProbeDiagnostics["gpu_chunk_length_sequence"] ?: "unavailable",
        fragmentationClusterCount = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_count"] ?: "unavailable",
        fragmentationClusterMaxLength = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_max_length"] ?: "unavailable",
        fragmentationClusterAvgLength = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_avg_length"]
            ?: "unavailable",
        callbackQualityClassification = flags.callbackQualityClassification
            ?: classifyCallbackQuality(
                callbackCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                twoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                averageChunkLength = flags.gpuCallbackAverageChunkLength,
            ),
        callbackCorruptionEarliestStage = flags.callbackCorruptionEarliestStage
            ?: classifyGpuOutputSourceCorruptionStage(
                suspiciousReason = suspiciousReason,
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            ),
        cpuCompareRequested = flags.cpuCompareRequested.toDiagnosticValue(),
        cpuCompareEnabled = flags.cpuCompareEnabled.toDiagnosticValue(),
        cpuCompareStarted = flags.cpuCompareStarted.toDiagnosticValue(),
        cpuCompareFinished = flags.cpuCompareFinished.toDiagnosticValue(),
        cpuCompareSkippedReason = flags.cpuCompareSkippedReason.toDiagnosticValue(),
        cpuCompareFailureStage = flags.cpuCompareFailureStage.toDiagnosticValue(),
        cpuCompareElapsedMs = flags.cpuCompareElapsedMs?.toString() ?: "unavailable",
        cpuAverageChunkLength = flags.cpuCallbackAverageChunkLength ?: "unavailable",
        cpuMedianChunkLength = flags.cpuCallbackMedianChunkLength ?: "unavailable",
        cpuP90ChunkLength = flags.cpuCallbackP90ChunkLength ?: "unavailable",
        cpuP95ChunkLength = flags.cpuCallbackP95ChunkLength ?: "unavailable",
        cpuOneCharChunkCount = flags.cpuCallbackOneCharChunkCount?.toString() ?: "unavailable",
        cpuTwoCharOrLessChunkCount = flags.cpuCallbackTwoCharOrLessChunkCount?.toString() ?: "unavailable",
        cpuOneCharChunkRatio = flags.cpuCallbackOneCharChunkRatio ?: "unavailable",
        gpuAverageChunkLength = flags.gpuCallbackAverageChunkLength ?: "unavailable",
        cpuCallbackCount = flags.cpuCompareCallbackInvokedCount?.toString() ?: "unavailable",
        cpuEmptyTextCount = flags.cpuCompareEmptyTextCount?.toString() ?: "unavailable",
        cpuNonEmptyTextCount = flags.cpuCompareNonEmptyTextCount?.toString() ?: "unavailable",
        gpuCallbackCount = (flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount)?.toString()
            ?: "unavailable",
        cpuTwoCharOrLessRatio = flags.cpuCallbackTwoCharOrLessRatio ?: "unavailable",
        cpuChunkLengthHistogram = flags.cpuCallbackChunkLengthHistogram.toDiagnosticValue(),
        cpuCallbackFirstChunks = flags.cpuCallbackFirstChunksArtifact.toDiagnosticValue(),
        cpuCallbackLastChunks = flags.cpuCallbackLastChunksArtifact.toDiagnosticValue(),
        cpuCallbackQualityClassification = flags.cpuCallbackQualityClassification.toDiagnosticValue(),
        cpuOutputSuspiciousFragmentDetected = flags.cpuOutputSuspiciousFragmentDetected.toDiagnosticValue(),
        cpuOutputSuspiciousFragmentReason = flags.cpuOutputSuspiciousFragmentReason.toDiagnosticValue(),
        cpuOutputSourceCorruptionStage = flags.cpuOutputSourceCorruptionStage.toDiagnosticValue(),
        gpuTwoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio ?: "unavailable",
        callbackQualityCompareResult = flags.callbackQualityCompareResult
            ?: classifyCallbackQualityCompareResult(
                gpuCandidateResult = candidateResult,
                gpuSuspiciousDetected = suspiciousDetected,
                cpuSuspiciousDetected = flags.cpuOutputSuspiciousFragmentDetected,
                cpuFinished = flags.cpuCompareFinished,
                cpuSkippedReason = flags.cpuCompareSkippedReason,
                cpuExceptionClass = flags.cpuCompareExceptionClass,
                cpuFailureStage = flags.cpuCompareFailureStage,
                cpuCallbackCount = flags.cpuCompareCallbackInvokedCount,
            ),
        callbackQualityCompareReason = flags.callbackQualityCompareReason
            ?: classifyCallbackQualityCompareReason(
                cpuAvg = flags.cpuCallbackAverageChunkLength,
                gpuAvg = flags.gpuCallbackAverageChunkLength,
                cpuTwoCharRatio = flags.cpuCallbackTwoCharOrLessRatio,
                gpuTwoCharRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                cpuCount = flags.cpuCompareCallbackInvokedCount,
                gpuCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                cpuSkippedReason = flags.cpuCompareSkippedReason,
            ),
        cpuGpuAvgChunkLengthRatio = flags.cpuGpuAvgChunkLengthRatio
            ?: calculateDoubleRatio(
                numerator = flags.gpuCallbackAverageChunkLength,
                denominator = flags.cpuCallbackAverageChunkLength,
            ),
        cpuGpuTwoCharOrLessRatioDelta = flags.cpuGpuTwoCharOrLessRatioDelta
            ?: calculateDoubleDelta(
                lhs = flags.gpuCallbackTwoCharOrLessChunkRatio,
                rhs = flags.cpuCallbackTwoCharOrLessRatio,
            ),
        cpuGpuCallbackCountDelta = flags.cpuGpuCallbackCountDelta
            ?: calculateIntDelta(
                lhs = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                rhs = flags.cpuCompareCallbackInvokedCount,
            ),
        cpuGpuRawTextSimilarityHint = flags.cpuGpuRawTextSimilarityHint
            ?: classifyCpuGpuRawTextSimilarityHint(
                cpuCount = flags.cpuCompareCallbackInvokedCount,
                gpuCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                cpuSuspiciousDetected = flags.cpuOutputSuspiciousFragmentDetected,
                gpuSuspiciousDetected = suspiciousDetected,
                cpuAverageChunkLength = flags.cpuCallbackAverageChunkLength,
                gpuAverageChunkLength = flags.gpuCallbackAverageChunkLength,
            ),
        cpuGpuSamePrompt = flags.cpuGpuSamePrompt.toDiagnosticValue(),
        cpuGpuSameMaxTokens = flags.cpuGpuSameMaxTokens.toDiagnosticValue(),
        cpuGpuSameSamplerConfigHint = flags.cpuGpuSameSamplerConfigHint.toDiagnosticValue(),
        samplerRootCauseCandidate = flags.gpuPrefillProbeDiagnostics["gpu_sampler_root_cause_candidate"]
            ?: classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = suspiciousDetected,
                sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage
                    ?: classifyGpuOutputSourceCorruptionStage(
                        suspiciousReason = suspiciousReason,
                        uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
                    ),
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
                matrixMode = matrixMode,
                callbackQualityClassification = flags.callbackQualityClassification
                    ?: classifyCallbackQuality(
                        callbackCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                        twoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                        averageChunkLength = flags.gpuCallbackAverageChunkLength,
                    ),
            ),
    )
}

internal fun classifyGpuOutputSuspiciousFragmentReason(
    rawSample: String,
    promotedSample: String,
    finalSample: String,
    rawLength: Int?,
    finalLength: Int?,
    nonEmptyChunkCount: Int?,
): String {
    val samplePattern = Regex(":\\*\\*|ml2|g）に）：：|[：:]{3,}|[)）]{4,}|[*＊]{4,}|[{}\\[\\]]{8,}")
    val tailPattern = Regex("([：:）。、・*_＊#`\\-]){5,}|([ぁ-んァ-ヶ一-龠][：:）)]){3,}|([はがをにでとへもやの、。]){7,}")
    val markdownTailBias = Regex("(\\*\\*|###|__|[_*＊#`\\-]).*(\\*\\*|###|__|[_*＊#`\\-])")
    val repeatedPattern = Regex("([*＊#`_\\-]{2,}|[A-Za-z]{2,8}\\W?).*\\1.*\\1")
    val rawSuspicious = samplePattern.containsMatchIn(rawSample)
    val promotedSuspicious = samplePattern.containsMatchIn(promotedSample)
    val finalSuspicious = samplePattern.containsMatchIn(finalSample)
    val tailSample = listOf(rawSample.takeLast(80), promotedSample.takeLast(80), finalSample.takeLast(80)).joinToString(" ")
    val repeatedSuspicious = repeatedPattern.containsMatchIn(tailSample)
    val safeNonEmptyChunkCount = nonEmptyChunkCount ?: 0
    val averageRawChunkLength = if (safeNonEmptyChunkCount > 0 && rawLength != null) {
        rawLength.toDouble() / safeNonEmptyChunkCount
    } else {
        null
    }
    return when {
        safeNonEmptyChunkCount >= 24 && averageRawChunkLength != null && averageRawChunkLength <= 2.0 ->
            "many_tiny_fragments"
        safeNonEmptyChunkCount >= 16 && averageRawChunkLength != null && averageRawChunkLength <= 2.5 ->
            "tail_tiny_chunk_run"
        finalSuspicious && !rawSuspicious && !promotedSuspicious -> "final_text_only_suspicious_after_ui_or_markdown"
        promotedSuspicious && !rawSuspicious -> "promoted_text_suspicious_after_stream_join"
        rawSuspicious -> "raw_callback_suspicious_fragment"
        markdownTailBias.containsMatchIn(tailSample) -> "tail_markdown_fragment_bias"
        tailPattern.containsMatchIn(tailSample) -> "japanese_particle_or_punctuation_fragment_run"
        repeatedSuspicious -> "repeated_markdown_or_word_pattern"
        finalLength != null && rawLength != null && finalLength > rawLength * 3 && rawLength > 0 ->
            "final_text_expanded_unexpectedly"
        else -> "none"
    }
}

private fun classifyGpuOutputSuspiciousFragmentPosition(
    reason: String,
    headSample: String,
    tailSample: String,
): String {
    if (reason == "none" || reason == "unavailable") return "none"
    val samplePattern = Regex(":\\*\\*|ml2|g）に）：：|[：:]{3,}|[)）]{4,}|[*＊]{4,}|[{}\\[\\]]{8,}")
    val headSuspicious = samplePattern.containsMatchIn(headSample)
    val tailSuspicious = samplePattern.containsMatchIn(tailSample)
    return when {
        tailSuspicious && !headSuspicious -> "tail"
        headSuspicious && !tailSuspicious -> "head"
        tailSuspicious && headSuspicious -> "middle"
        reason == "many_tiny_fragments" -> "tail"
        else -> "middle"
    }
}

private fun calculateGpuOutputSuspiciousTailRatio(
    tailSample: String,
): String {
    if (tailSample.isBlank()) return "unavailable"
    val suspiciousChars = tailSample.count { ch ->
        ch in listOf('*', '＊', ':', '：', ')', '）', '(', '（', '}', '{', '[', ']') ||
            ch.isDigit() ||
            ch.code in 0x3040..0x309F && tailSample.contains("ml", ignoreCase = true)
    }
    return "%.3f".format(java.util.Locale.US, suspiciousChars.toDouble() / tailSample.length.coerceAtLeast(1))
}

private fun detectRepeatedMarkdownFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    return Regex("([*＊#`_\\-]{2,}).*\\1").containsMatchIn(combined) ||
        Regex("(:\\*\\*|\\*\\*)").findAll(combined).count() >= 2
}

private fun detectMixedJapaneseFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    val hasJapanese = combined.any { ch ->
        ch.code in 0x3040..0x30FF || ch.code in 0x4E00..0x9FFF
    }
    val hasAsciiNoise = Regex("(ml\\d+|[a-zA-Z]\\)|[a-zA-Z]）|\\d+[ぁ-んァ-ヶ一-龠])").containsMatchIn(combined)
    val manyJapanesePunctuation = Regex("[：）。、]{4,}").containsMatchIn(combined)
    return hasJapanese && (hasAsciiNoise || manyJapanesePunctuation)
}

private fun detectMixedLanguageFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    val hasJapanese = combined.any { ch ->
        ch.code in 0x3040..0x30FF || ch.code in 0x4E00..0x9FFF
    }
    val hasDevanagari = combined.any { ch -> ch.code in 0x0900..0x097F }
    val hasArabic = combined.any { ch -> ch.code in 0x0600..0x06FF }
    val hasLatinNoiseNearJapanese = Regex("[ぁ-んァ-ヶ一-龠][A-Za-z]{2,}|[A-Za-z]{2,}[ぁ-んァ-ヶ一-龠]").containsMatchIn(combined)
    return hasJapanese && (hasDevanagari || hasArabic || hasLatinNoiseNearJapanese)
}

private fun classifyGpuOutputQualityCandidateResult(
    suspiciousDetected: Boolean,
    finalLength: Int?,
    callbackNonEmptyCount: Int?,
): String =
    when {
        suspiciousDetected -> "quality_candidate_fail"
        (finalLength ?: 0) > 0 && (callbackNonEmptyCount ?: 0) > 0 -> "quality_candidate_pass"
        else -> "quality_candidate_unknown"
    }

private fun classifyGpuOutputQualityFailureBlockReason(
    suspiciousDetected: Boolean,
    suspiciousReason: String,
    collectOnlyEnabled: Boolean,
    uiAppendChangedText: Boolean?,
    sourceCorruptionStage: String?,
): String =
    when {
        !suspiciousDetected -> "none"
        uiAppendChangedText == true -> "ui_append_changed_callback_text"
        sourceCorruptionStage == "raw_callback" -> "callback_source_already_suspicious"
        collectOnlyEnabled -> "collect_only_still_suspicious"
        suspiciousReason in setOf("many_tiny_fragments", "tail_tiny_chunk_run") -> "chunk_boundary_or_sampler_fragmentation"
        else -> suspiciousReason
    }

private fun resolveGpuOutputQualityRecommendation(
    candidateResult: String,
    failureBlockReason: String,
    collectOnlyEnabled: Boolean,
): String =
    when {
        candidateResult == "quality_candidate_pass" -> "none"
        failureBlockReason == "ui_append_changed_callback_text" -> "inspect_ui_append_or_markdown_path"
        failureBlockReason == "callback_source_already_suspicious" -> "compare_sampler_modes_and_max_tokens"
        collectOnlyEnabled -> "compare_sampler_modes_or_runtime_stack"
        else -> "run_collect_only_and_sampler_matrix"
    }

private fun classifyGpuOutputSourceCorruptionStage(
    suspiciousReason: String,
    uiAppendChangedText: Boolean?,
): String =
    when {
        suspiciousReason == "none" -> "none"
        uiAppendChangedText == true -> "ui_append_or_final_commit"
        suspiciousReason == "final_text_only_suspicious_after_ui_or_markdown" -> "final_assistant_text"
        suspiciousReason == "promoted_text_suspicious_after_stream_join" -> "promoted_or_chunk_join"
        else -> "raw_callback"
    }

internal fun classifyCallbackQuality(
    callbackCount: Int?,
    twoCharOrLessRatio: String?,
    averageChunkLength: String?,
): String {
    val count = callbackCount ?: 0
    val smallRatio = twoCharOrLessRatio?.toDoubleOrNull()
    val average = averageChunkLength?.toDoubleOrNull()
    return when {
        count == 0 -> "unavailable"
        smallRatio != null && smallRatio >= 0.85 && count >= 20 -> "pathological_single_char_stream"
        smallRatio != null && smallRatio >= 0.65 && count >= 16 -> "severe_fragmentation"
        average != null && average < 4.0 && count >= 12 -> "moderate_fragmentation"
        average != null && average >= 8.0 -> "healthy_large_chunks"
        else -> "moderate_fragmentation"
    }
}

internal fun classifyCallbackQualityCompareResult(
    gpuCandidateResult: String?,
    gpuSuspiciousDetected: Boolean?,
    cpuSuspiciousDetected: Boolean?,
    cpuFinished: Boolean?,
    cpuSkippedReason: String?,
    cpuExceptionClass: String? = null,
    cpuFailureStage: String? = null,
    cpuCallbackCount: Int? = null,
): String {
    val gpuCorrupt = gpuCandidateResult == "quality_candidate_fail" || gpuSuspiciousDetected == true
    if (
        isCpuCallbackCompareUnavailable(
            cpuFinished = cpuFinished,
            cpuSkippedReason = cpuSkippedReason,
            cpuExceptionClass = cpuExceptionClass,
            cpuFailureStage = cpuFailureStage,
            cpuCallbackCount = cpuCallbackCount,
        )
    ) {
        return if (gpuCorrupt) "gpu_corrupt_cpu_unavailable" else "comparison_unavailable"
    }
    val cpuCorrupt = cpuSuspiciousDetected == true
    return when {
        gpuCorrupt && cpuCorrupt -> "cpu_and_gpu_corrupt"
        gpuCorrupt -> "gpu_only_corrupt"
        cpuCorrupt -> "cpu_only_corrupt"
        else -> "both_pass"
    }
}

private fun isCpuCallbackCompareUnavailable(
    cpuFinished: Boolean?,
    cpuSkippedReason: String?,
    cpuExceptionClass: String?,
    cpuFailureStage: String?,
    cpuCallbackCount: Int?,
): Boolean {
    if (cpuFinished != true) return true
    if (!cpuSkippedReason.isNullOrBlank() && cpuSkippedReason != "none") return true
    if (!cpuExceptionClass.isNullOrBlank() && cpuExceptionClass !in setOf("none", "unavailable")) return true
    if (cpuFailureStage in setOf("timeout", "engine_initialize", "conversation_create", "generate_start", "generate_collect")) {
        return true
    }
    if (cpuCallbackCount == null || cpuCallbackCount <= 0) return true
    return false
}

private fun classifyCallbackQualityCompareReason(
    cpuAvg: String?,
    gpuAvg: String?,
    cpuTwoCharRatio: String?,
    gpuTwoCharRatio: String?,
    cpuCount: Int?,
    gpuCount: Int?,
    cpuSkippedReason: String?,
): String {
    if (!cpuSkippedReason.isNullOrBlank() && cpuSkippedReason != "none") {
        return "cpu_compare_skipped:$cpuSkippedReason"
    }
    val cpuAverage = cpuAvg?.toDoubleOrNull()
    val gpuAverage = gpuAvg?.toDoubleOrNull()
    val cpuSmall = cpuTwoCharRatio?.toDoubleOrNull()
    val gpuSmall = gpuTwoCharRatio?.toDoubleOrNull()
    return when {
        cpuCount == null || gpuCount == null || cpuCount <= 0 || gpuCount <= 0 -> "comparison_unavailable"
        cpuAverage != null && gpuAverage != null &&
            cpuAverage >= 6.0 && gpuAverage <= 3.0 -> "gpu_chunks_much_smaller_than_cpu"
        cpuSmall != null && gpuSmall != null &&
            gpuSmall - cpuSmall >= 0.40 -> "gpu_two_char_ratio_much_higher_than_cpu"
        cpuAverage != null && gpuAverage != null &&
            kotlin.math.abs(cpuAverage - gpuAverage) <= 2.0 -> "cpu_gpu_callback_chunks_similar"
        else -> "cpu_gpu_callback_quality_recorded"
    }
}

private fun calculateDoubleRatio(
    numerator: String?,
    denominator: String?,
): String {
    val n = numerator?.toDoubleOrNull()
    val d = denominator?.toDoubleOrNull()
    if (n == null || d == null || d == 0.0) return "unavailable"
    return "%.3f".format(java.util.Locale.US, n / d)
}

private fun calculateDoubleDelta(
    lhs: String?,
    rhs: String?,
): String {
    val left = lhs?.toDoubleOrNull()
    val right = rhs?.toDoubleOrNull()
    if (left == null || right == null) return "unavailable"
    return "%.3f".format(java.util.Locale.US, left - right)
}

private fun calculateIntDelta(
    lhs: Int?,
    rhs: Int?,
): String =
    if (lhs == null || rhs == null) {
        "unavailable"
    } else {
        (lhs - rhs).toString()
    }

private fun classifyCpuGpuRawTextSimilarityHint(
    cpuCount: Int?,
    gpuCount: Int?,
    cpuSuspiciousDetected: Boolean?,
    gpuSuspiciousDetected: Boolean?,
    cpuAverageChunkLength: String?,
    gpuAverageChunkLength: String?,
): String {
    if (cpuCount == null || gpuCount == null || cpuCount <= 0 || gpuCount <= 0) return "comparison_unavailable"
    if (gpuSuspiciousDetected == true && cpuSuspiciousDetected != true) return "gpu_raw_callback_suspicious_cpu_clean"
    if (gpuSuspiciousDetected == true && cpuSuspiciousDetected == true) return "both_raw_callbacks_suspicious"
    val cpuAverage = cpuAverageChunkLength?.toDoubleOrNull()
    val gpuAverage = gpuAverageChunkLength?.toDoubleOrNull()
    return when {
        cpuAverage != null && gpuAverage != null && kotlin.math.abs(cpuAverage - gpuAverage) <= 2.0 ->
            "chunk_shape_similar"
        cpuAverage != null && gpuAverage != null && gpuAverage < cpuAverage ->
            "gpu_chunks_smaller"
        cpuAverage != null && gpuAverage != null && gpuAverage > cpuAverage ->
            "gpu_chunks_larger"
        else -> "raw_text_not_directly_compared"
    }
}

internal fun classifyGpuSamplerRootCauseCandidate(
    suspiciousDetected: Boolean,
    sourceCorruptionStage: String?,
    uiAppendChangedText: Boolean?,
    matrixMode: String?,
    callbackQualityClassification: String?,
): String =
    when {
        !suspiciousDetected -> "unknown"
        uiAppendChangedText == true -> "streaming_join_issue"
        sourceCorruptionStage == "raw_callback" &&
            matrixMode in setOf(
                GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION,
                GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
            ) -> "not_sampler_related"
        sourceCorruptionStage == "raw_callback" &&
            callbackQualityClassification in setOf("severe_fragmentation", "pathological_single_char_stream") ->
            "runtime_decode_fragmentation"
        sourceCorruptionStage == "raw_callback" -> "callback_source_corruption"
        matrixMode == GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL -> "sampler_related"
        else -> "unknown"
    }

private fun buildGpuPerformanceDiagnostics(flags: LocalRouteDiagnosticFlags): GpuPerformanceDiagnostics {
    val engineCreateOrReuse = flags.gpuPerfEngineCreateOrReuse ?: when (flags.heldEngineReused) {
        true -> "reuse"
        false -> "create"
        null -> "unavailable"
    }
    val visibleTps = flags.gpuPerfLamiVisibleTokensPerSecond
        ?: estimateVisibleTokensPerSecond(
            charLength = flags.gpuOutputPromotedTextLength ?: flags.gpuOutputFinalAssistantTextLength,
            elapsedMs = flags.gpuPerfFirstToLastCallbackMs ?: flags.gpuPerfCallbackTotalElapsedMs,
        )
    val slowReason = flags.gpuPerfSlowPathReason
        ?: classifyGpuPerfSlowPathReason(
            engineCreateOrReuse = engineCreateOrReuse,
            engineAcquireElapsedMs = flags.gpuPerfEngineAcquireElapsedMs,
            generateToFirstTokenMs = flags.gpuPerfGenerateToFirstTokenMs,
            callbackTotalElapsedMs = flags.gpuPerfCallbackTotalElapsedMs,
            visibleTokensPerSecond = visibleTps,
            tokenizerCountDurationMs = flags.gpuPerfTokenizerCountDurationMs,
        )
    val slowDetected = flags.gpuPerfSlowPathDetected
        ?: (slowReason != "none" && slowReason != "unavailable")
    return GpuPerformanceDiagnostics(
        engineAcquireElapsedMs = flags.gpuPerfEngineAcquireElapsedMs?.toString() ?: "unavailable",
        engineCreateOrReuse = engineCreateOrReuse,
        conversationCreateElapsedMs = flags.gpuPerfConversationCreateElapsedMs?.toString() ?: "unavailable",
        generateToFirstTokenMs = flags.gpuPerfGenerateToFirstTokenMs?.toString() ?: "unavailable",
        firstToLastCallbackMs = flags.gpuPerfFirstToLastCallbackMs?.toString() ?: "unavailable",
        callbackTotalElapsedMs = flags.gpuPerfCallbackTotalElapsedMs?.toString() ?: "unavailable",
        backendTokensPerSecond = flags.gpuPerfBackendTokensPerSecond ?: "unavailable",
        lamiVisibleTokensPerSecond = visibleTps,
        tokenizerCountDurationMs = flags.gpuPerfTokenizerCountDurationMs?.toString() ?: "unavailable",
        slowPathDetected = slowDetected.toString(),
        slowPathReason = slowReason,
    )
}

private fun estimateVisibleTokensPerSecond(
    charLength: Int?,
    elapsedMs: Long?,
): String {
    if (charLength == null || elapsedMs == null || elapsedMs <= 0L) return "unavailable"
    val estimatedTokens = (charLength / 4.0).coerceAtLeast(1.0)
    return "%.1f".format(java.util.Locale.US, estimatedTokens * 1000.0 / elapsedMs)
}

internal fun classifyGpuPerfSlowPathReason(
    engineCreateOrReuse: String,
    engineAcquireElapsedMs: Long?,
    generateToFirstTokenMs: Long?,
    callbackTotalElapsedMs: Long?,
    visibleTokensPerSecond: String,
    tokenizerCountDurationMs: Long?,
): String {
    val visibleTps = visibleTokensPerSecond.toDoubleOrNull()
    return when {
        generateToFirstTokenMs != null && generateToFirstTokenMs > 2_000L -> "slow_first_token"
        tokenizerCountDurationMs != null && tokenizerCountDurationMs > 1_000L -> "slow_tokenizer_count"
        engineCreateOrReuse == "create" && engineAcquireElapsedMs != null && engineAcquireElapsedMs > 2_000L ->
            "cold_engine_load"
        callbackTotalElapsedMs != null && visibleTps != null && visibleTps < 8.0 -> "slow_callback_stream"
        engineCreateOrReuse == "reuse" && visibleTps != null && visibleTps < 8.0 -> "runtime_or_backend_slow"
        else -> "none"
    }
}

private fun buildGpuHolderLifecycleDiagnostics(flags: LocalRouteDiagnosticFlags): GpuHolderLifecycleDiagnostics {
    val clearReason = flags.gpuHolderLifecycleClearReasonDetail
        ?: flags.gpuAlignmentHolderClearReason
        ?: flags.heldEngineDestroyReason
        ?: "unavailable"
    val eventAfterSuccess = flags.gpuHolderLifecycleEventAfterSuccess
        ?: when {
            flags.gpuHolderLifecycleClearAfterSuccessMs != null -> "clear_after_success"
            flags.gpuHolderLifecycleClearDuringActiveGenerate == true -> "clear_during_active_generate"
            clearReason != "unavailable" -> "clear_without_recorded_success"
            else -> "none"
        }
    val backgroundSource = flags.gpuHolderLifecycleBackgroundDetectionSource
        ?: when {
            clearReason == "app-backgrounded" -> "HeldEngineLifecycleBridge.onStop"
            clearReason == "background-timeout" -> "LocalInferenceEngineHolder.background_timeout"
            else -> "unavailable"
        }
    val visibility = flags.gpuHolderLifecycleLastAppVisibility
        ?: when (flags.gpuHolderLifecycleLastActivityState) {
            "background" -> "background"
            "foreground" -> "foreground"
            else -> "unavailable"
        }
    return GpuHolderLifecycleDiagnostics(
        eventAfterSuccess = eventAfterSuccess,
        lastActivityState = flags.gpuHolderLifecycleLastActivityState ?: "unavailable",
        lastAppVisibility = visibility,
        clearTriggerElapsedMs = flags.gpuHolderLifecycleClearTriggerElapsedMs?.toString() ?: "unavailable",
        clearAfterSuccessMs = flags.gpuHolderLifecycleClearAfterSuccessMs?.toString() ?: "unavailable",
        clearDuringActiveGenerate = flags.gpuHolderLifecycleClearDuringActiveGenerate.toDiagnosticValue(),
        clearAfterUiAppend = flags.gpuHolderLifecycleClearAfterUiAppend.toDiagnosticValue(),
        clearReasonDetail = clearReason,
        backgroundDetectionSource = backgroundSource,
        onStopDeferred = flags.gpuHolderLifecycleOnStopDeferred.toDiagnosticValue(),
        onStopDeferReason = flags.gpuHolderLifecycleOnStopDeferReason.toDiagnosticValue(),
        clearSuppressedAfterSuccess = flags.gpuHolderLifecycleClearSuppressedAfterSuccess.toDiagnosticValue(),
        clearSuppressedReason = flags.gpuHolderLifecycleClearSuppressedReason.toDiagnosticValue(),
        actualBackgroundConfirmed = flags.gpuHolderLifecycleActualBackgroundConfirmed.toDiagnosticValue(),
        reuseExpectedNextTurn = flags.gpuHolderLifecycleReuseExpectedNextTurn.toDiagnosticValue(),
    )
}

private fun buildGpuPrefillProbeClarityDiagnostics(
    flags: LocalRouteDiagnosticFlags,
): GpuPrefillProbeClarityDiagnostics {
    val parsed = flags.gpuPrefillProbeDiagnostics
    val requested = flags.gpuPrefillProbeRequested
        ?: parsed["probe_requested"]?.toBooleanStrictOrNull()
        ?: false
    val enabled = flags.gpuPrefillProbeEnabled
        ?: parsed["probe_enabled"]?.toBooleanStrictOrNull()
        ?: false
    val blocks = flags.gpuPrefillProbeBlocksNormalGenerate
        ?: parsed["probe_skipped_normal_generate"]?.toBooleanStrictOrNull()
        ?: false
    val reason = flags.gpuPrefillProbeBlockReason
        ?: parsed["probe_start_blocked_reason"]
        ?: parsed["probe_normal_generate_blocked_reason"]
        ?: if (blocks) "probe_opt_in_runs_without_normal_generate" else "none"
    val requiresHeld = flags.gpuPrefillProbeRequiresHeldEngine
        ?: parsed["probe_use_held_engine_requested"]?.toBooleanStrictOrNull()
        ?: false
    val heldPresent = flags.gpuPrefillProbeHeldEnginePresent
        ?: parsed["probe_held_engine_present_before"]?.toBooleanStrictOrNull()
    val recommendation = flags.gpuPrefillProbeDisableRecommendation
        ?: if (blocks) "set_debug.lami.gpu_prefill_probe_false_for_normal_generation" else "none"
    return GpuPrefillProbeClarityDiagnostics(
        enabled = enabled.toString(),
        requested = requested.toString(),
        blocksNormalGenerate = (enabled && blocks).toString(),
        blockReason = if (enabled && blocks) reason else "none",
        requiresHeldEngine = requiresHeld.toString(),
        heldEnginePresent = heldPresent.toDiagnosticValue(),
        disableRecommendation = recommendation,
    )
}

internal fun buildLocalRouteDiagnosticTrace(
    stage: String,
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags = LocalRouteDiagnosticFlags(),
    elapsedMs: Long = 0L,
    gpuWatchdogTimeoutMs: Long = GPU_EXPERIMENTAL_STAGE_TIMEOUT_MS,
): String {
    val normalizedElapsedMs = elapsedMs.coerceAtLeast(0L)
    val failureStage = flags.failureStage?.takeIf { it.isNotBlank() } ?: "none"
    val gpuTimeoutStage = resolveGpuExperimentalTimeoutStage(failureStage, flags)
    val engineCreateDurationMs = flags.engineCreateDurationMs
        ?: normalizedElapsedMs.takeIf {
            flags.engineCreateStarted == true &&
                (flags.engineCreateFinished == false || flags.engineCreateFinished == true)
        }
    val engineCreateTimeoutSuspected =
        gpuTimeoutStage == "engine_constructor" &&
            flags.engineCreateStarted == true &&
            flags.engineCreateFinished == false &&
            failureStage != "none"
    val gpuInitializationTimeoutSuspected =
        gpuTimeoutStage in setOf("engine_config_build", "engine_constructor", "engine_initialize", "conversation_create") &&
            failureStage != "none"
    val gpuGenerateBeforeFirstTokenTimeoutSuspected =
        gpuTimeoutStage == "generate_before_first_token" && failureStage != "none"
    val gpuTimeoutFailure = failureStage.contains("timeout") &&
        context.baselineRole == LITERT_LM_BASELINE_GPU_EXPERIMENTAL
    val gpuGenerateExceptionFailure =
        flags.gpuGenerateExceptionSeen == true &&
            context.baselineRole == LITERT_LM_BASELINE_GPU_EXPERIMENTAL
    val guardRecommendation = if (
        engineCreateTimeoutSuspected ||
        gpuInitializationTimeoutSuspected ||
        gpuTimeoutFailure ||
        gpuGenerateExceptionFailure
    ) {
        GPU_EXPERIMENTAL_TIMEOUT_GUARD_RECOMMENDATION
    } else {
        "unavailable"
    }
    val gpuConfig = flags.gpuConfigDiagnostics
        ?: buildGpuRouteConfigDiagnostics(
            modelPath = context.selectedModelPath,
            cacheDirPath = null,
            preferredBackend = context.preferredBackend,
        )
    val artisanApi = buildLiteRtLmBackendArtisanApiDiagnostics(
        selectedModelPath = context.selectedModelPath,
    )
    val gpuFailureClassification = classifyGpuLiteRtFailure(
        message = flags.gpuPrefillProbeDiagnostics["probe_exception_cause_message_raw"]
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_cause_message"]
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_root_cause_message"]
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_chain"]
            ?: flags.gpuGenerateExceptionMessageRaw
            ?: flags.gpuGenerateExceptionMessageSanitized
            ?: flags.gpuCallbackExceptionMessage
            ?: flags.gpuCallbackExceptionChain,
        failureStage = failureStage,
        timeoutStage = gpuTimeoutStage,
        generateStarted = flags.generateStarted,
        firstTokenReceived = flags.firstTokenReceived,
        engineInitializeFinished = flags.engineInitializeFinished,
        conversationCreateFinished = flags.conversationCreateFinished,
    )
    val liteRtLmError = classifyLiteRtLmError(
        message = flags.gpuGenerateExceptionMessageRaw
            ?: flags.gpuGenerateExceptionMessageSanitized
            ?: flags.gpuCallbackExceptionMessage
            ?: flags.gpuCallbackExceptionChain
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_cause_message_raw"]
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_cause_message"]
            ?: flags.gpuPrefillProbeDiagnostics["probe_exception_chain"],
    )
    val effectiveLiteRtLmError = LiteRtLmErrorClassification(
        kind = flags.liteRtLmErrorKind ?: liteRtLmError.kind,
        statusCode = flags.liteRtLmErrorStatusCode ?: liteRtLmError.statusCode,
        primaryFile = flags.liteRtLmErrorPrimaryFile ?: liteRtLmError.primaryFile,
        primaryLine = flags.liteRtLmErrorPrimaryLine ?: liteRtLmError.primaryLine,
        secondaryFile = flags.liteRtLmErrorSecondaryFile ?: liteRtLmError.secondaryFile,
        secondaryLine = flags.liteRtLmErrorSecondaryLine ?: liteRtLmError.secondaryLine,
        recoverabilityHint = flags.liteRtLmErrorRecoverabilityHint ?: liteRtLmError.recoverabilityHint,
        summary = flags.gpuGenerateExceptionSummary ?: liteRtLmError.summary,
    )
    val compiledModelExecutorFailureCategory =
        classifyLiteRtCompiledModelExecutorFailureCategory(effectiveLiteRtLmError)
    val galleryStackGpuProbe = buildGalleryStackGpuProbeRuntimeDiagnostics(
        selectedModelPath = context.selectedModelPath,
        nativeLibraryDir = context.nativeLibraryDir,
        preferredBackend = context.preferredBackend,
    )
    val standardGpuProbe = buildStandardGpuProbeDiagnostics(
        context = context,
        flags = flags,
        failureStage = failureStage,
    )
    val runtimeAlignmentResultCandidate = resolveRuntimeAlignmentProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )
    val runtimeAlignmentProbe = buildRuntimeAlignmentProbeDiagnostics(
        nativeLibraryDir = context.nativeLibraryDir,
        resultCandidate = runtimeAlignmentResultCandidate,
        successGate = resolveRuntimeAlignmentProbeSuccessGate(
            context = context,
            flags = flags,
        ),
    )
    val minimalRuntimeResultCandidate = resolveMinimalRuntimeProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )
    val minimalRuntimeProbe = buildMinimalRuntimeProbeDiagnostics(
        nativeLibraryDir = context.nativeLibraryDir,
        resultCandidate = minimalRuntimeResultCandidate,
        successGate = resolveMinimalRuntimeProbeSuccessGate(
            context = context,
            flags = flags,
        ),
    )
    val loadedRuntimeNativeStack = buildLoadedRuntimeNativeStackDiagnostics(
        nativeLibraryDir = context.nativeLibraryDir,
        standardCandidateResult = standardGpuProbe.runtimeAlignmentCandidateResult,
        runtimeAlignmentResult = runtimeAlignmentResultCandidate,
    )
    val gpuInternalSurfaceProbe = flags.gpuInternalSurfaceProbeDiagnostics.ifEmpty {
        buildGpuInternalSurfaceProbeDiagnostics(
            preferredBackend = context.preferredBackend,
            nativeLibraryDir = context.nativeLibraryDir,
        ).toRouteDiagnosticMap()
    }.withGpuInternalSurfaceProbePresenceDefaults(
        preferredBackend = context.preferredBackend,
    )
    val standardGpuMinimalRuntimeCandidate = buildStandardGpuMinimalRuntimeCandidateDiagnostics(
        context = context,
        flags = flags,
        failureStage = failureStage,
        loadedRuntimeNativeStack = loadedRuntimeNativeStack,
    )
    val gpuAlignmentHolder = buildGpuAlignmentHolderDiagnostics(
        flags = flags,
        failureStage = failureStage,
    )
    val gpuOutputQuality = buildGpuOutputQualityDiagnostics(flags)
    val gpuPerformance = buildGpuPerformanceDiagnostics(flags)
    val gpuHolderLifecycle = buildGpuHolderLifecycleDiagnostics(flags)
    val gpuPrefillProbe = buildGpuPrefillProbeClarityDiagnostics(flags)
    val runtimeFingerprints = buildRuntimeExecutorFingerprints(
        context = context,
        gpuConfig = gpuConfig,
        gpuOutputQuality = gpuOutputQuality,
        loadedRuntimeNativeStack = loadedRuntimeNativeStack,
        artisanApi = artisanApi,
        compiledModelExecutorFailureCategory = compiledModelExecutorFailureCategory,
        failureStage = failureStage,
    )
    val finalResponseProbeDiagnostics = flags.gpuPrefillProbeDiagnostics
    val npuStandardRouteDevGateDiagnostics = buildNpuStandardRoutePhase1Diagnostics(context = context)
    return (
        listOf(
        "LOCAL_ROUTE_DIAG",
        "stage=$stage",
        "selected_model_name=${context.selectedModelName}",
        "selected_model_file=${context.selectedModelFile}",
        "selected_model_path=${context.selectedModelPath}",
        "selected_model_slot=${context.selectedModelSlot}",
        "generic_fallback_model_configured=${context.genericFallbackModelConfigured}",
        "npu_preview_model_configured=${context.npuPreviewModelConfigured}",
        "model_kind=${context.modelKind}",
        "preferred_backend=${context.preferredBackend}",
        "baseline_role=${context.baselineRole}",
        "generic_model_cpu_baseline=${context.genericModelCpuBaseline}",
        "npu_standard_route_mode=${context.npuStandardRouteMode}",
        "effective_npu_standard_route_mode=${context.effectiveNpuStandardRouteMode}",
        "should_enter_npu_s1=${context.shouldEnterNpuS1}",
        "local_route_entered=${context.localRouteEntered}",
        "normal_chat_native_route_blocked=${context.normalChatNativeRouteBlocked}",
        "blocked_reason=${context.blockedReason}",
        "guard_recommendation=$guardRecommendation",
        "held_engine_exists=${flags.heldEngineExists.toDiagnosticValue()}",
        "held_engine_reused=${flags.heldEngineReused.toDiagnosticValue()}",
        "holder_created=${flags.holderCreated.toDiagnosticValue()}",
        "holder_acquired=${flags.holderAcquired.toDiagnosticValue()}",
        "holder_reused=${flags.holderReused.toDiagnosticValue()}",
        "holder_invalidated=${flags.holderInvalidated.toDiagnosticValue()}",
        "holder_closed=${flags.holderClosed.toDiagnosticValue()}",
        "holder_timeout_cleanup=${flags.holderTimeoutCleanup.toDiagnosticValue()}",
        "holder_failure_cleanup=${flags.holderFailureCleanup.toDiagnosticValue()}",
        "holder_process_restart=${flags.holderProcessRestart.toDiagnosticValue()}",
        "held_engine_lifecycle_history=${flags.heldEngineLifecycleHistory.toDiagnosticValue()}",
        "held_engine_destroy_reason=${flags.heldEngineDestroyReason.toDiagnosticValue()}",
        "held_engine_last_owner=${flags.heldEngineLastOwner.toDiagnosticValue()}",
        "held_engine_last_failure_stage=${flags.heldEngineLastFailureStage.toDiagnosticValue()}",
        "held_engine_snapshot_before_destroy=${flags.heldEngineSnapshotBeforeDestroy.toDiagnosticValue()}",
        "gpu_alignment_holder_present_before_acquire=${gpuAlignmentHolder.presentBeforeAcquire}",
        "gpu_alignment_holder_acquire_result=${gpuAlignmentHolder.acquireResult}",
        "gpu_alignment_holder_reused=${gpuAlignmentHolder.reused}",
        "gpu_alignment_holder_created=${gpuAlignmentHolder.created}",
        "gpu_alignment_holder_cleared=${gpuAlignmentHolder.cleared}",
        "gpu_alignment_holder_clear_reason=${gpuAlignmentHolder.clearReason}",
        "gpu_alignment_holder_close_started=${gpuAlignmentHolder.closeStarted}",
        "gpu_alignment_holder_close_finished=${gpuAlignmentHolder.closeFinished}",
        "gpu_alignment_holder_reuse_block_reason=${gpuAlignmentHolder.reuseBlockReason}",
        "gpu_alignment_holder_model_path_changed=${gpuAlignmentHolder.modelPathChanged}",
        "gpu_alignment_holder_backend_changed=${gpuAlignmentHolder.backendChanged}",
        "gpu_alignment_holder_app_process_start_marker=${gpuAlignmentHolder.appProcessStartMarker}",
        "gpu_alignment_turn_index_if_available=${gpuAlignmentHolder.turnIndexIfAvailable}",
        "gpu_alignment_previous_turn_success=${gpuAlignmentHolder.previousTurnSuccess}",
        "gpu_alignment_previous_turn_failure_stage=${gpuAlignmentHolder.previousTurnFailureStage}",
        "engine_create_started=${flags.engineCreateStarted.toDiagnosticValue()}",
        "engine_create_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
        "engine_config_build_started=${flags.engineConfigBuildStarted.toDiagnosticValue()}",
        "engine_config_build_finished=${flags.engineConfigBuildFinished.toDiagnosticValue()}",
        "engine_initialize_started=${flags.engineInitializeStarted.toDiagnosticValue()}",
        "engine_initialize_finished=${flags.engineInitializeFinished.toDiagnosticValue()}",
        "conversation_create_started=${flags.conversationCreateStarted.toDiagnosticValue()}",
        "conversation_create_finished=${flags.conversationCreateFinished.toDiagnosticValue()}",
        "generate_started=${flags.generateStarted.toDiagnosticValue()}",
        "first_token_received=${flags.firstTokenReceived.toDiagnosticValue()}",
        "failure_stage=$failureStage",
        "gpu_watchdog_failure_stage=${resolveGpuWatchdogFailureStage(failureStage, flags)}",
        "fallback_used=${flags.fallbackUsed.toDiagnosticValue()}",
        "stale_callback_ignored=${flags.staleCallbackIgnored.toDiagnosticValue()}",
        "elapsed_ms=$normalizedElapsedMs",
        "gpu_watchdog_timeout_ms=$gpuWatchdogTimeoutMs",
        "gpu_watchdog_mode=${resolveGpuExperimentalWatchdogMode(gpuWatchdogTimeoutMs)}",
        "gpu_timeout_stage=$gpuTimeoutStage",
        "gpu_timeout_elapsed_ms=$normalizedElapsedMs",
        "gpu_engine_create_duration_ms=${engineCreateDurationMs?.coerceAtLeast(0L)?.toString() ?: "unavailable"}",
        "gpu_engine_create_started=${flags.engineCreateStarted.toDiagnosticValue()}",
        "gpu_engine_create_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
        "gpu_engine_create_timeout_suspected=$engineCreateTimeoutSuspected",
        "gpu_conversation_create_started=${flags.conversationCreateStarted.toDiagnosticValue()}",
        "gpu_conversation_create_finished=${flags.conversationCreateFinished.toDiagnosticValue()}",
        "gpu_generate_started=${flags.generateStarted.toDiagnosticValue()}",
        "gpu_first_token_received=${flags.firstTokenReceived.toDiagnosticValue()}",
        "gpu_first_token_elapsed_ms=${flags.firstTokenElapsedMs?.coerceAtLeast(0L)?.toString() ?: "unavailable"}",
        "generate_call_started_at_elapsed_ms=${flags.generateStartedElapsedMs?.coerceAtLeast(0L)?.toString() ?: "unavailable"}",
        "first_token_received_at_elapsed_ms=${flags.firstTokenElapsedMs?.coerceAtLeast(0L)?.toString() ?: "unavailable"}",
        "generate_before_first_token_elapsed_ms=${resolveGenerateBeforeFirstTokenElapsedMs(flags, normalizedElapsedMs)}",
        "gpu_generate_before_first_token_timeout_suspected=$gpuGenerateBeforeFirstTokenTimeoutSuspected",
        "gpu_last_known_stage=${resolveGpuLastKnownStage(flags)}",
        "gpu_held_engine_exists=${flags.heldEngineExists.toDiagnosticValue()}",
        "gpu_held_engine_reused=${flags.heldEngineReused.toDiagnosticValue()}",
        "gpu_model_kind=${context.modelKind}",
        "gpu_selected_model_name=${context.selectedModelName}",
        "gpu_selected_model_file=${context.selectedModelFile}",
        "gpu_model_path=${gpuConfig.modelPath}",
        "gpu_model_path_tail=${gpuConfig.modelPathTail}",
        "gpu_backend_setting=${context.preferredBackend}",
        "gpu_compatibility_mode=${resolveGpuCompatibilityModeForBackend(context.preferredBackend)}",
        "gpu_engine_config_profile=${resolveGpuEngineConfigProfileForBackend(context.preferredBackend)}",
        "gpu_experiment_mode=${gpuConfig.experimentMode}",
        "experiment_mode=${gpuConfig.experimentMode}",
        "gpu_experiment_modes_available=${gpuConfig.availableExperimentModes}",
        "gpu_normal_chat_engine_config_style=${gpuConfig.normalChatEngineConfigStyle}",
        "gpu_recommended_next_config_variant=${gpuConfig.recommendedNextConfigVariant}",
        "gpu_cache_dir_mode=${resolveGpuCacheDirModeForBackend(context.preferredBackend, gpuConfig.experimentMode)}",
        "gpu_engine_config_model_path=${gpuConfig.modelPath}",
        "gpu_engine_config_model_path_tail=${gpuConfig.modelPathTail}",
        "gpu_engine_config_cache_dir=${gpuConfig.cacheDir}",
        "gpu_engine_config_cache_dir_present=${gpuConfig.cacheDirPresent}",
        "gpu_engine_config_backend=${gpuConfig.backend}",
        "gpu_engine_config_vision_backend=${gpuConfig.visionBackend}",
        "gpu_engine_config_audio_backend=${gpuConfig.audioBackend}",
        "gpu_engine_config_max_tokens=${gpuConfig.maxTokens}",
        "gpu_engine_config_build_started=${flags.engineConfigBuildStarted.toDiagnosticValue()}",
        "gpu_engine_config_build_finished=${flags.engineConfigBuildFinished.toDiagnosticValue()}",
        "gpu_engine_constructor_started=${flags.engineCreateStarted.toDiagnosticValue()}",
        "gpu_engine_constructor_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
        "gpu_engine_initialize_started=${flags.engineInitializeStarted.toDiagnosticValue()}",
        "gpu_engine_initialize_finished=${flags.engineInitializeFinished.toDiagnosticValue()}",
        "gpu_engine_initialize_call_state=${resolveGpuInitializeCallState(flags)}",
        "gpu_timeout_checkpoint=${resolveGpuTimeoutCheckpoint(flags)}",
        "gpu_model_path_mode=${resolveGpuModelPathModeForBackend(context.preferredBackend)}",
        "gpu_sampler_config_profile=${resolveGpuSamplerConfigProfileForBackend(context.preferredBackend)}",
        "gpu_sampler_config_enabled=${gpuConfig.samplerConfigEnabled}",
        "gpu_sampler_config_top_k=${gpuConfig.samplerTopK}",
        "gpu_sampler_config_top_p=${gpuConfig.samplerTopP}",
        "gpu_sampler_config_temperature=${gpuConfig.samplerTemperature}",
        "gpu_sampler_acceleration_policy=${gpuConfig.samplerAccelerationPolicy}",
        "gpu_conversation_config_profile=${gpuConfig.conversationConfigProfile}",
        "gpu_conversation_config_sampler_present=${gpuConfig.conversationConfigSamplerPresent}",
        "gpu_options_configured=${gpuConfig.gpuOptionsConfigured}",
        "gpu_options_source=${gpuConfig.gpuOptionsSource}",
        "gpu_thinking_enabled=${gpuConfig.thinkingEnabled}",
        "gpu_speculative_decoding_enabled=${gpuConfig.speculativeDecodingEnabled}",
        "executor_selection_fingerprint=${runtimeFingerprints.executorSelectionFingerprint}",
        "runtime_backend_fingerprint=${runtimeFingerprints.runtimeBackendFingerprint}",
        "runtime_executor_fingerprint=${runtimeFingerprints.runtimeExecutorFingerprint}",
        "runtime_dispatch_fingerprint=${runtimeFingerprints.runtimeDispatchFingerprint}",
        "runtime_compiled_model_fingerprint=${runtimeFingerprints.runtimeCompiledModelFingerprint}",
        "engine_config_fingerprint=${runtimeFingerprints.engineConfigFingerprint}",
        "conversation_config_fingerprint=${runtimeFingerprints.conversationConfigFingerprint}",
        "sampler_config_fingerprint=${runtimeFingerprints.samplerConfigFingerprint}",
        "gpu_output_quality_matrix_mode=${gpuOutputQuality.matrixMode}",
        "gpu_output_quality_sampler_mode=${gpuOutputQuality.samplerMode}",
        "gpu_output_quality_streaming_mode=${gpuOutputQuality.streamingMode}",
        "gpu_output_quality_effective_max_tokens=${gpuOutputQuality.effectiveMaxTokens}",
        "gpu_output_quality_collect_only_enabled=${gpuOutputQuality.collectOnlyEnabled}",
        "gpu_output_quality_ui_incremental_append_enabled=${gpuOutputQuality.uiIncrementalAppendEnabled}",
        "gpu_output_quality_probe_short_max_tokens=${gpuConfig.outputQualityProbeShortMaxTokensEnabled}",
        "gpu_output_quality_probe_effective_max_tokens=${gpuConfig.outputQualityProbeEffectiveMaxTokens}",
        "edge_gallery_parity_mode=${resolveEdgeGalleryParityModeForMatrixMode(gpuOutputQuality.matrixMode)}",
        "edge_gallery_parity_engine_config_profile=${if (gpuOutputQuality.matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES) resolveGpuEngineConfigProfileForBackend(context.preferredBackend) else "unavailable"}",
        "edge_gallery_parity_conversation_config_profile=${if (gpuOutputQuality.matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES) gpuConfig.conversationConfigProfile else "unavailable"}",
        "edge_gallery_parity_callback_mode=${resolveEdgeGalleryParityCallbackModeForMatrixMode(gpuOutputQuality.matrixMode, gpuOutputQuality.streamingMode)}",
        "edge_gallery_parity_holder_reuse=${resolveEdgeGalleryParityHolderReuseForMatrixMode(gpuOutputQuality.matrixMode, flags.gpuCallbackStreamingReusedHeldEngine ?: flags.heldEngineReused)}",
        "edge_gallery_parity_cache_dir_mode=${if (gpuOutputQuality.matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES) resolveGpuCacheDirModeForBackend(context.preferredBackend, gpuConfig.experimentMode) else "unavailable"}",
        "edge_gallery_parity_sampler_present=${if (gpuOutputQuality.matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES) gpuConfig.conversationConfigSamplerPresent else "unavailable"}",
        "edge_gallery_parity_candidate_result=${if (gpuOutputQuality.matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES) gpuOutputQuality.candidateResult else "unavailable"}",
        "edge_gallery_parity_difference_summary=${resolveEdgeGalleryParityDifferenceSummary(gpuOutputQuality.matrixMode, gpuOutputQuality.sourceCorruptionStage, gpuOutputQuality.candidateResult, gpuOutputQuality.samplerRootCauseCandidate)}",
        "edge_gallery_executor_probe_result=${runtimeFingerprints.edgeGalleryExecutorProbeResult}",
        "gpu_internal_surface_probe_enabled=${gpuInternalSurfaceProbe["gpu_internal_surface_probe_enabled"]?.toDiagnosticValue() ?: "unavailable"}",
        "gpu_internal_surface_probe_result=${gpuInternalSurfaceProbe["gpu_internal_surface_probe_result"]?.toDiagnosticValue() ?: "unavailable"}",
        "gpu_internal_surface_probe_disabled_reason=${gpuInternalSurfaceProbe["gpu_internal_surface_probe_disabled_reason"]?.toDiagnosticValue() ?: "unavailable"}",
        "edge_gallery_executor_difference_summary=${runtimeFingerprints.edgeGalleryExecutorDifferenceSummary}",
        "edge_gallery_generate_api_candidate=${finalResponseProbeDiagnostics["edge_gallery_generate_api_candidate"] ?: "unavailable"}",
        "edge_gallery_callback_text_semantics_candidate=${finalResponseProbeDiagnostics["edge_gallery_callback_text_semantics_candidate"] ?: "unavailable"}",
        "edge_gallery_callback_done_semantics_candidate=${finalResponseProbeDiagnostics["edge_gallery_callback_done_semantics_candidate"] ?: "unavailable"}",
        "lami_callback_join_strategy_candidate=${finalResponseProbeDiagnostics["lami_callback_join_strategy_candidate"] ?: "unavailable"}",
        "gpu_callback_last_non_empty_text_length=${finalResponseProbeDiagnostics["gpu_callback_last_non_empty_text_length"] ?: "unavailable"}",
        "gpu_callback_last_non_empty_text_sha256=${finalResponseProbeDiagnostics["gpu_callback_last_non_empty_text_sha256"] ?: "unavailable"}",
        "gpu_callback_accumulated_text_sha256=${finalResponseProbeDiagnostics["gpu_callback_accumulated_text_sha256"] ?: "unavailable"}",
        "gpu_callback_final_candidate_text_sha256=${finalResponseProbeDiagnostics["gpu_callback_final_candidate_text_sha256"] ?: "unavailable"}",
        "gpu_callback_final_candidate_source=${finalResponseProbeDiagnostics["gpu_callback_final_candidate_source"] ?: "unavailable"}",
        "edge_gallery_final_response_probe_result=${finalResponseProbeDiagnostics["edge_gallery_final_response_probe_result"] ?: "unavailable"}",
        "edge_gallery_final_response_probe_difference_summary=${finalResponseProbeDiagnostics["edge_gallery_final_response_probe_difference_summary"] ?: "unavailable"}",
        "gpu_max_tokens=${gpuConfig.maxTokens}",
        "gpu_top_k=${gpuConfig.samplerTopK}",
        "gpu_top_p=${gpuConfig.samplerTopP}",
        "gpu_temperature=${gpuConfig.samplerTemperature}",
        "gpu_dispatcher=Dispatchers.IO",
        "gpu_engine_initialize_api=Engine.initialize",
        "gpu_edge_gallery_diff_applied=${shouldApplyEdgeGalleryLikeGpuCompatibilityMode(context.preferredBackend)}",
        "gpu_route_divergence_point=${resolveGpuRouteDivergencePoint(flags, gpuTimeoutStage)}",
        "debug_lami_gpu_generate_probe_mode=${flags.gpuGenerateProbeMode.toDiagnosticValue()}",
        "gpu_generate_call_entered=${flags.gpuGenerateCallEntered.toDiagnosticValue()}",
        "gpu_generate_call_returned=${flags.gpuGenerateCallReturned.toDiagnosticValue()}",
        "gpu_callback_invoked_count=${flags.gpuCallbackInvokedCount?.toString() ?: "unavailable"}",
        "gpu_callback_first_invoked_at_elapsed_ms=${flags.gpuCallbackFirstInvokedAtElapsedMs?.toString() ?: "unavailable"}",
        "gpu_callback_last_invoked_at_elapsed_ms=${flags.gpuCallbackLastInvokedAtElapsedMs?.toString() ?: "unavailable"}",
        "gpu_callback_thread_name=${flags.gpuCallbackThreadName.toDiagnosticValue()}",
        "gpu_callback_done_true_seen=${flags.gpuCallbackDoneTrueSeen.toDiagnosticValue()}",
        "gpu_done_true_seen=${flags.gpuCallbackDoneTrueSeen.toDiagnosticValue()}",
        "gpu_callback_error_seen=${flags.gpuCallbackErrorSeen.toDiagnosticValue()}",
        "gpu_callback_empty_text_count=${flags.gpuCallbackEmptyTextCount?.toString() ?: "unavailable"}",
        "gpu_callback_non_empty_text_count=${flags.gpuCallbackNonEmptyTextCount?.toString() ?: "unavailable"}",
        "gpu_callback_last_text_length=${flags.gpuCallbackLastTextLength?.toString() ?: "unavailable"}",
        "gpu_callback_last_text_head=${flags.gpuCallbackLastTextHead.toDiagnosticValue()}",
        "gpu_first_non_empty_text_elapsed_ms=${flags.gpuFirstNonEmptyTextElapsedMs?.toString() ?: "unavailable"}",
        "gpu_first_token_classification_reason=${flags.gpuFirstTokenClassificationReason.toDiagnosticValue()}",
        "gpu_callback_exception_class=${flags.gpuCallbackExceptionClass.toDiagnosticValue()}",
        "gpu_callback_exception_message=${flags.gpuCallbackExceptionMessage.toDiagnosticValue()}",
        "gpu_callback_exception_chain=${flags.gpuCallbackExceptionChain.toDiagnosticValue()}",
        "gpu_callback_exception_stage=${flags.gpuCallbackExceptionStage.toDiagnosticValue()}",
        "gpu_generate_stall_interpretation=${flags.gpuGenerateStallInterpretation ?: resolveGpuGenerateStallInterpretation(flags)}",
        "cpu_callback_invoked_count=${resolveCpuCallbackValue(context, flags.gpuCallbackInvokedCount?.toString())}",
        "cpu_done_true_seen=${resolveCpuCallbackValue(context, flags.gpuCallbackDoneTrueSeen?.toString())}",
        "cpu_first_non_empty_text_elapsed_ms=${resolveCpuCallbackValue(context, flags.gpuFirstNonEmptyTextElapsedMs?.toString())}",
        "gpu_done_true_seen_compare=${resolveGpuCallbackValue(context, flags.gpuCallbackDoneTrueSeen?.toString())}",
        "gpu_first_non_empty_text_elapsed_ms_compare=${resolveGpuCallbackValue(context, flags.gpuFirstNonEmptyTextElapsedMs?.toString())}",
        "callback_route_diff=${resolveCallbackRouteDiff(context, flags)}",
        "gpu_generate_actual_prompt=${flags.gpuGeneratePrompt.toDiagnosticValue()}",
        "gpu_generate_prompt_length_chars=${flags.gpuGeneratePromptLengthChars?.toString() ?: "unavailable"}",
        "gpu_generate_input_token_estimate=${flags.gpuGenerateInputTokenEstimate.toDiagnosticValue()}",
        "gpu_generate_exception_seen=${flags.gpuGenerateExceptionSeen.toDiagnosticValue()}",
        "gpu_generate_exception_class=${flags.gpuGenerateExceptionClass.toDiagnosticValue()}",
        "gpu_generate_exception_message_raw=${flags.gpuGenerateExceptionMessageRaw.toDiagnosticValue()}",
        "gpu_generate_exception_message_sanitized=${flags.gpuGenerateExceptionMessageSanitized.toDiagnosticValue()}",
        "gpu_generate_exception_status_code=${flags.gpuGenerateExceptionStatusCode ?: liteRtLmError.statusCode}",
        "gpu_generate_exception_error_file=${flags.gpuGenerateExceptionErrorFile ?: liteRtLmError.primaryFile}",
        "gpu_generate_exception_error_line=${flags.gpuGenerateExceptionErrorLine ?: liteRtLmError.primaryLine}",
        "gpu_generate_exception_summary=${flags.gpuGenerateExceptionSummary ?: liteRtLmError.summary}",
        "gpu_generate_failed_before_first_token=${flags.gpuGenerateFailedBeforeFirstToken.toDiagnosticValue()}",
        "gpu_watchdog_bypassed_due_to_generate_exception=${flags.gpuWatchdogBypassedDueToGenerateException.toDiagnosticValue()}",
        "litert_lm_error_kind=${flags.liteRtLmErrorKind ?: liteRtLmError.kind}",
        "litert_lm_error_status_code=${flags.liteRtLmErrorStatusCode ?: liteRtLmError.statusCode}",
        "litert_lm_error_primary_file=${flags.liteRtLmErrorPrimaryFile ?: liteRtLmError.primaryFile}",
        "litert_lm_error_primary_line=${flags.liteRtLmErrorPrimaryLine ?: liteRtLmError.primaryLine}",
        "litert_lm_error_secondary_file=${flags.liteRtLmErrorSecondaryFile ?: liteRtLmError.secondaryFile}",
        "litert_lm_error_secondary_line=${flags.liteRtLmErrorSecondaryLine ?: liteRtLmError.secondaryLine}",
        "litert_lm_error_recoverability_hint=${flags.liteRtLmErrorRecoverabilityHint ?: liteRtLmError.recoverabilityHint}",
        "litert_compiled_model_executor_failure_category=$compiledModelExecutorFailureCategory",
        "cpu_compare_requested=${gpuOutputQuality.cpuCompareRequested}",
        "cpu_compare_enabled=${gpuOutputQuality.cpuCompareEnabled}",
        "cpu_compare_started=${flags.cpuCompareStarted.toDiagnosticValue()}",
        "cpu_compare_finished=${gpuOutputQuality.cpuCompareFinished}",
        "cpu_compare_skipped_reason=${gpuOutputQuality.cpuCompareSkippedReason}",
        "cpu_compare_failure_stage=${gpuOutputQuality.cpuCompareFailureStage}",
        "cpu_compare_elapsed_ms=${gpuOutputQuality.cpuCompareElapsedMs}",
        "cpu_compare_engine_initialize_finished=${flags.cpuCompareEngineInitializeFinished.toDiagnosticValue()}",
        "cpu_compare_conversation_create_finished=${flags.cpuCompareConversationCreateFinished.toDiagnosticValue()}",
        "cpu_compare_generate_started=${flags.cpuCompareGenerateStarted.toDiagnosticValue()}",
        "cpu_compare_callback_invoked_count=${flags.cpuCompareCallbackInvokedCount?.toString() ?: "unavailable"}",
        "cpu_compare_empty_text_count=${gpuOutputQuality.cpuEmptyTextCount}",
        "cpu_compare_non_empty_text_count=${gpuOutputQuality.cpuNonEmptyTextCount}",
        "cpu_compare_first_non_empty_text_elapsed_ms=${flags.cpuCompareFirstNonEmptyTextElapsedMs?.toString() ?: "unavailable"}",
        "cpu_compare_done_true_seen=${flags.cpuCompareDoneTrueSeen.toDiagnosticValue()}",
        "cpu_compare_exception_class=${flags.cpuCompareExceptionClass.toDiagnosticValue()}",
        "cpu_compare_exception_message=${flags.cpuCompareExceptionMessage.toDiagnosticValue()}",
        "cpu_gpu_generate_diff=${flags.cpuGpuGenerateDiff.toDiagnosticValue()}",
        "cpu_callback_invoked_count=${flags.cpuCompareCallbackInvokedCount?.toString() ?: "unavailable"}",
        "cpu_callback_empty_text_count=${gpuOutputQuality.cpuEmptyTextCount}",
        "cpu_callback_non_empty_text_count=${gpuOutputQuality.cpuNonEmptyTextCount}",
        "cpu_avg_chunk_length=${gpuOutputQuality.cpuAverageChunkLength}",
        "cpu_median_chunk_length=${gpuOutputQuality.cpuMedianChunkLength}",
        "cpu_p90_chunk_length=${gpuOutputQuality.cpuP90ChunkLength}",
        "cpu_p95_chunk_length=${gpuOutputQuality.cpuP95ChunkLength}",
        "cpu_one_char_chunk_count=${gpuOutputQuality.cpuOneCharChunkCount}",
        "cpu_two_char_or_less_chunk_count=${gpuOutputQuality.cpuTwoCharOrLessChunkCount}",
        "cpu_one_char_chunk_ratio=${gpuOutputQuality.cpuOneCharChunkRatio}",
        "gpu_avg_chunk_length=${gpuOutputQuality.gpuAverageChunkLength}",
        "cpu_callback_count=${gpuOutputQuality.cpuCallbackCount}",
        "gpu_callback_count=${gpuOutputQuality.gpuCallbackCount}",
        "cpu_two_char_or_less_ratio=${gpuOutputQuality.cpuTwoCharOrLessRatio}",
        "cpu_chunk_length_histogram=${gpuOutputQuality.cpuChunkLengthHistogram}",
        "cpu_callback_first_30_chunks=${gpuOutputQuality.cpuCallbackFirstChunks}",
        "cpu_callback_last_30_chunks=${gpuOutputQuality.cpuCallbackLastChunks}",
        "cpu_callback_quality_classification=${gpuOutputQuality.cpuCallbackQualityClassification}",
        "cpu_output_suspicious_fragment_detected=${gpuOutputQuality.cpuOutputSuspiciousFragmentDetected}",
        "cpu_output_suspicious_fragment_reason=${gpuOutputQuality.cpuOutputSuspiciousFragmentReason}",
        "cpu_output_source_corruption_stage=${gpuOutputQuality.cpuOutputSourceCorruptionStage}",
        "gpu_two_char_or_less_ratio=${gpuOutputQuality.gpuTwoCharOrLessRatio}",
        "callback_quality_compare_result=${gpuOutputQuality.callbackQualityCompareResult}",
        "callback_quality_compare_reason=${gpuOutputQuality.callbackQualityCompareReason}",
        "cpu_gpu_avg_chunk_length_ratio=${gpuOutputQuality.cpuGpuAvgChunkLengthRatio}",
        "cpu_gpu_two_char_or_less_ratio_delta=${gpuOutputQuality.cpuGpuTwoCharOrLessRatioDelta}",
        "cpu_gpu_callback_count_delta=${gpuOutputQuality.cpuGpuCallbackCountDelta}",
        "cpu_gpu_raw_text_similarity_hint=${gpuOutputQuality.cpuGpuRawTextSimilarityHint}",
        "cpu_gpu_same_prompt=${gpuOutputQuality.cpuGpuSamePrompt}",
        "cpu_gpu_same_max_tokens=${gpuOutputQuality.cpuGpuSameMaxTokens}",
        "cpu_gpu_same_sampler_config_hint=${gpuOutputQuality.cpuGpuSameSamplerConfigHint}",
        "gpu_callback_to_ui_enabled=${flags.gpuCallbackToUiEnabled.toDiagnosticValue()}",
        "gpu_callback_text_promoted_to_ui=${flags.gpuCallbackTextPromotedToUi.toDiagnosticValue()}",
        "gpu_callback_promoted_text_length=${flags.gpuCallbackPromotedTextLength?.toString() ?: "unavailable"}",
        "gpu_callback_promoted_non_empty_count=${flags.gpuCallbackPromotedNonEmptyCount?.toString() ?: "unavailable"}",
        "gpu_callback_success_classification=${(flags.gpuCallbackSuccessClassification ?: resolveGpuCallbackSuccessClassification(flags)).toDiagnosticValue()}",
        "gpu_raw_callback_probe_status=${(flags.gpuRawCallbackProbeStatus ?: resolveGpuRawCallbackProbeStatus(flags)).toDiagnosticValue()}",
        "gpu_ui_append_started=${flags.gpuUiAppendStarted.toDiagnosticValue()}",
        "gpu_ui_append_finished=${flags.gpuUiAppendFinished.toDiagnosticValue()}",
        "gpu_ui_first_visible_text_elapsed_ms=${flags.gpuUiFirstVisibleTextElapsedMs?.toString() ?: "unavailable"}",
        "gpu_streaming_completion_reason=${flags.gpuStreamingCompletionReason.toDiagnosticValue()}",
        "gpu_normal_route_use_callback_streaming=${flags.gpuNormalRouteUseCallbackStreaming.toDiagnosticValue()}",
        "gpu_callback_streaming_path_selected=${flags.gpuCallbackStreamingPathSelected.toDiagnosticValue()}",
        "gpu_callback_streaming_path_reason=${flags.gpuCallbackStreamingPathReason.toDiagnosticValue()}",
        "gpu_callback_streaming_success_count=${flags.gpuCallbackStreamingSuccessCount?.toString() ?: "unavailable"}",
        "gpu_callback_streaming_empty_callback_count=${flags.gpuCallbackStreamingEmptyCallbackCount?.toString() ?: "unavailable"}",
        "gpu_callback_streaming_non_empty_callback_count=${flags.gpuCallbackStreamingNonEmptyCallbackCount?.toString() ?: "unavailable"}",
        "gpu_callback_streaming_done_true_seen=${flags.gpuCallbackStreamingDoneTrueSeen.toDiagnosticValue()}",
        "gpu_callback_streaming_final_text_length=${flags.gpuCallbackStreamingFinalTextLength?.toString() ?: "unavailable"}",
        "gpu_callback_streaming_reused_held_engine=${flags.gpuCallbackStreamingReusedHeldEngine.toDiagnosticValue()}",
        "gpu_callback_streaming_completion_reason=${flags.gpuCallbackStreamingCompletionReason.toDiagnosticValue()}",
        "gpu_callback_streaming_failure_reason=${flags.gpuCallbackStreamingFailureReason.toDiagnosticValue()}",
        "gpu_output_raw_callback_text_length=${gpuOutputQuality.rawLength}",
        "gpu_output_raw_callback_text_head=${gpuOutputQuality.rawHead}",
        "gpu_output_raw_callback_text_tail=${gpuOutputQuality.rawTail}",
        "gpu_output_promoted_text_length=${gpuOutputQuality.promotedLength}",
        "gpu_output_promoted_text_head=${gpuOutputQuality.promotedHead}",
        "gpu_output_promoted_text_tail=${gpuOutputQuality.promotedTail}",
        "gpu_output_final_assistant_text_length=${gpuOutputQuality.finalLength}",
        "gpu_output_final_assistant_text_head=${gpuOutputQuality.finalHead}",
        "gpu_output_final_assistant_text_tail=${gpuOutputQuality.finalTail}",
        "gpu_output_callback_chunk_count=${gpuOutputQuality.chunkCount}",
        "gpu_output_empty_chunk_count=${gpuOutputQuality.emptyChunkCount}",
        "gpu_output_non_empty_chunk_count=${gpuOutputQuality.nonEmptyChunkCount}",
        "callback_count=${gpuOutputQuality.chunkCount}",
        "non_empty_callback_count=${gpuOutputQuality.nonEmptyChunkCount}",
        "empty_callback_count=${gpuOutputQuality.emptyChunkCount}",
        "average_chunk_length=${gpuOutputQuality.callbackAverageChunkLength}",
        "median_chunk_length=${gpuOutputQuality.callbackMedianChunkLength}",
        "p50_chunk_length=${gpuOutputQuality.callbackP50ChunkLength}",
        "p90_chunk_length=${gpuOutputQuality.callbackP90ChunkLength}",
        "p95_chunk_length=${gpuOutputQuality.callbackP95ChunkLength}",
        "one_char_chunk_count=${gpuOutputQuality.callbackOneCharChunkCount}",
        "two_char_or_less_chunk_count=${gpuOutputQuality.callbackTwoCharOrLessChunkCount}",
        "one_char_chunk_ratio=${gpuOutputQuality.callbackOneCharChunkRatio}",
        "two_char_or_less_chunk_ratio=${gpuOutputQuality.callbackTwoCharOrLessChunkRatio}",
        "longest_chunk_length=${gpuOutputQuality.callbackLongestChunkLength}",
        "shortest_non_empty_chunk_length=${gpuOutputQuality.callbackShortestNonEmptyChunkLength}",
        "callback_first_30_chunks=${gpuOutputQuality.callbackFirstChunksArtifact}",
        "callback_last_30_chunks=${gpuOutputQuality.callbackLastChunksArtifact}",
        "callback_quality_classification=${gpuOutputQuality.callbackQualityClassification}",
        "callback_corruption_earliest_stage=${gpuOutputQuality.callbackCorruptionEarliestStage}",
        "gpu_fragmentation_score=${gpuOutputQuality.fragmentationScore}",
        "gpu_fragmentation_percentile=${gpuOutputQuality.fragmentationPercentile}",
        "gpu_fragmentation_tail_score=${gpuOutputQuality.fragmentationTailScore}",
        "gpu_fragmentation_middle_score=${gpuOutputQuality.fragmentationMiddleScore}",
        "gpu_fragmentation_head_score=${gpuOutputQuality.fragmentationHeadScore}",
        "gpu_chunk_size_distribution=${gpuOutputQuality.chunkSizeDistribution}",
        "gpu_chunk_length_sequence=${gpuOutputQuality.chunkLengthSequence}",
        "gpu_fragmentation_cluster_count=${gpuOutputQuality.fragmentationClusterCount}",
        "gpu_fragmentation_cluster_max_length=${gpuOutputQuality.fragmentationClusterMaxLength}",
        "gpu_fragmentation_cluster_avg_length=${gpuOutputQuality.fragmentationClusterAvgLength}",
        "gpu_output_suspicious_fragment_detected=${gpuOutputQuality.suspiciousDetected}",
        "gpu_output_suspicious_fragment_reason=${gpuOutputQuality.suspiciousReason}",
        "gpu_output_suspicious_fragment_position=${gpuOutputQuality.suspiciousPosition}",
        "gpu_output_suspicious_fragment_tail_ratio=${gpuOutputQuality.suspiciousTailRatio}",
        "gpu_output_repeated_markdown_fragment_detected=${gpuOutputQuality.repeatedMarkdownFragmentDetected}",
        "gpu_output_mixed_japanese_fragment_detected=${gpuOutputQuality.mixedJapaneseFragmentDetected}",
        "gpu_output_mixed_language_fragment_detected=${gpuOutputQuality.mixedLanguageFragmentDetected}",
        "gpu_output_chunk_join_strategy=${gpuOutputQuality.chunkJoinStrategy}",
        "gpu_output_chunk_boundary_suspected=${gpuOutputQuality.chunkBoundarySuspected}",
        "gpu_output_last_chunks_summary=${gpuOutputQuality.lastChunksSummary}",
        "gpu_output_chunk_length_histogram=${gpuOutputQuality.chunkLengthHistogram}",
        "gpu_output_quality_candidate_result=${gpuOutputQuality.candidateResult}",
        "gpu_output_quality_failure_block_reason=${gpuOutputQuality.failureBlockReason}",
        "gpu_output_quality_recommendation=${gpuOutputQuality.recommendation}",
        "gpu_output_quality_gate_status=${resolveGpuOutputQualityGateStatus(gpuOutputQuality)}",
        "gpu_output_quality_promotion_blocker=${resolveGpuOutputQualityPromotionBlocker(gpuOutputQuality)}",
        "gpu_output_quality_summary=${resolveGpuOutputQualitySummary(gpuOutputQuality)}",
        "gpu_sampler_root_cause_candidate=${gpuOutputQuality.samplerRootCauseCandidate}",
        "gpu_output_actual_ui_appended_text_length=${gpuOutputQuality.actualUiAppendedLength}",
        "gpu_output_actual_ui_appended_text_head=${gpuOutputQuality.actualUiAppendedHead}",
        "gpu_output_actual_ui_appended_text_tail=${gpuOutputQuality.actualUiAppendedTail}",
        "gpu_output_ui_append_changed_text=${gpuOutputQuality.uiAppendChangedText}",
        "gpu_output_source_corruption_stage=${gpuOutputQuality.sourceCorruptionStage}",
        "gpu_perf_engine_acquire_elapsed_ms=${gpuPerformance.engineAcquireElapsedMs}",
        "gpu_perf_engine_create_or_reuse=${gpuPerformance.engineCreateOrReuse}",
        "gpu_perf_conversation_create_elapsed_ms=${gpuPerformance.conversationCreateElapsedMs}",
        "gpu_perf_generate_to_first_token_ms=${gpuPerformance.generateToFirstTokenMs}",
        "gpu_perf_first_to_last_callback_ms=${gpuPerformance.firstToLastCallbackMs}",
        "gpu_perf_callback_total_elapsed_ms=${gpuPerformance.callbackTotalElapsedMs}",
        "gpu_perf_backend_tokens_per_second=${gpuPerformance.backendTokensPerSecond}",
        "gpu_perf_lami_visible_tokens_per_second=${gpuPerformance.lamiVisibleTokensPerSecond}",
        "gpu_perf_tokenizer_count_duration_ms=${gpuPerformance.tokenizerCountDurationMs}",
        "gpu_perf_slow_path_detected=${gpuPerformance.slowPathDetected}",
        "gpu_perf_slow_path_reason=${gpuPerformance.slowPathReason}",
        "gpu_holder_lifecycle_event_after_success=${gpuHolderLifecycle.eventAfterSuccess}",
        "gpu_holder_lifecycle_last_activity_state=${gpuHolderLifecycle.lastActivityState}",
        "gpu_holder_lifecycle_last_app_visibility=${gpuHolderLifecycle.lastAppVisibility}",
        "gpu_holder_lifecycle_clear_trigger_elapsed_ms=${gpuHolderLifecycle.clearTriggerElapsedMs}",
        "gpu_holder_lifecycle_clear_after_success_ms=${gpuHolderLifecycle.clearAfterSuccessMs}",
        "gpu_holder_lifecycle_clear_during_active_generate=${gpuHolderLifecycle.clearDuringActiveGenerate}",
        "gpu_holder_lifecycle_clear_after_ui_append=${gpuHolderLifecycle.clearAfterUiAppend}",
        "gpu_holder_lifecycle_clear_reason_detail=${gpuHolderLifecycle.clearReasonDetail}",
        "gpu_holder_lifecycle_background_detection_source=${gpuHolderLifecycle.backgroundDetectionSource}",
        "gpu_holder_lifecycle_onstop_deferred=${gpuHolderLifecycle.onStopDeferred}",
        "gpu_holder_lifecycle_onstop_defer_reason=${gpuHolderLifecycle.onStopDeferReason}",
        "gpu_holder_lifecycle_clear_suppressed_after_success=${gpuHolderLifecycle.clearSuppressedAfterSuccess}",
        "gpu_holder_lifecycle_clear_suppressed_reason=${gpuHolderLifecycle.clearSuppressedReason}",
        "gpu_holder_lifecycle_actual_background_confirmed=${gpuHolderLifecycle.actualBackgroundConfirmed}",
        "gpu_holder_lifecycle_reuse_expected_next_turn=${gpuHolderLifecycle.reuseExpectedNextTurn}",
        "gpu_prefill_probe_enabled=${gpuPrefillProbe.enabled}",
        "gpu_prefill_probe_requested=${gpuPrefillProbe.requested}",
        "gpu_prefill_probe_blocks_normal_generate=${gpuPrefillProbe.blocksNormalGenerate}",
        "gpu_prefill_probe_block_reason=${gpuPrefillProbe.blockReason}",
        "gpu_prefill_probe_requires_held_engine=${gpuPrefillProbe.requiresHeldEngine}",
        "gpu_prefill_probe_held_engine_present=${gpuPrefillProbe.heldEnginePresent}",
        "gpu_prefill_probe_disable_recommendation=${gpuPrefillProbe.disableRecommendation}",
        "gpu_litert_executor_error_file=${gpuFailureClassification.executorErrorFile}",
        "gpu_litert_executor_error_line=${gpuFailureClassification.executorErrorLine}",
        "gpu_litert_compiled_model_error_file=${gpuFailureClassification.compiledModelErrorFile}",
        "gpu_litert_compiled_model_error_line=${gpuFailureClassification.compiledModelErrorLine}",
        "gpu_engine_initialize_internal_error_detected=${gpuFailureClassification.engineInitializeInternalErrorDetected}",
        "gpu_compiled_model_creation_failed=${gpuFailureClassification.compiledModelCreationFailed}",
        "gpu_failure_interpretation=${gpuFailureClassification.interpretation}",
        "litert_lm_backend_candidates=${artisanApi.backendCandidates}",
        "litert_lm_backend_gpu_artisan_available=${artisanApi.gpuArtisanAvailable}",
        "litert_lm_backend_cpu_artisan_available=${artisanApi.cpuArtisanAvailable}",
        "litert_lm_backend_google_tensor_artisan_available=${artisanApi.googleTensorArtisanAvailable}",
        "litert_lm_engine_config_artisan_api_available=${artisanApi.engineConfigArtisanApiAvailable}",
        "litert_lm_runtime_config_available=${artisanApi.runtimeConfigAvailable}",
        "litert_lm_backend_constraint_api_available=${artisanApi.backendConstraintApiAvailable}",
        "litert_lm_preferred_engine_type_api_available=${artisanApi.preferredEngineTypeApiAvailable}",
        "selected_model_backend_constraint_hint=${artisanApi.selectedModelBackendConstraintHint}",
        "selected_model_artisan_hint=${artisanApi.selectedModelArtisanHint}",
        "edge_gallery_artisan_static_evidence=${artisanApi.edgeGalleryArtisanStaticEvidence}",
        "litert_runtime_executor_candidates=${artisanApi.runtimeExecutorCandidates}",
        "litert_runtime_executor_selection_hint=${artisanApi.runtimeExecutorSelectionHint}",
        "litert_runtime_backend_constraint_hint=${artisanApi.runtimeBackendConstraintHint}",
        "litert_runtime_compiled_model_executor_hint=${artisanApi.runtimeCompiledModelExecutorHint}",
        "litert_runtime_gpu_executor_hint=${artisanApi.runtimeGpuExecutorHint}",
        "litert_runtime_artisan_evidence=${artisanApi.runtimeArtisanEvidence}",
        "gpu_fallback_used=${flags.fallbackUsed.toDiagnosticValue()}",
        "gpu_stale_callback_ignored=${flags.staleCallbackIgnored.toDiagnosticValue()}",
        ) +
            buildGalleryStackGpuProbeRouteDiagnosticLines(galleryStackGpuProbe) +
            buildStandardGpuProbeRouteDiagnosticLines(standardGpuProbe) +
            buildStandardGpuMinimalRuntimeCandidateRouteDiagnosticLines(standardGpuMinimalRuntimeCandidate) +
            buildRuntimeAlignmentProbeRouteDiagnosticLines(runtimeAlignmentProbe) +
            buildMinimalRuntimeProbeRouteDiagnosticLines(minimalRuntimeProbe) +
            buildLoadedRuntimeNativeStackRouteDiagnosticLines(
                diagnostics = loadedRuntimeNativeStack,
                emit = context.preferredBackend.equals("GPU", ignoreCase = true) ||
                    standardGpuProbe.emit ||
                    standardGpuMinimalRuntimeCandidate.emit ||
                    runtimeAlignmentProbe.flavor ||
                    minimalRuntimeProbe.flavor,
            ) +
            buildGpuInternalSurfaceProbeRouteDiagnosticLines(gpuInternalSurfaceProbe) +
            buildCpuRouteDiagnosticLines(flags.cpuRouteDiagnostics) +
            buildNpuStandardRoutePhase1DiagnosticLines(npuStandardRouteDevGateDiagnostics) +
            buildGpuPrefillProbeDiagnosticLines(flags.gpuPrefillProbeDiagnostics)
        ).joinToString(" ")
}

private fun resolveGpuOutputQualityGateStatus(diagnostics: GpuOutputQualityDiagnostics): String =
    when (diagnostics.candidateResult) {
        "quality_candidate_fail" -> "fail"
        "quality_candidate_pass" -> "pass"
        else -> "unknown"
    }

private fun resolveGpuOutputQualityPromotionBlocker(diagnostics: GpuOutputQualityDiagnostics): String =
    (
        diagnostics.candidateResult == "quality_candidate_fail" ||
            diagnostics.callbackCorruptionEarliestStage == "raw_callback" ||
            diagnostics.samplerRootCauseCandidate == "runtime_decode_fragmentation"
        ).toString()

private fun resolveGpuOutputQualitySummary(diagnostics: GpuOutputQualityDiagnostics): String =
    when {
        diagnostics.candidateResult == "quality_candidate_fail" &&
            diagnostics.callbackCorruptionEarliestStage == "raw_callback" ->
            "runtime_callback_source_corruption_suspected"
        diagnostics.samplerRootCauseCandidate == "runtime_decode_fragmentation" ->
            "runtime_decode_fragmentation_suspected"
        diagnostics.samplerRootCauseCandidate == "streaming_join_issue" ->
            "ui_or_streaming_join_issue_suspected"
        diagnostics.candidateResult == "quality_candidate_pass" -> "quality_gate_pass"
        else -> "quality_gate_unknown"
    }

private fun buildGalleryStackGpuProbeRouteDiagnosticLines(
    diagnostics: GalleryStackGpuProbeRuntimeDiagnostics,
): List<String> {
    if (!diagnostics.flavor) return emptyList()
    return listOf(
        "gallery_stack_probe_flavor=${diagnostics.flavor}",
        "gallery_stack_probe_enabled=${diagnostics.enabled}",
        "gallery_stack_probe_application_id=${diagnostics.applicationId}",
        "gallery_stack_probe_native_stack_source=${diagnostics.nativeStackSource.toDiagnosticValue()}",
        "gallery_stack_probe_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "gallery_stack_probe_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "gallery_stack_probe_libs_manifest_present=${diagnostics.libsManifestPresent}",
        "gallery_stack_probe_edge_gallery_model_expected=${diagnostics.edgeGalleryModelExpected.toDiagnosticValue()}",
        "gallery_stack_probe_model_path=${diagnostics.modelPath.toDiagnosticValue()}",
        "gallery_stack_probe_model_exists=${diagnostics.modelExists}",
        "gallery_stack_probe_model_size_bytes=${diagnostics.modelSizeBytes}",
        "gallery_stack_probe_model_sha256_if_available=${diagnostics.modelSha256IfAvailable}",
        "gallery_stack_probe_allowlist_config_applied=${diagnostics.allowlistConfigApplied}",
        "gallery_stack_probe_runtime_stack_alignment_level=${diagnostics.runtimeStackAlignmentLevel}",
        "gallery_stack_probe_thinking_api_available=${diagnostics.thinkingApiAvailable}",
        "gallery_stack_probe_speculative_decoding_api_available=${diagnostics.speculativeDecodingApiAvailable}",
        "gallery_stack_probe_allowlist_accelerators=${diagnostics.allowlistAccelerators}",
        "gallery_stack_probe_allowlist_vision_accelerator=${diagnostics.allowlistVisionAccelerator}",
        "gallery_stack_probe_allowlist_top_k=${diagnostics.allowlistTopK}",
        "gallery_stack_probe_allowlist_top_p=${diagnostics.allowlistTopP}",
        "gallery_stack_probe_allowlist_temperature=${diagnostics.allowlistTemperature}",
        "gallery_stack_probe_allowlist_max_tokens=${diagnostics.allowlistMaxTokens}",
        "gallery_stack_probe_allowlist_max_context_length=${diagnostics.allowlistMaxContextLength}",
    )
}

private fun buildStandardGpuProbeRouteDiagnosticLines(
    diagnostics: StandardGpuProbeDiagnostics,
): List<String> {
    if (!diagnostics.emit) return emptyList()
    return listOf(
        "standard_gpu_probe_expected_edge_gallery_e2b=${diagnostics.expectedEdgeGalleryE2b}",
        "standard_gpu_probe_model_size_bytes=${diagnostics.modelSizeBytes}",
        "standard_gpu_probe_model_sha256_expected=${diagnostics.modelSha256Expected}",
        "standard_gpu_probe_model_sha256_actual=${diagnostics.modelSha256Actual}",
        "standard_gpu_probe_model_identity_hint=${diagnostics.modelIdentityHint}",
        "standard_gpu_probe_runtime_stack=${diagnostics.runtimeStack}",
        "standard_gpu_probe_callback_streaming_gate=${diagnostics.callbackStreamingGate}",
        "standard_gpu_probe_result_candidate=${diagnostics.resultCandidate}",
        "standard_gpu_runtime_alignment_candidate_enabled=${diagnostics.runtimeAlignmentCandidateEnabled}",
        "standard_gpu_runtime_alignment_candidate_eligible=${diagnostics.runtimeAlignmentCandidateEligible}",
        "standard_gpu_runtime_alignment_candidate_block_reason=${diagnostics.runtimeAlignmentCandidateBlockReason}",
        "standard_gpu_runtime_alignment_candidate_model_size_bytes=${diagnostics.runtimeAlignmentCandidateModelSizeBytes}",
        "standard_gpu_runtime_alignment_candidate_model_identity_hint=${diagnostics.runtimeAlignmentCandidateModelIdentityHint}",
        "standard_gpu_runtime_alignment_candidate_runtime_stack=${diagnostics.runtimeAlignmentCandidateRuntimeStack}",
        "standard_gpu_runtime_alignment_candidate_result=${diagnostics.runtimeAlignmentCandidateResult}",
        "standard_gpu_runtime_stack_mismatch_high_priority_candidates=${diagnostics.runtimeStackMismatchHighPriorityCandidates}",
        "standard_gpu_runtime_stack_mismatch_summary=${diagnostics.runtimeStackMismatchSummary}",
        "standard_gpu_runtime_stack_required_alignment_unit=${diagnostics.runtimeStackRequiredAlignmentUnit}",
        "standard_gpu_runtime_stack_single_so_swap_forbidden=${diagnostics.runtimeStackSingleSoSwapForbidden}",
        "standard_gpu_runtime_stack_promotion_blocked_reason=${diagnostics.runtimeStackPromotionBlockedReason}",
    )
}

private fun buildStandardGpuMinimalRuntimeCandidateRouteDiagnosticLines(
    diagnostics: StandardGpuMinimalRuntimeCandidateDiagnostics,
): List<String> {
    if (!diagnostics.emit) return emptyList()
    return listOf(
        "standard_gpu_minimal_runtime_candidate_flavor=${diagnostics.flavor}",
        "standard_gpu_minimal_runtime_candidate_application_id=${diagnostics.applicationId}",
        "standard_gpu_minimal_runtime_candidate_enabled=${diagnostics.enabled}",
        "standard_gpu_minimal_runtime_candidate_eligible=${diagnostics.eligible}",
        "standard_gpu_minimal_runtime_candidate_block_reason=${diagnostics.blockReason}",
        "standard_gpu_minimal_runtime_candidate_result=${diagnostics.result}",
        "standard_gpu_minimal_runtime_candidate_success_gate=${diagnostics.successGate}",
        "standard_gpu_minimal_runtime_candidate_loaded_liblitert_sha256=${diagnostics.loadedLibLiteRtSha256}",
        "standard_gpu_minimal_runtime_candidate_loaded_liblitertlm_jni_sha256=${diagnostics.loadedLibLiteRtLmJniSha256}",
        "standard_gpu_minimal_runtime_candidate_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "standard_gpu_minimal_runtime_candidate_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "standard_gpu_minimal_runtime_candidate_dispatch_present=${diagnostics.dispatchPresent}",
        "standard_gpu_minimal_runtime_candidate_compiler_plugin_present=${diagnostics.compilerPluginPresent}",
        "standard_gpu_minimal_runtime_candidate_constraint_provider_present=${diagnostics.constraintProviderPresent}",
        "standard_gpu_minimal_runtime_candidate_runtime_stack=${diagnostics.runtimeStack}",
        "standard_gpu_minimal_runtime_candidate_runtime_stack_source=${diagnostics.runtimeStackSource.toDiagnosticValue()}",
        "standard_gpu_minimal_runtime_candidate_interpretation=${diagnostics.interpretation}",
    )
}

private fun buildRuntimeAlignmentProbeRouteDiagnosticLines(
    diagnostics: RuntimeAlignmentProbeDiagnostics,
): List<String> {
    if (!diagnostics.flavor) return emptyList()
    return listOf(
        "runtime_alignment_probe_flavor=${diagnostics.flavor}",
        "runtime_alignment_stack_source=${diagnostics.stackSource.toDiagnosticValue()}",
        "runtime_alignment_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "runtime_alignment_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "runtime_alignment_dispatch_qualcomm_present=${diagnostics.dispatchQualcommPresent}",
        "runtime_alignment_compiler_plugin_qualcomm_present=${diagnostics.compilerPluginQualcommPresent}",
        "runtime_alignment_gemma_constraint_provider_present=${diagnostics.gemmaConstraintProviderPresent}",
        "runtime_alignment_result_candidate=${diagnostics.resultCandidate}",
        "runtime_alignment_success_gate=${diagnostics.successGate}",
    )
}

private fun buildMinimalRuntimeProbeRouteDiagnosticLines(
    diagnostics: MinimalRuntimeProbeDiagnostics,
): List<String> {
    if (!diagnostics.flavor) return emptyList()
    return listOf(
        "minimal_runtime_probe_flavor=${diagnostics.flavor}",
        "minimal_runtime_probe_liblitert_present=${diagnostics.libLiteRtPresent}",
        "minimal_runtime_probe_liblitertlm_jni_present=${diagnostics.libLiteRtLmJniPresent}",
        "minimal_runtime_probe_runtime_stack_source=${diagnostics.runtimeStackSource.toDiagnosticValue()}",
        "minimal_runtime_probe_result_candidate=${diagnostics.resultCandidate}",
        "minimal_runtime_probe_success_gate=${diagnostics.successGate}",
        "minimal_runtime_probe_loaded_liblitert_sha256=${diagnostics.loadedLibLiteRtSha256}",
        "minimal_runtime_probe_loaded_liblitertlm_jni_sha256=${diagnostics.loadedLibLiteRtLmJniSha256}",
        "minimal_runtime_probe_dispatch_present=${diagnostics.dispatchPresent}",
        "minimal_runtime_probe_compiler_plugin_present=${diagnostics.compilerPluginPresent}",
        "minimal_runtime_probe_constraint_provider_present=${diagnostics.constraintProviderPresent}",
    )
}

private fun buildLoadedRuntimeNativeStackRouteDiagnosticLines(
    diagnostics: LoadedRuntimeNativeStackDiagnostics,
    emit: Boolean,
): List<String> {
    if (!emit) return emptyList()
    return listOf(
        "runtime_stack_loaded_source_flavor=${diagnostics.sourceFlavor}",
        "runtime_stack_loaded_native_library_dir=${diagnostics.nativeLibraryDir.toDiagnosticValue()}",
        "runtime_stack_loaded_native_stack_source=${diagnostics.nativeStackSource.toDiagnosticValue()}",
        "loaded_native_lib_count=${diagnostics.loadedNativeLibCount}",
        "loaded_native_libs_sha256=${diagnostics.loadedNativeLibsSha256}",
        "loaded_native_runtime_stack_fingerprint=${diagnostics.loadedNativeRuntimeStackFingerprint}",
        "runtime_stack_loaded_liblitert_present=${diagnostics.libLiteRtPresent}",
        "runtime_stack_loaded_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "runtime_stack_loaded_liblitertlm_jni_present=${diagnostics.libLiteRtLmJniPresent}",
        "runtime_stack_loaded_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "runtime_stack_loaded_dispatch_qualcomm_present=${diagnostics.dispatchQualcommPresent}",
        "runtime_stack_loaded_dispatch_qualcomm_sha256=${diagnostics.dispatchQualcommSha256}",
        "runtime_stack_loaded_compiler_plugin_qualcomm_present=${diagnostics.compilerPluginQualcommPresent}",
        "runtime_stack_loaded_compiler_plugin_qualcomm_sha256=${diagnostics.compilerPluginQualcommSha256}",
        "runtime_stack_loaded_gemma_constraint_provider_present=${diagnostics.gemmaConstraintProviderPresent}",
        "runtime_stack_loaded_gemma_constraint_provider_sha256=${diagnostics.gemmaConstraintProviderSha256}",
        "runtime_stack_loaded_full_stack_candidate_unit=${diagnostics.fullStackCandidateUnit.toDiagnosticValue()}",
        "runtime_stack_alignment_interpretation=${diagnostics.alignmentInterpretation}",
    )
}

internal val GPU_INTERNAL_SURFACE_PROBE_DIAGNOSTIC_KEYS = listOf(
    "gpu_internal_surface_probe_enabled",
    "gpu_internal_surface_probe_result",
    "gpu_internal_surface_probe_disabled_reason",
    "gpu_internal_runtime_config_class_present",
    "gpu_internal_backend_constraint_class_present",
    "gpu_internal_preferred_engine_type_class_present",
    "gpu_internal_gpu_options_class_present",
    "gpu_internal_artisan_class_present",
    "gpu_internal_llm_gpu_artisan_executor_symbol_present",
    "gpu_internal_kv_cache_symbol_present",
    "gpu_internal_runtime_config_methods",
    "gpu_internal_backend_constraint_methods",
    "gpu_internal_gpu_options_methods",
    "gpu_internal_probe_exception_class",
    "gpu_internal_probe_exception_message",
)

private fun buildGpuInternalSurfaceProbeRouteDiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> {
    if (diagnostics.isEmpty()) return emptyList()
    return GPU_INTERNAL_SURFACE_PROBE_DIAGNOSTIC_KEYS.map { key ->
        "$key=${diagnostics[key]?.toDiagnosticValue() ?: "unavailable"}"
    }
}

private fun Map<String, String>.withGpuInternalSurfaceProbePresenceDefaults(
    preferredBackend: String,
): Map<String, String> {
    if (isEmpty()) return emptyMap()
    val enabled = this["gpu_internal_surface_probe_enabled"]?.takeIf { it.isNotBlank() }
        ?: "false"
    val result = this["gpu_internal_surface_probe_result"]?.takeIf { it.isNotBlank() }
        ?: when {
            !preferredBackend.equals("GPU", ignoreCase = true) -> "not_eligible"
            enabled == "true" -> "completed_with_missing_symbols"
            else -> "disabled"
        }
    val disabledReason = this["gpu_internal_surface_probe_disabled_reason"]?.takeIf { it.isNotBlank() }
        ?: when {
            enabled == "true" -> "none"
            !preferredBackend.equals("GPU", ignoreCase = true) -> "not_gpu_backend"
            else -> "property_off"
        }
    return this +
        mapOf(
            "gpu_internal_surface_probe_enabled" to enabled,
            "gpu_internal_surface_probe_result" to result,
            "gpu_internal_surface_probe_disabled_reason" to disabledReason,
        )
}

private fun GpuInternalSurfaceProbeDiagnostics.toRouteDiagnosticMap(): Map<String, String> {
    if (!emit) return emptyMap()
    return mapOf(
        "gpu_internal_surface_probe_enabled" to enabled,
        "gpu_internal_surface_probe_result" to result,
        "gpu_internal_surface_probe_disabled_reason" to disabledReason,
        "gpu_internal_runtime_config_class_present" to runtimeConfigClassPresent,
        "gpu_internal_backend_constraint_class_present" to backendConstraintClassPresent,
        "gpu_internal_preferred_engine_type_class_present" to preferredEngineTypeClassPresent,
        "gpu_internal_gpu_options_class_present" to gpuOptionsClassPresent,
        "gpu_internal_artisan_class_present" to artisanClassPresent,
        "gpu_internal_llm_gpu_artisan_executor_symbol_present" to llmGpuArtisanExecutorSymbolPresent,
        "gpu_internal_kv_cache_symbol_present" to kvCacheSymbolPresent,
        "gpu_internal_runtime_config_methods" to runtimeConfigMethods,
        "gpu_internal_backend_constraint_methods" to backendConstraintMethods,
        "gpu_internal_gpu_options_methods" to gpuOptionsMethods,
        "gpu_internal_probe_exception_class" to exceptionClass,
        "gpu_internal_probe_exception_message" to exceptionMessage,
    )
}

private fun buildRuntimeExecutorFingerprints(
    context: LocalRouteDiagnosticContext,
    gpuConfig: GpuRouteConfigDiagnostics,
    gpuOutputQuality: GpuOutputQualityDiagnostics,
    loadedRuntimeNativeStack: LoadedRuntimeNativeStackDiagnostics,
    artisanApi: LiteRtLmBackendArtisanApiDiagnostics,
    compiledModelExecutorFailureCategory: String,
    failureStage: String,
): RuntimeExecutorFingerprintDiagnostics {
    if (!context.preferredBackend.equals("GPU", ignoreCase = true)) {
        return RuntimeExecutorFingerprintDiagnostics(
            executorSelectionFingerprint = "unavailable",
            runtimeBackendFingerprint = "unavailable",
            runtimeExecutorFingerprint = "unavailable",
            runtimeDispatchFingerprint = "unavailable",
            runtimeCompiledModelFingerprint = "unavailable",
            engineConfigFingerprint = "unavailable",
            conversationConfigFingerprint = "unavailable",
            samplerConfigFingerprint = "unavailable",
            edgeGalleryExecutorProbeResult = "unavailable",
            edgeGalleryExecutorDifferenceSummary = "unavailable",
        )
    }
    val engineConfigMaterial = listOf(
        "model=${gpuConfig.modelPathTail}",
        "backend=${gpuConfig.backend}",
        "vision=${gpuConfig.visionBackend}",
        "audio=${gpuConfig.audioBackend}",
        "cache=${gpuConfig.cacheDirPresent}:${gpuConfig.cacheDir}",
        "max=${gpuConfig.maxTokens}",
        "profile=${resolveGpuEngineConfigProfileForBackend(context.preferredBackend)}",
    ).joinToString("|")
    val conversationConfigMaterial = listOf(
        "profile=${gpuConfig.conversationConfigProfile}",
        "sampler_present=${gpuConfig.conversationConfigSamplerPresent}",
        "streaming=${gpuOutputQuality.streamingMode}",
        "collect_only=${gpuOutputQuality.collectOnlyEnabled}",
    ).joinToString("|")
    val samplerConfigMaterial = listOf(
        "enabled=${gpuConfig.samplerConfigEnabled}",
        "top_k=${gpuConfig.samplerTopK}",
        "top_p=${gpuConfig.samplerTopP}",
        "temperature=${gpuConfig.samplerTemperature}",
        "policy=${gpuConfig.samplerAccelerationPolicy}",
    ).joinToString("|")
    val runtimeBackendMaterial = listOf(
        "source_flavor=${loadedRuntimeNativeStack.sourceFlavor}",
        "native_source=${loadedRuntimeNativeStack.nativeStackSource}",
        "libs=${loadedRuntimeNativeStack.loadedNativeLibsSha256}",
        "backend_candidates=${artisanApi.backendCandidates}",
        "gpu_artisan=${artisanApi.gpuArtisanAvailable}",
    ).joinToString("|")
    val runtimeExecutorMaterial = listOf(
        "executor_candidates=${artisanApi.runtimeExecutorCandidates}",
        "selection_hint=${artisanApi.runtimeExecutorSelectionHint}",
        "compiled_hint=${artisanApi.runtimeCompiledModelExecutorHint}",
        "gpu_hint=${artisanApi.runtimeGpuExecutorHint}",
        "artisan_evidence=${artisanApi.runtimeArtisanEvidence}",
    ).joinToString("|")
    val dispatchMaterial = listOf(
        "dispatch_present=${loadedRuntimeNativeStack.dispatchQualcommPresent}",
        "dispatch_sha=${loadedRuntimeNativeStack.dispatchQualcommSha256}",
        "compiler_present=${loadedRuntimeNativeStack.compilerPluginQualcommPresent}",
        "compiler_sha=${loadedRuntimeNativeStack.compilerPluginQualcommSha256}",
        "constraint_present=${loadedRuntimeNativeStack.gemmaConstraintProviderPresent}",
        "constraint_sha=${loadedRuntimeNativeStack.gemmaConstraintProviderSha256}",
    ).joinToString("|")
    val compiledModelMaterial = listOf(
        "failure_stage=$failureStage",
        "compiled_category=$compiledModelExecutorFailureCategory",
        "quality=${gpuOutputQuality.candidateResult}",
        "source_stage=${gpuOutputQuality.sourceCorruptionStage}",
        "sampler_root=${gpuOutputQuality.samplerRootCauseCandidate}",
    ).joinToString("|")
    val engineFingerprint = stableLocalRouteFingerprint(engineConfigMaterial)
    val conversationFingerprint = stableLocalRouteFingerprint(conversationConfigMaterial)
    val samplerFingerprint = stableLocalRouteFingerprint(samplerConfigMaterial)
    val runtimeBackendFingerprint = stableLocalRouteFingerprint(runtimeBackendMaterial)
    val runtimeExecutorFingerprint = stableLocalRouteFingerprint(runtimeExecutorMaterial)
    val runtimeDispatchFingerprint = stableLocalRouteFingerprint(dispatchMaterial)
    val runtimeCompiledModelFingerprint = stableLocalRouteFingerprint(compiledModelMaterial)
    val executorSelectionFingerprint = stableLocalRouteFingerprint(
        listOf(
            runtimeBackendFingerprint,
            runtimeExecutorFingerprint,
            runtimeDispatchFingerprint,
            runtimeCompiledModelFingerprint,
            engineFingerprint,
            conversationFingerprint,
            samplerFingerprint,
        ).joinToString("|"),
    )
    val probeResult = resolveEdgeGalleryExecutorProbeResult(
        matrixMode = gpuOutputQuality.matrixMode,
        loadedRuntimeNativeStack = loadedRuntimeNativeStack,
        gpuOutputQuality = gpuOutputQuality,
        samplerFingerprint = samplerFingerprint,
    )
    return RuntimeExecutorFingerprintDiagnostics(
        executorSelectionFingerprint = executorSelectionFingerprint,
        runtimeBackendFingerprint = runtimeBackendFingerprint,
        runtimeExecutorFingerprint = runtimeExecutorFingerprint,
        runtimeDispatchFingerprint = runtimeDispatchFingerprint,
        runtimeCompiledModelFingerprint = runtimeCompiledModelFingerprint,
        engineConfigFingerprint = engineFingerprint,
        conversationConfigFingerprint = conversationFingerprint,
        samplerConfigFingerprint = samplerFingerprint,
        edgeGalleryExecutorProbeResult = probeResult,
        edgeGalleryExecutorDifferenceSummary = resolveEdgeGalleryExecutorProbeDifferenceSummary(
            probeResult = probeResult,
            matrixMode = gpuOutputQuality.matrixMode,
            sourceFlavor = loadedRuntimeNativeStack.sourceFlavor,
            sourceCorruptionStage = gpuOutputQuality.sourceCorruptionStage,
            samplerRootCauseCandidate = gpuOutputQuality.samplerRootCauseCandidate,
        ),
    )
}

private fun resolveEdgeGalleryExecutorProbeResult(
    matrixMode: String,
    loadedRuntimeNativeStack: LoadedRuntimeNativeStackDiagnostics,
    gpuOutputQuality: GpuOutputQualityDiagnostics,
    samplerFingerprint: String,
): String {
    if (!isEdgeGalleryExecutorProbeMode(matrixMode)) return "unavailable"
    val edgeGalleryRuntimeStack =
        loadedRuntimeNativeStack.libLiteRtSha256.equals(GALLERY_STACK_GPU_PROBE_EDGE_LITERT_SHA256, ignoreCase = true) &&
            loadedRuntimeNativeStack.libLiteRtLmJniSha256.equals(
                GALLERY_STACK_GPU_PROBE_EDGE_LITERTLM_JNI_SHA256,
                ignoreCase = true,
            )
    val minimalRuntimeStack =
        loadedRuntimeNativeStack.libLiteRtSha256.equals(
            STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256,
            ignoreCase = true,
        ) &&
            loadedRuntimeNativeStack.libLiteRtLmJniSha256.equals(
                STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
                ignoreCase = true,
            )
    return when {
        edgeGalleryRuntimeStack && gpuOutputQuality.candidateResult == "quality_candidate_pass" -> "same_runtime_stack"
        edgeGalleryRuntimeStack -> "same_executor_different_runtime"
        minimalRuntimeStack &&
            samplerFingerprint != "unavailable" &&
            gpuOutputQuality.samplerRootCauseCandidate == "runtime_decode_fragmentation" ->
            "same_sampler_different_executor"
        minimalRuntimeStack -> "different_runtime_stack"
        loadedRuntimeNativeStack.libLiteRtSha256 != "unavailable" ||
            loadedRuntimeNativeStack.libLiteRtLmJniSha256 != "unavailable" -> "different_runtime_stack"
        else -> "unknown"
    }
}

private fun resolveEdgeGalleryExecutorProbeDifferenceSummary(
    probeResult: String,
    matrixMode: String,
    sourceFlavor: String,
    sourceCorruptionStage: String,
    samplerRootCauseCandidate: String,
): String =
    when {
        !isEdgeGalleryExecutorProbeMode(matrixMode) -> "unavailable"
        probeResult == "same_runtime_stack" -> "edge_gallery_runtime_stack_matched"
        probeResult == "same_sampler_different_executor" &&
            sourceCorruptionStage == "raw_callback" &&
            samplerRootCauseCandidate == "runtime_decode_fragmentation" ->
            "same_sampler_lami_runtime_decode_fragmentation_executor_selection_suspected"
        probeResult == "different_runtime_stack" ->
            "runtime_stack_differs_from_edge_gallery_source_flavor=$sourceFlavor"
        probeResult == "same_executor_different_runtime" ->
            "edge_gallery_core_runtime_matched_but_quality_or_config_differs"
        else -> "executor_difference_unknown"
    }

private fun stableLocalRouteFingerprint(material: String): String {
    if (material.isBlank()) return "unavailable"
    return runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
            .take(16)
    }.getOrDefault("unavailable")
}

private fun resolveRuntimeAlignmentProbeResultCandidate(
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): String =
    resolveStandardGpuProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )

private fun resolveRuntimeAlignmentProbeSuccessGate(
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags,
): String =
    (
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (flags.gpuNormalRouteUseCallbackStreaming == true || flags.gpuCallbackStreamingPathSelected == true)
        ).toString()

internal fun resolveMinimalRuntimeProbeResultCandidateForDebug(
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): String =
    resolveMinimalRuntimeProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )

private fun resolveMinimalRuntimeProbeResultCandidate(
    flags: LocalRouteDiagnosticFlags,
    failureStage: String,
): String =
    resolveStandardGpuProbeResultCandidate(
        flags = flags,
        failureStage = failureStage,
    )

private fun resolveMinimalRuntimeProbeSuccessGate(
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags,
): String =
    (
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (flags.gpuNormalRouteUseCallbackStreaming == true || flags.gpuCallbackStreamingPathSelected == true)
        ).toString()

private fun Boolean?.toDiagnosticValue(): String = this?.toString() ?: "unknown"

private fun String?.toDiagnosticValue(): String =
    this
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.replace(Regex("\\s+"), "_")
        ?.ifBlank { "unavailable" }
        ?: "unavailable"

internal fun LocalRouteDiagnosticFlags.withHeldEngineSnapshot(
    snapshot: HeldEngineDevDiagnosticSnapshot?,
): LocalRouteDiagnosticFlags {
    if (snapshot == null) return this
    return copy(
        holderCreated = snapshot.holderCreated,
        holderAcquired = snapshot.holderAcquired,
        holderReused = snapshot.holderReused,
        holderInvalidated = snapshot.holderInvalidated,
        holderClosed = snapshot.holderClosed,
        holderTimeoutCleanup = snapshot.holderTimeoutCleanup,
        holderFailureCleanup = snapshot.holderFailureCleanup,
        holderProcessRestart = snapshot.holderProcessRestart,
        heldEngineLifecycleHistory = snapshot.heldEngineLifecycleHistory,
        heldEngineDestroyReason = snapshot.heldEngineDestroyReason,
        heldEngineLastOwner = snapshot.heldEngineLastOwner,
        heldEngineLastFailureStage = snapshot.heldEngineLastFailureStage,
        heldEngineSnapshotBeforeDestroy = snapshot.heldEngineSnapshotBeforeDestroy,
        gpuAlignmentHolderReused = gpuAlignmentHolderReused ?: snapshot.holderReused,
        gpuAlignmentHolderCreated = gpuAlignmentHolderCreated ?: snapshot.holderCreated,
        gpuAlignmentHolderCleared = gpuAlignmentHolderCleared ?: (snapshot.holderInvalidated || snapshot.holderClosed),
        gpuAlignmentHolderClearReason = gpuAlignmentHolderClearReason ?: snapshot.heldEngineDestroyReason,
        gpuAlignmentHolderCloseStarted = gpuAlignmentHolderCloseStarted ?: snapshot.holderClosed,
        gpuAlignmentHolderCloseFinished = gpuAlignmentHolderCloseFinished ?: snapshot.holderClosed,
        gpuAlignmentHolderModelPathChanged = gpuAlignmentHolderModelPathChanged
            ?: snapshot.heldEngineDestroyReason?.contains("model", ignoreCase = true),
        gpuAlignmentHolderBackendChanged = gpuAlignmentHolderBackendChanged
            ?: snapshot.heldEngineDestroyReason?.contains("backend", ignoreCase = true),
        gpuAlignmentHolderAppProcessStartMarker = gpuAlignmentHolderAppProcessStartMarker
            ?: GPU_ALIGNMENT_APP_PROCESS_START_MARKER,
        gpuAlignmentPreviousTurnFailureStage = gpuAlignmentPreviousTurnFailureStage
            ?: snapshot.heldEngineLastFailureStage,
        gpuHolderLifecycleEventAfterSuccess = gpuHolderLifecycleEventAfterSuccess
            ?: snapshot.gpuHolderLifecycleEventAfterSuccess,
        gpuHolderLifecycleLastActivityState = gpuHolderLifecycleLastActivityState
            ?: snapshot.gpuHolderLifecycleLastActivityState,
        gpuHolderLifecycleLastAppVisibility = gpuHolderLifecycleLastAppVisibility
            ?: snapshot.gpuHolderLifecycleLastAppVisibility,
        gpuHolderLifecycleClearTriggerElapsedMs = gpuHolderLifecycleClearTriggerElapsedMs
            ?: snapshot.gpuHolderLifecycleClearTriggerElapsedMs,
        gpuHolderLifecycleClearAfterSuccessMs = gpuHolderLifecycleClearAfterSuccessMs
            ?: snapshot.gpuHolderLifecycleClearAfterSuccessMs,
        gpuHolderLifecycleClearDuringActiveGenerate = gpuHolderLifecycleClearDuringActiveGenerate
            ?: snapshot.gpuHolderLifecycleClearDuringActiveGenerate,
        gpuHolderLifecycleClearAfterUiAppend = gpuHolderLifecycleClearAfterUiAppend
            ?: snapshot.gpuHolderLifecycleClearAfterUiAppend,
        gpuHolderLifecycleClearReasonDetail = gpuHolderLifecycleClearReasonDetail
            ?: snapshot.gpuHolderLifecycleClearReasonDetail,
        gpuHolderLifecycleBackgroundDetectionSource = gpuHolderLifecycleBackgroundDetectionSource
            ?: snapshot.gpuHolderLifecycleBackgroundDetectionSource,
        gpuHolderLifecycleOnStopDeferred = gpuHolderLifecycleOnStopDeferred
            ?: snapshot.gpuHolderLifecycleOnStopDeferred,
        gpuHolderLifecycleOnStopDeferReason = gpuHolderLifecycleOnStopDeferReason
            ?: snapshot.gpuHolderLifecycleOnStopDeferReason,
        gpuHolderLifecycleClearSuppressedAfterSuccess = gpuHolderLifecycleClearSuppressedAfterSuccess
            ?: snapshot.gpuHolderLifecycleClearSuppressedAfterSuccess,
        gpuHolderLifecycleClearSuppressedReason = gpuHolderLifecycleClearSuppressedReason
            ?: snapshot.gpuHolderLifecycleClearSuppressedReason,
        gpuHolderLifecycleActualBackgroundConfirmed = gpuHolderLifecycleActualBackgroundConfirmed
            ?: snapshot.gpuHolderLifecycleActualBackgroundConfirmed,
        gpuHolderLifecycleReuseExpectedNextTurn = gpuHolderLifecycleReuseExpectedNextTurn
            ?: snapshot.gpuHolderLifecycleReuseExpectedNextTurn,
    )
}

internal fun resolveGpuGenerateStallInterpretation(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.gpuCallbackExceptionClass?.takeIf { it.isNotBlank() && it != "none" && it != "unavailable" } != null ->
            "callback_exception_before_first_token"
        (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 &&
            (flags.gpuCallbackTextPromotedToUi == true || flags.gpuCallbackToUiEnabled == true) ->
            "gpu_callback_text_observed"
        (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 &&
            flags.gpuGenerateProbeMode == "raw_callback_only" ->
            "gpu_callback_text_observed"
        flags.gpuGenerateCallEntered == true &&
            flags.gpuCallbackInvokedCount == 0 &&
            flags.firstTokenReceived == false ->
            "native_generate_no_callback"
        (flags.gpuCallbackInvokedCount ?: 0) > 0 &&
            (flags.gpuCallbackNonEmptyTextCount ?: 0) == 0 &&
            flags.gpuCallbackDoneTrueSeen == true ->
            "callback_done_without_text"
        (flags.gpuCallbackInvokedCount ?: 0) > 0 &&
            (flags.gpuCallbackNonEmptyTextCount ?: 0) == 0 ->
            "callback_empty_until_timeout"
        (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 &&
            flags.firstTokenReceived == false ->
            "ui_first_token_detection_missed"
        else -> "unknown"
    }

private fun resolveCpuCallbackValue(
    context: LocalRouteDiagnosticContext,
    value: String?,
): String =
    if (context.preferredBackend.equals("CPU", ignoreCase = true)) {
        value ?: "unavailable"
    } else {
        "unavailable"
    }

private fun resolveGpuCallbackValue(
    context: LocalRouteDiagnosticContext,
    value: String?,
): String =
    if (context.preferredBackend.equals("GPU", ignoreCase = true)) {
        value ?: "unavailable"
    } else {
        "unavailable"
    }

private fun resolveCallbackRouteDiff(
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags,
): String =
    when {
        context.preferredBackend.equals("CPU", ignoreCase = true) &&
            (flags.gpuCallbackInvokedCount ?: 0) > 0 -> "cpu_callback_observed"
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            flags.gpuGenerateCallEntered == true &&
            (flags.gpuCallbackInvokedCount ?: 0) == 0 -> "gpu_generate_entered_no_callback"
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 -> "gpu_callback_non_empty_observed"
        context.preferredBackend.equals("GPU", ignoreCase = true) &&
            (flags.gpuCallbackInvokedCount ?: 0) > 0 -> "gpu_callback_only_empty_or_done_observed"
        else -> "unavailable_single_route"
    }

private fun resolveGpuCallbackSuccessClassification(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.gpuCallbackExceptionClass?.takeIf { it.isNotBlank() && it != "none" && it != "unavailable" } != null ->
            "callback_exception"
        flags.gpuCallbackTextPromotedToUi == true -> "gpu_callback_text_promoted_to_ui"
        (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 -> "gpu_callback_text_observed"
        (flags.gpuCallbackInvokedCount ?: 0) > 0 -> "gpu_callback_without_text"
        else -> "unavailable"
    }

private fun resolveGpuRawCallbackProbeStatus(flags: LocalRouteDiagnosticFlags): String =
    if (flags.gpuGenerateProbeMode != "raw_callback_only") {
        "not_raw_callback_probe"
    } else {
        when {
            flags.gpuCallbackExceptionClass?.takeIf { it.isNotBlank() && it != "none" && it != "unavailable" } != null ->
                "exception"
            (flags.gpuCallbackNonEmptyTextCount ?: 0) > 0 -> "success"
            (flags.gpuCallbackInvokedCount ?: 0) > 0 -> "no_text"
            else -> "no_callback"
        }
    }

internal val GPU_PREFILL_PROBE_DIAGNOSTIC_KEYS = listOf(
    "probe_requested",
    "probe_enabled",
    "probe_run_started",
    "probe_run_finished",
    "probe_run_timed_out",
    "probe_skipped_normal_generate",
    "probe_isolated_engine_used",
    "probe_shared_engine_used",
    "probe_prompt_variant",
    "probe_prompt_length_chars",
    "probe_max_tokens",
    "probe_sampler_enabled",
    "probe_cache_dir_mode",
    "probe_engine_config_started",
    "probe_engine_config_finished",
    "probe_engine_initialize_started",
    "probe_engine_initialize_finished",
    "probe_conversation_create_started",
    "probe_conversation_create_finished",
    "probe_generate_started",
    "probe_first_token_received",
    "probe_generate_before_first_token_elapsed_ms",
    "probe_timeout_stage",
    "probe_failure_stage",
    "probe_exception_class",
    "probe_exception_message",
    "probe_exception_cause_class",
    "probe_exception_cause_message",
    "probe_exception_cause_message_raw",
    "probe_exception_cause_message_sanitized",
    "probe_exception_root_cause_class",
    "probe_exception_root_cause_message",
    "probe_exception_chain",
    "probe_reflection_target_exception_class",
    "probe_reflection_target_exception_message",
    "probe_reflection_target_exception_root_cause_class",
    "probe_reflection_target_exception_root_cause_message",
    "probe_result_text_length",
    "probe_result_text_head",
    "probe_stale_callback_ignored",
    "probe_elapsed_ms",
    "probe_cleanup_started",
    "probe_cleanup_finished",
    "probe_cleanup_result",
    "probe_invalidated_held_engine",
    "probe_start_blocked_reason",
    "probe_normal_generate_blocked_reason",
    "previous_invocation_still_processing_detected",
    "probe_use_held_engine_requested",
    "probe_used_held_engine",
    "probe_held_engine_present_before",
    "probe_held_engine_acquire_result",
    "probe_held_engine_generate_started",
    "probe_held_engine_first_token_received",
    "probe_held_engine_failure_stage",
    "probe_held_engine_timeout_stage",
    "probe_held_engine_invalidated_after",
    "normal_gpu_last_known_stage",
    "normal_gpu_can_initialize_with_held_engine_hint",
    "isolated_gpu_engine_initialize_failed_hint",
    "gpu_litert_executor_error_file",
    "gpu_litert_executor_error_line",
    "gpu_litert_compiled_model_error_file",
    "gpu_litert_compiled_model_error_line",
    "gpu_engine_initialize_internal_error_detected",
    "gpu_compiled_model_creation_failed",
    "gpu_failure_interpretation",
    "gpu_callback_raw_artifact_enabled",
    "gpu_callback_raw_artifact_disabled_reason",
    "gpu_callback_raw_stream_artifact_dir",
    "gpu_callback_raw_full_artifact_path",
    "gpu_callback_accumulated_final_artifact_path",
    "gpu_callback_raw_artifact_write_result",
    "gpu_callback_raw_passthrough",
    "gpu_callback_raw_sha256",
    "gpu_ui_text_sha256",
    "gpu_callback_ui_identical",
)

internal val CPU_ROUTE_DIAGNOSTIC_KEYS = listOf(
    "cpu_route_selected",
    "cpu_engine_config_backend",
    "cpu_selected_model_name",
    "cpu_selected_model_file",
    "cpu_selected_model_path_tail",
    "cpu_model_size_bytes",
    "cpu_generate_started",
    "cpu_generate_finished",
    "cpu_generate_failed_before_first_token",
    "cpu_generate_call_entered",
    "cpu_generate_call_returned",
    "cpu_callback_invoked_count",
    "cpu_callback_non_empty_text_count",
    "cpu_first_non_empty_text_elapsed_ms",
    "cpu_first_token_received",
    "cpu_generate_exception_class",
    "cpu_generate_exception_message_sanitized",
    "cpu_generate_exception_status_code",
    "cpu_generate_exception_error_file",
    "cpu_generate_exception_error_line",
    "cpu_generate_exception_summary",
    "cpu_failure_stage",
    "cpu_failure_interpretation",
    "cpu_holder_reused",
    "cpu_holder_reuse_block_reason",
    "cpu_previous_holder_backend",
    "cpu_route_probe_enabled",
    "cpu_route_probe_result",
    "cpu_route_probe_failure_stage",
    "cpu_route_probe_callback_count",
)

internal fun parseDiagnosticKeyValueText(text: String?): Map<String, String> =
    buildMap {
        text
            ?.lineSequence()
            ?.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("LOCAL_ROUTE_DIAG ")) {
                    trimmed.split(' ')
                        .asSequence()
                        .drop(1)
                        .forEach { token ->
                            val separatorIndex = token.indexOf('=')
                            if (separatorIndex > 0) {
                                put(token.substring(0, separatorIndex).trim(), token.substring(separatorIndex + 1).trim())
                            }
                        }
                } else {
                    val separatorIndex = trimmed.indexOf('=').takeIf { it > 0 }
                        ?: trimmed.indexOf(':').takeIf { it > 0 }
                    separatorIndex?.let { index ->
                        put(trimmed.substring(0, index).trim(), trimmed.substring(index + 1).trim())
                    }
                }
            }
    }

internal fun extractGpuPrefillProbeDiagnostics(text: String?): Map<String, String> =
    parseDiagnosticKeyValueText(text).filterKeys { key -> key in GPU_PREFILL_PROBE_DIAGNOSTIC_KEYS }

internal fun extractCpuRouteDiagnostics(text: String?): Map<String, String> =
    parseDiagnosticKeyValueText(text).filterKeys { key -> key in CPU_ROUTE_DIAGNOSTIC_KEYS }

internal fun buildGpuPrefillProbeDiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> {
    if (diagnostics.isEmpty()) return emptyList()
    return GPU_PREFILL_PROBE_DIAGNOSTIC_KEYS.map { key ->
        "$key=${diagnostics[key]?.replace(Regex("\\s+"), "_") ?: "unavailable"}"
    }
}

internal fun buildCpuRouteDiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> {
    if (diagnostics.isEmpty()) return emptyList()
    return CPU_ROUTE_DIAGNOSTIC_KEYS.map { key ->
        "$key=${diagnostics[key]?.replace(Regex("\\s+"), "_") ?: "unavailable"}"
    }
}

internal fun classifyGpuLiteRtFailure(
    message: String?,
    failureStage: String? = null,
    timeoutStage: String? = null,
    generateStarted: Boolean? = null,
    firstTokenReceived: Boolean? = null,
    engineInitializeFinished: Boolean? = null,
    conversationCreateFinished: Boolean? = null,
): GpuLiteRtFailureClassification {
    val normalizedMessage = message.orEmpty()
    val fileLines = extractGpuLiteRtFileLines(normalizedMessage)
    val executor = fileLines.firstOrNull { it.first.endsWith("llm_litert_compiled_model_executor.cc") }
    val compiledModel = fileLines.firstOrNull { it.first.endsWith("litert_compiled_model.h") }
    val internalErrorDetected = normalizedMessage.contains("INTERNAL", ignoreCase = true) ||
        normalizedMessage.contains("_INTERNAL_", ignoreCase = true)
    val compiledModelInvokeFailed = normalizedMessage.contains("Failed_to_invoke_the_compiled_model", ignoreCase = true) ||
        normalizedMessage.contains("Failed to invoke the compiled model", ignoreCase = true)
    val compiledModelCreationFailed = !compiledModelInvokeFailed && (
        normalizedMessage.contains("Failed_to_create_engine", ignoreCase = true) ||
            normalizedMessage.contains("Failed to create engine", ignoreCase = true) ||
            executor?.second == "1546" ||
            compiledModel != null
        )
    val generatedWithoutFirstToken =
        generateStarted == true &&
            firstTokenReceived == false &&
            (engineInitializeFinished == true || conversationCreateFinished == true)
    val rawCallbackSuccess =
        failureStage == "gpu_raw_callback_probe_success" ||
            failureStage == "gpu_raw_callback_probe_completed"
    val beforeConversation =
        timeoutStage == "engine_initialize" ||
            failureStage?.contains("engine_initialize", ignoreCase = true) == true ||
            conversationCreateFinished == false
    val interpretation = when {
        rawCallbackSuccess -> "gpu_raw_callback_success_ui_path_needs_promotion"
        compiledModelInvokeFailed && generateStarted == true -> "compiled_model_invoke_failed_during_generate"
        compiledModelCreationFailed && beforeConversation -> "compiled_model_creation_failed_before_conversation"
        generatedWithoutFirstToken -> "normal_route_generate_hangs_after_successful_initialize"
        failureStage?.contains("gpu_prefill_probe", ignoreCase = true) == true ->
            "isolated_probe_differs_from_held_engine_lifecycle"
        else -> "unknown"
    }
    return GpuLiteRtFailureClassification(
        executorErrorFile = executor?.first ?: "unavailable",
        executorErrorLine = executor?.second ?: "unavailable",
        compiledModelErrorFile = compiledModel?.first ?: "unavailable",
        compiledModelErrorLine = compiledModel?.second ?: "unavailable",
        engineInitializeInternalErrorDetected = internalErrorDetected,
        compiledModelCreationFailed = compiledModelCreationFailed,
        interpretation = interpretation,
    )
}

internal fun classifyLiteRtLmError(message: String?): LiteRtLmErrorClassification {
    val raw = message.orEmpty()
    val wordNormalized = normalizeLiteRtLmErrorText(raw)
    val statusCode = Regex("""Status[\s_]*Code[\s_:]*([0-9]+)""", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?: "unavailable"
    val fileLines = extractGpuLiteRtFileLines(raw)
    val primary = fileLines.firstOrNull()
    val secondary = fileLines.drop(1).firstOrNull()
    val failedInvoke = wordNormalized.contains("Failed to invoke the compiled model", ignoreCase = true)
    val inputTooLong = wordNormalized.contains("Input token ids are too long", ignoreCase = true)
    val createEngineFailed = wordNormalized.contains("Failed to create engine", ignoreCase = true) ||
        wordNormalized.contains("Failed create engine", ignoreCase = true)
    val compiledModelCreation = createEngineFailed ||
        fileLines.any { it.first.endsWith("llm_litert_compiled_model_executor.cc") && it.second == "1546" } ||
        fileLines.any { it.first.endsWith("litert_compiled_model.h") && it.second == "1140" }
    val kind = when {
        statusCode == "13" && failedInvoke -> "compiled_model_invoke_failed"
        statusCode == "3" && inputTooLong -> "max_tokens_too_small"
        compiledModelCreation -> "compiled_model_creation_failed"
        statusCode != "unavailable" -> "status_code_$statusCode"
        else -> "unknown"
    }
    val summary = when (kind) {
        "compiled_model_invoke_failed" -> "failed_to_invoke_compiled_model"
        "max_tokens_too_small" -> "input_token_ids_too_long"
        "compiled_model_creation_failed" -> "failed_to_create_engine"
        else -> "unknown"
    }
    val recoverabilityHint = when (kind) {
        "compiled_model_invoke_failed" -> "try_gpu_runtime_stack_alignment"
        "max_tokens_too_small" -> "max_tokens_too_small"
        "compiled_model_creation_failed" -> "try_different_gpu_backend_config"
        else -> "unknown"
    }
    return LiteRtLmErrorClassification(
        kind = kind,
        statusCode = statusCode,
        primaryFile = primary?.first ?: "unavailable",
        primaryLine = primary?.second ?: "unavailable",
        secondaryFile = secondary?.first ?: "unavailable",
        secondaryLine = secondary?.second ?: "unavailable",
        recoverabilityHint = recoverabilityHint,
        summary = summary,
    )
}

internal fun classifyLiteRtCompiledModelExecutorFailureCategory(
    error: LiteRtLmErrorClassification,
): String =
    when {
        error.primaryFile.endsWith("llm_litert_compiled_model_executor.cc") &&
            error.primaryLine == "735" &&
            error.kind == "compiled_model_invoke_failed" -> "compiled_model_invoke"
        error.kind == "compiled_model_creation_failed" -> "compiled_model_load"
        error.kind == "max_tokens_too_small" -> "compiled_model_invoke_input_budget"
        error.primaryFile.endsWith("llm_litert_compiled_model_executor.cc") -> "compiled_model_executor"
        error.primaryFile != "unavailable" -> "unknown_litert_native_error"
        else -> "unknown"
    }

internal fun normalizeLiteRtLmErrorText(message: String?): String =
    message
        ?.replace('_', ' ')
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()

internal fun sanitizeGpuLiteRtFailureMessage(value: String?): String =
    value
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.replace(Regex("\\s+"), "_")
        ?.ifBlank { "none" }
        ?: "none"

internal fun resolveGpuRouteDivergencePoint(
    flags: LocalRouteDiagnosticFlags,
    gpuTimeoutStage: String,
): String {
    val probeStage = flags.gpuPrefillProbeDiagnostics["probe_timeout_stage"]
    val probeFailure = flags.gpuPrefillProbeDiagnostics["probe_failure_stage"].orEmpty()
    val startBlocked = flags.gpuPrefillProbeDiagnostics["probe_start_blocked_reason"]
    return when {
        startBlocked == "no_held_engine" -> "held_engine_probe_blocked_no_held_engine"
        probeStage == "engine_initialize" && probeFailure.contains("engine_initialize") ->
            "isolated_probe_engine_initialize_failed_before_conversation"
        flags.gpuGenerateExceptionSeen == true && flags.firstTokenReceived != true ->
            "normal_route_generate_exception_before_first_token"
        flags.engineInitializeFinished == true &&
            flags.conversationCreateFinished == true &&
            flags.generateStarted == true &&
            flags.firstTokenReceived == false ->
            "normal_route_generate_started_before_first_token_timeout"
        gpuTimeoutStage == "generate_before_first_token" ->
            "normal_route_generate_before_first_token"
        else -> "unknown"
    }
}

private fun extractGpuLiteRtFileLines(message: String): List<Pair<String, String>> {
    val regex = Regex("""([A-Za-z0-9_./-]+(?:\.cc|\.h)):(\d+)""")
    return regex.findAll(message)
        .map { match -> match.groupValues[1] to match.groupValues[2] }
        .distinct()
        .toList()
}

internal fun buildLiteRtLmBackendArtisanApiDiagnostics(
    selectedModelPath: String?,
): LiteRtLmBackendArtisanApiDiagnostics {
    val snapshot = liteRtLmBackendArtisanApiReflectionSnapshot
    return LiteRtLmBackendArtisanApiDiagnostics(
        backendCandidates = snapshot.backendCandidates,
        gpuArtisanAvailable = snapshot.gpuArtisanAvailable,
        cpuArtisanAvailable = snapshot.cpuArtisanAvailable,
        googleTensorArtisanAvailable = snapshot.googleTensorArtisanAvailable,
        engineConfigArtisanApiAvailable = snapshot.engineConfigArtisanApiAvailable,
        runtimeConfigAvailable = snapshot.runtimeConfigAvailable,
        backendConstraintApiAvailable = snapshot.backendConstraintApiAvailable,
        preferredEngineTypeApiAvailable = snapshot.preferredEngineTypeApiAvailable,
        selectedModelBackendConstraintHint = inferSelectedModelBackendConstraintHint(selectedModelPath),
        selectedModelArtisanHint = inferSelectedModelArtisanHint(selectedModelPath),
        runtimeExecutorCandidates = snapshot.runtimeExecutorCandidates,
        runtimeExecutorSelectionHint = snapshot.runtimeExecutorSelectionHint,
        runtimeBackendConstraintHint = snapshot.runtimeBackendConstraintHint,
        runtimeCompiledModelExecutorHint = snapshot.runtimeCompiledModelExecutorHint,
        runtimeGpuExecutorHint = snapshot.runtimeGpuExecutorHint,
        runtimeArtisanEvidence = snapshot.runtimeArtisanEvidence,
    )
}

private data class LiteRtLmBackendArtisanApiReflectionSnapshot(
    val backendCandidates: String,
    val gpuArtisanAvailable: String,
    val cpuArtisanAvailable: String,
    val googleTensorArtisanAvailable: String,
    val engineConfigArtisanApiAvailable: String,
    val runtimeConfigAvailable: String,
    val backendConstraintApiAvailable: String,
    val preferredEngineTypeApiAvailable: String,
    val runtimeExecutorCandidates: String,
    val runtimeExecutorSelectionHint: String,
    val runtimeBackendConstraintHint: String,
    val runtimeCompiledModelExecutorHint: String,
    val runtimeGpuExecutorHint: String,
    val runtimeArtisanEvidence: String,
)

private val liteRtLmBackendArtisanApiReflectionSnapshot: LiteRtLmBackendArtisanApiReflectionSnapshot by lazy(
    LazyThreadSafetyMode.PUBLICATION,
) {
    val apiClasses = listOf(
        "com.google.ai.edge.litertlm.Backend",
        "com.google.ai.edge.litertlm.EngineConfig",
        "com.google.ai.edge.litertlm.EngineConfig\$Builder",
        "com.google.ai.edge.litertlm.RuntimeConfig",
        "com.google.ai.edge.litertlm.RuntimeConfig\$Builder",
        "com.google.ai.edge.litertlm.ExecutorConfig",
        "com.google.ai.edge.litertlm.ExecutorConfig\$Builder",
        "com.google.ai.edge.litertlm.ExecutorSelection",
        "com.google.ai.edge.litertlm.ExecutorSelection\$Builder",
        "com.google.ai.edge.litertlm.PreferredEngineType",
        "com.google.ai.edge.litertlm.BackendConstraint",
        "com.google.ai.edge.litertlm.BackendConstraint\$Builder",
        "com.google.ai.edge.litertlm.CompiledModelExecutor",
        "com.google.ai.edge.litertlm.GpuExecutor",
        "com.google.ai.edge.litertlm.LlmGpuArtisanExecutor",
        "com.google.ai.edge.litertlm.BackendType",
        "com.google.ai.edge.litertlm.AdapterBackend",
        "com.google.ai.edge.litertlm.EncoderBackend",
        "com.google.ai.edge.litertlm.SamplerBackend",
    )
    val loadedClasses = apiClasses.mapNotNull { className ->
        runCatching { Class.forName(className) }.getOrNull()
    }
    val backendClass = loadedClasses.firstOrNull { it.name == "com.google.ai.edge.litertlm.Backend" }
    val backendCandidates = collectLiteRtLmBackendCandidates(backendClass)
    val apiSurfaceNames = collectLiteRtLmApiSurfaceNames(loadedClasses)
    val normalizedBackendCandidates = backendCandidates.map(::normalizeLiteRtLmApiTokenForMatch)
    val normalizedApiSurface = apiSurfaceNames.map(::normalizeLiteRtLmApiTokenForMatch)
    val runtimeConfigAvailable = loadedClasses.any { it.name == "com.google.ai.edge.litertlm.RuntimeConfig" }
    val executorCandidates = collectLiteRtLmRuntimeExecutorCandidates(
        apiSurfaceNames = apiSurfaceNames,
        backendCandidates = backendCandidates,
    )
    val hasPublicArtisanSurface = normalizedBackendCandidates.any { it.contains("ARTISAN") } ||
        normalizedApiSurface.any { it.contains("ARTISAN") }
    val hasExecutorSelectionSurface = normalizedApiSurface.any { name ->
        name.contains("EXECUTORSELECTION") ||
            name.contains("EXECUTORCONFIG") ||
            name.contains("PREFERREDENGINETYPE") ||
            name.contains("PREFERREDENGINE") ||
            name.contains("ENGINETYPE")
    }
    val hasBackendConstraintSurface = normalizedApiSurface.any { name ->
        name.contains("BACKENDCONSTRAINT") ||
            name.contains("CONSTRAINT") ||
            name.contains("SUPPORTEDBACKEND") ||
            name.contains("REQUIREDBACKEND") ||
            name.contains("MODELREQUIRES")
    }
    val hasCompiledModelExecutorSurface = normalizedApiSurface.any { name ->
        name.contains("COMPILEDMODELEXECUTOR") ||
            name.contains("LITERTCOMPILEDMODELEXECUTOR")
    }
    val hasGpuExecutorSurface = normalizedApiSurface.any { name ->
        name.contains("GPUEXECUTOR") ||
            name.contains("LITERTGPU") ||
            name.contains("GPUARTISAN")
    }
    LiteRtLmBackendArtisanApiReflectionSnapshot(
        backendCandidates = backendCandidates.joinToString(",").ifBlank {
            if (backendClass == null) "Backend_class_unavailable" else "none_detected"
        },
        gpuArtisanAvailable = normalizedBackendCandidates.any { it.contains("GPUARTISAN") }.toString(),
        cpuArtisanAvailable = normalizedBackendCandidates.any { it.contains("CPUARTISAN") }.toString(),
        googleTensorArtisanAvailable = normalizedBackendCandidates.any { it.contains("GOOGLETENSORARTISAN") }.toString(),
        engineConfigArtisanApiAvailable = normalizedApiSurface.any { it.contains("ARTISAN") }.toString(),
        runtimeConfigAvailable = runtimeConfigAvailable.toString(),
        backendConstraintApiAvailable = normalizedApiSurface.any { name ->
            name.contains("CONSTRAINT") ||
                name.contains("SUPPORTEDBACKEND") ||
                name.contains("REQUIREDBACKEND") ||
                name.contains("MODELREQUIRES")
        }.toString(),
        preferredEngineTypeApiAvailable = normalizedApiSurface.any { name ->
            name.contains("PREFERREDENGINETYPE") ||
                name.contains("PREFERREDENGINE") ||
                name.contains("ENGINETYPE")
        }.toString(),
        runtimeExecutorCandidates = executorCandidates.joinToString(",").ifBlank { "none_detected" },
        runtimeExecutorSelectionHint = when {
            hasExecutorSelectionSurface -> "public_api_executor_selection_surface_detected"
            runtimeConfigAvailable -> "runtime_config_public_but_no_executor_selection_surface"
            else -> "public_api_executor_selection_surface_unavailable"
        },
        runtimeBackendConstraintHint = when {
            hasBackendConstraintSurface -> "public_api_backend_constraint_surface_detected"
            else -> "public_api_backend_constraint_surface_unavailable"
        },
        runtimeCompiledModelExecutorHint = when {
            hasCompiledModelExecutorSurface -> "public_api_compiled_model_executor_surface_detected"
            else -> "native_or_internal_compiled_model_executor_only"
        },
        runtimeGpuExecutorHint = when {
            normalizedBackendCandidates.any { it.contains("GPUARTISAN") } -> "public_backend_gpu_artisan_available"
            hasGpuExecutorSurface -> "public_gpu_executor_surface_detected"
            normalizedBackendCandidates.any { it == "GPU" || it.contains("GPU") } -> "public_backend_gpu_only"
            else -> "no_public_gpu_executor_surface_detected"
        },
        runtimeArtisanEvidence = when {
            hasPublicArtisanSurface -> "public_api_artisan_surface_detected"
            EDGE_GALLERY_ARTISAN_STATIC_EVIDENCE.isNotBlank() -> "edge_gallery_static_only_public_api_unavailable"
            else -> "none_detected"
        },
    )
}

private fun collectLiteRtLmBackendCandidates(
    backendClass: Class<*>?,
): List<String> {
    if (backendClass == null) return emptyList()
    val candidates = linkedSetOf<String>()
    (backendClass.declaredClasses.asList() + backendClass.classes.asList())
        .forEach { clazz -> candidates += clazz.simpleName.ifBlank { clazz.name.substringAfterLast('.') } }
    (backendClass.methods.asList() + backendClass.declaredMethods.asList())
        .filter { method -> method.parameterTypes.isEmpty() && Modifier.isStatic(method.modifiers) }
        .filter { method -> backendClass.isAssignableFrom(method.returnType) || method.name.contains("backend", ignoreCase = true) }
        .forEach { method -> candidates += method.name }
    (backendClass.fields.asList() + backendClass.declaredFields.asList())
        .filter { field -> Modifier.isStatic(field.modifiers) }
        .forEach { field -> candidates += field.name }
    return candidates
        .map { it.replace('$', '.') }
        .filter { it.isNotBlank() }
        .sorted()
}

private fun collectLiteRtLmApiSurfaceNames(
    classes: List<Class<*>>,
): List<String> {
    val names = linkedSetOf<String>()
    classes.forEach { clazz ->
        names += clazz.name
        (clazz.declaredClasses.asList() + clazz.classes.asList()).forEach { nested ->
            names += nested.name
        }
        (clazz.methods.asList() + clazz.declaredMethods.asList()).forEach { method ->
            names += "${clazz.simpleName}.${method.name}"
            method.parameterTypes.forEach { type -> names += type.name }
            names += method.returnType.name
        }
        (clazz.fields.asList() + clazz.declaredFields.asList()).forEach { field ->
            names += "${clazz.simpleName}.${field.name}"
            names += field.type.name
        }
        (clazz.constructors.asList() + clazz.declaredConstructors.asList()).forEach { constructor ->
            names += "${clazz.simpleName}.<init>"
            constructor.parameterTypes.forEach { type -> names += type.name }
        }
    }
    return names.toList()
}

private fun collectLiteRtLmRuntimeExecutorCandidates(
    apiSurfaceNames: List<String>,
    backendCandidates: List<String>,
): List<String> {
    val tokens = linkedSetOf<String>()
    (apiSurfaceNames + backendCandidates)
        .map { it.replace('$', '.') }
        .filter { value ->
            value.contains("Executor", ignoreCase = true) ||
                value.contains("RuntimeConfig", ignoreCase = true) ||
                value.contains("PreferredEngine", ignoreCase = true) ||
                value.contains("BackendConstraint", ignoreCase = true) ||
                value.contains("Gpu", ignoreCase = true) ||
                value.contains("Artisan", ignoreCase = true) ||
                value.contains("CompiledModel", ignoreCase = true)
        }
        .map { value ->
            value
                .substringAfterLast("com.google.ai.edge.litertlm.")
                .substringAfterLast("java.lang.")
                .take(96)
        }
        .filter { it.isNotBlank() }
        .sorted()
        .forEach { tokens += it }
    return tokens.take(40)
}

private fun normalizeLiteRtLmApiTokenForMatch(value: String): String =
    value
        .uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }

private fun inferSelectedModelBackendConstraintHint(selectedModelPath: String?): String {
    val path = selectedModelPath.orEmpty()
    val normalized = path
        .takeIf { it.isNotBlank() && it != "unknown" }
        ?.let { File(it).name.ifBlank { it } }
        .orEmpty()
        .lowercase()
    return when {
        normalized.isBlank() || normalized == "unknown" -> "unavailable"
        "gpu_artisan" in normalized -> "path_contains_gpu_artisan"
        "cpu_artisan" in normalized -> "path_contains_cpu_artisan"
        "artisan" in normalized -> "path_contains_artisan"
        "sm8750" in normalized || "qualcomm" in normalized -> "path_contains_sm8750_or_qualcomm"
        "gpu" in normalized -> "path_contains_gpu"
        "npu" in normalized -> "path_contains_npu"
        else -> "not_detected_by_path"
    }
}

private fun inferSelectedModelArtisanHint(selectedModelPath: String?): String {
    val path = selectedModelPath.orEmpty()
    val normalized = path
        .takeIf { it.isNotBlank() && it != "unknown" }
        ?.let { File(it).name.ifBlank { it } }
        .orEmpty()
        .lowercase()
    return when {
        normalized.isBlank() || normalized == "unknown" -> "unavailable"
        "artisan" in normalized -> "path_contains_artisan"
        else -> "not_detected_by_path"
    }
}

internal const val GPU_EXPERIMENTAL_STAGE_TIMEOUT_STANDARD_MS = 20_000L
internal const val GPU_EXPERIMENTAL_STAGE_TIMEOUT_EXTENDED_DEV_MS = 60_000L
internal const val GPU_EXPERIMENTAL_STAGE_TIMEOUT_MS = GPU_EXPERIMENTAL_STAGE_TIMEOUT_EXTENDED_DEV_MS
internal const val GPU_EXPERIMENTAL_TIMEOUT_MESSAGE =
    "GPU backend の初期化または生成開始がタイムアウトしました。Generic LiteRT-LMモデルではCPU backendを選択してください。"
internal const val GPU_EXPERIMENTAL_TIMEOUT_GUARD_RECOMMENDATION = "switch_to_cpu_or_npu"
internal const val GPU_COMPATIBILITY_MODE_EDGE_GALLERY_LIKE = "edge_gallery_like"
internal const val GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE = "edge_gallery_like_text_only"
internal const val GPU_ENGINE_CONFIG_PROFILE_TEXT_ONLY_NULL_MODALITIES =
    "normal_chat_text_gpu_null_modalities"
internal const val GPU_RECOMMENDED_NEXT_CONFIG_TEXT_ONLY_NULL_MODALITIES =
    "try_gpu_text_only_null_modalities"
internal const val GPU_RECOMMENDED_NEXT_CONFIG_NONE_ALREADY_TEXT_ONLY =
    "none_current_config_already_text_gpu_null_modalities"
internal const val GPU_CACHE_DIR_MODE_EDGE_GALLERY_LIKE = "gallery_like_null_for_app_model_path"
internal const val GPU_MODEL_PATH_MODE_SELECTED_FILE = "selected_litertlm_file"
internal const val GPU_SAMPLER_CONFIG_PROFILE_EDGE_GALLERY_LIKE = "gallery_defaults_64_0.95_1.0"
internal const val GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE = "gallery_like_sampler_config_non_npu"
internal const val GPU_EDGE_GALLERY_LIKE_MAX_TOKENS = 1024
internal const val GPU_EDGE_GALLERY_LIKE_TOP_K = 64
internal const val GPU_EDGE_GALLERY_LIKE_TOP_P = "0.95"
internal const val GPU_EDGE_GALLERY_LIKE_TEMPERATURE = "1.0"
internal const val GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE = "edge_gallery_like"
internal const val GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL = "gpu_sampler_only_minimal"
internal const val GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION = "gpu_no_sampling_acceleration"
internal const val GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE = "gpu_disable_topk_gpu_sampler_candidate"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_NULL = "gpu_cache_dir_null"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES = "gpu_cache_dir_app_files"
internal const val GPU_EXPERIMENT_MODE_MAX_TOKENS_32 = "gpu_max_tokens_32"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER = "gpu_cache_dir_app_files_no_sampler"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER = "gpu_cache_dir_null_no_sampler"
internal const val GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES = "gpu_text_only_null_modalities"
internal const val EDGE_GALLERY_ARTISAN_STATIC_EVIDENCE =
    "GPU_ARTISAN,CPU_ARTISAN,GOOGLE_TENSOR_ARTISAN,Artisan_model_detected,LlmGpuArtisanExecutor"
internal val GPU_DIAGNOSTIC_EXPERIMENT_MODES = listOf(
    GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE,
    GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL,
    GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
    GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
    GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
    GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
    GPU_EXPERIMENT_MODE_MAX_TOKENS_32,
    GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER,
    GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER,
    GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES,
)

internal fun shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend: String): Boolean =
    preferredBackend.equals("GPU", ignoreCase = true)

internal fun resolveGpuDiagnosticExperimentModeForBackend(
    preferredBackend: String,
    overrideValue: String? = null,
): String {
    if (!shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) return "unavailable"
    val requested = overrideValue?.trim()?.takeIf { it.isNotBlank() }
        ?: readGpuDiagnosticExperimentModeFromDebugProperty()
    return requested
        ?.takeIf { mode -> GPU_DIAGNOSTIC_EXPERIMENT_MODES.any { it.equals(mode, ignoreCase = true) } }
        ?.let { mode -> GPU_DIAGNOSTIC_EXPERIMENT_MODES.first { it.equals(mode, ignoreCase = true) } }
        ?: GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE
}

private fun readGpuDiagnosticExperimentModeFromDebugProperty(): String? {
    if (!BuildConfig.DEBUG) return null
    val systemProperty = runCatching {
        System.getProperty("lami.gpu_experiment_mode")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (systemProperty != null) return systemProperty
    val env = runCatching {
        System.getenv("LAMI_GPU_EXPERIMENT_MODE")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (env != null) return env
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        listOf("debug.lami.gpu_experiment_mode", "lami.gpu_experiment_mode")
            .firstNotNullOfOrNull { key ->
                (method.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotBlank() }
            }
    }.getOrNull()
}

internal fun resolveGpuCompatibilityModeForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_COMPATIBILITY_MODE_EDGE_GALLERY_LIKE
    } else {
        "unavailable"
    }

internal fun resolveGpuEngineConfigProfileForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE
    } else {
        "unavailable"
    }

internal fun resolveNormalChatGpuEngineConfigStyle(
    preferredBackend: String,
    experimentMode: String,
): String =
    if (!shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        "unavailable"
    } else if (experimentMode == GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES) {
        GPU_ENGINE_CONFIG_PROFILE_TEXT_ONLY_NULL_MODALITIES
    } else {
        GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE
    }

internal fun shouldUseNormalChatGpuTextOnlyNullModalities(
    preferredBackend: PreferredBackendDryRunSetting,
    experimentMode: String,
): Boolean =
    preferredBackend == PreferredBackendDryRunSetting.GPU &&
        experimentMode == GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES

internal fun recommendedNextGpuConfigVariant(
    preferredBackend: PreferredBackendDryRunSetting,
    configStyle: String,
): String =
    when {
        preferredBackend != PreferredBackendDryRunSetting.GPU -> "none"
        configStyle == GPU_ENGINE_CONFIG_PROFILE_TEXT_ONLY_NULL_MODALITIES ||
            configStyle == GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE ->
            GPU_RECOMMENDED_NEXT_CONFIG_NONE_ALREADY_TEXT_ONLY
        else -> GPU_RECOMMENDED_NEXT_CONFIG_TEXT_ONLY_NULL_MODALITIES
    }

internal fun resolveGpuCacheDirModeForBackend(
    preferredBackend: String,
    experimentMode: String = resolveGpuDiagnosticExperimentModeForBackend(preferredBackend),
): String =
    if (!shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        "unavailable"
    } else {
        when (experimentMode) {
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER -> "forced_null"
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER -> "forced_app_cache_dir"
            else -> GPU_CACHE_DIR_MODE_EDGE_GALLERY_LIKE
        }
    }

internal fun resolveGpuModelPathModeForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_MODEL_PATH_MODE_SELECTED_FILE
    } else {
        "unavailable"
    }

internal fun resolveGpuSamplerConfigProfileForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_SAMPLER_CONFIG_PROFILE_EDGE_GALLERY_LIKE
    } else {
        "unavailable"
    }

internal fun resolveGpuConversationConfigProfileForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE
    } else {
        "unavailable"
    }

internal fun resolveGpuMaxTokensForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        resolveGpuMaxTokensForExperiment(resolveGpuDiagnosticExperimentModeForBackend(preferredBackend))
    } else {
        "unavailable"
    }

internal fun resolveGpuTopKForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_EDGE_GALLERY_LIKE_TOP_K.toString()
    } else {
        "unavailable"
    }

internal fun resolveGpuTopPForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_EDGE_GALLERY_LIKE_TOP_P
    } else {
        "unavailable"
    }

internal fun resolveGpuTemperatureForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_EDGE_GALLERY_LIKE_TEMPERATURE
    } else {
        "unavailable"
    }

internal fun buildGpuRouteConfigDiagnostics(
    modelPath: String?,
    cacheDirPath: String?,
    preferredBackend: String,
    experimentMode: String = resolveGpuDiagnosticExperimentModeForBackend(preferredBackend),
): GpuRouteConfigDiagnostics {
    if (!shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        return GpuRouteConfigDiagnostics()
    }
    val resolvedModelPath = modelPath?.takeIf { it.isNotBlank() } ?: "unavailable"
    val resolvedCacheDir = resolveGpuExperimentCacheDirForDiagnostics(
        modelPath = resolvedModelPath,
        cacheDirPath = cacheDirPath,
        experimentMode = experimentMode,
    )
    val samplerEnabled = shouldUseGpuDiagnosticSamplerConfig(experimentMode)
    val matrixMode = resolveGpuOutputQualityMatrixModeForDebug(preferredBackend)
    val shortMaxTokensProbeEnabled = isGpuOutputQualityProbeShortMaxTokensEnabledForDebug(preferredBackend)
    val numericMaxTokensOverride = resolveGpuOutputQualityMaxTokensOverrideForDebug(preferredBackend)
    val configStyle = resolveNormalChatGpuEngineConfigStyle(
        preferredBackend = preferredBackend,
        experimentMode = experimentMode,
    )
    val resolvedMaxTokens = when {
        numericMaxTokensOverride != null -> numericMaxTokensOverride.toString()
        shortMaxTokensProbeEnabled -> GPU_OUTPUT_QUALITY_PROBE_SHORT_MAX_TOKENS.toString()
        shouldApplyGalleryStackGpuProbeAllowlistConfig(preferredBackend) ->
            GALLERY_STACK_GPU_PROBE_ALLOWLIST_MAX_TOKENS.toString()
        else -> resolveGpuMaxTokensForExperiment(experimentMode)
    }
    val samplerPolicy = when (experimentMode) {
        GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION -> "conversation_config_without_sampler"
        GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE -> "topk_gpu_sampler_candidate_disabled_by_no_sampler_config"
        GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER,
        GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER -> "cache_dir_probe_without_sampler"
        GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL -> "gallery_sampler_only_minimal"
        else -> "gallery_sampler_config"
    }
    return GpuRouteConfigDiagnostics(
        experimentMode = experimentMode,
        availableExperimentModes = GPU_DIAGNOSTIC_EXPERIMENT_MODES.joinToString(","),
        modelPath = resolvedModelPath,
        modelPathTail = resolvedModelPath.substringAfterLast('/').ifBlank { resolvedModelPath },
        cacheDir = resolvedCacheDir ?: "null",
        cacheDirPresent = (resolvedCacheDir != null).toString(),
        backend = "GPU",
        visionBackend = "null",
        audioBackend = "null",
        maxTokens = resolvedMaxTokens,
        normalChatEngineConfigStyle = configStyle,
        recommendedNextConfigVariant = recommendedNextGpuConfigVariant(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            configStyle = configStyle,
        ),
        samplerConfigEnabled = samplerEnabled.toString(),
        samplerTopK = if (samplerEnabled) GPU_EDGE_GALLERY_LIKE_TOP_K.toString() else "unavailable",
        samplerTopP = if (samplerEnabled) GPU_EDGE_GALLERY_LIKE_TOP_P else "unavailable",
        samplerTemperature = if (samplerEnabled) GPU_EDGE_GALLERY_LIKE_TEMPERATURE else "unavailable",
        samplerAccelerationPolicy = samplerPolicy,
        conversationConfigProfile = when (experimentMode) {
            GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
            GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER,
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER -> "no_sampler_config"
            GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL -> "sampler_only_minimal"
            else -> GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE
        },
        conversationConfigSamplerPresent = samplerEnabled.toString(),
        gpuOptionsConfigured = "false",
        gpuOptionsSource = "EngineConfig_backend_only_no_explicit_GpuOptions",
        thinkingEnabled = "false",
        speculativeDecodingEnabled = "false",
        outputQualityProbeShortMaxTokensEnabled = shortMaxTokensProbeEnabled.toString(),
        outputQualityProbeEffectiveMaxTokens = resolvedMaxTokens,
        outputQualityMatrixMode = matrixMode,
        outputQualitySamplerMode = resolveGpuOutputQualitySamplerMode(experimentMode),
        outputQualityStreamingMode = resolveGpuOutputQualityStreamingModeForDebug(
            preferredBackend = preferredBackend,
            matrixMode = matrixMode,
        ),
        outputQualityEffectiveMaxTokens = resolvedMaxTokens,
        outputQualityCollectOnlyEnabled = isGpuOutputQualityCollectOnlyMode(
            preferredBackend = preferredBackend,
            matrixMode = matrixMode,
        ).toString(),
        outputQualityUiIncrementalAppendEnabled = (!isGpuOutputQualityCollectOnlyMode(
            preferredBackend = preferredBackend,
            matrixMode = matrixMode,
        )).toString(),
    )
}

internal const val GPU_OUTPUT_QUALITY_PROBE_SHORT_MAX_TOKENS = 256
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE = "baseline"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL = "sampler_minimal"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION = "no_sampling_acceleration"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE =
    "disable_topk_gpu_sampler_candidate"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY = "collect_only"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_MINIMAL = "edge_gallery_parity_minimal"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING = "edge_gallery_parity_no_streaming"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL = "edge_gallery_parity_collect_final"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE =
    "edge_gallery_parity_no_holder_reuse"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_APP_FILES =
    "edge_gallery_parity_cache_app_files"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_NULL = "edge_gallery_parity_cache_null"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_DEFAULT =
    "edge_gallery_parity_sampler_default"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_NONE =
    "edge_gallery_parity_sampler_none"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE =
    "edge_gallery_final_response_probe"
internal const val GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE =
    "edge_gallery_executor_probe"
private val GPU_OUTPUT_QUALITY_ALLOWED_MAX_TOKENS = setOf(128, 256, 512, 1024, 4000)
internal val EDGE_GALLERY_PARITY_MATRIX_MODES = setOf(
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_MINIMAL,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_APP_FILES,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_NULL,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_DEFAULT,
    GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_NONE,
)

internal fun resolveGpuOutputQualityMatrixModeForDebug(
    preferredBackend: String,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): String {
    if (!BuildConfig.DEBUG || !standardGpuMinimalRuntimeCandidateFlavor) return "unavailable"
    if (!preferredBackend.equals("GPU", ignoreCase = true)) return "unavailable"
    val requested = propertyReader("debug.lami.gpu_output_quality_matrix_mode")
        ?: propertyReader("lami.gpu_output_quality_matrix_mode")
        ?: return GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE
    return when (requested.trim().lowercase(java.util.Locale.US)) {
        GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE,
        "edge_gallery_like",
        "baseline_edge_gallery_like" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL,
        "gpu_sampler_only_minimal",
        "sampler_only_minimal" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION,
        "gpu_no_sampling_acceleration",
        "no_sampler" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION
        GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
        "gpu_disable_topk_gpu_sampler_candidate",
        "disable_topk" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY,
        "collect_then_commit",
        "collect_only_final_commit" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_MINIMAL,
        "parity_minimal" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_MINIMAL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING,
        "parity_no_streaming" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL,
        "parity_collect_final" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE,
        "parity_no_holder_reuse" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_APP_FILES,
        "parity_cache_app_files" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_APP_FILES
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_NULL,
        "parity_cache_null" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_NULL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_DEFAULT,
        "parity_sampler_default" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_DEFAULT
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_NONE,
        "parity_sampler_none" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_NONE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
        "edge_gallery_final_probe",
        "final_response_probe",
        "parity_final_response_probe" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE,
        "executor_probe",
        "parity_executor_probe" -> GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE
        else -> GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE
    }
}

internal fun resolveGpuOutputQualityExperimentOverrideForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): String? =
    when (
        resolveGpuOutputQualityMatrixModeForDebug(
            preferredBackend = preferredBackend.name,
            propertyReader = propertyReader,
            standardGpuMinimalRuntimeCandidateFlavor = standardGpuMinimalRuntimeCandidateFlavor,
        )
    ) {
        GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL -> GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION -> GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION
        GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE ->
            GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_APP_FILES ->
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_CACHE_NULL ->
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_SAMPLER_NONE ->
            GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION
        else -> null
    }

internal fun isGpuOutputQualityCollectOnlyModeForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): Boolean =
    resolveGpuOutputQualityMatrixModeForDebug(
        preferredBackend = preferredBackend.name,
        propertyReader = propertyReader,
        standardGpuMinimalRuntimeCandidateFlavor = standardGpuMinimalRuntimeCandidateFlavor,
    ) in setOf(
        GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY,
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING,
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL,
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE,
    )

private fun isGpuOutputQualityCollectOnlyMode(
    preferredBackend: String,
    matrixMode: String,
): Boolean =
    BuildConfig.DEBUG &&
        BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR &&
        preferredBackend.equals("GPU", ignoreCase = true) &&
        matrixMode in setOf(
            GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY,
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING,
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL,
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE,
        )

internal fun resolveGpuOutputQualityMaxTokensOverrideForDebug(
    preferredBackend: String,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): Int? {
    if (!BuildConfig.DEBUG || !standardGpuMinimalRuntimeCandidateFlavor) return null
    if (!preferredBackend.equals("GPU", ignoreCase = true)) return null
    val requested = propertyReader("debug.lami.gpu_output_quality_max_tokens")
        ?: propertyReader("lami.gpu_output_quality_max_tokens")
        ?: return null
    return requested.toIntOrNull()?.takeIf { it in GPU_OUTPUT_QUALITY_ALLOWED_MAX_TOKENS }
}

private fun resolveGpuOutputQualitySamplerMode(experimentMode: String): String =
    when (experimentMode) {
        GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL -> GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL
        GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION -> GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION
        GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE ->
            GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE
        GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER,
        GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER -> "cache_dir_no_sampler"
        else -> "edge_gallery_like"
    }

internal fun isEdgeGalleryParityNoHolderReuseModeForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): Boolean =
    resolveGpuOutputQualityMatrixModeForDebug(
        preferredBackend = preferredBackend.name,
        propertyReader = propertyReader,
        standardGpuMinimalRuntimeCandidateFlavor = standardGpuMinimalRuntimeCandidateFlavor,
    ) == GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE

internal fun resolveEdgeGalleryParityModeForMatrixMode(matrixMode: String): String =
    matrixMode.takeIf { it in EDGE_GALLERY_PARITY_MATRIX_MODES } ?: "unavailable"

internal fun isEdgeGalleryFinalResponseProbeMode(matrixMode: String): Boolean =
    matrixMode == GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE

internal fun isEdgeGalleryExecutorProbeMode(matrixMode: String): Boolean =
    matrixMode == GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE

internal fun resolveEdgeGalleryParityCallbackModeForMatrixMode(
    matrixMode: String,
    streamingMode: String,
): String =
    when (matrixMode) {
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_STREAMING,
        GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_COLLECT_FINAL -> "collect_only_final_commit"
        in EDGE_GALLERY_PARITY_MATRIX_MODES -> streamingMode
        else -> "unavailable"
    }

internal fun resolveEdgeGalleryParityHolderReuseForMatrixMode(
    matrixMode: String,
    holderReused: Boolean?,
): String =
    when {
        matrixMode == GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_NO_HOLDER_REUSE -> "disabled_for_parity_probe"
        matrixMode in EDGE_GALLERY_PARITY_MATRIX_MODES -> holderReused.toDiagnosticValue()
        else -> "unavailable"
    }

internal fun resolveEdgeGalleryParityDifferenceSummary(
    matrixMode: String,
    sourceCorruptionStage: String,
    candidateResult: String,
    samplerRootCauseCandidate: String,
): String =
    when {
        matrixMode !in EDGE_GALLERY_PARITY_MATRIX_MODES -> "unavailable"
        candidateResult == "quality_candidate_pass" -> "lami_gpu_parity_mode_passed"
        sourceCorruptionStage == "raw_callback" &&
            samplerRootCauseCandidate == "runtime_decode_fragmentation" ->
            "edge_gallery_gpu_ok_lami_gpu_raw_callback_decode_fragmentation"
        sourceCorruptionStage == "raw_callback" -> "edge_gallery_gpu_ok_lami_gpu_raw_callback_corruption"
        samplerRootCauseCandidate == "streaming_join_issue" -> "lami_streaming_join_diff_candidate"
        else -> "edge_gallery_parity_difference_still_unclassified"
    }

internal fun resolveEdgeGalleryCallbackTextSemanticsCandidate(
    matrixMode: String,
    callbackCount: Int,
    accumulatedTextLength: Int,
    lastNonEmptyTextLength: Int,
): String {
    if (!isEdgeGalleryFinalResponseProbeMode(matrixMode)) return "unavailable"
    if (callbackCount <= 0 || lastNonEmptyTextLength <= 0 || accumulatedTextLength <= 0) return "unknown"
    if (callbackCount == 1) return "final_only"
    val ratio = lastNonEmptyTextLength.toDouble() / accumulatedTextLength.coerceAtLeast(1)
    return when {
        ratio >= 0.80 -> "accumulated_text"
        ratio <= 0.25 -> "delta_chunks"
        else -> "unknown"
    }
}

internal fun resolveEdgeGalleryFinalResponseProbeResult(
    matrixMode: String,
    finalCandidateLength: Int,
    finalCandidateSuspiciousReason: String,
): String =
    when {
        !isEdgeGalleryFinalResponseProbeMode(matrixMode) -> "unavailable"
        finalCandidateLength <= 0 -> "unavailable"
        finalCandidateSuspiciousReason == "none" -> "pass"
        else -> "fail"
    }

internal fun resolveEdgeGalleryFinalResponseProbeDifferenceSummary(
    matrixMode: String,
    appendAllSuspiciousReason: String,
    finalCandidateSuspiciousReason: String,
    callbackSemanticsCandidate: String,
    accumulatedTextLength: Int,
    finalCandidateLength: Int,
): String {
    if (!isEdgeGalleryFinalResponseProbeMode(matrixMode)) return "unavailable"
    if (finalCandidateLength <= 0) return "final_response_probe_no_non_empty_callback"
    if (
        callbackSemanticsCandidate == "delta_chunks" &&
        accumulatedTextLength > 256 &&
        finalCandidateLength < accumulatedTextLength / 4
    ) {
        return "last_non_empty_callback_is_delta_not_final_response"
    }
    return when {
        appendAllSuspiciousReason != "none" && finalCandidateSuspiciousReason == "none" ->
            "append_all_chunks_suspicious_last_non_empty_clean"
        appendAllSuspiciousReason != "none" && finalCandidateSuspiciousReason != "none" ->
            "append_all_chunks_and_last_non_empty_both_suspicious"
        appendAllSuspiciousReason == "none" && finalCandidateSuspiciousReason != "none" ->
            "append_all_chunks_clean_last_non_empty_suspicious"
        else -> "final_response_probe_pass"
    }
}

private fun resolveGpuOutputQualityStreamingModeForDebug(
    preferredBackend: String,
    matrixMode: String,
): String =
    if (isGpuOutputQualityCollectOnlyMode(preferredBackend, matrixMode)) {
        "collect_only_final_commit"
    } else if (preferredBackend.equals("GPU", ignoreCase = true)) {
        "incremental_callback_streaming"
    } else {
        "unavailable"
    }

internal fun isGpuOutputQualityProbeShortMaxTokensEnabledForDebug(
    preferredBackend: String,
    propertyReader: (String) -> String? = ::readLocalRouteDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG || !BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR) return false
    if (!preferredBackend.equals("GPU", ignoreCase = true)) return false
    val enabled = propertyReader("debug.lami.gpu_output_quality_probe_short_max_tokens")
        ?: propertyReader("lami.gpu_output_quality_probe_short_max_tokens")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

private fun readLocalRouteDebugProperty(key: String): String? {
    val jvmProperty = runCatching {
        System.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (jvmProperty != null) return jvmProperty
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

internal fun resolveGpuMaxTokensForExperiment(experimentMode: String): String =
    if (experimentMode == GPU_EXPERIMENT_MODE_MAX_TOKENS_32) {
        "32"
    } else {
        GPU_EDGE_GALLERY_LIKE_MAX_TOKENS.toString()
    }

internal fun shouldUseGpuDiagnosticSamplerConfig(experimentMode: String): Boolean =
    experimentMode != GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION &&
        experimentMode != GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE &&
        experimentMode != GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER &&
        experimentMode != GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER

internal fun resolveGpuExperimentCacheDirForDiagnostics(
    modelPath: String,
    cacheDirPath: String?,
    experimentMode: String,
): String? =
    when (experimentMode) {
        GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
        GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER -> null
        GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
        GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER -> cacheDirPath
        else -> if (modelPath.startsWith("/data/local/tmp")) cacheDirPath else null
    }

internal fun shouldApplyGpuExperimentalStageTimeout(
    context: LocalRouteDiagnosticContext,
): Boolean =
    context.localRouteEntered &&
        context.baselineRole == LITERT_LM_BASELINE_GPU_EXPERIMENTAL

internal fun resolveGpuExperimentalTimeoutFailureStage(
    lastStage: String?,
): String =
    when (lastStage) {
        "engine_config_build_started" -> "engine_config_build_timeout"
        "engine_config_build_finished" -> "engine_create_timeout"
        "engine_create_started" -> "engine_create_timeout"
        "engine_create_finished" -> "engine_initialize_timeout"
        "engine_initialize_started" -> "engine_initialize_timeout"
        "engine_initialize_finished" -> "conversation_create_timeout"
        "conversation_create_started" -> "conversation_create_timeout"
        "conversation_create_finished" -> "generate_start_timeout"
        "generate_started",
        "generate_call_entered",
        "generate_call_returned",
        "generate_call_returned_null",
        "generate_callback_invoked",
        "generate_callback_exception" -> "first_token_timeout"
        else -> "engine_create_timeout"
    }

internal fun resolveGpuExperimentalTimeoutStage(
    failureStage: String?,
    flags: LocalRouteDiagnosticFlags? = null,
): String {
    if (flags != null && failureStage != null && failureStage.contains("timeout")) {
        val stageFromFlags = resolveGpuExperimentalTimeoutStageFromFlags(flags)
        if (stageFromFlags != "unknown") return stageFromFlags
    }
    return when (failureStage) {
        "gpu_watchdog_timeout_generate_before_first_token",
        "first_token_timeout" -> "generate_before_first_token"
        "gpu_watchdog_timeout_generate_after_first_token" -> "generate_after_first_token"
        "generate_start_timeout" -> "generate_start"
        "conversation_create_timeout" -> "conversation_create"
        "engine_initialize_timeout" -> "engine_initialize"
        "engine_config_build_timeout" -> "engine_config_build"
        "engine_create_timeout", "gpu_watchdog_timeout" -> "engine_constructor"
        else -> "unknown"
    }
}

internal fun resolveGpuExperimentalTimeoutStageFromFlags(
    flags: LocalRouteDiagnosticFlags,
): String =
    when {
        flags.engineConfigBuildStarted == true && flags.engineConfigBuildFinished != true -> "engine_config_build"
        flags.engineCreateStarted == true && flags.engineCreateFinished != true -> "engine_constructor"
        flags.engineInitializeStarted == true && flags.engineInitializeFinished != true -> "engine_initialize"
        flags.conversationCreateStarted == true && flags.conversationCreateFinished != true -> "conversation_create"
        flags.generateStarted == true && flags.firstTokenReceived == true -> "generate_after_first_token"
        flags.generateStarted == true -> "generate_before_first_token"
        flags.conversationCreateFinished == true -> "generate_start"
        else -> "unknown"
    }

internal fun resolveGpuWatchdogFailureStage(
    failureStage: String?,
    flags: LocalRouteDiagnosticFlags,
): String =
    if (failureStage == "gpu_watchdog_timeout") {
        "gpu_watchdog_timeout_${resolveGpuExperimentalTimeoutStageFromFlags(flags)}"
    } else {
        failureStage?.takeIf { it.isNotBlank() } ?: "none"
    }

internal fun resolveGpuExperimentalWatchdogMode(
    timeoutMs: Long,
): String =
    when (timeoutMs) {
        GPU_EXPERIMENTAL_STAGE_TIMEOUT_EXTENDED_DEV_MS -> "extended_dev_60s"
        GPU_EXPERIMENTAL_STAGE_TIMEOUT_STANDARD_MS -> "standard_20s"
        else -> "custom_${timeoutMs.coerceAtLeast(0L)}ms"
    }

private fun resolveGpuLastKnownStage(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.firstTokenReceived == true -> "first_token_received"
        flags.generateStarted == true -> "generate_started"
        flags.conversationCreateFinished == true -> "conversation_create_finished"
        flags.conversationCreateStarted == true -> "conversation_create_started"
        flags.engineInitializeFinished == true -> "engine_initialize_finished"
        flags.engineInitializeStarted == true -> "engine_initialize_started"
        flags.engineCreateFinished == true -> "engine_create_finished"
        flags.engineCreateStarted == true -> "engine_create_started"
        flags.engineConfigBuildFinished == true -> "engine_config_build_finished"
        flags.engineConfigBuildStarted == true -> "engine_config_build_started"
        else -> "unavailable"
    }

private fun resolveGpuInitializeCallState(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.engineInitializeFinished == true -> "finished"
        flags.engineInitializeStarted == true -> "started"
        flags.engineCreateFinished == true -> "not_started_after_engine_constructor"
        flags.engineCreateStarted == true -> "not_reached_engine_constructor_pending"
        flags.engineConfigBuildFinished == true -> "not_reached_engine_constructor_not_started"
        flags.engineConfigBuildStarted == true -> "not_reached_engine_config_pending"
        else -> "unavailable"
    }

private fun resolveGpuTimeoutCheckpoint(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.firstTokenReceived == true -> "after_first_token"
        flags.generateStarted == true -> "generate_started"
        flags.conversationCreateStarted == true -> "conversation_create"
        flags.engineInitializeStarted == true && flags.engineInitializeFinished != true -> "engine_initialize"
        flags.engineCreateStarted == true && flags.engineCreateFinished != true -> "engine_constructor"
        flags.engineConfigBuildStarted == true && flags.engineConfigBuildFinished != true -> "engine_config_build"
        else -> resolveGpuLastKnownStage(flags)
    }

private fun resolveGenerateBeforeFirstTokenElapsedMs(
    flags: LocalRouteDiagnosticFlags,
    elapsedMs: Long,
): String {
    if (flags.generateStarted != true || flags.firstTokenReceived == true) return "unavailable"
    val generateStartedAtMs = flags.generateStartedElapsedMs ?: return "unavailable"
    return (elapsedMs - generateStartedAtMs).coerceAtLeast(0L).toString()
}
