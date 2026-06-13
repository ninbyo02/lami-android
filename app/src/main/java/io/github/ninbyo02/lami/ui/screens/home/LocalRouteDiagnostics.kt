package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import java.io.File

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
    val gpuInitializationTimeoutSuspected =
        gpuTimeoutStage in setOf("engine_config_build", "engine_create", "engine_initialize", "conversation_create") &&
            failureStage != "none"
    val guardRecommendation = if (engineCreateTimeoutSuspected || gpuInitializationTimeoutSuspected) {
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
    return listOf(
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
        "gpu_model_path=${gpuConfig.modelPath}",
        "gpu_model_path_tail=${gpuConfig.modelPathTail}",
        "gpu_backend_setting=${context.preferredBackend}",
        "gpu_compatibility_mode=${resolveGpuCompatibilityModeForBackend(context.preferredBackend)}",
        "gpu_engine_config_profile=${resolveGpuEngineConfigProfileForBackend(context.preferredBackend)}",
        "gpu_experiment_mode=${gpuConfig.experimentMode}",
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
internal const val GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE = "edge_gallery_like"
internal const val GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL = "gpu_sampler_only_minimal"
internal const val GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION = "gpu_no_sampling_acceleration"
internal const val GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE = "gpu_disable_topk_gpu_sampler_candidate"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_NULL = "gpu_cache_dir_null"
internal const val GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES = "gpu_cache_dir_app_files"
internal const val GPU_EXPERIMENT_MODE_MAX_TOKENS_32 = "gpu_max_tokens_32"
internal val GPU_DIAGNOSTIC_EXPERIMENT_MODES = listOf(
    GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE,
    GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL,
    GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
    GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
    GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
    GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
    GPU_EXPERIMENT_MODE_MAX_TOKENS_32,
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
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL -> "forced_null"
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES -> "forced_app_cache_dir"
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
            GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE -> "no_sampler_config"
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
        experimentMode != GPU_EXPERIMENT_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE

internal fun resolveGpuExperimentCacheDirForDiagnostics(
    modelPath: String,
    cacheDirPath: String?,
    experimentMode: String,
): String? =
    when (experimentMode) {
        GPU_EXPERIMENT_MODE_CACHE_DIR_NULL -> null
        GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES -> cacheDirPath
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
        "generate_started" -> "first_token_timeout"
        else -> "engine_create_timeout"
    }

internal fun resolveGpuExperimentalTimeoutStage(
    failureStage: String?,
): String =
    when (failureStage) {
        "first_token_timeout" -> "first_token_wait"
        "generate_start_timeout" -> "generate"
        "conversation_create_timeout" -> "conversation_create"
        "engine_initialize_timeout" -> "engine_initialize"
        "engine_config_build_timeout" -> "engine_config_build"
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
