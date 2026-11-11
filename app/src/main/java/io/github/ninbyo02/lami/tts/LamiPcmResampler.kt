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

    /** Preserves the global rational phase and FIR history across arbitrary input chunks. */
    class Stream(private val inputRate: Int, private val outputRate: Int) {
        private val radius = 64
        private val step: Int
        private val phases: Int
        private val weights: Array<DoubleArray>
        private var history = FloatArray(0)
        private var historyStart = 0
        private var totalInput = 0
        private var nextOutput = 0
        private var finished = false

        init {
            require(inputRate in 8_000..192_000 && outputRate in 8_000..192_000)
            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val divisor = gcd(inputRate, outputRate)
            step = inputRate / divisor
            phases = outputRate / divisor
            val cutoff = 0.90 * min(1.0, outputRate.toDouble() / inputRate)
            weights = Array(phases) { phase ->
                val fraction = phase.toDouble() / phases
                DoubleArray(radius * 2) { tap ->
                    val x = tap - radius + 1 - fraction
                    val window = if (abs(x) >= radius) 0.0 else
                        0.42 + 0.5 * cos(PI * x / radius) + 0.08 * cos(2 * PI * x / radius)
                    val z = PI * cutoff * x
                    cutoff * (if (abs(z) < 1e-12) 1.0 else sin(z) / z) * window
                }
            }
        }

        fun append(input: FloatArray): Result {
            check(!finished) { "Resampler is already finished" }
            requireFiniteSource(input)
            require(totalInput.toLong() + input.size <= inputRate * 30L) {
                "Source speech exceeds 30 seconds: samples=${totalInput.toLong() + input.size} rate=$inputRate"
            }
            totalInput += input.size
            if (inputRate == outputRate) return copyClippedOutput(input)
            history += input
            return render(final = false)
        }

        fun finish(): Result {
            check(!finished) { "Resampler is already finished" }
            finished = true
            if (totalInput == 0 || inputRate == outputRate) return Result(FloatArray(0), 0)
            return render(final = true).also { history = FloatArray(0) }
        }

        private fun render(final: Boolean): Result {
            val target = (totalInput.toDouble() * outputRate / inputRate).roundToInt().coerceAtLeast(1)
            var end = nextOutput
            while (end < target && (final || end.toLong() * step / phases + radius < totalInput)) end++
            val output = FloatArray(end - nextOutput)
            var clipped = 0
            for (offset in output.indices) {
                val position = (nextOutput + offset).toLong() * step
                val base = (position / phases).toInt()
                val coefficients = weights[(position % phases).toInt()]
                var sum = 0.0
                var normalizer = 0.0
                for (tap in coefficients.indices) {
                    val source = base + tap - radius + 1
                    if (source in 0 until totalInput) {
                        check(source - historyStart in history.indices)
                        sum += history[source - historyStart] * coefficients[tap]
                        normalizer += coefficients[tap]
                    }
                }
                val value = if (abs(normalizer) > 1e-12) (sum / normalizer).toFloat() else 0f
                if (value < -1f || value > 1f) clipped++
                output[offset] = value.coerceIn(-1f, 1f)
            }
            nextOutput = end
            val keepFrom = ((nextOutput.toLong() * step / phases).toInt() - radius + 1).coerceAtLeast(0)
            val discard = (keepFrom - historyStart).coerceIn(0, history.size)
            if (discard > 0) {
                history = history.copyOfRange(discard, history.size)
                historyStart += discard
            }
            return Result(output, clipped)
        }
    }

    fun resample(input: FloatArray, inputRate: Int, outputRate: Int): Result {
        require(inputRate in 8_000..192_000 && outputRate in 8_000..192_000)
        require(input.isNotEmpty() && input.size.toLong() <= inputRate * 30L)
        requireFiniteSource(input)
        if (inputRate == outputRate) return copyClippedOutput(input)
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

    private fun requireFiniteSource(input: FloatArray) {
        require(input.all { it.isFinite() }) {
            "Invalid source PCM: finite=false peak=${input.maxOfOrNull { abs(it) }}"
        }
    }

    /** Match the FIR output contract when no sample-rate conversion is needed. */
    private fun copyClippedOutput(input: FloatArray): Result {
        var clipped = 0
        val output = FloatArray(input.size) { index ->
            val value = input[index]
            if (value < -1f || value > 1f) clipped++
            value.coerceIn(-1f, 1f)
        }
        return Result(output, clipped)
    }
}
