package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1ArtifactFreshnessResult(
    val status: Status,
    val reasonCode: String,
    val artifactFresh: Boolean,
) {
    enum class Status {
        FRESH,
        STALE,
        STALE_OR_UNKNOWN,
        STALE_OR_INVALID,
    }
}

object DevOnlyNpuPhaseH1ArtifactFreshness {
    const val DEFAULT_FRESHNESS_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    fun evaluate(
        artifactTimestampMs: Long?,
        nowMs: Long,
        freshnessWindowMs: Long = DEFAULT_FRESHNESS_WINDOW_MS,
    ): DevOnlyNpuPhaseH1ArtifactFreshnessResult =
        when {
            artifactTimestampMs == null -> result(
                status = DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE_OR_UNKNOWN,
                reasonCode = "stale_or_unknown",
            )
            artifactTimestampMs > nowMs -> result(
                status = DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE_OR_INVALID,
                reasonCode = "stale_or_invalid",
            )
            nowMs - artifactTimestampMs > freshnessWindowMs -> result(
                status = DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE,
                reasonCode = "stale_artifact",
            )
            else -> DevOnlyNpuPhaseH1ArtifactFreshnessResult(
                status = DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.FRESH,
                reasonCode = "fresh",
                artifactFresh = true,
            )
        }

    fun timestampFrom(values: Map<String, String>): Long? =
        listOf(
            "artifact_timestamp_ms",
            "artifact_timestamp",
            "synced_at",
            "created_at",
        ).firstNotNullOfOrNull { key ->
            values[key]?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        }

    private fun result(
        status: DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status,
        reasonCode: String,
    ): DevOnlyNpuPhaseH1ArtifactFreshnessResult =
        DevOnlyNpuPhaseH1ArtifactFreshnessResult(
            status = status,
            reasonCode = reasonCode,
            artifactFresh = false,
        )
}
