package io.github.ninbyo02.lami.tts

import kotlin.math.ceil

/** Startup safety for slow Qwen generation; codec EOS makes the remaining PCM finite. */
internal class LamiQwenStartupBudget(private val isSlowGeneration: () -> Boolean) {
    companion object {
        private const val SAMPLE_RATE = 24_000
        private const val SAMPLES_PER_CODEC_FRAME = 1_920
        private const val MAX_CODEC_SAMPLES = 256 * SAMPLES_PER_CODEC_FRAME
        private const val MAX_BUFFER_SAMPLES = 30 * SAMPLE_RATE
        private const val PREDICTION_MARGIN = 1.20
        private const val SAFETY_MARGIN_MS = 160.0
    }

    private data class Decode(val samples: Int, val startedAtMs: Long, val ordinal: Int)
    private var inFlight: Decode? = null
    private var decodeCalls = 0
    private var decodedSamples = 0
    private var finalSamples: Int? = null
    private var latestWarmMsPerSample: Double? = null

    @Synchronized
    fun codecCompleted(totalSamples: Int) {
        validatePrefixSamples(totalSamples)
        require(totalSamples >= decodedSamples && totalSamples >= (inFlight?.samples ?: 0))
        require(finalSamples == null || finalSamples == totalSamples) { "Codec EOS changed the final sample count" }
        finalSamples = totalSamples
    }

    @Synchronized
    fun decodeStarted(prefixSamples: Int, atMs: Long) {
        validatePrefixSamples(prefixSamples)
        require(atMs >= 0 && inFlight == null && prefixSamples > decodedSamples)
        require(finalSamples == null || prefixSamples <= checkNotNull(finalSamples))
        inFlight = Decode(prefixSamples, atMs, decodeCalls++)
    }

    @Synchronized
    fun decodeCompleted(prefixSamples: Int, atMs: Long) {
        val current = checkNotNull(inFlight) { "Decoder completion has no matching start" }
        require(prefixSamples == current.samples && atMs >= current.startedAtMs)
        val duration = atMs - current.startedAtMs
        // The first forward includes cold delegate/kernel/page initialization,
        // even if the module itself was preloaded. It is not a warm rate sample.
        if (current.ordinal > 0 && duration > 0) {
            latestWarmMsPerSample = duration.toDouble() / prefixSamples
        }
        decodedSamples = prefixSamples
        inFlight = null
    }

    @Synchronized
    fun canStart(bufferedSamples: Int, nowMs: Long): Boolean {
        require(bufferedSamples in 0..MAX_BUFFER_SAMPLES && nowMs >= 0)
        if (!isSlowGeneration()) return true
        val total = finalSamples ?: return false
        if (decodedSamples >= total) return true
        val warmRate = latestWarmMsPerSample ?: return false

        var remainingMs = 0.0
        val current = inFlight
        if (current != null) {
            if (nowMs < current.startedAtMs) return false
            val expectedMs = warmRate * current.samples
            val elapsedMs = (nowMs - current.startedAtMs).toDouble()
            // An overrun disproves this estimate. Do not interpret an unfinished
            // native call as zero remaining work and prematurely release audio.
            if (elapsedMs > expectedMs * PREDICTION_MARGIN) return false
            remainingMs += (expectedMs - elapsedMs).coerceAtLeast(0.0)
        }
        if (current == null || current.samples < total) {
            // Coalescing can leave one in-flight prefix plus the final prefix.
            // The latter is a cumulative decode: charge its full cost, not only
            // the samples beyond the previously decoded waveform.
            remainingMs += warmRate * total
        }
        val requiredMs = ceil(remainingMs * PREDICTION_MARGIN) + SAFETY_MARGIN_MS
        return bufferedSamples * 1_000.0 / SAMPLE_RATE >= requiredMs
    }

    private fun validatePrefixSamples(samples: Int) {
        require(samples in 2 * SAMPLES_PER_CODEC_FRAME..MAX_CODEC_SAMPLES && samples % SAMPLES_PER_CODEC_FRAME == 0)
    }
}
