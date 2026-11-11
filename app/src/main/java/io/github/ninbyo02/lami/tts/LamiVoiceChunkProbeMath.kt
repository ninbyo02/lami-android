package io.github.ninbyo02.lami.tts

import kotlin.math.sqrt

/** Numerical diagnostic only; this does not authorize releasing partial audio. */
internal object LamiVoiceChunkProbeMath {
    const val STREAMING_STARTUP_PREFIX_FRAMES = 2
    const val STREAMING_STEADY_PREFIX_INTERVAL_FRAMES = 8

    fun shouldEmitStreamingPrefix(frames: Int): Boolean {
        require(frames in 1..256)
        return frames == STREAMING_STARTUP_PREFIX_FRAMES ||
            frames % STREAMING_STEADY_PREFIX_INTERVAL_FRAMES == 0
    }

    fun prefixCodes(values: LongArray, frames: Int, prefix: Int): LongArray {
        require(frames in 2..256 && values.size == frames * 16)
        require(prefix in 2..frames)
        return LongArray(prefix * 16) { index -> values[(index / prefix) * frames + index % prefix] }
    }

    data class PrefixTolerance(val maxAbsolute: Double = 0.0, val rms: Double = 0.0) {
        init {
            require(maxAbsolute.isFinite() && rms.isFinite() && maxAbsolute >= 0.0 && rms >= 0.0)
        }
    }

    fun appendedPcm(previous: FloatArray, next: FloatArray, tolerance: PrefixTolerance = PrefixTolerance()): FloatArray {
        require(next.size > previous.size) { "PCM prefix did not advance" }
        if (previous.isNotEmpty()) {
            // Only equality is needed here; leave RMS calculation to diagnostics.
            var unchanged = true
            for (index in previous.indices) {
                require(next[index].isFinite() && previous[index].isFinite())
                if (next[index] != previous[index]) unchanged = false
            }
            if (!unchanged) {
                val difference = prefixDifference(previous, next)
                require(difference.maxAbsolute <= tolerance.maxAbsolute && difference.rms <= tolerance.rms) {
                    "Provisional PCM changed after more codes: $difference tolerance=$tolerance"
                }
            }
        }
        return next.copyOfRange(previous.size, next.size)
    }

    data class PrefixDifference(
        val samples: Int,
        val changedSamples: Int,
        val maxAbsolute: Double,
        val rms: Double,
        val firstChanged: Int,
        val lastChanged: Int,
        val maxDifferenceIndex: Int,
    )

    fun prefixDifference(previous: FloatArray, next: FloatArray): PrefixDifference {
        require(previous.isNotEmpty() && next.size >= previous.size)
        var changed = 0
        var first = -1
        var last = -1
        var maximumIndex = -1
        var maximum = 0.0
        var squared = 0.0
        for (index in previous.indices) {
            require(previous[index].isFinite() && next[index].isFinite())
            val delta = kotlin.math.abs(previous[index].toDouble() - next[index].toDouble())
            if (delta != 0.0) {
                changed++
                if (first < 0) first = index
                last = index
            }
            if (delta > maximum) { maximum = delta; maximumIndex = index }
            squared += delta * delta
        }
        return PrefixDifference(previous.size, changed, maximum, sqrt(squared / previous.size), first, last, maximumIndex)
    }

    data class Difference(val samples: Int, val maxAbsolute: Double, val rms: Double)
    fun difference(full: FloatArray, prefix: FloatArray, samples: Int): Difference {
        require(samples in 1..minOf(full.size, prefix.size))
        var maximum = 0.0
        var squared = 0.0
        for (index in 0 until samples) {
            require(full[index].isFinite() && prefix[index].isFinite())
            val delta = kotlin.math.abs(full[index].toDouble() - prefix[index].toDouble())
            maximum = maxOf(maximum, delta)
            squared += delta * delta
        }
        return Difference(samples, maximum, sqrt(squared / samples))
    }
}
