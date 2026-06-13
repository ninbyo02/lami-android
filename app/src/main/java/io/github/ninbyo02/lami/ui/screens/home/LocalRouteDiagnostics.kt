package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.lang.reflect.Modifier

internal data class LocalRouteDiagnosticContext(
    val selectedModelName: String,
    val selectedModelFile: String,
    val selectedModelPath: String = selectedModelFile,
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
    val cpuCompareStarted: Boolean? = null,
    val cpuCompareEngineInitializeFinished: Boolean? = null,
    val cpuCompareConversationCreateFinished: Boolean? = null,
    val cpuCompareGenerateStarted: Boolean? = null,
    val cpuCompareCallbackInvokedCount: Int? = null,
    val cpuCompareFirstNonEmptyTextElapsedMs: Long? = null,
    val cpuCompareDoneTrueSeen: Boolean? = null,
    val cpuCompareExceptionClass: String? = null,
    val cpuCompareExceptionMessage: String? = null,
    val cpuGpuGenerateDiff: String? = null,
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

internal fun buildLocalRouteDiagnosticContext(
    selectedModelName: String?,
    selectedModelFile: String?,
    selectedModelPath: String? = selectedModelFile,
    preferredBackend: String,
    npuStandardRouteMode: String,
    effectiveNpuStandardRouteMode: String = npuStandardRouteMode,
    shouldEnterNpuS1: Boolean,
    localRouteEntered: Boolean,
    normalChatNativeRouteBlocked: Boolean = false,
    blockedReason: String = "none",
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
    return (
        listOf(
        "LOCAL_ROUTE_DIAG",
        "stage=$stage",
        "selected_model_name=${context.selectedModelName}",
        "selected_model_file=${context.selectedModelFile}",
        "selected_model_path=${context.selectedModelPath}",
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
        "cpu_compare_started=${flags.cpuCompareStarted.toDiagnosticValue()}",
        "cpu_compare_engine_initialize_finished=${flags.cpuCompareEngineInitializeFinished.toDiagnosticValue()}",
        "cpu_compare_conversation_create_finished=${flags.cpuCompareConversationCreateFinished.toDiagnosticValue()}",
        "cpu_compare_generate_started=${flags.cpuCompareGenerateStarted.toDiagnosticValue()}",
        "cpu_compare_callback_invoked_count=${flags.cpuCompareCallbackInvokedCount?.toString() ?: "unavailable"}",
        "cpu_compare_first_non_empty_text_elapsed_ms=${flags.cpuCompareFirstNonEmptyTextElapsedMs?.toString() ?: "unavailable"}",
        "cpu_compare_done_true_seen=${flags.cpuCompareDoneTrueSeen.toDiagnosticValue()}",
        "cpu_compare_exception_class=${flags.cpuCompareExceptionClass.toDiagnosticValue()}",
        "cpu_compare_exception_message=${flags.cpuCompareExceptionMessage.toDiagnosticValue()}",
        "cpu_gpu_generate_diff=${flags.cpuGpuGenerateDiff.toDiagnosticValue()}",
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
        ) + buildGpuPrefillProbeDiagnosticLines(flags.gpuPrefillProbeDiagnostics)
        ).joinToString(" ")
}

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
    )
}

internal fun resolveGpuGenerateStallInterpretation(flags: LocalRouteDiagnosticFlags): String =
    when {
        flags.gpuCallbackExceptionClass?.takeIf { it.isNotBlank() && it != "none" && it != "unavailable" } != null ->
            "callback_exception_before_first_token"
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

internal fun buildGpuPrefillProbeDiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> {
    if (diagnostics.isEmpty()) return emptyList()
    return GPU_PREFILL_PROBE_DIAGNOSTIC_KEYS.map { key ->
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
    val beforeConversation =
        timeoutStage == "engine_initialize" ||
            failureStage?.contains("engine_initialize", ignoreCase = true) == true ||
            conversationCreateFinished == false
    val interpretation = when {
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
    val normalized = selectedModelPath.orEmpty().lowercase()
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
    val normalized = selectedModelPath.orEmpty().lowercase()
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
        maxTokens = resolveGpuMaxTokensForExperiment(experimentMode),
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
    )
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
