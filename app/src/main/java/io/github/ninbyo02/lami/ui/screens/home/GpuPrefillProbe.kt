package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val GPU_PREFILL_PROBE_DEFAULT_TIMEOUT_MS = 15_000L
private const val GPU_PREFILL_PROBE_DEFAULT_PROMPT = "こんにちは"
private const val GPU_PREFILL_PROBE_DEFAULT_MAX_TOKENS = 1
private const val GPU_PREFILL_PROBE_CACHE_DIR_NULL = "null"
private const val GPU_PREFILL_PROBE_CACHE_DIR_APP_CACHE = "app_cache"
private const val GPU_PREFILL_PROBE_SAMPLER_NONE = "no_sampler"
private const val GPU_PREFILL_PROBE_SAMPLER_GALLERY_DEFAULT = "gallery_default_sampler"

internal data class ReusableLocalEngineCreateDiagnostic(
    val engine: HeldLocalEngine?,
    val stage: String?,
    val className: String?,
    val message: String?,
    val preferredBackendApplyResult: PreferredBackendApplyResult? = null,
    val failureDiagnosticsText: String? = null,
)

internal data class GpuPrefillProbeRequest(
    val modelPath: String,
    val cacheDirPath: String?,
    val prompt: String = GPU_PREFILL_PROBE_DEFAULT_PROMPT,
    val maxTokens: Int = GPU_PREFILL_PROBE_DEFAULT_MAX_TOKENS,
    val samplerEnabled: Boolean = false,
    val cacheDirMode: String = GPU_PREFILL_PROBE_CACHE_DIR_NULL,
    val timeoutMs: Long = GPU_PREFILL_PROBE_DEFAULT_TIMEOUT_MS,
    val skippedNormalGenerate: Boolean = true,
    val isolatedEngineUsed: Boolean = true,
    val sharedEngineUsed: Boolean = false,
    val invalidatesHeldEngine: Boolean = true,
    val usedHeldEngine: Boolean = false,
    val heldEnginePresentBefore: Boolean = false,
    val normalGpuLastKnownStage: String = "normal_generate_skipped_before_start",
)

internal data class StandardGpuRuntimeAlignmentCandidateEligibility(
    val enabled: Boolean,
    val eligible: Boolean,
    val blockReason: String,
    val modelSizeBytes: String,
    val modelIdentityHint: String,
    val runtimeStack: String = STANDARD_GPU_RUNTIME_ALIGNMENT_CANDIDATE_RUNTIME_STACK,
)

internal data class StandardGpuMinimalRuntimeCandidateEligibility(
    val enabled: Boolean,
    val eligible: Boolean,
    val blockReason: String,
    val modelSizeBytes: String,
    val modelIdentityHint: String,
    val runtimeStack: String = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_RUNTIME_STACK,
)

internal data class GpuPrefillProbeState(
    val request: GpuPrefillProbeRequest,
    val startedAtMs: Long = SystemClock.elapsedRealtime(),
    val elapsedOverrideMs: Long? = null,
    val engineConfigStarted: AtomicReference<Boolean> = AtomicReference(false),
    val engineConfigFinished: AtomicReference<Boolean> = AtomicReference(false),
    val engineInitializeStarted: AtomicReference<Boolean> = AtomicReference(false),
    val engineInitializeFinished: AtomicReference<Boolean> = AtomicReference(false),
    val conversationCreateStarted: AtomicReference<Boolean> = AtomicReference(false),
    val conversationCreateFinished: AtomicReference<Boolean> = AtomicReference(false),
    val generateStarted: AtomicReference<Boolean> = AtomicReference(false),
    val firstTokenReceived: AtomicReference<Boolean> = AtomicReference(false),
    val generateStartedAtMs: AtomicReference<Long?> = AtomicReference(null),
    val firstTokenReceivedAtMs: AtomicReference<Long?> = AtomicReference(null),
    val exceptionClass: AtomicReference<String?> = AtomicReference(null),
    val exceptionMessage: AtomicReference<String?> = AtomicReference(null),
    val exceptionExpansion: AtomicReference<LocalFailureExceptionExpansion?> = AtomicReference(null),
    val resultText: AtomicReference<String> = AtomicReference(""),
    val staleCallbackIgnored: AtomicReference<Boolean> = AtomicReference(false),
    val runStarted: AtomicReference<Boolean> = AtomicReference(false),
    val runFinished: AtomicReference<Boolean> = AtomicReference(false),
    val runTimedOut: AtomicReference<Boolean> = AtomicReference(false),
    val cleanupStarted: AtomicReference<Boolean> = AtomicReference(false),
    val cleanupFinished: AtomicReference<Boolean> = AtomicReference(false),
    val cleanupResult: AtomicReference<String> = AtomicReference("not_started"),
) {
    fun elapsedMs(): Long = elapsedOverrideMs ?: (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
}

internal fun resolveGpuPrefillProbeRequestForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    modelPath: String,
    cacheDirPath: String?,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): GpuPrefillProbeRequest? {
    if (!BuildConfig.DEBUG) return null
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return null
    if (!isGpuPrefillProbeRequestedForDebug(preferredBackend, propertyReader)) return null
    return buildGpuPrefillProbeRequestFromDebugProperties(
        modelPath = modelPath,
        cacheDirPath = cacheDirPath,
        propertyReader = propertyReader,
    )
}

internal fun resolveGpuHeldEnginePrefillProbeRequestForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    modelPath: String,
    cacheDirPath: String?,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): GpuPrefillProbeRequest? {
    if (!BuildConfig.DEBUG) return null
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return null
    if (!isGpuHeldEnginePrefillProbeRequestedForDebug(preferredBackend, propertyReader)) return null
    return buildGpuPrefillProbeRequestFromDebugProperties(
        modelPath = modelPath,
        cacheDirPath = cacheDirPath,
        propertyReader = propertyReader,
    ).copy(
        isolatedEngineUsed = false,
        sharedEngineUsed = true,
        usedHeldEngine = true,
        invalidatesHeldEngine = true,
        normalGpuLastKnownStage = "held_engine_probe_normal_generate_skipped_before_start",
    )
}

private fun buildGpuPrefillProbeRequestFromDebugProperties(
    modelPath: String,
    cacheDirPath: String?,
    propertyReader: (String) -> String?,
): GpuPrefillProbeRequest {
    val prompt = propertyReader("debug.lami.gpu_prefill_probe_prompt")
        ?: propertyReader("lami.gpu_prefill_probe_prompt")
        ?: GPU_PREFILL_PROBE_DEFAULT_PROMPT
    val maxTokens = (propertyReader("debug.lami.gpu_prefill_probe_max_tokens")
        ?: propertyReader("lami.gpu_prefill_probe_max_tokens"))
        ?.toIntOrNull()
        ?.coerceIn(1, 32)
        ?: GPU_PREFILL_PROBE_DEFAULT_MAX_TOKENS
    val samplerValue = propertyReader("debug.lami.gpu_prefill_probe_sampler")
        ?: propertyReader("lami.gpu_prefill_probe_sampler")
        ?: GPU_PREFILL_PROBE_SAMPLER_NONE
    val cacheDirMode = (propertyReader("debug.lami.gpu_prefill_probe_cache_dir")
        ?: propertyReader("lami.gpu_prefill_probe_cache_dir")
        ?: GPU_PREFILL_PROBE_CACHE_DIR_NULL)
        .lowercase(Locale.US)
    val timeoutMs = (propertyReader("debug.lami.gpu_prefill_probe_timeout_ms")
        ?: propertyReader("lami.gpu_prefill_probe_timeout_ms"))
        ?.toLongOrNull()
        ?.coerceIn(5_000L, 30_000L)
        ?: GPU_PREFILL_PROBE_DEFAULT_TIMEOUT_MS
    return GpuPrefillProbeRequest(
        modelPath = modelPath,
        cacheDirPath = cacheDirPath,
        prompt = prompt,
        maxTokens = maxTokens,
        samplerEnabled = samplerValue.equals("gallery", ignoreCase = true) ||
            samplerValue.equals(GPU_PREFILL_PROBE_SAMPLER_GALLERY_DEFAULT, ignoreCase = true) ||
            samplerValue.equals("true", ignoreCase = true),
        cacheDirMode = when (cacheDirMode) {
            GPU_PREFILL_PROBE_CACHE_DIR_APP_CACHE, "app_files", "app" -> GPU_PREFILL_PROBE_CACHE_DIR_APP_CACHE
            else -> GPU_PREFILL_PROBE_CACHE_DIR_NULL
        },
        timeoutMs = timeoutMs,
    )
}

internal fun isGpuPrefillProbeRequestedForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG) return false
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return false
    val enabled = propertyReader("debug.lami.gpu_prefill_probe")
        ?: propertyReader("lami.gpu_prefill_probe")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

internal fun isGpuHeldEnginePrefillProbeRequestedForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG) return false
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return false
    val enabled = propertyReader("debug.lami.gpu_probe_use_held_engine")
        ?: propertyReader("lami.gpu_probe_use_held_engine")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

internal fun resolveGpuGenerateProbeModeForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): String {
    if (!BuildConfig.DEBUG) return GPU_GENERATE_PROBE_MODE_NORMAL
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return GPU_GENERATE_PROBE_MODE_NORMAL
    val requested = propertyReader("debug.lami.gpu_generate_probe_mode")
        ?: propertyReader("lami.gpu_generate_probe_mode")
        ?: return GPU_GENERATE_PROBE_MODE_NORMAL
    return when (requested.trim().lowercase(Locale.US)) {
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT -> GPU_GENERATE_PROBE_MODE_ASCII_PROMPT
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_1 -> GPU_GENERATE_PROBE_MODE_MAX_TOKENS_1
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_MAX_TOKENS_32 -> GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_MAX_TOKENS_32
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_NO_SAMPLER -> GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_NO_SAMPLER
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_16 -> GPU_GENERATE_PROBE_MODE_MAX_TOKENS_16
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_32 -> GPU_GENERATE_PROBE_MODE_MAX_TOKENS_32
        GPU_GENERATE_PROBE_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER -> GPU_GENERATE_PROBE_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER
        GPU_GENERATE_PROBE_MODE_CACHE_DIR_NULL_NO_SAMPLER -> GPU_GENERATE_PROBE_MODE_CACHE_DIR_NULL_NO_SAMPLER
        GPU_GENERATE_PROBE_MODE_NO_SAMPLER -> GPU_GENERATE_PROBE_MODE_NO_SAMPLER
        GPU_GENERATE_PROBE_MODE_NO_STREAMING_UI -> GPU_GENERATE_PROBE_MODE_NO_STREAMING_UI
        GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY -> GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY
        GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI -> GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI
        GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING -> GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING
        else -> GPU_GENERATE_PROBE_MODE_NORMAL
    }
}

// The pinned provider is packaged only for arm64; other ABIs keep their existing route.
internal fun isVerifiedStandardGpuOpenClRuntime(): Boolean =
    BuildConfig.STANDARD_GPU_OPENCL_RUNTIME &&
        android.os.Process.is64Bit() &&
        android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

internal fun isGpuNormalRouteUseCallbackStreamingRequestedForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
    verifiedOpenClRuntime: Boolean = false,
): Boolean {
    if (verifiedOpenClRuntime) return preferredBackend == PreferredBackendDryRunSetting.GPU
    if (!BuildConfig.DEBUG) return false
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return false
    val enabled = propertyReader("debug.lami.gpu_normal_route_use_callback_streaming")
        ?: propertyReader("lami.gpu_normal_route_use_callback_streaming")
    if (enabled != null) {
        return enabled.equals("true", ignoreCase = true) || enabled == "1"
    }
    return BuildConfig.CURRENT_FLAVOR == "standard"
}

internal fun isGpuCallbackRawPassthroughEnabledForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
    standardGpuMinimalRuntimeCandidateFlavor: Boolean = BuildConfig.STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR,
): Boolean {
    if (!BuildConfig.DEBUG || !standardGpuMinimalRuntimeCandidateFlavor) return false
    if (preferredBackend != PreferredBackendDryRunSetting.GPU) return false
    val enabled = propertyReader("debug.lami.gpu_callback_raw_passthrough")
        ?: propertyReader("lami.gpu_callback_raw_passthrough")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

internal fun isStandardGpuRuntimeAlignmentCandidateEnabledForDebug(
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG) return false
    if (BuildConfig.CURRENT_FLAVOR != "standard") return false
    val enabled = propertyReader("debug.lami.standard_gpu_runtime_alignment_candidate")
        ?: propertyReader("lami.standard_gpu_runtime_alignment_candidate")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

internal fun isStandardGpuMinimalRuntimeCandidateEnabledForDebug(
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG) return false
    if (BuildConfig.CURRENT_FLAVOR != "standard") return false
    val enabled = propertyReader("debug.lami.standard_gpu_minimal_runtime_candidate")
        ?: propertyReader("lami.standard_gpu_minimal_runtime_candidate")
        ?: return false
    return enabled.equals("true", ignoreCase = true) || enabled == "1"
}

internal fun resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    modelPath: String?,
    callbackStreamingGateEnabled: Boolean,
    gpuGenerateProbeMode: String = GPU_GENERATE_PROBE_MODE_NORMAL,
    activeGenerationAlreadyRunning: Boolean = false,
    modelOrBackendSwitchInProgress: Boolean = false,
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): StandardGpuRuntimeAlignmentCandidateEligibility {
    val enabled = isStandardGpuRuntimeAlignmentCandidateEnabledForDebug(propertyReader)
    val modelFile = modelPath
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" }
        ?.let(::File)
    val sizeBytes = modelFile?.takeIf { it.isFile }?.length()
    val sizeDiagnostic = sizeBytes?.toString() ?: "unavailable"
    val pathText = listOfNotNull(modelPath, modelFile?.name)
        .joinToString(" ")
        .lowercase(Locale.US)
    val nameLooksLikeEdgeGalleryE2b =
        pathText.contains("gemma-4-e2b-it-edge-gallery.litertlm") ||
            pathText.contains("gemma_4_e2b_it") ||
            pathText.contains("litert-community/gemma-4-e2b-it-litert-lm") ||
            pathText.endsWith("gemma-4-e2b-it.litertlm")
    val sizeMatches = sizeBytes == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES
    val modelIdentityHint = when {
        !nameLooksLikeEdgeGalleryE2b -> "not_edge_gallery_e2b"
        sizeBytes == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES -> "edge_gallery_e2b_expected"
        sizeBytes == null -> "edge_gallery_e2b_expected_size_unavailable"
        else -> "edge_gallery_e2b_size_mismatch"
    }
    val blockReason = when {
        BuildConfig.CURRENT_FLAVOR != "standard" -> "not_standard_flavor"
        !enabled -> "candidate_gate_disabled"
        preferredBackend != PreferredBackendDryRunSetting.GPU -> "selected_backend_not_gpu"
        !callbackStreamingGateEnabled -> "callback_streaming_gate_disabled"
        gpuGenerateProbeMode !in STANDARD_GPU_RUNTIME_ALIGNMENT_CANDIDATE_ALLOWED_PROBE_MODES ->
            "unsupported_gpu_generate_probe_mode"
        activeGenerationAlreadyRunning -> "active_generation_already_running"
        modelOrBackendSwitchInProgress -> "model_or_backend_switch_in_progress"
        !nameLooksLikeEdgeGalleryE2b -> "model_identity_not_edge_gallery_e2b"
        sizeBytes == null -> "model_size_unavailable"
        !sizeMatches -> "model_size_mismatch"
        else -> "none"
    }
    return StandardGpuRuntimeAlignmentCandidateEligibility(
        enabled = enabled,
        eligible = blockReason == "none",
        blockReason = blockReason,
        modelSizeBytes = sizeDiagnostic,
        modelIdentityHint = modelIdentityHint,
    )
}

internal fun resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
    preferredBackend: PreferredBackendDryRunSetting,
    modelPath: String?,
    callbackStreamingGateEnabled: Boolean,
    gpuGenerateProbeMode: String = GPU_GENERATE_PROBE_MODE_NORMAL,
    libLiteRtSha256: String = "unavailable",
    libLiteRtLmJniSha256: String = "unavailable",
    dispatchPresent: String = "unavailable",
    compilerPluginPresent: String = "unavailable",
    constraintProviderPresent: String = "unavailable",
    propertyReader: (String) -> String? = ::readGpuPrefillProbeDebugProperty,
): StandardGpuMinimalRuntimeCandidateEligibility {
    val enabled = isStandardGpuMinimalRuntimeCandidateEnabledForDebug(propertyReader)
    val modelFile = modelPath
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "unknown" && it != "unavailable" }
        ?.let(::File)
    val sizeBytes = modelFile?.takeIf { it.isFile }?.length()
    val sizeDiagnostic = sizeBytes?.toString() ?: "unavailable"
    val pathText = listOfNotNull(modelPath, modelFile?.name)
        .joinToString(" ")
        .lowercase(Locale.US)
    val nameLooksLikeEdgeGalleryE2b =
        pathText.contains("gemma-4-e2b-it-edge-gallery.litertlm") ||
            pathText.contains("gemma_4_e2b_it") ||
            pathText.contains("litert-community/gemma-4-e2b-it-litert-lm") ||
            pathText.endsWith("gemma-4-e2b-it.litertlm")
    val sizeMatches = sizeBytes == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES
    val modelIdentityHint = when {
        !nameLooksLikeEdgeGalleryE2b -> "not_edge_gallery_e2b"
        sizeBytes == STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES -> "edge_gallery_e2b_expected"
        sizeBytes == null -> "edge_gallery_e2b_expected_size_unavailable"
        else -> "edge_gallery_e2b_size_mismatch"
    }
    val blockReason = when {
        BuildConfig.CURRENT_FLAVOR != "standard" -> "not_standard_flavor"
        !enabled -> "candidate_gate_disabled"
        preferredBackend != PreferredBackendDryRunSetting.GPU -> "selected_backend_not_gpu"
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
        enabled = enabled,
        eligible = blockReason == "none",
        blockReason = blockReason,
        modelSizeBytes = sizeDiagnostic,
        modelIdentityHint = modelIdentityHint,
    )
}

internal val STANDARD_GPU_RUNTIME_ALIGNMENT_CANDIDATE_ALLOWED_PROBE_MODES = setOf(
    GPU_GENERATE_PROBE_MODE_NORMAL,
    GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING,
)

internal fun usesGpuCallbackStreamingPathForDebug(probeMode: String): Boolean =
    probeMode == GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI ||
        probeMode == GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING

internal fun isGpuCallbackStreamingPathSelectedForDebug(
    probeMode: String,
    normalRouteUseCallbackStreaming: Boolean,
): Boolean =
    usesGpuCallbackStreamingPathForDebug(probeMode) ||
        (probeMode == GPU_GENERATE_PROBE_MODE_NORMAL && normalRouteUseCallbackStreaming)

internal fun resolveGpuCallbackStreamingPathReasonForDebug(
    probeMode: String,
    normalRouteUseCallbackStreaming: Boolean,
): String =
    when {
        probeMode == GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI -> "probe_callback_to_ui"
        probeMode == GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING -> "probe_normal_callback_streaming"
        probeMode == GPU_GENERATE_PROBE_MODE_NORMAL && normalRouteUseCallbackStreaming -> "dev_gate_normal_route"
        else -> "not_selected"
    }

internal fun usesDirectGpuCallbackAppendForDebug(
    probeMode: String,
    normalRouteUseCallbackStreaming: Boolean,
): Boolean =
    probeMode == GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY ||
        isGpuCallbackStreamingPathSelectedForDebug(
            probeMode = probeMode,
            normalRouteUseCallbackStreaming = normalRouteUseCallbackStreaming,
        )

internal fun suppressesGpuStreamingUiForDebug(probeMode: String): Boolean =
    probeMode == GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY ||
        probeMode == GPU_GENERATE_PROBE_MODE_NO_STREAMING_UI

internal fun resolveGpuExperimentOverrideForGenerateProbeMode(
    probeMode: String,
): String? =
    when (probeMode) {
        GPU_GENERATE_PROBE_MODE_NO_SAMPLER,
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_NO_SAMPLER -> GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_32,
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_MAX_TOKENS_32 -> GPU_EXPERIMENT_MODE_MAX_TOKENS_32
        GPU_GENERATE_PROBE_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER -> GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER
        GPU_GENERATE_PROBE_MODE_CACHE_DIR_NULL_NO_SAMPLER -> GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER
        else -> null
    }

internal fun resolveGpuGenerateProbePromptForDebug(
    originalPrompt: String,
    probeMode: String,
): String =
    when (probeMode) {
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT,
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_MAX_TOKENS_32,
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_NO_SAMPLER -> "hi"
        else -> originalPrompt
    }

internal fun overrideGpuConfigForGenerateProbeMode(
    diagnostics: GpuRouteConfigDiagnostics,
    probeMode: String,
): GpuRouteConfigDiagnostics =
    when (probeMode) {
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_1 -> diagnostics.copy(
            maxTokens = "1",
            outputQualityEffectiveMaxTokens = "1",
            outputQualityProbeEffectiveMaxTokens = "1",
        )
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_16 -> diagnostics.copy(
            maxTokens = "16",
            outputQualityEffectiveMaxTokens = "16",
            outputQualityProbeEffectiveMaxTokens = "16",
        )
        GPU_GENERATE_PROBE_MODE_MAX_TOKENS_32,
        GPU_GENERATE_PROBE_MODE_ASCII_PROMPT_MAX_TOKENS_32 -> diagnostics.copy(
            maxTokens = "32",
            outputQualityEffectiveMaxTokens = "32",
            outputQualityProbeEffectiveMaxTokens = "32",
        )
        else -> diagnostics
    }

internal fun readGpuPrefillProbeDebugProperty(key: String): String? {
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

internal suspend fun runGpuPrefillProbe(
    request: GpuPrefillProbeRequest,
    appendTrace: (String) -> Unit = {},
): String {
    val state = GpuPrefillProbeState(request = request)
    val deferred = CoroutineScope(Dispatchers.IO).async {
        var engine: Any? = null
        var conversation: Any? = null
        try {
            state.runStarted.set(true)
            state.engineConfigStarted.set(true)
            val engineConfig = EngineConfig(
                modelPath = request.modelPath,
                backend = Backend.GPU(),
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = request.maxTokens,
                cacheDir = resolveGpuPrefillProbeCacheDir(request),
            )
            state.engineConfigFinished.set(true)
            val createdEngine = Engine(engineConfig)
            engine = createdEngine
            state.engineInitializeStarted.set(true)
            createdEngine.javaClass.methods.firstOrNull { method ->
                method.name == "initialize" && method.parameterTypes.isEmpty()
            }?.invoke(createdEngine)
            state.engineInitializeFinished.set(true)
            state.conversationCreateStarted.set(true)
            conversation = createGpuPrefillProbeConversation(createdEngine, request)
            state.conversationCreateFinished.set(conversation != null)
            if (conversation == null) return@async
            state.generateStarted.set(true)
            state.generateStartedAtMs.set(state.elapsedMs())
            runGpuPrefillProbeGenerate(
                conversation = conversation,
                request = request,
                state = state,
            )
        } catch (throwable: Throwable) {
            val exceptionClass = throwable.javaClass.name
            val exceptionMessage = throwable.message ?: "none"
            state.exceptionClass.set(exceptionClass)
            state.exceptionMessage.set(exceptionMessage)
            state.exceptionExpansion.set(
                buildLocalFailureExceptionExpansion(
                    throwable = throwable,
                    parsed = emptyMap(),
                    failureExceptionClass = exceptionClass,
                    failureExceptionMessage = exceptionMessage,
                ),
            )
        } finally {
            state.cleanupStarted.set(true)
            val conversationOutcome = runCatching { closeQuietly(conversation, appendTrace) }
            val engineOutcome = runCatching { closeQuietly(engine, appendTrace) }
            state.cleanupResult.set(
                when {
                    conversationOutcome.isSuccess && engineOutcome.isSuccess -> "closed_probe_conversation_and_engine"
                    else -> "close_attempt_failed"
                },
            )
            state.cleanupFinished.set(true)
            state.runFinished.set(!state.staleCallbackIgnored.get())
        }
    }
    while (!deferred.isCompleted) {
        if (state.elapsedMs() >= request.timeoutMs) {
            state.staleCallbackIgnored.set(true)
            state.runTimedOut.set(true)
            state.cleanupStarted.set(true)
            state.cleanupResult.set("cancel_requested_native_generate_may_still_be_processing")
            deferred.cancel()
            break
        }
        delay(100L)
    }
    if (deferred.isCompleted) {
        try {
            deferred.await()
        } catch (_: Throwable) {
            // Diagnostic probe completion failures are already recorded in state.
        }
    }
    val text = buildGpuPrefillProbeDiagnosticsText(state)
    safeAppendTrace(appendTrace, text)
    return text
}

internal suspend fun runGpuHeldEnginePrefillProbe(
    heldEngine: HeldLocalEngine,
    request: GpuPrefillProbeRequest,
    appendTrace: (String) -> Unit = {},
): String {
    val heldRequest = request.copy(
        isolatedEngineUsed = false,
        sharedEngineUsed = true,
        usedHeldEngine = true,
        invalidatesHeldEngine = true,
    )
    val state = GpuPrefillProbeState(request = heldRequest)
    val deferred = CoroutineScope(Dispatchers.IO).async {
        var conversation: Any? = null
        try {
            state.runStarted.set(true)
            state.engineConfigStarted.set(true)
            state.engineConfigFinished.set(true)
            state.engineInitializeStarted.set(true)
            state.engineInitializeFinished.set(true)
            state.conversationCreateStarted.set(true)
            conversation = createGpuPrefillProbeConversation(heldEngine.engineInstance, heldRequest)
            state.conversationCreateFinished.set(conversation != null)
            if (conversation == null) {
                state.exceptionClass.set("ConversationCreateReturnedNull")
                state.exceptionMessage.set("createConversation returned null")
                return@async
            }
            state.generateStarted.set(true)
            state.generateStartedAtMs.set(state.elapsedMs())
            runGpuPrefillProbeGenerate(
                conversation = conversation,
                request = heldRequest,
                state = state,
            )
        } catch (throwable: Throwable) {
            val exceptionClass = throwable.javaClass.name
            val exceptionMessage = throwable.message ?: "none"
            state.exceptionClass.set(exceptionClass)
            state.exceptionMessage.set(exceptionMessage)
            state.exceptionExpansion.set(
                buildLocalFailureExceptionExpansion(
                    throwable = throwable,
                    parsed = emptyMap(),
                    failureExceptionClass = exceptionClass,
                    failureExceptionMessage = exceptionMessage,
                ),
            )
        } finally {
            state.cleanupStarted.set(true)
            val conversationOutcome = runCatching { closeQuietly(conversation, appendTrace) }
            state.cleanupResult.set(
                if (conversationOutcome.isSuccess) {
                    "closed_probe_conversation_held_engine_recreate_required"
                } else {
                    "close_probe_conversation_failed_held_engine_recreate_required"
                },
            )
            state.cleanupFinished.set(true)
            state.runFinished.set(!state.staleCallbackIgnored.get())
        }
    }
    while (!deferred.isCompleted) {
        if (state.elapsedMs() >= heldRequest.timeoutMs) {
            state.staleCallbackIgnored.set(true)
            state.runTimedOut.set(true)
            state.cleanupStarted.set(true)
            state.cleanupResult.set("cancel_requested_held_engine_recreate_required")
            deferred.cancel()
            break
        }
        delay(100L)
    }
    if (deferred.isCompleted) {
        try {
            deferred.await()
        } catch (_: Throwable) {
            // Diagnostic probe completion failures are already recorded in state.
        }
    }
    val text = buildGpuPrefillProbeDiagnosticsText(state)
    safeAppendTrace(appendTrace, text)
    return text
}

private class GpuPrefillProbeFirstToken : RuntimeException()

private suspend fun runGpuPrefillProbeGenerate(
    conversation: Any,
    request: GpuPrefillProbeRequest,
    state: GpuPrefillProbeState,
) {
    val sendMessageAsync = findSendMessageAsyncMethod(
        conversationClass = conversation.javaClass,
        namespace = "com.google.ai.edge.litertlm",
    )
    if (sendMessageAsync != null) {
        val flowValue = invokeSendMessageAsync(
            conversation = conversation,
            method = sendMessageAsync,
            namespace = "com.google.ai.edge.litertlm",
            prompt = request.prompt,
        )
        val flow = flowValue as? Flow<*> ?: return
        try {
            flow.collect { message ->
                currentCoroutineContext().ensureActive()
                val text = extractOfficialMessageTextWithTrace(
                    path = "gpu-prefill-probe",
                    value = message,
                    appendTrace = {},
                )?.trim().orEmpty()
                if (text.isBlank()) return@collect
                state.firstTokenReceived.set(true)
                state.firstTokenReceivedAtMs.set(state.elapsedMs())
                state.resultText.set(text)
                throw GpuPrefillProbeFirstToken()
            }
        } catch (_: GpuPrefillProbeFirstToken) {
            return
        }
        return
    }
    val blocking = findBlockingSendMethod(
        conversationClass = conversation.javaClass,
        namespace = "com.google.ai.edge.litertlm",
    ) ?: return
    val value = invokeBlockingSend(
        conversation = conversation,
        method = blocking,
        namespace = "com.google.ai.edge.litertlm",
        prompt = request.prompt,
    ) ?: return
    val text = extractOfficialMessageTextWithTrace(
        path = "gpu-prefill-probe-blocking",
        value = value,
        appendTrace = {},
    )?.trim().orEmpty()
    if (text.isNotBlank()) {
        state.firstTokenReceived.set(true)
        state.firstTokenReceivedAtMs.set(state.elapsedMs())
        state.resultText.set(text)
    }
}

private fun createGpuPrefillProbeConversation(
    engine: Any,
    request: GpuPrefillProbeRequest,
): Any? {
    val config = if (request.samplerEnabled) {
        ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = GPU_EDGE_GALLERY_LIKE_TOP_K,
                topP = GPU_EDGE_GALLERY_LIKE_TOP_P.toDouble(),
                temperature = GPU_EDGE_GALLERY_LIKE_TEMPERATURE.toDouble(),
            ),
        )
    } else {
        ConversationConfig()
    }
    val createConversationMethod = engine.javaClass.methods.firstOrNull { method ->
        method.name == "createConversation" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0].name == "com.google.ai.edge.litertlm.ConversationConfig"
    } ?: return null
    return createConversationMethod.invoke(engine, config)
}

private fun resolveGpuPrefillProbeCacheDir(request: GpuPrefillProbeRequest): String? =
    if (request.cacheDirMode == GPU_PREFILL_PROBE_CACHE_DIR_APP_CACHE) request.cacheDirPath else null

internal fun buildGpuPrefillProbeDiagnosticsText(state: GpuPrefillProbeState): String {
    val elapsedMs = state.elapsedMs()
    val timeoutStage = resolveGpuPrefillProbeTimeoutStage(
        engineConfigStarted = state.engineConfigStarted.get(),
        engineConfigFinished = state.engineConfigFinished.get(),
        engineInitializeStarted = state.engineInitializeStarted.get(),
        engineInitializeFinished = state.engineInitializeFinished.get(),
        conversationCreateStarted = state.conversationCreateStarted.get(),
        conversationCreateFinished = state.conversationCreateFinished.get(),
        generateStarted = state.generateStarted.get(),
        firstTokenReceived = state.firstTokenReceived.get(),
    )
    val timedOut = state.staleCallbackIgnored.get() && !state.firstTokenReceived.get() && state.exceptionClass.get() == null
    val failureStage = when {
        state.exceptionClass.get() != null -> resolveGpuPrefillProbeExceptionFailureStage(
            timeoutStage = timeoutStage,
            exceptionClass = state.exceptionClass.get(),
        )
        timedOut -> "gpu_prefill_probe_timeout_$timeoutStage"
        else -> "none"
    }
    val generateBeforeFirstTokenElapsedMs =
        if (state.generateStarted.get() && !state.firstTokenReceived.get()) {
            state.generateStartedAtMs.get()?.let { (elapsedMs - it).coerceAtLeast(0L).toString() } ?: "unavailable"
        } else {
            "unavailable"
        }
    val resultText = state.resultText.get()
    val exceptionExpansion = state.exceptionExpansion.get() ?: LocalFailureExceptionExpansion()
    val causeMessageRaw = exceptionExpansion.failureCauseMessage
    val classification = classifyGpuLiteRtFailure(
        message = listOf(
            causeMessageRaw,
            exceptionExpansion.failureRootCauseMessage,
            exceptionExpansion.reflectionTargetExceptionMessage,
            exceptionExpansion.exceptionChain,
        ).joinToString(" "),
        failureStage = failureStage,
        timeoutStage = timeoutStage,
        generateStarted = state.generateStarted.get(),
        firstTokenReceived = state.firstTokenReceived.get(),
        engineInitializeFinished = state.engineInitializeFinished.get(),
        conversationCreateFinished = state.conversationCreateFinished.get(),
    )
    val previousInvocationStillProcessing = state.exceptionMessage.get()
        ?.let { isLiteRtLmPreviousInvocationStillProcessing(listOf(it)) }
        ?: false
    return listOf(
        "[DEV診断: GPU prefill probe]",
        "probe_requested=true",
        "probe_enabled=true",
        "probe_run_started=${state.runStarted.get()}",
        "probe_run_finished=${state.runFinished.get()}",
        "probe_run_timed_out=${state.runTimedOut.get()}",
        "probe_skipped_normal_generate=${state.request.skippedNormalGenerate}",
        "probe_isolated_engine_used=${state.request.isolatedEngineUsed}",
        "probe_shared_engine_used=${state.request.sharedEngineUsed}",
        "probe_prompt_variant=${resolveGpuPrefillProbePromptVariant(state.request.prompt)}",
        "probe_prompt_length_chars=${state.request.prompt.length}",
        "probe_max_tokens=${state.request.maxTokens}",
        "probe_sampler_enabled=${state.request.samplerEnabled}",
        "probe_cache_dir_mode=${state.request.cacheDirMode}",
        "probe_engine_config_started=${state.engineConfigStarted.get()}",
        "probe_engine_config_finished=${state.engineConfigFinished.get()}",
        "probe_engine_initialize_started=${state.engineInitializeStarted.get()}",
        "probe_engine_initialize_finished=${state.engineInitializeFinished.get()}",
        "probe_conversation_create_started=${state.conversationCreateStarted.get()}",
        "probe_conversation_create_finished=${state.conversationCreateFinished.get()}",
        "probe_generate_started=${state.generateStarted.get()}",
        "probe_first_token_received=${state.firstTokenReceived.get()}",
        "probe_generate_before_first_token_elapsed_ms=$generateBeforeFirstTokenElapsedMs",
        "probe_timeout_stage=$timeoutStage",
        "probe_failure_stage=$failureStage",
        "probe_exception_class=${state.exceptionClass.get() ?: "none"}",
        "probe_exception_message=${escapeGpuPrefillProbeValue(state.exceptionMessage.get() ?: "none")}",
        "probe_exception_cause_class=${exceptionExpansion.failureCauseClass}",
        "probe_exception_cause_message=${escapeGpuPrefillProbeValue(causeMessageRaw)}",
        "probe_exception_cause_message_raw=${escapeGpuPrefillProbeValue(causeMessageRaw)}",
        "probe_exception_cause_message_sanitized=${sanitizeGpuLiteRtFailureMessage(causeMessageRaw)}",
        "probe_exception_root_cause_class=${exceptionExpansion.failureRootCauseClass}",
        "probe_exception_root_cause_message=${escapeGpuPrefillProbeValue(exceptionExpansion.failureRootCauseMessage)}",
        "probe_exception_chain=${escapeGpuPrefillProbeValue(exceptionExpansion.exceptionChain)}",
        "probe_reflection_target_exception_class=${exceptionExpansion.reflectionTargetExceptionClass}",
        "probe_reflection_target_exception_message=${escapeGpuPrefillProbeValue(exceptionExpansion.reflectionTargetExceptionMessage)}",
        "probe_reflection_target_exception_root_cause_class=${exceptionExpansion.reflectionTargetExceptionRootCauseClass}",
        "probe_reflection_target_exception_root_cause_message=${escapeGpuPrefillProbeValue(exceptionExpansion.reflectionTargetExceptionRootCauseMessage)}",
        "probe_result_text_length=${resultText.length}",
        "probe_result_text_head=${escapeGpuPrefillProbeValue(resultText.take(80).ifBlank { "none" })}",
        "probe_stale_callback_ignored=${state.staleCallbackIgnored.get()}",
        "probe_cleanup_started=${state.cleanupStarted.get()}",
        "probe_cleanup_finished=${state.cleanupFinished.get()}",
        "probe_cleanup_result=${state.cleanupResult.get()}",
        "probe_invalidated_held_engine=${state.request.invalidatesHeldEngine}",
        "probe_normal_generate_blocked_reason=probe_opt_in_runs_without_normal_generate",
        "previous_invocation_still_processing_detected=$previousInvocationStillProcessing",
        "probe_use_held_engine_requested=${state.request.usedHeldEngine}",
        "probe_used_held_engine=${state.request.usedHeldEngine}",
        "probe_held_engine_present_before=${state.request.heldEnginePresentBefore}",
        "probe_held_engine_acquire_result=${if (state.request.usedHeldEngine) "acquired" else "not_requested"}",
        "probe_held_engine_generate_started=${state.generateStarted.get()}",
        "probe_held_engine_first_token_received=${state.firstTokenReceived.get()}",
        "probe_held_engine_failure_stage=${if (state.request.usedHeldEngine) failureStage else "not_requested"}",
        "probe_held_engine_timeout_stage=${if (state.request.usedHeldEngine) timeoutStage else "not_requested"}",
        "probe_held_engine_invalidated_after=${state.request.invalidatesHeldEngine}",
        "normal_gpu_last_known_stage=${state.request.normalGpuLastKnownStage}",
        "normal_gpu_can_initialize_with_held_engine_hint=${state.request.heldEnginePresentBefore}",
        "isolated_gpu_engine_initialize_failed_hint=${timeoutStage == "engine_initialize" && state.exceptionClass.get() != null}",
        "gpu_litert_executor_error_file=${classification.executorErrorFile}",
        "gpu_litert_executor_error_line=${classification.executorErrorLine}",
        "gpu_litert_compiled_model_error_file=${classification.compiledModelErrorFile}",
        "gpu_litert_compiled_model_error_line=${classification.compiledModelErrorLine}",
        "gpu_engine_initialize_internal_error_detected=${classification.engineInitializeInternalErrorDetected}",
        "gpu_compiled_model_creation_failed=${classification.compiledModelCreationFailed}",
        "gpu_failure_interpretation=${classification.interpretation}",
        "probe_elapsed_ms=$elapsedMs",
    ).joinToString("\n")
}

internal fun resolveGpuPrefillProbeExceptionFailureStage(
    timeoutStage: String,
    exceptionClass: String?,
): String =
    when {
        timeoutStage == "engine_initialize" &&
            exceptionClass == "java.lang.reflect.InvocationTargetException" ->
            "gpu_prefill_probe_engine_initialize_invocation_target_exception"
        timeoutStage == "engine_initialize" -> "gpu_prefill_probe_engine_initialize_exception"
        else -> "gpu_prefill_probe_exception"
    }

internal fun buildGpuPrefillProbeDisabledDiagnosticsText(reason: String): String =
    listOf(
        "[DEV診断: GPU prefill probe]",
        "probe_requested=false",
        "probe_enabled=false",
        "probe_disabled_reason=${escapeGpuPrefillProbeValue(reason)}",
    ).joinToString("\n")

internal fun buildGpuPrefillProbeStartBlockedDiagnosticsText(
    reason: String,
    useHeldEngineRequested: Boolean = false,
    heldEnginePresentBefore: Boolean = false,
    heldEngineAcquireResult: String = "not_attempted",
): String =
    listOf(
        "[DEV診断: GPU prefill probe]",
        "probe_requested=true",
        "probe_enabled=true",
        "probe_run_started=false",
        "probe_run_finished=false",
        "probe_run_timed_out=false",
        "probe_skipped_normal_generate=true",
        "probe_isolated_engine_used=false",
        "probe_shared_engine_used=false",
        "probe_timeout_stage=unknown",
        "probe_failure_stage=gpu_prefill_probe_start_blocked",
        "probe_stale_callback_ignored=false",
        "probe_cleanup_started=false",
        "probe_cleanup_finished=false",
        "probe_cleanup_result=not_started",
        "probe_invalidated_held_engine=false",
        "probe_start_blocked_reason=${escapeGpuPrefillProbeValue(reason)}",
        "probe_normal_generate_blocked_reason=${escapeGpuPrefillProbeValue(reason)}",
        "previous_invocation_still_processing_detected=false",
        "probe_use_held_engine_requested=$useHeldEngineRequested",
        "probe_used_held_engine=false",
        "probe_held_engine_present_before=$heldEnginePresentBefore",
        "probe_held_engine_acquire_result=${escapeGpuPrefillProbeValue(heldEngineAcquireResult)}",
        "probe_held_engine_generate_started=false",
        "probe_held_engine_first_token_received=false",
        "probe_held_engine_failure_stage=gpu_prefill_probe_start_blocked",
        "probe_held_engine_timeout_stage=unknown",
        "probe_held_engine_invalidated_after=false",
        "normal_gpu_last_known_stage=normal_generate_skipped_before_start",
        "normal_gpu_can_initialize_with_held_engine_hint=$heldEnginePresentBefore",
        "isolated_gpu_engine_initialize_failed_hint=false",
        "gpu_litert_executor_error_file=unavailable",
        "gpu_litert_executor_error_line=unavailable",
        "gpu_litert_compiled_model_error_file=unavailable",
        "gpu_litert_compiled_model_error_line=unavailable",
        "gpu_engine_initialize_internal_error_detected=false",
        "gpu_compiled_model_creation_failed=false",
        "gpu_failure_interpretation=unknown",
        "probe_elapsed_ms=0",
    ).joinToString("\n")

internal fun resolveGpuPrefillProbeTimeoutStage(
    engineConfigStarted: Boolean,
    engineConfigFinished: Boolean,
    engineInitializeStarted: Boolean,
    engineInitializeFinished: Boolean,
    conversationCreateStarted: Boolean,
    conversationCreateFinished: Boolean,
    generateStarted: Boolean,
    firstTokenReceived: Boolean,
): String =
    when {
        engineConfigStarted && !engineConfigFinished -> "engine_config_build"
        engineConfigFinished && !engineInitializeStarted -> "engine_constructor"
        engineInitializeStarted && !engineInitializeFinished -> "engine_initialize"
        conversationCreateStarted && !conversationCreateFinished -> "conversation_create"
        generateStarted && !firstTokenReceived -> "generate_before_first_token"
        generateStarted && firstTokenReceived -> "generate_after_first_token"
        else -> "unknown"
    }

private fun resolveGpuPrefillProbePromptVariant(prompt: String): String =
    when (prompt) {
        "hi" -> "single_ascii"
        "." -> "single_token_like"
        else -> "empty_or_minimal"
    }

private fun escapeGpuPrefillProbeValue(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ').trim().ifBlank { "none" }
