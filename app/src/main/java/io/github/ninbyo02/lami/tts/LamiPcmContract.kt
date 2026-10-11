package io.github.ninbyo02.lami.tts

/** Reject invalid decoder output before submitting any audio. */
internal object LamiPcmContract {
    const val SAMPLE_RATE = 24_000
    fun validate(samples: FloatArray) {
        require(samples.isNotEmpty()) { "Decoder returned empty PCM" }
        require(samples.size <= SAMPLE_RATE * 30) { "Decoder PCM exceeds diagnostic limit" }
        require(samples.all { it.isFinite() && it in -1f..1f }) { "Invalid decoder PCM" }
        require(samples.any { kotlin.math.abs(it) > 0.00001f }) { "Decoder returned silence" }
    }
}
