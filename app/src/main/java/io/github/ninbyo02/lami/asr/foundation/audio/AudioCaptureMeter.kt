package io.github.ninbyo02.lami.asr.foundation.audio

import kotlin.math.log10
import kotlin.math.sqrt

data class AudioLevelSample(
    val rmsDbFs: Float,
    val peakDbFs: Float,
) {
    init {
        require(rmsDbFs in MIN_AUDIO_DBFS..0f)
        require(peakDbFs in MIN_AUDIO_DBFS..0f)
        require(rmsDbFs <= peakDbFs)
    }
}

enum class AudioInputInterruptionReason {
    CONFIGURATION_INACTIVE,
    CLIENT_SILENCED,
}

sealed interface AudioCaptureEvent {
    data object Started : AudioCaptureEvent
    data class Level(val sample: AudioLevelSample) : AudioCaptureEvent
    data class InputInterrupted(
        val reason: AudioInputInterruptionReason,
    ) : AudioCaptureEvent
    data object InputResumed : AudioCaptureEvent
    data object Stopped : AudioCaptureEvent
    data object Inactive : AudioCaptureEvent
}

fun interface AudioCaptureObserver {
    fun onEvent(event: AudioCaptureEvent)

    companion object {
        val NONE = AudioCaptureObserver { }
    }
}
internal const val MIN_AUDIO_DBFS = -120f

internal fun measureAudioLevel(samples: ShortArray): AudioLevelSample {
    if (samples.isEmpty()) {
        return AudioLevelSample(MIN_AUDIO_DBFS, MIN_AUDIO_DBFS)
    }

    var sumSquares = 0.0
    var peak = 0
    samples.forEach { sample ->
        val value = sample.toInt()
        val magnitude = if (value < 0) -value else value
        if (magnitude > peak) peak = magnitude
        sumSquares += value.toDouble() * value.toDouble()
    }

    val rms = sqrt(sumSquares / samples.size)
    return AudioLevelSample(
        rmsDbFs = amplitudeToDbFs(rms),
        peakDbFs = amplitudeToDbFs(peak.toDouble()),
    )
}

private fun amplitudeToDbFs(amplitude: Double): Float {
    if (amplitude <= 0.0) return MIN_AUDIO_DBFS
    return (20.0 * log10(amplitude / PCM_FULL_SCALE))
        .coerceIn(MIN_AUDIO_DBFS.toDouble(), 0.0)
        .toFloat()
}

private const val PCM_FULL_SCALE = 32_768.0
