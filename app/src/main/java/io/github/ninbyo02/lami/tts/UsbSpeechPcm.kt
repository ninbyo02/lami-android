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
        return ShortArray(outputFrames * 2) { index ->
            val position = (index / 2) * rate.toDouble() / 48_000
            val first = position.toInt().coerceAtMost(frames - 1)
            val next = (first + 1).coerceAtMost(frames - 1)
            val channel = if (channels == 1) 0 else index % 2
            val a = samples[first * channels + channel].toDouble()
            val b = samples[next * channels + channel].toDouble()
            (a + (b - a) * (position - first)).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
