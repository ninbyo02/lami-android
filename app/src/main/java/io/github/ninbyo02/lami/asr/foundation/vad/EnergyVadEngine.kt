package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import kotlin.math.sqrt

/**
 * Local-only PCM energy VAD used by the first offline Android integration.
 *
 * It emits the strict sequence required by [VadEndpointPolicy]: silence, one speech start,
 * speech frames, one speech end, then silence. Threshold hysteresis avoids rapid toggling.
 */
class EnergyVadEngine(
    private val startThresholdRms: Double = DEFAULT_START_THRESHOLD_RMS,
    private val continueThresholdRms: Double = DEFAULT_CONTINUE_THRESHOLD_RMS,
) : VadEngine {
    private var inSpeech = false
    private var closed = false

    init {
        require(startThresholdRms > 0)
        require(continueThresholdRms > 0)
        require(continueThresholdRms <= startThresholdRms)
    }

    override fun accept(frame: Pcm16Frame): VadEvent {
        check(!closed) { "VAD is closed" }
        val rms = rootMeanSquare(frame.copySamples())
        return when {
            !inSpeech && rms >= startThresholdRms -> {
                inSpeech = true
                VadEvent.SpeechStarted
            }
            inSpeech && rms >= continueThresholdRms -> VadEvent.Speech
            inSpeech -> {
                inSpeech = false
                VadEvent.SpeechEnded
            }
            else -> VadEvent.Silence
        }
    }

    override fun reset() {
        check(!closed) { "VAD is closed" }
        inSpeech = false
    }

    override fun close() {
        if (closed) return
        closed = true
        inSpeech = false
    }

    private fun rootMeanSquare(samples: ShortArray): Double {
        var sum = 0.0
        samples.forEach { sample ->
            val value = sample.toDouble()
            sum += value * value
        }
        samples.fill(0)
        return sqrt(sum / samples.size)
    }

    private companion object {
        const val DEFAULT_START_THRESHOLD_RMS = 650.0
        const val DEFAULT_CONTINUE_THRESHOLD_RMS = 350.0
    }
}
