package io.github.ninbyo02.lami.tts

/** Reject invalid decoder output before submitting any audio. */
internal object LamiPcmContract {
    const val SAMPLE_RATE = 24_000
    const val STARTUP_PREBUFFER_MS = 50
    fun validate(samples: FloatArray) {
        require(samples.isNotEmpty()) { "Decoder returned empty PCM" }
        require(samples.size <= SAMPLE_RATE * 30) { "Decoder PCM exceeds diagnostic limit" }
        var hasAudibleSample = false
        for (sample in samples) {
            require(sample.isFinite() && sample in -1f..1f) { "Invalid decoder PCM" }
            if (!hasAudibleSample && kotlin.math.abs(sample) > 0.00001f) {
                hasAudibleSample = true
            }
        }
        require(hasAudibleSample) { "Decoder returned silence" }
    }

    fun startupPrebufferSamples(totalSamples: Int, minBufferBytes: Int): Int {
        require(totalSamples > 0) { "PCM sample count must be positive" }
        require(minBufferBytes > 0) { "AudioTrack minimum buffer must be positive" }
        val minimumBufferSamples = (minBufferBytes + Float.SIZE_BYTES - 1) / Float.SIZE_BYTES
        val targetSamples = SAMPLE_RATE * STARTUP_PREBUFFER_MS / 1000
        return maxOf(minimumBufferSamples, targetSamples).coerceAtMost(totalSamples)
    }
}
