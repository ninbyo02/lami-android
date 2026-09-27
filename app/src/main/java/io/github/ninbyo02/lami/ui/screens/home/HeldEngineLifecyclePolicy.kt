package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal const val MAX_HELD_ENGINE_REUSE_COUNT = 3
private const val ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT = false

internal enum class HeldEngineLifecycleReason {
    MODEL_CHANGED,
    BACKEND_CHANGED,
    EXPLICIT_RESET,
    FATAL_ERROR,
    LOW_MEMORY,
    APP_BACKGROUNDED,
    TTS_PLAYBACK,
    BACKGROUND_TIMEOUT,
    IDLE_TIMEOUT,
    KEEP_HELD,
}

internal enum class HeldEngineLifecycleAction {
    KEEP_HELD,
    CLOSE_AND_RECREATE,
    CLEAR_ONLY,
    NO_OP,
}
internal data class HeldEngineLifecycleDecision(
    val reason: HeldEngineLifecycleReason,
    val action: HeldEngineLifecycleAction,
    val clearReason: String,
)

internal enum class GpuOnStopLifecycleAction {
    DEFER_ACTIVE_GENERATE,
    KEEP_FOREGROUND_REUSE,
    RELEASE_CONFIRMED_BACKGROUND,
}

internal fun decideHeldEngineAcquireLifecycle(
    currentEngineKey: HeldEngineKey?,
    currentModelPath: String?,
    currentUseCount: Int,
    requested: HeldEngineKey,
): HeldEngineLifecycleDecision {
    if (currentEngineKey == null) {
        return HeldEngineLifecycleDecision(
            reason = HeldEngineLifecycleReason.KEEP_HELD,
            action = HeldEngineLifecycleAction.NO_OP,
            clearReason = "keep-held",
        )
    }

    if (currentEngineKey == requested) {
        if (
            ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT &&
            currentUseCount >= MAX_HELD_ENGINE_REUSE_COUNT
        ) {
            return HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = "reuse-limit",
            )
        }
        return HeldEngineLifecycleDecision(
            reason = HeldEngineLifecycleReason.KEEP_HELD,
            action = HeldEngineLifecycleAction.KEEP_HELD,
            clearReason = "keep-held",
        )
    }

    val reason = if (currentModelPath != requested.modelPath) {
        HeldEngineLifecycleReason.MODEL_CHANGED
    } else {
        HeldEngineLifecycleReason.BACKEND_CHANGED
    }
    return HeldEngineLifecycleDecision(
        reason = reason,
        action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
        clearReason = if (reason == HeldEngineLifecycleReason.MODEL_CHANGED) {
            "model-changed"
        } else {
            "backend-changed"
        },
    )
}

internal fun resolveHeldEngineLifecycleDecision(reason: String): HeldEngineLifecycleDecision {
    val lifecycleReason = when (reason) {
        "backend-changed" -> HeldEngineLifecycleReason.BACKEND_CHANGED
        "explicit-reset" -> HeldEngineLifecycleReason.EXPLICIT_RESET
        "fatal-error" -> HeldEngineLifecycleReason.FATAL_ERROR
        "low-memory" -> HeldEngineLifecycleReason.LOW_MEMORY
        "app-backgrounded" -> HeldEngineLifecycleReason.APP_BACKGROUNDED
        "tts-playback" -> HeldEngineLifecycleReason.TTS_PLAYBACK
        "background-timeout" -> HeldEngineLifecycleReason.BACKGROUND_TIMEOUT
        "idle-timeout" -> HeldEngineLifecycleReason.IDLE_TIMEOUT
        else -> null
    }
    return if (lifecycleReason != null) {
        HeldEngineLifecycleDecision(
            reason = lifecycleReason,
            action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
            clearReason = reason,
        )
    } else {
        HeldEngineLifecycleDecision(
            reason = HeldEngineLifecycleReason.KEEP_HELD,
            action = HeldEngineLifecycleAction.CLEAR_ONLY,
            clearReason = reason,
        )
    }
}

internal fun resolveGpuOnStopLifecycleAction(
    gpuGenerateActive: Boolean,
    preferredBackend: PreferredBackendDryRunSetting,
    transientProtectionEnabled: Boolean,
    recentSuccess: Boolean,
): GpuOnStopLifecycleAction = when {
    preferredBackend != PreferredBackendDryRunSetting.GPU ->
        GpuOnStopLifecycleAction.RELEASE_CONFIRMED_BACKGROUND
    gpuGenerateActive -> GpuOnStopLifecycleAction.DEFER_ACTIVE_GENERATE
    transientProtectionEnabled && recentSuccess ->
        GpuOnStopLifecycleAction.KEEP_FOREGROUND_REUSE
    else -> GpuOnStopLifecycleAction.RELEASE_CONFIRMED_BACKGROUND
}

internal fun resolveGpuLifecycleRaceSequenceForTest(
    preferredBackend: PreferredBackendDryRunSetting,
): List<GpuOnStopLifecycleAction> = listOf(
    resolveGpuOnStopLifecycleAction(
        gpuGenerateActive = true,
        preferredBackend = preferredBackend,
        transientProtectionEnabled = false,
        recentSuccess = false,
       ),
    GpuOnStopLifecycleAction.KEEP_FOREGROUND_REUSE,
    resolveGpuOnStopLifecycleAction(
        gpuGenerateActive = false,
        preferredBackend = preferredBackend,
        transientProtectionEnabled = false,
        recentSuccess = false,
    ),
)
