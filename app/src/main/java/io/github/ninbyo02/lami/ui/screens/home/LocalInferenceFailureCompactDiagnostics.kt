package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.lang.reflect.InvocationTargetException

internal data class LocalInferenceFailureCompactInput(
    val inputPrompt: String,
    val preferredBackendSetting: PreferredBackendDryRunSetting,
    val npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    val status: String = "failure",
    val reason: String = "local_inference_failure",
    val failureStage: String = "unknown",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val failureCauseClass: String = "unavailable",
    val failureCauseMessage: String = "unavailable",
    val failureRootCauseClass: String = "unavailable",
    val failureRootCauseMessage: String = "unavailable",
    val reflectionTargetExceptionClass: String = "unavailable",
    val reflectionTargetExceptionMessage: String = "unavailable",
    val reflectionTargetExceptionRootCauseClass: String = "unavailable",
    val reflectionTargetExceptionRootCauseMessage: String = "unavailable",
    val exceptionChain: String = "unavailable",
    val liteRtLmPreviousInvocationStillProcessing: Boolean = false,
    val generateConcurrencyViolationSuspected: Boolean = false,
    val gpuPrefillProbeDiagnostics: Map<String, String> = emptyMap(),
    val engineConfigBackend: String = "unavailable",
    val normalChatNativeRouteBlocked: Boolean = false,
    val blockedReason: String = "none",
    val guardRecommendation: String = "unavailable",
    val localRouteStarted: Boolean = true,
    val localEngineCreateStarted: Boolean = false,
    val localEngineCreateFinished: Boolean = false,
    val localGenerateStarted: Boolean = false,
    val localGenerateFinished: Boolean = false,
    val fallbackUsed: Boolean = false,
    val timeout: Boolean = false,
    val freshCrash: Boolean = false,
    val ttsRequested: Boolean = false,
    val dbRequested: Boolean = false,
    val markdownRequested: Boolean = false,
    val streamingRequested: Boolean = false,
    val gpuWatchdogTimeoutMs: String = "unavailable",
    val gpuWatchdogMode: String = "unavailable",
    val gpuWatchdogFailureStage: String = "unavailable",
    val gpuTimeoutStage: String = "unavailable",
    val gpuTimeoutElapsedMs: String = "unavailable",
    val gpuEngineCreateDurationMs: String = "unavailable",
    val gpuEngineCreateStarted: String = "unavailable",
    val gpuEngineCreateFinished: String = "unavailable",
    val gpuEngineCreateTimeoutSuspected: String = "unavailable",
    val gpuConversationCreateStarted: String = "unavailable",
    val gpuConversationCreateFinished: String = "unavailable",
    val gpuGenerateStarted: String = "unavailable",
    val gpuFirstTokenReceived: String = "unavailable",
    val gpuFirstTokenElapsedMs: String = "unavailable",
    val generateCallStartedAtElapsedMs: String = "unavailable",
    val firstTokenReceivedAtElapsedMs: String = "unavailable",
    val generateBeforeFirstTokenElapsedMs: String = "unavailable",
    val gpuGenerateBeforeFirstTokenTimeoutSuspected: String = "unavailable",
    val gpuLastKnownStage: String = "unavailable",
    val gpuHeldEngineExists: String = "unavailable",
    val gpuHeldEngineReused: String = "unavailable",
    val holderCreated: String = "unavailable",
    val holderAcquired: String = "unavailable",
    val holderReused: String = "unavailable",
    val holderInvalidated: String = "unavailable",
    val holderClosed: String = "unavailable",
    val holderTimeoutCleanup: String = "unavailable",
    val holderFailureCleanup: String = "unavailable",
    val holderProcessRestart: String = "unavailable",
    val heldEngineLifecycleHistory: String = "unavailable",
    val heldEngineDestroyReason: String = "unavailable",
    val heldEngineLastOwner: String = "unavailable",
    val heldEngineLastFailureStage: String = "unavailable",
    val heldEngineSnapshotBeforeDestroy: String = "unavailable",
    val gpuModelKind: String = "unavailable",
    val gpuSelectedModelName: String = "unavailable",
    val gpuSelectedModelFile: String = "unavailable",
    val gpuModelPath: String = "unavailable",
    val gpuModelPathTail: String = "unavailable",
    val gpuBackendSetting: String = "unavailable",
    val gpuCompatibilityMode: String = "unavailable",
    val gpuEngineConfigProfile: String = "unavailable",
    val gpuExperimentMode: String = "unavailable",
    val gpuExperimentModesAvailable: String = "unavailable",
    val gpuCacheDirMode: String = "unavailable",
    val gpuEngineConfigModelPath: String = "unavailable",
    val gpuEngineConfigModelPathTail: String = "unavailable",
    val gpuEngineConfigCacheDir: String = "unavailable",
    val gpuEngineConfigCacheDirPresent: String = "unavailable",
    val gpuEngineConfigBackend: String = "unavailable",
    val gpuEngineConfigVisionBackend: String = "unavailable",
    val gpuEngineConfigAudioBackend: String = "unavailable",
    val gpuEngineConfigMaxTokens: String = "unavailable",
    val gpuEngineConfigBuildStarted: String = "unavailable",
    val gpuEngineConfigBuildFinished: String = "unavailable",
    val gpuEngineConstructorStarted: String = "unavailable",
    val gpuEngineConstructorFinished: String = "unavailable",
    val gpuEngineInitializeStarted: String = "unavailable",
    val gpuEngineInitializeFinished: String = "unavailable",
    val gpuEngineInitializeCallState: String = "unavailable",
    val gpuTimeoutCheckpoint: String = "unavailable",
    val gpuModelPathMode: String = "unavailable",
    val gpuSamplerConfigProfile: String = "unavailable",
    val gpuSamplerConfigEnabled: String = "unavailable",
    val gpuSamplerConfigTopK: String = "unavailable",
    val gpuSamplerConfigTopP: String = "unavailable",
    val gpuSamplerConfigTemperature: String = "unavailable",
    val gpuSamplerAccelerationPolicy: String = "unavailable",
    val gpuConversationConfigProfile: String = "unavailable",
    val gpuConversationConfigSamplerPresent: String = "unavailable",
    val gpuOptionsConfigured: String = "unavailable",
    val gpuOptionsSource: String = "unavailable",
    val gpuThinkingEnabled: String = "unavailable",
    val gpuSpeculativeDecodingEnabled: String = "unavailable",
    val gpuMaxTokens: String = "unavailable",
    val gpuTopK: String = "unavailable",
    val gpuTopP: String = "unavailable",
    val gpuTemperature: String = "unavailable",
    val gpuDispatcher: String = "unavailable",
    val gpuEngineInitializeApi: String = "unavailable",
    val gpuEdgeGalleryDiffApplied: String = "unavailable",
    val gpuRouteDivergencePoint: String = "unavailable",
    val debugLamiGpuGenerateProbeMode: String = "unavailable",
    val gpuGenerateCallEntered: String = "unavailable",
    val gpuGenerateCallReturned: String = "unavailable",
    val gpuCallbackInvokedCount: String = "unavailable",
    val gpuCallbackFirstInvokedAtElapsedMs: String = "unavailable",
    val gpuCallbackLastInvokedAtElapsedMs: String = "unavailable",
    val gpuCallbackThreadName: String = "unavailable",
    val gpuCallbackDoneTrueSeen: String = "unavailable",
    val gpuDoneTrueSeen: String = "unavailable",
    val gpuCallbackErrorSeen: String = "unavailable",
    val gpuCallbackEmptyTextCount: String = "unavailable",
    val gpuCallbackNonEmptyTextCount: String = "unavailable",
    val gpuCallbackLastTextLength: String = "unavailable",
    val gpuCallbackLastTextHead: String = "unavailable",
    val gpuFirstNonEmptyTextElapsedMs: String = "unavailable",
    val gpuFirstTokenClassificationReason: String = "unavailable",
    val gpuCallbackExceptionClass: String = "unavailable",
    val gpuCallbackExceptionMessage: String = "unavailable",
    val gpuCallbackExceptionChain: String = "unavailable",
    val gpuCallbackExceptionStage: String = "unavailable",
    val gpuGenerateStallInterpretation: String = "unavailable",
    val cpuCallbackInvokedCount: String = "unavailable",
    val cpuDoneTrueSeen: String = "unavailable",
    val cpuFirstNonEmptyTextElapsedMs: String = "unavailable",
    val callbackRouteDiff: String = "unavailable",
    val gpuGenerateActualPrompt: String = "unavailable",
    val gpuGeneratePromptLengthChars: String = "unavailable",
    val gpuGenerateInputTokenEstimate: String = "unavailable",
    val gpuGenerateExceptionSeen: String = "unavailable",
    val gpuGenerateExceptionClass: String = "unavailable",
    val gpuGenerateExceptionMessageRaw: String = "unavailable",
    val gpuGenerateExceptionMessageSanitized: String = "unavailable",
    val gpuGenerateExceptionStatusCode: String = "unavailable",
    val gpuGenerateExceptionErrorFile: String = "unavailable",
    val gpuGenerateExceptionErrorLine: String = "unavailable",
    val gpuGenerateExceptionSummary: String = "unavailable",
    val gpuGenerateFailedBeforeFirstToken: String = "unavailable",
    val gpuWatchdogBypassedDueToGenerateException: String = "unavailable",
    val liteRtLmErrorKind: String = "unavailable",
    val liteRtLmErrorStatusCode: String = "unavailable",
    val liteRtLmErrorPrimaryFile: String = "unavailable",
    val liteRtLmErrorPrimaryLine: String = "unavailable",
    val liteRtLmErrorSecondaryFile: String = "unavailable",
    val liteRtLmErrorSecondaryLine: String = "unavailable",
    val liteRtLmErrorRecoverabilityHint: String = "unavailable",
    val liteRtCompiledModelExecutorFailureCategory: String = "unavailable",
    val cpuCompareStarted: String = "unavailable",
    val cpuCompareEngineInitializeFinished: String = "unavailable",
    val cpuCompareConversationCreateFinished: String = "unavailable",
    val cpuCompareGenerateStarted: String = "unavailable",
    val cpuCompareCallbackInvokedCount: String = "unavailable",
    val cpuCompareFirstNonEmptyTextElapsedMs: String = "unavailable",
    val cpuCompareDoneTrueSeen: String = "unavailable",
    val cpuCompareExceptionClass: String = "unavailable",
    val cpuCompareExceptionMessage: String = "unavailable",
    val cpuGpuGenerateDiff: String = "unavailable",
    val gpuCallbackToUiEnabled: String = "unavailable",
    val gpuCallbackTextPromotedToUi: String = "unavailable",
    val gpuCallbackPromotedTextLength: String = "unavailable",
    val gpuCallbackPromotedNonEmptyCount: String = "unavailable",
    val gpuCallbackSuccessClassification: String = "unavailable",
    val gpuRawCallbackProbeStatus: String = "unavailable",
    val gpuUiAppendStarted: String = "unavailable",
    val gpuUiAppendFinished: String = "unavailable",
    val gpuUiFirstVisibleTextElapsedMs: String = "unavailable",
    val gpuStreamingCompletionReason: String = "unavailable",
    val gpuNormalRouteUseCallbackStreaming: String = "unavailable",
    val gpuCallbackStreamingPathSelected: String = "unavailable",
    val gpuCallbackStreamingPathReason: String = "unavailable",
    val gpuCallbackStreamingSuccessCount: String = "unavailable",
    val gpuCallbackStreamingEmptyCallbackCount: String = "unavailable",
    val gpuCallbackStreamingNonEmptyCallbackCount: String = "unavailable",
    val gpuCallbackStreamingDoneTrueSeen: String = "unavailable",
    val gpuCallbackStreamingFinalTextLength: String = "unavailable",
    val gpuCallbackStreamingReusedHeldEngine: String = "unavailable",
    val gpuCallbackStreamingCompletionReason: String = "unavailable",
    val gpuCallbackStreamingFailureReason: String = "unavailable",
    val standardGpuProbeExpectedEdgeGalleryE2b: String = "unavailable",
    val standardGpuProbeModelSizeBytes: String = "unavailable",
    val standardGpuProbeModelSha256Expected: String = "unavailable",
    val standardGpuProbeModelSha256Actual: String = "unavailable",
    val standardGpuProbeModelIdentityHint: String = "unavailable",
    val standardGpuProbeRuntimeStack: String = "unavailable",
    val standardGpuProbeCallbackStreamingGate: String = "unavailable",
    val standardGpuProbeResultCandidate: String = "unavailable",
    val gpuLiteRtExecutorErrorFile: String = "unavailable",
    val gpuLiteRtExecutorErrorLine: String = "unavailable",
    val gpuLiteRtCompiledModelErrorFile: String = "unavailable",
    val gpuLiteRtCompiledModelErrorLine: String = "unavailable",
    val gpuEngineInitializeInternalErrorDetected: String = "unavailable",
    val gpuCompiledModelCreationFailed: String = "unavailable",
    val gpuFailureInterpretation: String = "unavailable",
    val liteRtLmBackendCandidates: String = "unavailable",
    val liteRtLmBackendGpuArtisanAvailable: String = "unavailable",
    val liteRtLmBackendCpuArtisanAvailable: String = "unavailable",
    val liteRtLmBackendGoogleTensorArtisanAvailable: String = "unavailable",
    val liteRtLmEngineConfigArtisanApiAvailable: String = "unavailable",
    val liteRtLmRuntimeConfigAvailable: String = "unavailable",
    val liteRtLmBackendConstraintApiAvailable: String = "unavailable",
    val liteRtLmPreferredEngineTypeApiAvailable: String = "unavailable",
    val selectedModelBackendConstraintHint: String = "unavailable",
    val selectedModelArtisanHint: String = "unavailable",
    val edgeGalleryArtisanStaticEvidence: String = "unavailable",
    val liteRtRuntimeExecutorCandidates: String = "unavailable",
    val liteRtRuntimeExecutorSelectionHint: String = "unavailable",
    val liteRtRuntimeBackendConstraintHint: String = "unavailable",
    val liteRtRuntimeCompiledModelExecutorHint: String = "unavailable",
    val liteRtRuntimeGpuExecutorHint: String = "unavailable",
    val liteRtRuntimeArtisanEvidence: String = "unavailable",
    val galleryStackProbeDiagnostics: GalleryStackGpuProbeRuntimeDiagnostics? = null,
    val runtimeAlignmentProbeDiagnostics: RuntimeAlignmentProbeDiagnostics? = null,
    val gpuFallbackUsed: String = "unavailable",
    val gpuStaleCallbackIgnored: String = "unavailable",
    val modelName: String = "unavailable",
    val modelFile: String = "unavailable",
    val threadName: String = Thread.currentThread().name.ifBlank { "unavailable" },
    val processPid: String = "unavailable",
    val memoryBefore: MemorySnapshot? = null,
    val memoryAfter: MemorySnapshot? = null,
    val javaHeapUsedMb: Long? = memoryAfter?.javaHeapUsedMb ?: memoryBefore?.javaHeapUsedMb,
    val nativeHeapAllocMb: Long? = memoryAfter?.nativeHeapAllocatedMb ?: memoryBefore?.nativeHeapAllocatedMb,
)

internal fun buildLocalInferenceFailureCompactDiagnosticsText(
    input: LocalInferenceFailureCompactInput,
): String {
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = input.preferredBackendSetting,
        npuStandardRouteMode = input.npuStandardRouteMode,
    )
    val modelFile = input.modelFile.takeIf { it.isNotBlank() && it != "unavailable" }
    val model = modelFile?.let(::File)
    val guardRecommendation = if (input.generateConcurrencyViolationSuspected) {
        "reset_gpu_engine_or_force_cpu"
    } else {
        input.guardRecommendation.ifBlank { "unavailable" }
    }
    return (
        listOf(
        "[DEV診断: Local inference failure compact]",
        "input_prompt=${escapeLocalInferenceFailureValue(input.inputPrompt)}",
        "selected_backend=${backendDiagnostics.selectedBackend}",
        "requested_backend=${backendDiagnostics.requestedBackend}",
        "effective_backend=${backendDiagnostics.effectiveBackend}",
        "route_family=${backendDiagnostics.routeFamily}",
        "backend_evidence=${backendDiagnostics.backendEvidence}",
        "status=${input.status}",
        "reason=${input.reason}",
        "failure_stage=${input.failureStage.ifBlank { "unknown" }}",
        "failure_exception_class=${input.failureExceptionClass.ifBlank { "unavailable" }}",
        "failure_exception_message=${escapeLocalInferenceFailureValue(input.failureExceptionMessage.ifBlank { "unavailable" })}",
        "failure_cause_class=${input.failureCauseClass.ifBlank { "unavailable" }}",
        "failure_cause_message=${escapeLocalInferenceFailureValue(input.failureCauseMessage.ifBlank { "unavailable" })}",
        "failure_root_cause_class=${input.failureRootCauseClass.ifBlank { "unavailable" }}",
        "failure_root_cause_message=${escapeLocalInferenceFailureValue(input.failureRootCauseMessage.ifBlank { "unavailable" })}",
        "reflection_target_exception_class=${input.reflectionTargetExceptionClass.ifBlank { "unavailable" }}",
        "reflection_target_exception_message=${escapeLocalInferenceFailureValue(input.reflectionTargetExceptionMessage.ifBlank { "unavailable" })}",
        "reflection_target_exception_root_cause_class=${input.reflectionTargetExceptionRootCauseClass.ifBlank { "unavailable" }}",
        "reflection_target_exception_root_cause_message=${escapeLocalInferenceFailureValue(input.reflectionTargetExceptionRootCauseMessage.ifBlank { "unavailable" })}",
        "exception_chain=${escapeLocalInferenceFailureValue(input.exceptionChain.ifBlank { "unavailable" })}",
        "lite_rt_lm_previous_invocation_still_processing=${input.liteRtLmPreviousInvocationStillProcessing}",
        "generate_concurrency_violation_suspected=${input.generateConcurrencyViolationSuspected}",
        "engine_config_backend=${input.engineConfigBackend.ifBlank { "unavailable" }}",
        "preferred_backend_setting=${input.preferredBackendSetting.name}",
        "npu_standard_route_setting=${input.npuStandardRouteMode.name}",
        "normal_chat_native_route_blocked=${input.normalChatNativeRouteBlocked}",
        "blocked_reason=${input.blockedReason.ifBlank { "none" }}",
        "guard_recommendation=$guardRecommendation",
        "local_route_started=${input.localRouteStarted}",
        "local_engine_create_started=${input.localEngineCreateStarted}",
        "local_engine_create_finished=${input.localEngineCreateFinished}",
        "local_generate_started=${input.localGenerateStarted}",
        "local_generate_finished=${input.localGenerateFinished}",
        "fallback_used=${input.fallbackUsed}",
        "timeout=${input.timeout}",
        "fresh_crash=${input.freshCrash}",
        "tts_requested=${input.ttsRequested}",
        "db_requested=${input.dbRequested}",
        "markdown_requested=${input.markdownRequested}",
        "streaming_requested=${input.streamingRequested}",
        "gpu_watchdog_timeout_ms=${input.gpuWatchdogTimeoutMs}",
        "gpu_watchdog_mode=${input.gpuWatchdogMode}",
        "gpu_watchdog_failure_stage=${input.gpuWatchdogFailureStage}",
        "gpu_timeout_stage=${input.gpuTimeoutStage}",
        "gpu_timeout_elapsed_ms=${input.gpuTimeoutElapsedMs}",
        "gpu_engine_create_duration_ms=${input.gpuEngineCreateDurationMs}",
        "gpu_engine_create_started=${input.gpuEngineCreateStarted}",
        "gpu_engine_create_finished=${input.gpuEngineCreateFinished}",
        "gpu_engine_create_timeout_suspected=${input.gpuEngineCreateTimeoutSuspected}",
        "gpu_conversation_create_started=${input.gpuConversationCreateStarted}",
        "gpu_conversation_create_finished=${input.gpuConversationCreateFinished}",
        "gpu_generate_started=${input.gpuGenerateStarted}",
        "gpu_first_token_received=${input.gpuFirstTokenReceived}",
        "gpu_first_token_elapsed_ms=${input.gpuFirstTokenElapsedMs}",
        "generate_call_started_at_elapsed_ms=${input.generateCallStartedAtElapsedMs}",
        "first_token_received_at_elapsed_ms=${input.firstTokenReceivedAtElapsedMs}",
        "generate_before_first_token_elapsed_ms=${input.generateBeforeFirstTokenElapsedMs}",
        "gpu_generate_before_first_token_timeout_suspected=${input.gpuGenerateBeforeFirstTokenTimeoutSuspected}",
        "gpu_last_known_stage=${input.gpuLastKnownStage}",
        "gpu_held_engine_exists=${input.gpuHeldEngineExists}",
        "gpu_held_engine_reused=${input.gpuHeldEngineReused}",
        "held_engine_exists=${input.gpuHeldEngineExists}",
        "held_engine_reused=${input.gpuHeldEngineReused}",
        "holder_created=${input.holderCreated}",
        "holder_acquired=${input.holderAcquired}",
        "holder_reused=${input.holderReused}",
        "holder_invalidated=${input.holderInvalidated}",
        "holder_closed=${input.holderClosed}",
        "holder_timeout_cleanup=${input.holderTimeoutCleanup}",
        "holder_failure_cleanup=${input.holderFailureCleanup}",
        "holder_process_restart=${input.holderProcessRestart}",
        "held_engine_lifecycle_history=${escapeLocalInferenceFailureValue(input.heldEngineLifecycleHistory)}",
        "held_engine_destroy_reason=${input.heldEngineDestroyReason}",
        "held_engine_last_owner=${input.heldEngineLastOwner}",
        "held_engine_last_failure_stage=${input.heldEngineLastFailureStage}",
        "held_engine_snapshot_before_destroy=${escapeLocalInferenceFailureValue(input.heldEngineSnapshotBeforeDestroy)}",
        "gpu_model_kind=${input.gpuModelKind}",
        "gpu_selected_model_name=${escapeLocalInferenceFailureValue(input.gpuSelectedModelName)}",
        "gpu_selected_model_file=${escapeLocalInferenceFailureValue(input.gpuSelectedModelFile)}",
        "gpu_model_path=${escapeLocalInferenceFailureValue(input.gpuModelPath)}",
        "gpu_model_path_tail=${escapeLocalInferenceFailureValue(input.gpuModelPathTail)}",
        "gpu_backend_setting=${input.gpuBackendSetting}",
        "gpu_compatibility_mode=${input.gpuCompatibilityMode}",
        "gpu_engine_config_profile=${input.gpuEngineConfigProfile}",
        "gpu_experiment_mode=${input.gpuExperimentMode}",
        "experiment_mode=${input.gpuExperimentMode}",
        "gpu_experiment_modes_available=${input.gpuExperimentModesAvailable}",
        "gpu_cache_dir_mode=${input.gpuCacheDirMode}",
        "gpu_engine_config_model_path=${escapeLocalInferenceFailureValue(input.gpuEngineConfigModelPath)}",
        "gpu_engine_config_model_path_tail=${escapeLocalInferenceFailureValue(input.gpuEngineConfigModelPathTail)}",
        "gpu_engine_config_cache_dir=${escapeLocalInferenceFailureValue(input.gpuEngineConfigCacheDir)}",
        "gpu_engine_config_cache_dir_present=${input.gpuEngineConfigCacheDirPresent}",
        "gpu_engine_config_backend=${input.gpuEngineConfigBackend}",
        "gpu_engine_config_vision_backend=${input.gpuEngineConfigVisionBackend}",
        "gpu_engine_config_audio_backend=${input.gpuEngineConfigAudioBackend}",
        "gpu_engine_config_max_tokens=${input.gpuEngineConfigMaxTokens}",
        "gpu_engine_config_build_started=${input.gpuEngineConfigBuildStarted}",
        "gpu_engine_config_build_finished=${input.gpuEngineConfigBuildFinished}",
        "gpu_engine_constructor_started=${input.gpuEngineConstructorStarted}",
        "gpu_engine_constructor_finished=${input.gpuEngineConstructorFinished}",
        "gpu_engine_initialize_started=${input.gpuEngineInitializeStarted}",
        "gpu_engine_initialize_finished=${input.gpuEngineInitializeFinished}",
        "gpu_engine_initialize_call_state=${input.gpuEngineInitializeCallState}",
        "gpu_timeout_checkpoint=${input.gpuTimeoutCheckpoint}",
        "gpu_model_path_mode=${input.gpuModelPathMode}",
        "gpu_sampler_config_profile=${input.gpuSamplerConfigProfile}",
        "gpu_sampler_config_enabled=${input.gpuSamplerConfigEnabled}",
        "gpu_sampler_config_top_k=${input.gpuSamplerConfigTopK}",
        "gpu_sampler_config_top_p=${input.gpuSamplerConfigTopP}",
        "gpu_sampler_config_temperature=${input.gpuSamplerConfigTemperature}",
        "gpu_sampler_acceleration_policy=${input.gpuSamplerAccelerationPolicy}",
        "gpu_conversation_config_profile=${input.gpuConversationConfigProfile}",
        "gpu_conversation_config_sampler_present=${input.gpuConversationConfigSamplerPresent}",
        "gpu_options_configured=${input.gpuOptionsConfigured}",
        "gpu_options_source=${input.gpuOptionsSource}",
        "gpu_thinking_enabled=${input.gpuThinkingEnabled}",
        "gpu_speculative_decoding_enabled=${input.gpuSpeculativeDecodingEnabled}",
        "gpu_max_tokens=${input.gpuMaxTokens}",
        "gpu_top_k=${input.gpuTopK}",
        "gpu_top_p=${input.gpuTopP}",
        "gpu_temperature=${input.gpuTemperature}",
        "gpu_dispatcher=${input.gpuDispatcher}",
        "gpu_engine_initialize_api=${input.gpuEngineInitializeApi}",
        "gpu_edge_gallery_diff_applied=${input.gpuEdgeGalleryDiffApplied}",
        "gpu_route_divergence_point=${input.gpuRouteDivergencePoint}",
        "debug_lami_gpu_generate_probe_mode=${input.debugLamiGpuGenerateProbeMode}",
        "gpu_generate_call_entered=${input.gpuGenerateCallEntered}",
        "gpu_generate_call_returned=${input.gpuGenerateCallReturned}",
        "gpu_callback_invoked_count=${input.gpuCallbackInvokedCount}",
        "gpu_callback_first_invoked_at_elapsed_ms=${input.gpuCallbackFirstInvokedAtElapsedMs}",
        "gpu_callback_last_invoked_at_elapsed_ms=${input.gpuCallbackLastInvokedAtElapsedMs}",
        "gpu_callback_thread_name=${escapeLocalInferenceFailureValue(input.gpuCallbackThreadName)}",
        "gpu_callback_done_true_seen=${input.gpuCallbackDoneTrueSeen}",
        "gpu_done_true_seen=${input.gpuDoneTrueSeen}",
        "gpu_callback_error_seen=${input.gpuCallbackErrorSeen}",
        "gpu_callback_empty_text_count=${input.gpuCallbackEmptyTextCount}",
        "gpu_callback_non_empty_text_count=${input.gpuCallbackNonEmptyTextCount}",
        "gpu_callback_last_text_length=${input.gpuCallbackLastTextLength}",
        "gpu_callback_last_text_head=${escapeLocalInferenceFailureValue(input.gpuCallbackLastTextHead)}",
        "gpu_first_non_empty_text_elapsed_ms=${input.gpuFirstNonEmptyTextElapsedMs}",
        "gpu_first_token_classification_reason=${input.gpuFirstTokenClassificationReason}",
        "gpu_callback_exception_class=${input.gpuCallbackExceptionClass}",
        "gpu_callback_exception_message=${escapeLocalInferenceFailureValue(input.gpuCallbackExceptionMessage)}",
        "gpu_callback_exception_chain=${escapeLocalInferenceFailureValue(input.gpuCallbackExceptionChain)}",
        "gpu_callback_exception_stage=${input.gpuCallbackExceptionStage}",
        "gpu_generate_stall_interpretation=${input.gpuGenerateStallInterpretation}",
        "cpu_callback_invoked_count=${input.cpuCallbackInvokedCount}",
        "cpu_done_true_seen=${input.cpuDoneTrueSeen}",
        "cpu_first_non_empty_text_elapsed_ms=${input.cpuFirstNonEmptyTextElapsedMs}",
        "callback_route_diff=${input.callbackRouteDiff}",
        "gpu_generate_actual_prompt=${escapeLocalInferenceFailureValue(input.gpuGenerateActualPrompt)}",
        "gpu_generate_prompt_length_chars=${input.gpuGeneratePromptLengthChars}",
        "gpu_generate_input_token_estimate=${input.gpuGenerateInputTokenEstimate}",
        "gpu_generate_exception_seen=${input.gpuGenerateExceptionSeen}",
        "gpu_generate_exception_class=${input.gpuGenerateExceptionClass}",
        "gpu_generate_exception_message_raw=${escapeLocalInferenceFailureValue(input.gpuGenerateExceptionMessageRaw)}",
        "gpu_generate_exception_message_sanitized=${escapeLocalInferenceFailureValue(input.gpuGenerateExceptionMessageSanitized)}",
        "gpu_generate_exception_status_code=${input.gpuGenerateExceptionStatusCode}",
        "gpu_generate_exception_error_file=${input.gpuGenerateExceptionErrorFile}",
        "gpu_generate_exception_error_line=${input.gpuGenerateExceptionErrorLine}",
        "gpu_generate_exception_summary=${input.gpuGenerateExceptionSummary}",
        "gpu_generate_failed_before_first_token=${input.gpuGenerateFailedBeforeFirstToken}",
        "gpu_watchdog_bypassed_due_to_generate_exception=${input.gpuWatchdogBypassedDueToGenerateException}",
        "litert_lm_error_kind=${input.liteRtLmErrorKind}",
        "litert_lm_error_status_code=${input.liteRtLmErrorStatusCode}",
        "litert_lm_error_primary_file=${input.liteRtLmErrorPrimaryFile}",
        "litert_lm_error_primary_line=${input.liteRtLmErrorPrimaryLine}",
        "litert_lm_error_secondary_file=${input.liteRtLmErrorSecondaryFile}",
        "litert_lm_error_secondary_line=${input.liteRtLmErrorSecondaryLine}",
        "litert_lm_error_recoverability_hint=${input.liteRtLmErrorRecoverabilityHint}",
        "litert_compiled_model_executor_failure_category=${input.liteRtCompiledModelExecutorFailureCategory}",
        "cpu_compare_started=${input.cpuCompareStarted}",
        "cpu_compare_engine_initialize_finished=${input.cpuCompareEngineInitializeFinished}",
        "cpu_compare_conversation_create_finished=${input.cpuCompareConversationCreateFinished}",
        "cpu_compare_generate_started=${input.cpuCompareGenerateStarted}",
        "cpu_compare_callback_invoked_count=${input.cpuCompareCallbackInvokedCount}",
        "cpu_compare_first_non_empty_text_elapsed_ms=${input.cpuCompareFirstNonEmptyTextElapsedMs}",
        "cpu_compare_done_true_seen=${input.cpuCompareDoneTrueSeen}",
        "cpu_compare_exception_class=${input.cpuCompareExceptionClass}",
        "cpu_compare_exception_message=${escapeLocalInferenceFailureValue(input.cpuCompareExceptionMessage)}",
        "cpu_gpu_generate_diff=${input.cpuGpuGenerateDiff}",
        "gpu_callback_to_ui_enabled=${input.gpuCallbackToUiEnabled}",
        "gpu_callback_text_promoted_to_ui=${input.gpuCallbackTextPromotedToUi}",
        "gpu_callback_promoted_text_length=${input.gpuCallbackPromotedTextLength}",
        "gpu_callback_promoted_non_empty_count=${input.gpuCallbackPromotedNonEmptyCount}",
        "gpu_callback_success_classification=${input.gpuCallbackSuccessClassification}",
        "gpu_raw_callback_probe_status=${input.gpuRawCallbackProbeStatus}",
        "gpu_ui_append_started=${input.gpuUiAppendStarted}",
        "gpu_ui_append_finished=${input.gpuUiAppendFinished}",
        "gpu_ui_first_visible_text_elapsed_ms=${input.gpuUiFirstVisibleTextElapsedMs}",
        "gpu_streaming_completion_reason=${input.gpuStreamingCompletionReason}",
        "gpu_normal_route_use_callback_streaming=${input.gpuNormalRouteUseCallbackStreaming}",
        "gpu_callback_streaming_path_selected=${input.gpuCallbackStreamingPathSelected}",
        "gpu_callback_streaming_path_reason=${input.gpuCallbackStreamingPathReason}",
        "gpu_callback_streaming_success_count=${input.gpuCallbackStreamingSuccessCount}",
        "gpu_callback_streaming_empty_callback_count=${input.gpuCallbackStreamingEmptyCallbackCount}",
        "gpu_callback_streaming_non_empty_callback_count=${input.gpuCallbackStreamingNonEmptyCallbackCount}",
        "gpu_callback_streaming_done_true_seen=${input.gpuCallbackStreamingDoneTrueSeen}",
        "gpu_callback_streaming_final_text_length=${input.gpuCallbackStreamingFinalTextLength}",
        "gpu_callback_streaming_reused_held_engine=${input.gpuCallbackStreamingReusedHeldEngine}",
        "gpu_callback_streaming_completion_reason=${input.gpuCallbackStreamingCompletionReason}",
        "gpu_callback_streaming_failure_reason=${input.gpuCallbackStreamingFailureReason}",
        "standard_gpu_probe_expected_edge_gallery_e2b=${input.standardGpuProbeExpectedEdgeGalleryE2b}",
        "standard_gpu_probe_model_size_bytes=${input.standardGpuProbeModelSizeBytes}",
        "standard_gpu_probe_model_sha256_expected=${input.standardGpuProbeModelSha256Expected}",
        "standard_gpu_probe_model_sha256_actual=${input.standardGpuProbeModelSha256Actual}",
        "standard_gpu_probe_model_identity_hint=${input.standardGpuProbeModelIdentityHint}",
        "standard_gpu_probe_runtime_stack=${input.standardGpuProbeRuntimeStack}",
        "standard_gpu_probe_callback_streaming_gate=${input.standardGpuProbeCallbackStreamingGate}",
        "standard_gpu_probe_result_candidate=${input.standardGpuProbeResultCandidate}",
        "gpu_litert_executor_error_file=${input.gpuLiteRtExecutorErrorFile}",
        "gpu_litert_executor_error_line=${input.gpuLiteRtExecutorErrorLine}",
        "gpu_litert_compiled_model_error_file=${input.gpuLiteRtCompiledModelErrorFile}",
        "gpu_litert_compiled_model_error_line=${input.gpuLiteRtCompiledModelErrorLine}",
        "gpu_engine_initialize_internal_error_detected=${input.gpuEngineInitializeInternalErrorDetected}",
        "gpu_compiled_model_creation_failed=${input.gpuCompiledModelCreationFailed}",
        "gpu_failure_interpretation=${input.gpuFailureInterpretation}",
        "litert_lm_backend_candidates=${escapeLocalInferenceFailureValue(input.liteRtLmBackendCandidates)}",
        "litert_lm_backend_gpu_artisan_available=${input.liteRtLmBackendGpuArtisanAvailable}",
        "litert_lm_backend_cpu_artisan_available=${input.liteRtLmBackendCpuArtisanAvailable}",
        "litert_lm_backend_google_tensor_artisan_available=${input.liteRtLmBackendGoogleTensorArtisanAvailable}",
        "litert_lm_engine_config_artisan_api_available=${input.liteRtLmEngineConfigArtisanApiAvailable}",
        "litert_lm_runtime_config_available=${input.liteRtLmRuntimeConfigAvailable}",
        "litert_lm_backend_constraint_api_available=${input.liteRtLmBackendConstraintApiAvailable}",
        "litert_lm_preferred_engine_type_api_available=${input.liteRtLmPreferredEngineTypeApiAvailable}",
        "selected_model_backend_constraint_hint=${input.selectedModelBackendConstraintHint}",
        "selected_model_artisan_hint=${input.selectedModelArtisanHint}",
        "edge_gallery_artisan_static_evidence=${input.edgeGalleryArtisanStaticEvidence}",
        "litert_runtime_executor_candidates=${escapeLocalInferenceFailureValue(input.liteRtRuntimeExecutorCandidates)}",
        "litert_runtime_executor_selection_hint=${input.liteRtRuntimeExecutorSelectionHint}",
        "litert_runtime_backend_constraint_hint=${input.liteRtRuntimeBackendConstraintHint}",
        "litert_runtime_compiled_model_executor_hint=${input.liteRtRuntimeCompiledModelExecutorHint}",
        "litert_runtime_gpu_executor_hint=${input.liteRtRuntimeGpuExecutorHint}",
        "litert_runtime_artisan_evidence=${input.liteRtRuntimeArtisanEvidence}",
        "gpu_fallback_used=${input.gpuFallbackUsed}",
        "gpu_stale_callback_ignored=${input.gpuStaleCallbackIgnored}",
        "stale_callback_ignored=${input.gpuStaleCallbackIgnored}",
        "model_name=${escapeLocalInferenceFailureValue(input.modelName.ifBlank { "unavailable" })}",
        "model_file=${escapeLocalInferenceFailureValue(input.modelFile.ifBlank { "unavailable" })}",
        "model_exists=${model?.isFile ?: false}",
        "model_size_bytes=${model?.takeIf { it.isFile }?.length()?.toString() ?: "unavailable"}",
        "thread_name=${escapeLocalInferenceFailureValue(input.threadName)}",
        "process_pid=${input.processPid.ifBlank { "unavailable" }}",
        "memory_before_total_pss_mb=${formatLocalFailureNullableLong(input.memoryBefore?.totalPssMb)}",
        "memory_after_total_pss_mb=${formatLocalFailureNullableLong(input.memoryAfter?.totalPssMb)}",
        "java_heap_used_mb=${formatLocalFailureNullableLong(input.javaHeapUsedMb)}",
        "native_heap_alloc_mb=${formatLocalFailureNullableLong(input.nativeHeapAllocMb)}",
        ) +
            buildGalleryStackGpuProbeCompactDiagnosticLines(input.galleryStackProbeDiagnostics) +
            buildRuntimeAlignmentProbeCompactDiagnosticLines(input.runtimeAlignmentProbeDiagnostics) +
            buildGpuAlignmentHolderCompactDiagnosticLines(input.gpuPrefillProbeDiagnostics) +
            buildGpuPrefillProbeDiagnosticLines(input.gpuPrefillProbeDiagnostics)
        ).joinToString("\n")
}

private val GPU_ALIGNMENT_HOLDER_DIAGNOSTIC_KEYS = listOf(
    "gpu_alignment_holder_present_before_acquire",
    "gpu_alignment_holder_acquire_result",
    "gpu_alignment_holder_reused",
    "gpu_alignment_holder_created",
    "gpu_alignment_holder_cleared",
    "gpu_alignment_holder_clear_reason",
    "gpu_alignment_holder_close_started",
    "gpu_alignment_holder_close_finished",
    "gpu_alignment_holder_reuse_block_reason",
    "gpu_alignment_holder_model_path_changed",
    "gpu_alignment_holder_backend_changed",
    "gpu_alignment_holder_app_process_start_marker",
    "gpu_alignment_turn_index_if_available",
    "gpu_alignment_previous_turn_success",
    "gpu_alignment_previous_turn_failure_stage",
)

private fun buildGpuAlignmentHolderCompactDiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> =
    GPU_ALIGNMENT_HOLDER_DIAGNOSTIC_KEYS.map { key ->
        "$key=${diagnostics[key]?.let(::escapeLocalInferenceFailureValue) ?: "unavailable"}"
    }

private fun extractGpuAlignmentHolderDiagnostics(text: String?): Map<String, String> =
    parseLocalInferenceFailureDiagnosticsText(text).filterKeys { it in GPU_ALIGNMENT_HOLDER_DIAGNOSTIC_KEYS }

private fun buildGalleryStackGpuProbeCompactDiagnosticLines(
    diagnostics: GalleryStackGpuProbeRuntimeDiagnostics?,
): List<String> {
    if (diagnostics?.flavor != true) return emptyList()
    return listOf(
        "gallery_stack_probe_flavor=${diagnostics.flavor}",
        "gallery_stack_probe_enabled=${diagnostics.enabled}",
        "gallery_stack_probe_application_id=${diagnostics.applicationId}",
        "gallery_stack_probe_native_stack_source=${escapeLocalInferenceFailureValue(diagnostics.nativeStackSource)}",
        "gallery_stack_probe_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "gallery_stack_probe_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "gallery_stack_probe_libs_manifest_present=${diagnostics.libsManifestPresent}",
        "gallery_stack_probe_edge_gallery_model_expected=${escapeLocalInferenceFailureValue(diagnostics.edgeGalleryModelExpected)}",
        "gallery_stack_probe_model_path=${escapeLocalInferenceFailureValue(diagnostics.modelPath)}",
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

private fun buildRuntimeAlignmentProbeCompactDiagnosticLines(
    diagnostics: RuntimeAlignmentProbeDiagnostics?,
): List<String> {
    if (diagnostics?.flavor != true) return emptyList()
    return listOf(
        "runtime_alignment_probe_flavor=${diagnostics.flavor}",
        "runtime_alignment_stack_source=${escapeLocalInferenceFailureValue(diagnostics.stackSource)}",
        "runtime_alignment_liblitert_sha256=${diagnostics.libLiteRtSha256}",
        "runtime_alignment_liblitertlm_jni_sha256=${diagnostics.libLiteRtLmJniSha256}",
        "runtime_alignment_dispatch_qualcomm_present=${diagnostics.dispatchQualcommPresent}",
        "runtime_alignment_compiler_plugin_qualcomm_present=${diagnostics.compilerPluginQualcommPresent}",
        "runtime_alignment_gemma_constraint_provider_present=${diagnostics.gemmaConstraintProviderPresent}",
        "runtime_alignment_result_candidate=${diagnostics.resultCandidate}",
        "runtime_alignment_success_gate=${diagnostics.successGate}",
    )
}

internal fun buildLocalInferenceFailureCompactInputFromTrace(
    inputPrompt: String,
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
    trace: LocalInferenceTrace?,
    status: String = "failure",
    reason: String = "local_inference_failure",
    failureDiagnosticsText: String? = trace?.localFailureDiagnosticsText,
    failureStage: String? = null,
    exceptionClass: String? = null,
    exceptionMessage: String? = null,
    throwable: Throwable? = null,
    routeContext: LocalRouteDiagnosticContext? = null,
    timeout: Boolean = false,
    modelName: String? = null,
    modelFile: String? = null,
    ttsRequested: Boolean = false,
    dbRequested: Boolean = false,
    markdownRequested: Boolean = false,
    streamingRequested: Boolean = false,
    processPid: String = "unavailable",
): LocalInferenceFailureCompactInput {
    val parsed = parseLocalInferenceFailureDiagnosticsText(failureDiagnosticsText)
    val probeDiagnostics = extractGpuPrefillProbeDiagnostics(failureDiagnosticsText) +
        extractGpuAlignmentHolderDiagnostics(failureDiagnosticsText)
    val snapshots = trace?.memorySnapshots.orEmpty()
    val before = snapshots.firstOrNull { it.stage == MEMORY_STAGE_BEFORE_GENERATE } ?: snapshots.firstOrNull()
    val after = snapshots.lastOrNull { it.stage == MEMORY_STAGE_GENERATION_FAILED } ?: snapshots.lastOrNull()
    val resolvedFailureExceptionClass = exceptionClass
        ?: parsed["failure_exception_class"]
        ?: parsed["exception class"]
        ?: trace?.sessionAsyncPocErrorClassName
        ?: trace?.sessionTokenProbeErrorClassName
        ?: "unavailable"
    val resolvedFailureExceptionMessage = exceptionMessage
        ?: parsed["failure_exception_message"]
        ?: parsed["exception message"]
        ?: trace?.sessionAsyncPocErrorMessage
        ?: trace?.preferredBackendApplyError
        ?: "unavailable"
    val exceptionExpansion = buildLocalFailureExceptionExpansion(
        throwable = throwable,
        parsed = parsed,
        failureExceptionClass = resolvedFailureExceptionClass,
        failureExceptionMessage = resolvedFailureExceptionMessage,
    )
    val previousInvocationStillProcessing = isLiteRtLmPreviousInvocationStillProcessing(
        listOf(
            resolvedFailureExceptionMessage,
            exceptionExpansion.failureCauseMessage,
            exceptionExpansion.failureRootCauseMessage,
            exceptionExpansion.reflectionTargetExceptionMessage,
            exceptionExpansion.reflectionTargetExceptionRootCauseMessage,
            exceptionExpansion.exceptionChain,
        ),
    )
    val gpuEngineCreateTimeoutSuspected = parsed["gpu_engine_create_timeout_suspected"] ?: "unavailable"
    val gpuFailureClassification = classifyGpuLiteRtFailure(
        message = parsed["probe_exception_cause_message_raw"]
            ?: parsed["probe_exception_cause_message"]
            ?: parsed["failure_cause_message"]
            ?: parsed["failure_root_cause_message"]
            ?: parsed["exception_chain"],
        failureStage = failureStage
            ?: parsed["failure_stage"]
            ?: parsed["failure stage"]
            ?: trace?.sessionAsyncPocErrorStage
            ?: trace?.sessionTokenProbeErrorStage,
        timeoutStage = parsed["gpu_timeout_stage"] ?: parsed["probe_timeout_stage"],
        generateStarted = (parsed["gpu_generate_started"] ?: parsed["generate_started"])?.toBooleanStrictOrNull(),
        firstTokenReceived = (parsed["gpu_first_token_received"] ?: parsed["first_token_received"])?.toBooleanStrictOrNull(),
        engineInitializeFinished =
            (parsed["gpu_engine_initialize_finished"] ?: parsed["engine_initialize_finished"])?.toBooleanStrictOrNull(),
        conversationCreateFinished =
            (parsed["gpu_conversation_create_finished"] ?: parsed["conversation_create_finished"])?.toBooleanStrictOrNull(),
    )
    val liteRtLmErrorMessageForCompact = parsed["gpu_generate_exception_message_raw"]
        ?: parsed["gpu_generate_exception_message_sanitized"]
        ?: parsed["gpu_callback_exception_message"]
        ?: parsed["gpu_callback_exception_chain"]
        ?: parsed["probe_exception_cause_message_raw"]
        ?: parsed["probe_exception_cause_message"]
        ?: parsed["probe_exception_chain"]
        ?: exceptionExpansion.exceptionChain.takeIf { it != "unavailable" }
        ?: resolvedFailureExceptionMessage
    val liteRtLmErrorClassification = classifyLiteRtLmError(liteRtLmErrorMessageForCompact)
    val effectiveLiteRtLmErrorClassification = LiteRtLmErrorClassification(
        kind = parsed["litert_lm_error_kind"] ?: liteRtLmErrorClassification.kind,
        statusCode = parsed["litert_lm_error_status_code"] ?: liteRtLmErrorClassification.statusCode,
        primaryFile = parsed["litert_lm_error_primary_file"] ?: liteRtLmErrorClassification.primaryFile,
        primaryLine = parsed["litert_lm_error_primary_line"] ?: liteRtLmErrorClassification.primaryLine,
        secondaryFile = parsed["litert_lm_error_secondary_file"] ?: liteRtLmErrorClassification.secondaryFile,
        secondaryLine = parsed["litert_lm_error_secondary_line"] ?: liteRtLmErrorClassification.secondaryLine,
        recoverabilityHint = parsed["litert_lm_error_recoverability_hint"]
            ?: liteRtLmErrorClassification.recoverabilityHint,
        summary = parsed["gpu_generate_exception_summary"] ?: liteRtLmErrorClassification.summary,
    )
    val galleryStackProbe = buildGalleryStackGpuProbeRuntimeDiagnostics(
        selectedModelPath = parsed["gallery_stack_probe_model_path"]
            ?: parsed["selected_model_path"]
            ?: routeContext?.selectedModelPath
            ?: modelFile,
        preferredBackend = parsed["preferred_backend"] ?: preferredBackendSetting.name,
    )
    val parsedRuntimeAlignmentProbe = parsed["runtime_alignment_probe_flavor"] == "true"
    val runtimeAlignmentProbe = if (parsedRuntimeAlignmentProbe) {
        RuntimeAlignmentProbeDiagnostics(
            flavor = true,
            stackSource = parsed["runtime_alignment_stack_source"] ?: "unavailable",
            libLiteRtSha256 = parsed["runtime_alignment_liblitert_sha256"] ?: "unavailable",
            libLiteRtLmJniSha256 = parsed["runtime_alignment_liblitertlm_jni_sha256"] ?: "unavailable",
            dispatchQualcommPresent = parsed["runtime_alignment_dispatch_qualcomm_present"] ?: "unavailable",
            compilerPluginQualcommPresent =
                parsed["runtime_alignment_compiler_plugin_qualcomm_present"] ?: "unavailable",
            gemmaConstraintProviderPresent =
                parsed["runtime_alignment_gemma_constraint_provider_present"] ?: "unavailable",
            resultCandidate = parsed["runtime_alignment_result_candidate"] ?: "unavailable",
            successGate = parsed["runtime_alignment_success_gate"] ?: "unavailable",
        )
    } else {
        buildRuntimeAlignmentProbeDiagnostics(
            resultCandidate = "unavailable",
            successGate = "unavailable",
        )
    }
    return LocalInferenceFailureCompactInput(
        inputPrompt = inputPrompt,
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        status = status,
        reason = reason,
        failureStage = failureStage
            ?: parsed["failure_stage"]
            ?: parsed["failure stage"]
            ?: trace?.sessionAsyncPocErrorStage
            ?: trace?.sessionTokenProbeErrorStage
            ?: if (timeout) "timeout" else "unknown",
        failureExceptionClass = resolvedFailureExceptionClass,
        failureExceptionMessage = resolvedFailureExceptionMessage,
        failureCauseClass = exceptionExpansion.failureCauseClass,
        failureCauseMessage = exceptionExpansion.failureCauseMessage,
        failureRootCauseClass = exceptionExpansion.failureRootCauseClass,
        failureRootCauseMessage = exceptionExpansion.failureRootCauseMessage,
        reflectionTargetExceptionClass = exceptionExpansion.reflectionTargetExceptionClass,
        reflectionTargetExceptionMessage = exceptionExpansion.reflectionTargetExceptionMessage,
        reflectionTargetExceptionRootCauseClass = exceptionExpansion.reflectionTargetExceptionRootCauseClass,
        reflectionTargetExceptionRootCauseMessage = exceptionExpansion.reflectionTargetExceptionRootCauseMessage,
        exceptionChain = exceptionExpansion.exceptionChain,
        liteRtLmPreviousInvocationStillProcessing = previousInvocationStillProcessing,
        generateConcurrencyViolationSuspected = previousInvocationStillProcessing,
        gpuPrefillProbeDiagnostics = probeDiagnostics,
        engineConfigBackend = trace?.appliedPreferredBackend
            ?: trace?.requestedPreferredBackend
            ?: when (preferredBackendSetting) {
                PreferredBackendDryRunSetting.DEFAULT -> "Automatic"
                else -> preferredBackendSetting.name
            },
        normalChatNativeRouteBlocked = routeContext?.normalChatNativeRouteBlocked ?: false,
        blockedReason = routeContext?.blockedReason ?: "none",
        guardRecommendation = parsed["guard_recommendation"]
            ?: GPU_EXPERIMENTAL_TIMEOUT_GUARD_RECOMMENDATION.takeIf {
                gpuEngineCreateTimeoutSuspected == "true" ||
                    (preferredBackendSetting == PreferredBackendDryRunSetting.GPU && timeout)
            }
            ?: "unavailable",
        localRouteStarted = true,
        localEngineCreateStarted = trace?.localTraceStartElapsedRealtimeMs != null ||
            trace?.heldEngineCreatePath != null ||
            trace?.llmInferenceCreateMethod != null ||
            trace?.createMethodSignature != null,
        localEngineCreateFinished = trace?.heldEngineHash != null ||
            trace?.llmInferenceCreateMethod != null ||
            trace?.createMethodSignature != null,
        localGenerateStarted = trace?.generateMethodSignature != null ||
            trace?.officialFlowAttempted == true ||
            trace?.sessionAsyncPocAttempted == true,
        localGenerateFinished = trace?.localTraceCompletedElapsedRealtimeMs != null,
        fallbackUsed = trace?.officialFlowFallbackReason != null,
        timeout = timeout || trace?.preferredBackendApplyResult == "timeout",
        freshCrash = false,
        ttsRequested = ttsRequested,
        dbRequested = dbRequested,
        markdownRequested = markdownRequested,
        streamingRequested = streamingRequested,
        gpuWatchdogTimeoutMs = parsed["gpu_watchdog_timeout_ms"] ?: "unavailable",
        gpuWatchdogMode = parsed["gpu_watchdog_mode"] ?: "unavailable",
        gpuWatchdogFailureStage = parsed["gpu_watchdog_failure_stage"] ?: "unavailable",
        gpuTimeoutStage = parsed["gpu_timeout_stage"]
            ?: resolveGpuExperimentalTimeoutStage(parsed["failure_stage"] ?: parsed["failure stage"]),
        gpuTimeoutElapsedMs = parsed["gpu_timeout_elapsed_ms"] ?: parsed["elapsed_ms"] ?: "unavailable",
        gpuEngineCreateDurationMs = parsed["gpu_engine_create_duration_ms"] ?: "unavailable",
        gpuEngineCreateStarted = parsed["gpu_engine_create_started"] ?: parsed["engine_create_started"] ?: "unavailable",
        gpuEngineCreateFinished = parsed["gpu_engine_create_finished"] ?: parsed["engine_create_finished"] ?: "unavailable",
        gpuEngineCreateTimeoutSuspected = gpuEngineCreateTimeoutSuspected,
        gpuConversationCreateStarted = parsed["gpu_conversation_create_started"] ?: parsed["conversation_create_started"] ?: "unavailable",
        gpuConversationCreateFinished = parsed["gpu_conversation_create_finished"] ?: parsed["conversation_create_finished"] ?: "unavailable",
        gpuGenerateStarted = parsed["gpu_generate_started"] ?: parsed["generate_started"] ?: "unavailable",
        gpuFirstTokenReceived = parsed["gpu_first_token_received"] ?: parsed["first_token_received"] ?: "unavailable",
        gpuFirstTokenElapsedMs = parsed["gpu_first_token_elapsed_ms"] ?: "unavailable",
        generateCallStartedAtElapsedMs = parsed["generate_call_started_at_elapsed_ms"] ?: "unavailable",
        firstTokenReceivedAtElapsedMs = parsed["first_token_received_at_elapsed_ms"] ?: "unavailable",
        generateBeforeFirstTokenElapsedMs = parsed["generate_before_first_token_elapsed_ms"] ?: "unavailable",
        gpuGenerateBeforeFirstTokenTimeoutSuspected =
            parsed["gpu_generate_before_first_token_timeout_suspected"] ?: "unavailable",
        gpuLastKnownStage = parsed["gpu_last_known_stage"] ?: "unavailable",
        gpuHeldEngineExists = parsed["gpu_held_engine_exists"] ?: parsed["held_engine_exists"] ?: "unavailable",
        gpuHeldEngineReused = parsed["gpu_held_engine_reused"] ?: parsed["held_engine_reused"] ?: "unavailable",
        holderCreated = parsed["holder_created"] ?: "unavailable",
        holderAcquired = parsed["holder_acquired"] ?: "unavailable",
        holderReused = parsed["holder_reused"] ?: "unavailable",
        holderInvalidated = parsed["holder_invalidated"] ?: "unavailable",
        holderClosed = parsed["holder_closed"] ?: "unavailable",
        holderTimeoutCleanup = parsed["holder_timeout_cleanup"] ?: "unavailable",
        holderFailureCleanup = parsed["holder_failure_cleanup"] ?: "unavailable",
        holderProcessRestart = parsed["holder_process_restart"] ?: "unavailable",
        heldEngineLifecycleHistory = parsed["held_engine_lifecycle_history"]
            ?: trace?.heldEngineLifecycleHistory
            ?: "unavailable",
        heldEngineDestroyReason = parsed["held_engine_destroy_reason"]
            ?: trace?.heldEngineDestroyReason
            ?: "unavailable",
        heldEngineLastOwner = parsed["held_engine_last_owner"]
            ?: trace?.heldEngineLastOwner
            ?: "unavailable",
        heldEngineLastFailureStage = parsed["held_engine_last_failure_stage"]
            ?: trace?.heldEngineLastFailureStage
            ?: "unavailable",
        heldEngineSnapshotBeforeDestroy = parsed["held_engine_snapshot_before_destroy"]
            ?: trace?.heldEngineSnapshotBeforeDestroy
            ?: "unavailable",
        gpuModelKind = parsed["gpu_model_kind"] ?: parsed["model_kind"] ?: "unavailable",
        gpuSelectedModelName = parsed["gpu_selected_model_name"] ?: parsed["selected_model_name"] ?: "unavailable",
        gpuSelectedModelFile = parsed["gpu_selected_model_file"] ?: parsed["selected_model_file"] ?: "unavailable",
        gpuModelPath = parsed["gpu_model_path"] ?: parsed["selected_model_path"] ?: "unavailable",
        gpuModelPathTail = parsed["gpu_model_path_tail"] ?: "unavailable",
        gpuBackendSetting = parsed["gpu_backend_setting"] ?: parsed["preferred_backend"] ?: "unavailable",
        gpuCompatibilityMode = parsed["gpu_compatibility_mode"] ?: "unavailable",
        gpuEngineConfigProfile = parsed["gpu_engine_config_profile"] ?: "unavailable",
        gpuExperimentMode = parsed["gpu_experiment_mode"] ?: "unavailable",
        gpuExperimentModesAvailable = parsed["gpu_experiment_modes_available"] ?: "unavailable",
        gpuCacheDirMode = parsed["gpu_cache_dir_mode"] ?: "unavailable",
        gpuEngineConfigModelPath = parsed["gpu_engine_config_model_path"] ?: "unavailable",
        gpuEngineConfigModelPathTail = parsed["gpu_engine_config_model_path_tail"] ?: "unavailable",
        gpuEngineConfigCacheDir = parsed["gpu_engine_config_cache_dir"] ?: "unavailable",
        gpuEngineConfigCacheDirPresent = parsed["gpu_engine_config_cache_dir_present"] ?: "unavailable",
        gpuEngineConfigBackend = parsed["gpu_engine_config_backend"] ?: "unavailable",
        gpuEngineConfigVisionBackend = parsed["gpu_engine_config_vision_backend"] ?: "unavailable",
        gpuEngineConfigAudioBackend = parsed["gpu_engine_config_audio_backend"] ?: "unavailable",
        gpuEngineConfigMaxTokens = parsed["gpu_engine_config_max_tokens"] ?: "unavailable",
        gpuEngineConfigBuildStarted = parsed["gpu_engine_config_build_started"] ?: parsed["engine_config_build_started"] ?: "unavailable",
        gpuEngineConfigBuildFinished = parsed["gpu_engine_config_build_finished"] ?: parsed["engine_config_build_finished"] ?: "unavailable",
        gpuEngineConstructorStarted = parsed["gpu_engine_constructor_started"] ?: parsed["engine_create_started"] ?: "unavailable",
        gpuEngineConstructorFinished = parsed["gpu_engine_constructor_finished"] ?: parsed["engine_create_finished"] ?: "unavailable",
        gpuEngineInitializeStarted = parsed["gpu_engine_initialize_started"] ?: parsed["engine_initialize_started"] ?: "unavailable",
        gpuEngineInitializeFinished = parsed["gpu_engine_initialize_finished"] ?: parsed["engine_initialize_finished"] ?: "unavailable",
        gpuEngineInitializeCallState = parsed["gpu_engine_initialize_call_state"] ?: "unavailable",
        gpuTimeoutCheckpoint = parsed["gpu_timeout_checkpoint"] ?: "unavailable",
        gpuModelPathMode = parsed["gpu_model_path_mode"] ?: "unavailable",
        gpuSamplerConfigProfile = parsed["gpu_sampler_config_profile"] ?: "unavailable",
        gpuSamplerConfigEnabled = parsed["gpu_sampler_config_enabled"] ?: "unavailable",
        gpuSamplerConfigTopK = parsed["gpu_sampler_config_top_k"] ?: "unavailable",
        gpuSamplerConfigTopP = parsed["gpu_sampler_config_top_p"] ?: "unavailable",
        gpuSamplerConfigTemperature = parsed["gpu_sampler_config_temperature"] ?: "unavailable",
        gpuSamplerAccelerationPolicy = parsed["gpu_sampler_acceleration_policy"] ?: "unavailable",
        gpuConversationConfigProfile = parsed["gpu_conversation_config_profile"] ?: "unavailable",
        gpuConversationConfigSamplerPresent = parsed["gpu_conversation_config_sampler_present"] ?: "unavailable",
        gpuOptionsConfigured = parsed["gpu_options_configured"] ?: "unavailable",
        gpuOptionsSource = parsed["gpu_options_source"] ?: "unavailable",
        gpuThinkingEnabled = parsed["gpu_thinking_enabled"] ?: "unavailable",
        gpuSpeculativeDecodingEnabled = parsed["gpu_speculative_decoding_enabled"] ?: "unavailable",
        gpuMaxTokens = parsed["gpu_max_tokens"] ?: "unavailable",
        gpuTopK = parsed["gpu_top_k"] ?: "unavailable",
        gpuTopP = parsed["gpu_top_p"] ?: "unavailable",
        gpuTemperature = parsed["gpu_temperature"] ?: "unavailable",
        gpuDispatcher = parsed["gpu_dispatcher"] ?: "unavailable",
        gpuEngineInitializeApi = parsed["gpu_engine_initialize_api"] ?: "unavailable",
        gpuEdgeGalleryDiffApplied = parsed["gpu_edge_gallery_diff_applied"] ?: "unavailable",
        gpuRouteDivergencePoint = parsed["gpu_route_divergence_point"] ?: "unavailable",
        debugLamiGpuGenerateProbeMode = parsed["debug_lami_gpu_generate_probe_mode"] ?: "unavailable",
        gpuGenerateCallEntered = parsed["gpu_generate_call_entered"] ?: "unavailable",
        gpuGenerateCallReturned = parsed["gpu_generate_call_returned"] ?: "unavailable",
        gpuCallbackInvokedCount = parsed["gpu_callback_invoked_count"] ?: "unavailable",
        gpuCallbackFirstInvokedAtElapsedMs = parsed["gpu_callback_first_invoked_at_elapsed_ms"] ?: "unavailable",
        gpuCallbackLastInvokedAtElapsedMs = parsed["gpu_callback_last_invoked_at_elapsed_ms"] ?: "unavailable",
        gpuCallbackThreadName = parsed["gpu_callback_thread_name"] ?: "unavailable",
        gpuCallbackDoneTrueSeen = parsed["gpu_callback_done_true_seen"] ?: "unavailable",
        gpuDoneTrueSeen = parsed["gpu_done_true_seen"] ?: parsed["gpu_callback_done_true_seen"] ?: "unavailable",
        gpuCallbackErrorSeen = parsed["gpu_callback_error_seen"] ?: "unavailable",
        gpuCallbackEmptyTextCount = parsed["gpu_callback_empty_text_count"] ?: "unavailable",
        gpuCallbackNonEmptyTextCount = parsed["gpu_callback_non_empty_text_count"] ?: "unavailable",
        gpuCallbackLastTextLength = parsed["gpu_callback_last_text_length"] ?: "unavailable",
        gpuCallbackLastTextHead = parsed["gpu_callback_last_text_head"] ?: "unavailable",
        gpuFirstNonEmptyTextElapsedMs = parsed["gpu_first_non_empty_text_elapsed_ms"] ?: "unavailable",
        gpuFirstTokenClassificationReason = parsed["gpu_first_token_classification_reason"] ?: "unavailable",
        gpuCallbackExceptionClass = parsed["gpu_callback_exception_class"] ?: "unavailable",
        gpuCallbackExceptionMessage = parsed["gpu_callback_exception_message"] ?: "unavailable",
        gpuCallbackExceptionChain = parsed["gpu_callback_exception_chain"] ?: "unavailable",
        gpuCallbackExceptionStage = parsed["gpu_callback_exception_stage"] ?: "unavailable",
        gpuGenerateStallInterpretation = parsed["gpu_generate_stall_interpretation"] ?: "unavailable",
        cpuCallbackInvokedCount = parsed["cpu_callback_invoked_count"] ?: "unavailable",
        cpuDoneTrueSeen = parsed["cpu_done_true_seen"] ?: "unavailable",
        cpuFirstNonEmptyTextElapsedMs = parsed["cpu_first_non_empty_text_elapsed_ms"] ?: "unavailable",
        callbackRouteDiff = parsed["callback_route_diff"] ?: "unavailable",
        gpuGenerateActualPrompt = parsed["gpu_generate_actual_prompt"] ?: "unavailable",
        gpuGeneratePromptLengthChars = parsed["gpu_generate_prompt_length_chars"] ?: "unavailable",
        gpuGenerateInputTokenEstimate = parsed["gpu_generate_input_token_estimate"] ?: "unavailable",
        gpuGenerateExceptionSeen = parsed["gpu_generate_exception_seen"] ?: "unavailable",
        gpuGenerateExceptionClass = parsed["gpu_generate_exception_class"] ?: "unavailable",
        gpuGenerateExceptionMessageRaw = parsed["gpu_generate_exception_message_raw"] ?: "unavailable",
        gpuGenerateExceptionMessageSanitized = parsed["gpu_generate_exception_message_sanitized"] ?: "unavailable",
        gpuGenerateExceptionStatusCode = parsed["gpu_generate_exception_status_code"] ?: "unavailable",
        gpuGenerateExceptionErrorFile = parsed["gpu_generate_exception_error_file"] ?: "unavailable",
        gpuGenerateExceptionErrorLine = parsed["gpu_generate_exception_error_line"] ?: "unavailable",
        gpuGenerateExceptionSummary = parsed["gpu_generate_exception_summary"] ?: "unavailable",
        gpuGenerateFailedBeforeFirstToken = parsed["gpu_generate_failed_before_first_token"] ?: "unavailable",
        gpuWatchdogBypassedDueToGenerateException =
            parsed["gpu_watchdog_bypassed_due_to_generate_exception"] ?: "unavailable",
        liteRtLmErrorKind = parsed["litert_lm_error_kind"] ?: "unavailable",
        liteRtLmErrorStatusCode = parsed["litert_lm_error_status_code"] ?: "unavailable",
        liteRtLmErrorPrimaryFile = parsed["litert_lm_error_primary_file"] ?: "unavailable",
        liteRtLmErrorPrimaryLine = parsed["litert_lm_error_primary_line"] ?: "unavailable",
        liteRtLmErrorSecondaryFile = parsed["litert_lm_error_secondary_file"] ?: "unavailable",
        liteRtLmErrorSecondaryLine = parsed["litert_lm_error_secondary_line"] ?: "unavailable",
        liteRtLmErrorRecoverabilityHint = parsed["litert_lm_error_recoverability_hint"] ?: "unavailable",
        liteRtCompiledModelExecutorFailureCategory =
            parsed["litert_compiled_model_executor_failure_category"]
                ?: classifyLiteRtCompiledModelExecutorFailureCategory(effectiveLiteRtLmErrorClassification),
        cpuCompareStarted = parsed["cpu_compare_started"] ?: "unavailable",
        cpuCompareEngineInitializeFinished = parsed["cpu_compare_engine_initialize_finished"] ?: "unavailable",
        cpuCompareConversationCreateFinished = parsed["cpu_compare_conversation_create_finished"] ?: "unavailable",
        cpuCompareGenerateStarted = parsed["cpu_compare_generate_started"] ?: "unavailable",
        cpuCompareCallbackInvokedCount = parsed["cpu_compare_callback_invoked_count"] ?: "unavailable",
        cpuCompareFirstNonEmptyTextElapsedMs = parsed["cpu_compare_first_non_empty_text_elapsed_ms"] ?: "unavailable",
        cpuCompareDoneTrueSeen = parsed["cpu_compare_done_true_seen"] ?: "unavailable",
        cpuCompareExceptionClass = parsed["cpu_compare_exception_class"] ?: "unavailable",
        cpuCompareExceptionMessage = parsed["cpu_compare_exception_message"] ?: "unavailable",
        cpuGpuGenerateDiff = parsed["cpu_gpu_generate_diff"] ?: "unavailable",
        gpuCallbackToUiEnabled = parsed["gpu_callback_to_ui_enabled"] ?: "unavailable",
        gpuCallbackTextPromotedToUi = parsed["gpu_callback_text_promoted_to_ui"] ?: "unavailable",
        gpuCallbackPromotedTextLength = parsed["gpu_callback_promoted_text_length"] ?: "unavailable",
        gpuCallbackPromotedNonEmptyCount = parsed["gpu_callback_promoted_non_empty_count"] ?: "unavailable",
        gpuCallbackSuccessClassification = parsed["gpu_callback_success_classification"] ?: "unavailable",
        gpuRawCallbackProbeStatus = parsed["gpu_raw_callback_probe_status"] ?: "unavailable",
        gpuUiAppendStarted = parsed["gpu_ui_append_started"] ?: "unavailable",
        gpuUiAppendFinished = parsed["gpu_ui_append_finished"] ?: "unavailable",
        gpuUiFirstVisibleTextElapsedMs = parsed["gpu_ui_first_visible_text_elapsed_ms"] ?: "unavailable",
        gpuStreamingCompletionReason = parsed["gpu_streaming_completion_reason"] ?: "unavailable",
        gpuNormalRouteUseCallbackStreaming = parsed["gpu_normal_route_use_callback_streaming"] ?: "unavailable",
        gpuCallbackStreamingPathSelected = parsed["gpu_callback_streaming_path_selected"] ?: "unavailable",
        gpuCallbackStreamingPathReason = parsed["gpu_callback_streaming_path_reason"] ?: "unavailable",
        gpuCallbackStreamingSuccessCount = parsed["gpu_callback_streaming_success_count"] ?: "unavailable",
        gpuCallbackStreamingEmptyCallbackCount =
            parsed["gpu_callback_streaming_empty_callback_count"] ?: "unavailable",
        gpuCallbackStreamingNonEmptyCallbackCount =
            parsed["gpu_callback_streaming_non_empty_callback_count"] ?: "unavailable",
        gpuCallbackStreamingDoneTrueSeen = parsed["gpu_callback_streaming_done_true_seen"] ?: "unavailable",
        gpuCallbackStreamingFinalTextLength =
            parsed["gpu_callback_streaming_final_text_length"] ?: "unavailable",
        gpuCallbackStreamingReusedHeldEngine =
            parsed["gpu_callback_streaming_reused_held_engine"] ?: "unavailable",
        gpuCallbackStreamingCompletionReason =
            parsed["gpu_callback_streaming_completion_reason"] ?: "unavailable",
        gpuCallbackStreamingFailureReason = parsed["gpu_callback_streaming_failure_reason"] ?: "unavailable",
        standardGpuProbeExpectedEdgeGalleryE2b =
            parsed["standard_gpu_probe_expected_edge_gallery_e2b"] ?: "unavailable",
        standardGpuProbeModelSizeBytes =
            parsed["standard_gpu_probe_model_size_bytes"] ?: "unavailable",
        standardGpuProbeModelSha256Expected =
            parsed["standard_gpu_probe_model_sha256_expected"] ?: "unavailable",
        standardGpuProbeModelSha256Actual =
            parsed["standard_gpu_probe_model_sha256_actual"] ?: "unavailable",
        standardGpuProbeModelIdentityHint =
            parsed["standard_gpu_probe_model_identity_hint"] ?: "unavailable",
        standardGpuProbeRuntimeStack =
            parsed["standard_gpu_probe_runtime_stack"] ?: "unavailable",
        standardGpuProbeCallbackStreamingGate =
            parsed["standard_gpu_probe_callback_streaming_gate"] ?: "unavailable",
        standardGpuProbeResultCandidate =
            parsed["standard_gpu_probe_result_candidate"] ?: "unavailable",
        gpuLiteRtExecutorErrorFile = parsed.diagnosticValueOrNull("gpu_litert_executor_error_file")
            ?: gpuFailureClassification.executorErrorFile,
        gpuLiteRtExecutorErrorLine = parsed.diagnosticValueOrNull("gpu_litert_executor_error_line")
            ?: gpuFailureClassification.executorErrorLine,
        gpuLiteRtCompiledModelErrorFile = parsed.diagnosticValueOrNull("gpu_litert_compiled_model_error_file")
            ?: gpuFailureClassification.compiledModelErrorFile,
        gpuLiteRtCompiledModelErrorLine = parsed.diagnosticValueOrNull("gpu_litert_compiled_model_error_line")
            ?: gpuFailureClassification.compiledModelErrorLine,
        gpuEngineInitializeInternalErrorDetected = parsed.diagnosticValueOrNull("gpu_engine_initialize_internal_error_detected")
            ?: gpuFailureClassification.engineInitializeInternalErrorDetected.toString(),
        gpuCompiledModelCreationFailed = parsed.diagnosticValueOrNull("gpu_compiled_model_creation_failed")
            ?: gpuFailureClassification.compiledModelCreationFailed.toString(),
        gpuFailureInterpretation = parsed.diagnosticValueOrNull("gpu_failure_interpretation")
            ?: gpuFailureClassification.interpretation,
        liteRtLmBackendCandidates = parsed["litert_lm_backend_candidates"] ?: "unavailable",
        liteRtLmBackendGpuArtisanAvailable = parsed["litert_lm_backend_gpu_artisan_available"] ?: "unavailable",
        liteRtLmBackendCpuArtisanAvailable = parsed["litert_lm_backend_cpu_artisan_available"] ?: "unavailable",
        liteRtLmBackendGoogleTensorArtisanAvailable =
            parsed["litert_lm_backend_google_tensor_artisan_available"] ?: "unavailable",
        liteRtLmEngineConfigArtisanApiAvailable = parsed["litert_lm_engine_config_artisan_api_available"] ?: "unavailable",
        liteRtLmRuntimeConfigAvailable = parsed["litert_lm_runtime_config_available"] ?: "unavailable",
        liteRtLmBackendConstraintApiAvailable = parsed["litert_lm_backend_constraint_api_available"] ?: "unavailable",
        liteRtLmPreferredEngineTypeApiAvailable =
            parsed["litert_lm_preferred_engine_type_api_available"] ?: "unavailable",
        selectedModelBackendConstraintHint = parsed["selected_model_backend_constraint_hint"] ?: "unavailable",
        selectedModelArtisanHint = parsed["selected_model_artisan_hint"] ?: "unavailable",
        edgeGalleryArtisanStaticEvidence = parsed["edge_gallery_artisan_static_evidence"] ?: "unavailable",
        liteRtRuntimeExecutorCandidates = parsed["litert_runtime_executor_candidates"] ?: "unavailable",
        liteRtRuntimeExecutorSelectionHint = parsed["litert_runtime_executor_selection_hint"] ?: "unavailable",
        liteRtRuntimeBackendConstraintHint = parsed["litert_runtime_backend_constraint_hint"] ?: "unavailable",
        liteRtRuntimeCompiledModelExecutorHint = parsed["litert_runtime_compiled_model_executor_hint"] ?: "unavailable",
        liteRtRuntimeGpuExecutorHint = parsed["litert_runtime_gpu_executor_hint"] ?: "unavailable",
        liteRtRuntimeArtisanEvidence = parsed["litert_runtime_artisan_evidence"] ?: "unavailable",
        galleryStackProbeDiagnostics = galleryStackProbe.takeIf {
            it.flavor || parsed["gallery_stack_probe_flavor"] == "true"
        },
        runtimeAlignmentProbeDiagnostics = runtimeAlignmentProbe.takeIf { it.flavor },
        gpuFallbackUsed = parsed["gpu_fallback_used"] ?: parsed["fallback_used"] ?: "unavailable",
        gpuStaleCallbackIgnored = parsed["gpu_stale_callback_ignored"] ?: parsed["stale_callback_ignored"] ?: "unavailable",
        modelName = modelName
            ?: trace?.localModelDisplayName
            ?: "unavailable",
        modelFile = modelFile
            ?: trace?.mediaPipeProbeModelPath
            ?: "unavailable",
        processPid = processPid,
        memoryBefore = before,
        memoryAfter = after,
    )
}

internal data class LocalFailureExceptionExpansion(
    val failureCauseClass: String = "unavailable",
    val failureCauseMessage: String = "unavailable",
    val failureRootCauseClass: String = "unavailable",
    val failureRootCauseMessage: String = "unavailable",
    val reflectionTargetExceptionClass: String = "unavailable",
    val reflectionTargetExceptionMessage: String = "unavailable",
    val reflectionTargetExceptionRootCauseClass: String = "unavailable",
    val reflectionTargetExceptionRootCauseMessage: String = "unavailable",
    val exceptionChain: String = "unavailable",
)

internal fun buildLocalFailureExceptionExpansion(
    throwable: Throwable?,
    parsed: Map<String, String>,
    failureExceptionClass: String,
    failureExceptionMessage: String,
): LocalFailureExceptionExpansion =
    throwable?.let(::buildLocalFailureExceptionExpansionFromThrowable)
        ?: buildLocalFailureExceptionExpansionFromParsed(
            parsed = parsed,
            failureExceptionClass = failureExceptionClass,
            failureExceptionMessage = failureExceptionMessage,
        )

private fun buildLocalFailureExceptionExpansionFromThrowable(
    throwable: Throwable,
): LocalFailureExceptionExpansion {
    val chain = localFailureThrowableChain(throwable)
    val root = chain.lastOrNull() ?: throwable
    val target = (throwable as? InvocationTargetException)?.targetException
    val targetChain = target?.let(::localFailureThrowableChain).orEmpty()
    val targetRoot = targetChain.lastOrNull() ?: target
    val failureCause = target ?: throwable.cause
    return LocalFailureExceptionExpansion(
        failureCauseClass = failureCause.localFailureClassNameOrNone(),
        failureCauseMessage = failureCause.localFailureMessageOrNone(),
        failureRootCauseClass = root.javaClass.name,
        failureRootCauseMessage = root.localFailureMessageOrNone(),
        reflectionTargetExceptionClass = target.localFailureClassNameOrNone(),
        reflectionTargetExceptionMessage = target.localFailureMessageOrNone(),
        reflectionTargetExceptionRootCauseClass = targetRoot.localFailureClassNameOrNone(),
        reflectionTargetExceptionRootCauseMessage = targetRoot.localFailureMessageOrNone(),
        exceptionChain = chain.joinToString(" -> ") { cause ->
            "${cause.javaClass.name}:${cause.localFailureMessageOrNone()}"
        }.ifBlank { "none" },
    )
}

private fun buildLocalFailureExceptionExpansionFromParsed(
    parsed: Map<String, String>,
    failureExceptionClass: String,
    failureExceptionMessage: String,
): LocalFailureExceptionExpansion {
    val rootCauseClass = parsed["failure_root_cause_class"]
        ?: parsed["root cause class"]
        ?: parsed["root-cause"]
    val rootCauseMessage = parsed["failure_root_cause_message"]
        ?: parsed["root cause message"]
        ?: parsed["root-cause-message"]
    val parsedTargetClass = parsed["reflection_target_exception_class"]
        ?: parsed["target-exception"]
        ?: parsed["MediaPipe target-exception"]
    val parsedTargetMessage = parsed["reflection_target_exception_message"]
        ?: parsed["target-exception-message"]
        ?: parsed["MediaPipe target-exception-message"]
    val inferredTargetClass = parsedTargetClass
        ?: rootCauseClass.takeIf { failureExceptionClass.endsWith("InvocationTargetException") }
    val inferredTargetMessage = parsedTargetMessage
        ?: rootCauseMessage.takeIf { failureExceptionClass.endsWith("InvocationTargetException") }
    val chain = parsed["exception_chain"]
        ?: parsed["cause chain summary"]
        ?: listOf(failureExceptionClass, failureExceptionMessage)
            .takeIf { failureExceptionClass != "unavailable" }
            ?.joinToString(":")
    return LocalFailureExceptionExpansion(
        failureCauseClass = inferredTargetClass ?: parsed["failure_cause_class"] ?: parsed["cause class"] ?: "unavailable",
        failureCauseMessage = normalizeLocalFailureMessage(
            inferredTargetMessage ?: parsed["failure_cause_message"] ?: parsed["cause message"] ?: "unavailable",
        ),
        failureRootCauseClass = rootCauseClass ?: "unavailable",
        failureRootCauseMessage = normalizeLocalFailureMessage(rootCauseMessage ?: "unavailable"),
        reflectionTargetExceptionClass = inferredTargetClass ?: "none",
        reflectionTargetExceptionMessage = normalizeLocalFailureMessage(inferredTargetMessage ?: "none"),
        reflectionTargetExceptionRootCauseClass = rootCauseClass ?: inferredTargetClass ?: "none",
        reflectionTargetExceptionRootCauseMessage = normalizeLocalFailureMessage(rootCauseMessage ?: inferredTargetMessage ?: "none"),
        exceptionChain = chain ?: "unavailable",
    )
}

private fun localFailureThrowableChain(throwable: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = throwable
    while (current != null && chain.size < LOCAL_FAILURE_EXCEPTION_CHAIN_MAX_DEPTH && current !in chain) {
        chain += current
        current = if (current is InvocationTargetException && current.targetException != null) {
            current.targetException
        } else {
            current.cause
        }
    }
    return chain
}

private fun parseLocalInferenceFailureDiagnosticsText(text: String?): Map<String, String> =
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

private fun Map<String, String>.diagnosticValueOrNull(key: String): String? =
    this[key]?.takeUnless { it == "unavailable" || it.isBlank() }

private fun Throwable?.localFailureClassNameOrNone(): String = this?.javaClass?.name ?: "none"

private fun Throwable?.localFailureMessageOrNone(): String =
    normalizeLocalFailureMessage(this?.message ?: "none")

private fun normalizeLocalFailureMessage(value: String): String =
    value.ifBlank { "none" }

internal fun isLiteRtLmPreviousInvocationStillProcessing(values: List<String?>): Boolean =
    values.any { value ->
        val normalized = value.orEmpty()
        normalized.contains("Previous invocation still processing", ignoreCase = true) &&
            normalized.contains("done=true", ignoreCase = true)
    }

private fun escapeLocalInferenceFailureValue(value: String): String =
    value
        .replace("\r", "\\r")
        .replace("\n", "\\n")

private fun formatLocalFailureNullableLong(value: Long?): String = value?.toString() ?: "unavailable"

private const val LOCAL_FAILURE_EXCEPTION_CHAIN_MAX_DEPTH = 5
