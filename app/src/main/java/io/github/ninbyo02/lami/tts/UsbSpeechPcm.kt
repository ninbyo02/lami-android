package io.github.ninbyo02.lami.tts

import kotlin.math.roundToInt

internal object UsbSpeechPcm {
    fun stereo48k(samples: ShortArray, rate: Int, channels: Int): ShortArray {
        require(rate in 8_000..48_000 && channels in 1..2)
        require(samples.isNotEmpty() && samples.size % channels == 0)
        if (rate == 48_000 && channels == 2) return samples
        val frames = samples.size / channels
        val outputFrames = (frames * 48_000L / rate).toInt()
        require(outputFrames in 1..2_880_000)
        val output = ShortArray(outputFrames * 2)
        for (frame in 0 until outputFrames) {
            val position = frame * rate.toDouble() / 48_000
            val first = position.toInt().coerceAtMost(frames - 1)
            val next = (first + 1).coerceAtMost(frames - 1)
            val fraction = position - first
            val out = frame * 2
            if (channels == 1) {
                val sample = interpolate(samples, first, next, 0, 1, fraction)
                output[out] = sample
                output[out + 1] = sample
            } else {
                output[out] = interpolate(samples, first, next, 0, 2, fraction)
                output[out + 1] = interpolate(samples, first, next, 1, 2, fraction)
            }
        }
        return output
    }

    private fun interpolate(
        samples: ShortArray,
        first: Int,
        next: Int,
        channel: Int,
        channels: Int,
        fraction: Double,
    ): Short {
        val a = samples[first * channels + channel].toDouble()
        val b = samples[next * channels + channel].toDouble()
        return (a + (b - a) * fraction).roundToInt().coerceIn(-32768, 32767).toShort()
    }
}
