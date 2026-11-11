package io.github.ninbyo02.lami.tts

/**
 * Estimates the final queued frame's deadline before AudioTrack.stop() resets
 * its head. All timestamps must share the System.nanoTime() clock domain.
 */
internal class LamiPcmEosDrainClock(
    writtenFrames: Int,
    headFrames: Int,
    sampleRate: Int,
    stoppedAtNs: Long,
    tailGuardNs: Long,
    timestampFrame: Long? = null,
    timestampNs: Long? = null,
) {
    val deadlineNs: Long

    init {
        require(writtenFrames >= 0 && headFrames in 0..writtenFrames)
        require(sampleRate > 0)
        require(writtenFrames.toLong() <= sampleRate.toLong() * MAXIMUM_DURATION_SECONDS)
        require(tailGuardNs in 0L..NANOSECONDS_PER_SECOND)

        val fromHead = addSaturated(stoppedAtNs, frameDurationNs(writtenFrames.toLong() - headFrames, sampleRate))
        val fromTimestamp = if (timestampFrame != null && timestampNs != null &&
            timestampFrame in 0L..writtenFrames.toLong() && timestampNs >= 0 &&
            timestampNs <= addSaturated(stoppedAtNs, MAXIMUM_FUTURE_TIMESTAMP_NS) &&
            (timestampNs >= stoppedAtNs || stoppedAtNs - timestampNs <= MAXIMUM_TIMESTAMP_AGE_NS)
        ) {
            addSaturated(timestampNs, frameDurationNs(writtenFrames.toLong() - timestampFrame, sampleRate))
        } else null
        deadlineNs = addSaturated(maxOf(fromHead, fromTimestamp ?: fromHead), tailGuardNs)
    }

    fun remainingNs(nowNs: Long): Long {
        if (nowNs >= deadlineNs) return 0L
        val remaining = deadlineNs - nowNs
        return if (remaining < 0) Long.MAX_VALUE else remaining
    }

    private companion object {
        const val NANOSECONDS_PER_SECOND = 1_000_000_000L
        const val MAXIMUM_DURATION_SECONDS = 180L
        const val MAXIMUM_FUTURE_TIMESTAMP_NS = 100_000_000L
        const val MAXIMUM_TIMESTAMP_AGE_NS = 2_000_000_000L

        fun frameDurationNs(frames: Long, rate: Int): Long {
            // frames is bounded by Int.MAX_VALUE, so this product fits Long.
            val numerator = frames * NANOSECONDS_PER_SECOND
            return numerator / rate + if (numerator % rate == 0L) 0L else 1L
        }

        fun addSaturated(timeNs: Long, durationNs: Long): Long =
            if (timeNs > Long.MAX_VALUE - durationNs) Long.MAX_VALUE else timeNs + durationNs
    }
}
