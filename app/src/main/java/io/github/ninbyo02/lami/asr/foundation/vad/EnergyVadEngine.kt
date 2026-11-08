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
    private val startConfirmationFrames: Int = 1,
    private val endConfirmationFrames: Int = 1,
) : VadEngine {
    private var inSpeech = false
    private var startFrames = 0
    private var endFrames = 0
    private var closed = false

    init {
        require(startThresholdRms.isFinite() && startThresholdRms > 0)
        require(continueThresholdRms.isFinite() && continueThresholdRms > 0)
        require(continueThresholdRms <= startThresholdRms)
        require(startConfirmationFrames in 1..10)
        require(endConfirmationFrames in 1..10)
    }

    override fun accept(frame: Pcm16Frame): VadEvent {
        check(!closed) { "VAD is closed" }
        require(frame.sampleRateHz == 16_000 && frame.sampleCount == 320)
        val rms = rootMeanSquare(frame.copySamples())
        return if (!inSpeech) {
            startFrames = if (rms >= startThresholdRms) startFrames + 1 else 0
            if (startFrames >= startConfirmationFrames) {
                inSpeech = true
                startFrames = 0
                VadEvent.SpeechStarted
            } else VadEvent.Silence
        } else {
            endFrames = if (rms < continueThresholdRms) endFrames + 1 else 0
            if (endFrames >= endConfirmationFrames) {
                inSpeech = false
                endFrames = 0
                VadEvent.SpeechEnded
            } else VadEvent.Speech
        }
    }

    override fun reset() {
        check(!closed) { "VAD is closed" }
        inSpeech = false
        startFrames = 0
        endFrames = 0
    }

    override fun close() {
        if (closed) return
        closed = true
        inSpeech = false
        startFrames = 0
        endFrames = 0
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
