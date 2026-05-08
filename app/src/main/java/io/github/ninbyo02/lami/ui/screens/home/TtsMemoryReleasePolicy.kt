package io.github.ninbyo02.lami.ui.screens.home

private const val DEFAULT_MIN_AVAILABLE_MEMORY_MB = 1_024L
private const val DEFAULT_MIN_AVAILABLE_THRESHOLD_MULTIPLIER = 4L
private const val DEFAULT_MAX_APP_PSS_FOR_TTS_MB = 700L
private const val KB_PER_MB = 1_024L

internal data class TtsMemorySnapshot(
    val lowMemory: Boolean,
    val availableMemoryMb: Long?,
    val thresholdMemoryMb: Long?,
    val appTotalPssMb: Int?,
    val appNativePssMb: Int?,
)

internal data class TtsMemoryReleaseDecision(
    val shouldReleaseHeldEngine: Boolean,
    val reason: String,
)

internal fun decideHeldEngineReleaseForTts(
    snapshot: TtsMemorySnapshot,
    minAvailableMemoryMb: Long = DEFAULT_MIN_AVAILABLE_MEMORY_MB,
    minAvailableThresholdMultiplier: Long = DEFAULT_MIN_AVAILABLE_THRESHOLD_MULTIPLIER,
    maxAppPssForTtsMb: Long = DEFAULT_MAX_APP_PSS_FOR_TTS_MB,
): TtsMemoryReleaseDecision {
    if (snapshot.lowMemory) {
        return TtsMemoryReleaseDecision(
            shouldReleaseHeldEngine = true,
            reason = "low-memory",
        )
    }

    val thresholdFloorMb = snapshot.thresholdMemoryMb
        ?.times(minAvailableThresholdMultiplier)
        ?: 0L
    val requiredAvailableMb = maxOf(minAvailableMemoryMb, thresholdFloorMb)
    val availableMb = snapshot.availableMemoryMb
    if (availableMb != null && availableMb < requiredAvailableMb) {
        return TtsMemoryReleaseDecision(
            shouldReleaseHeldEngine = true,
            reason = "available-memory-low:${availableMb}MB<${requiredAvailableMb}MB",
        )
    }

    val appPssMb = snapshot.appTotalPssMb?.toLong()
    if (appPssMb != null && appPssMb >= maxAppPssForTtsMb) {
        return TtsMemoryReleaseDecision(
            shouldReleaseHeldEngine = true,
            reason = "app-pss-high:${appPssMb}MB>=${maxAppPssForTtsMb}MB",
        )
    }

    return TtsMemoryReleaseDecision(
        shouldReleaseHeldEngine = false,
        reason = "memory-ok",
    )
}

internal fun kbToMb(kb: Int): Int = (kb.toLong() / KB_PER_MB).toInt()
