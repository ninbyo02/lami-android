package io.github.ninbyo02.lami.tts

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Windowed-sinc resampling. Keeps 44.1 kHz models out of the 24 kHz player contract. */
internal object LamiPcmResampler {
    data class Result(val samples: FloatArray, val clippedSamples: Int)

    fun resample(input: FloatArray, inputRate: Int, outputRate: Int): Result {
        require(inputRate in 8_000..192_000 && outputRate in 8_000..192_000)
        require(input.isNotEmpty() && input.size.toLong() <= inputRate * 30L)
        require(input.all { it.isFinite() && it in -1f..1f })
        if (inputRate == outputRate) return Result(input.copyOf(), 0)
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val divisor = gcd(inputRate, outputRate)
        val step = inputRate / divisor
        val phases = outputRate / divisor
        require(phases <= 192_000)
        val radius = 64
        val cutoff = 0.90 * min(1.0, outputRate.toDouble() / inputRate)
        // Only 80 fractional phases are needed for 44,100 -> 24,000.
        val weights = Array(phases) { phase ->
            val fraction = phase.toDouble() / phases
            DoubleArray(radius * 2) { tap ->
                val x = tap - radius + 1 - fraction
                val window = if (abs(x) >= radius) 0.0 else
                    0.42 + 0.5 * cos(PI * x / radius) + 0.08 * cos(2 * PI * x / radius)
                val z = PI * cutoff * x
                cutoff * (if (abs(z) < 1e-12) 1.0 else sin(z) / z) * window
            }
        }
        val count = (input.size.toDouble() * outputRate / inputRate).roundToInt().coerceAtLeast(1)
        val output = FloatArray(count)
        var clipped = 0
        for (i in output.indices) {
            val position = i.toLong() * step
            val base = (position / phases).toInt()
            val coefficients = weights[(position % phases).toInt()]
            var sum = 0.0
            var normalizer = 0.0
            for (tap in coefficients.indices) {
                val source = base + tap - radius + 1
                if (source in input.indices) {
                    sum += input[source] * coefficients[tap]
                    normalizer += coefficients[tap]
                }
            }
            val value = if (abs(normalizer) > 1e-12) (sum / normalizer).toFloat() else 0f
            if (value < -1f || value > 1f) clipped++
            output[i] = value.coerceIn(-1f, 1f)
        }
        return Result(output, clipped)
    }
}
