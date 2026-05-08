package io.github.ninbyo02.lami.ui.screens.home

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
): TtsMemoryReleaseDecision {
    if (snapshot.lowMemory) {
        return TtsMemoryReleaseDecision(
            shouldReleaseHeldEngine = true,
            reason = "low-memory",
        )
    }

    return TtsMemoryReleaseDecision(
        shouldReleaseHeldEngine = false,
        reason = "memory-ok",
    )
}

internal fun kbToMb(kb: Int): Int = (kb.toLong() / KB_PER_MB).toInt()
