package io.github.ninbyo02.lami.tts

/** Codec generation cadence is independent of a decoder catching up in bursts. */
internal class LamiPcmGenerationPace(
    private val sampleRate: Int = 24_000,
    private val normalReserveMs: Int = 640,
    private val slowReserveMs: Int = 2_400,
) {
    init { require(sampleRate > 0 && normalReserveMs > 0 && slowReserveMs >= normalReserveMs) }

    @Volatile var minimumBufferMs: Int = normalReserveMs
        private set
    private var previousSamples = 0
    private var previousAtMs: Long? = null

    @Synchronized fun prefixProduced(cumulativeSamples: Int, atMs: Long) {
        require(cumulativeSamples > previousSamples && atMs >= 0)
        previousAtMs?.let { previous ->
            require(atMs >= previous)
            val newSamples = cumulativeSamples - previousSamples
            val productionMs = atMs - previous
            // A 15% margin tolerates scheduling jitter. Once generation is
            // slower than playback, later decoder bursts must not shrink the
            // reserve again within this utterance.
            if (productionMs.toDouble() * sampleRate > newSamples * 1_000.0 * 1.15) {
                minimumBufferMs = slowReserveMs
            }
        }
        previousSamples = cumulativeSamples
        previousAtMs = atMs
    }
}
