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
        ) + buildGpuPrefillProbeDiagnosticLines(input.gpuPrefillProbeDiagnostics)
        ).joinToString("\n")
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
    val probeDiagnostics = extractGpuPrefillProbeDiagnostics(failureDiagnosticsText)
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
