package io.github.ninbyo02.lami.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Carries partial source frames and the interpolation phase across callback boundaries. */
internal class AndroidTtsPcmConverter(private val source: AndroidTtsPcmFormat, usbStereo48k: Boolean) {
    val outputFormat = AndroidTtsPcmFormat(if (usbStereo48k) 48_000 else source.rate,
        AndroidTtsPcmFormat.PCM_16, if (usbStereo48k) 2 else source.channels)
    private val resampling = source.rate != outputFormat.rate
    private var remainder = byteArrayOf()
    private var hasPrevious = false
    private var previousLeft: Short = 0
    private var previousRight: Short = 0
    private var inputFrames = 0L
    private var outputFrames = 0L
    var lastInputSampleCount: Int = 0
        private set

    fun convert(bytes: ByteArray): ShortArray {
        val input = if (remainder.isEmpty()) bytes else remainder + bytes
        val completeBytes = input.size - input.size % source.frameBytes
        remainder = input.copyOfRange(completeBytes, input.size)
        lastInputSampleCount = completeBytes / source.sampleBytes
        val decoded = ShortArray(lastInputSampleCount)
        val buffer = ByteBuffer.wrap(input, 0, completeBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (index in decoded.indices) decoded[index] = when (source.encoding) {
            AndroidTtsPcmFormat.PCM_8 -> (((buffer.get().toInt() and 255) - 128) * 256).toShort()
            AndroidTtsPcmFormat.PCM_16 -> buffer.short
            else -> {
                val value = buffer.float
                require(value.isFinite() && value in -1f..1f) { "Invalid Android TTS float PCM" }
                (value * 32767f).roundToInt().toShort()
            }
        }
        val frames = decoded.size / source.channels
        if (!resampling) {
            inputFrames += frames
            outputFrames += frames
            if (source.channels == outputFormat.channels) return decoded
            return ShortArray(frames * 2) { decoded[it / 2] }
        }
        val output = ShortArray(((frames + 2) * outputFormat.rate.toLong() / source.rate + 2).toInt() * 2)
        var size = 0
        for (frame in 0 until frames) {
            val left = decoded[frame * source.channels]
            val right = decoded[frame * source.channels + if (source.channels == 1) 0 else 1]
            if (hasPrevious) {
                while (outputFrames * source.rate < inputFrames * outputFormat.rate) {
                    val fraction = (outputFrames * source.rate - (inputFrames - 1) * outputFormat.rate).toDouble() / outputFormat.rate
                    output[size++] = interpolate(previousLeft, left, fraction)
                    output[size++] = interpolate(previousRight, right, fraction)
                    outputFrames++
                }
            }
            previousLeft = left
            previousRight = right
            hasPrevious = true
            inputFrames++
        }
        return output.copyOf(size)
    }

    fun finish(): ShortArray {
        require(remainder.isEmpty()) { "Android TTS ended with a partial PCM frame" }
        if (!resampling) return shortArrayOf()
        val expectedFrames = inputFrames * outputFormat.rate / source.rate
        if (!hasPrevious) return shortArrayOf()
        val tail = ShortArray((expectedFrames - outputFrames).toInt() * 2) { if (it % 2 == 0) previousLeft else previousRight }
        outputFrames = expectedFrames
        return tail
    }

    private fun interpolate(a: Short, b: Short, fraction: Double): Short =
        (a.toDouble() + (b.toDouble() - a) * fraction).roundToInt().coerceIn(-32768, 32767).toShort()
}
