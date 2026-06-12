package io.github.ninbyo02.lami.ui.screens.home

import java.io.File

internal data class LocalRouteDiagnosticContext(
    val selectedModelName: String,
    val selectedModelFile: String,
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
    val firstTokenReceived: Boolean? = null,
    val firstTokenElapsedMs: Long? = null,
    val failureStage: String? = null,
    val fallbackUsed: Boolean? = null,
    val staleCallbackIgnored: Boolean? = null,
)

internal fun buildLocalRouteDiagnosticContext(
    selectedModelName: String?,
    selectedModelFile: String?,
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
    val gpuTimeoutStage = resolveGpuExperimentalTimeoutStage(failureStage)
    val engineCreateDurationMs = flags.engineCreateDurationMs
        ?: normalizedElapsedMs.takeIf {
            flags.engineCreateStarted == true &&
                (flags.engineCreateFinished == false || flags.engineCreateFinished == true)
        }
    val engineCreateTimeoutSuspected =
        gpuTimeoutStage == "engine_create" &&
            flags.engineCreateStarted == true &&
            flags.engineCreateFinished == false &&
            failureStage != "none"
    val guardRecommendation = if (engineCreateTimeoutSuspected) {
        GPU_EXPERIMENTAL_TIMEOUT_GUARD_RECOMMENDATION
    } else {
        "unavailable"
    }
    return listOf(
        "LOCAL_ROUTE_DIAG",
        "stage=$stage",
        "selected_model_name=${context.selectedModelName}",
        "selected_model_file=${context.selectedModelFile}",
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
        "engine_create_started=${flags.engineCreateStarted.toDiagnosticValue()}",
        "engine_create_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
        "conversation_create_started=${flags.conversationCreateStarted.toDiagnosticValue()}",
        "conversation_create_finished=${flags.conversationCreateFinished.toDiagnosticValue()}",
        "generate_started=${flags.generateStarted.toDiagnosticValue()}",
        "first_token_received=${flags.firstTokenReceived.toDiagnosticValue()}",
        "failure_stage=$failureStage",
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
        "gpu_last_known_stage=${resolveGpuLastKnownStage(flags)}",
        "gpu_held_engine_exists=${flags.heldEngineExists.toDiagnosticValue()}",
        "gpu_held_engine_reused=${flags.heldEngineReused.toDiagnosticValue()}",
        "gpu_model_kind=${context.modelKind}",
        "gpu_selected_model_name=${context.selectedModelName}",
        "gpu_selected_model_file=${context.selectedModelFile}",
        "gpu_backend_setting=${context.preferredBackend}",
        "gpu_compatibility_mode=${resolveGpuCompatibilityModeForBackend(context.preferredBackend)}",
        "gpu_engine_config_profile=${resolveGpuEngineConfigProfileForBackend(context.preferredBackend)}",
        "gpu_cache_dir_mode=${resolveGpuCacheDirModeForBackend(context.preferredBackend)}",
        "gpu_model_path_mode=${resolveGpuModelPathModeForBackend(context.preferredBackend)}",
        "gpu_sampler_config_profile=${resolveGpuSamplerConfigProfileForBackend(context.preferredBackend)}",
        "gpu_conversation_config_profile=${resolveGpuConversationConfigProfileForBackend(context.preferredBackend)}",
        "gpu_thinking_enabled=false",
        "gpu_speculative_decoding_enabled=false",
        "gpu_max_tokens=${resolveGpuMaxTokensForBackend(context.preferredBackend)}",
        "gpu_top_k=${resolveGpuTopKForBackend(context.preferredBackend)}",
        "gpu_top_p=${resolveGpuTopPForBackend(context.preferredBackend)}",
        "gpu_temperature=${resolveGpuTemperatureForBackend(context.preferredBackend)}",
        "gpu_dispatcher=Dispatchers.IO",
        "gpu_engine_initialize_api=Engine.initialize",
        "gpu_edge_gallery_diff_applied=${shouldApplyEdgeGalleryLikeGpuCompatibilityMode(context.preferredBackend)}",
        "gpu_fallback_used=${flags.fallbackUsed.toDiagnosticValue()}",
        "gpu_stale_callback_ignored=${flags.staleCallbackIgnored.toDiagnosticValue()}",
    ).joinToString(" ")
}

private fun Boolean?.toDiagnosticValue(): String = this?.toString() ?: "unknown"

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

internal fun shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend: String): Boolean =
    preferredBackend.equals("GPU", ignoreCase = true)

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

internal fun resolveGpuCacheDirModeForBackend(preferredBackend: String): String =
    if (shouldApplyEdgeGalleryLikeGpuCompatibilityMode(preferredBackend)) {
        GPU_CACHE_DIR_MODE_EDGE_GALLERY_LIKE
    } else {
        "unavailable"
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
        GPU_EDGE_GALLERY_LIKE_MAX_TOKENS.toString()
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

internal fun shouldApplyGpuExperimentalStageTimeout(
    context: LocalRouteDiagnosticContext,
): Boolean =
    context.localRouteEntered &&
        context.baselineRole == LITERT_LM_BASELINE_GPU_EXPERIMENTAL

internal fun resolveGpuExperimentalTimeoutFailureStage(
    lastStage: String?,
): String =
    when (lastStage) {
        "engine_create_started" -> "engine_create_timeout"
        "conversation_create_started" -> "conversation_create_timeout"
        "conversation_create_finished" -> "generate_start_timeout"
        "generate_started" -> "first_token_timeout"
        else -> "engine_create_timeout"
    }

internal fun resolveGpuExperimentalTimeoutStage(
    failureStage: String?,
): String =
    when (failureStage) {
        "first_token_timeout" -> "first_token_wait"
        "generate_start_timeout" -> "generate"
        "conversation_create_timeout" -> "engine_create"
        "engine_create_timeout", "gpu_watchdog_timeout" -> "engine_create"
        else -> "unavailable"
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
        flags.engineCreateFinished == true -> "engine_create_finished"
        flags.engineCreateStarted == true -> "engine_create_started"
        else -> "unavailable"
    }
