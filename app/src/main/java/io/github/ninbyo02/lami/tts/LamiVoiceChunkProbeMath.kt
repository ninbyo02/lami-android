package io.github.ninbyo02.lami.tts

import kotlin.math.sqrt

/** Numerical diagnostic only; this does not authorize releasing partial audio. */
internal object LamiVoiceChunkProbeMath {
    fun prefixCodes(values: LongArray, frames: Int, prefix: Int): LongArray {
        require(frames in 2..256 && values.size == frames * 16)
        require(prefix in 2..frames)
        return LongArray(prefix * 16) { index -> values[(index / prefix) * frames + index % prefix] }
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
