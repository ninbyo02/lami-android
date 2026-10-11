package io.github.ninbyo02.lami.assistant

import kotlin.math.abs
import kotlin.math.sqrt

internal data class BluetoothMicSamples(val count: Int, val nonZero: Int, val peak: Int, val rms: Double) {
    companion object {
        fun measure(samples: ShortArray): BluetoothMicSamples {
            val peak = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
            val sum = samples.sumOf { it.toDouble() * it.toDouble() }
            return BluetoothMicSamples(samples.size, samples.count { it != 0.toShort() }, peak,
                if (samples.isEmpty()) 0.0 else sqrt(sum / samples.size))
        }

        fun playbackGain(samples: ShortArray): Double {
            val level = measure(samples)
            if (level.peak == 0) return 1.0
            return minOf(3000.0 / level.rms, 28672.0 / level.peak).coerceIn(1.0, 16.0)
        }

        fun amplifiedCopy(samples: ShortArray, gain: Double): ShortArray =
            ShortArray(samples.size) { i ->
                (samples[i] * gain.coerceIn(1.0, 16.0)).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
    }
}
