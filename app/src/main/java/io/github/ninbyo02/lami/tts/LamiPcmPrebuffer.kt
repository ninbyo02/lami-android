package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

internal data class LamiPcmPrebufferConfig(
    val minimumMs: Int = 640,
    val maximumMs: Int = 2400,
    val gapSafetyMarginMs: Int = 160,
    val sourceMinimumMs: () -> Int = { minimumMs },
    val startupReady: ((Int, Long) -> Boolean)? = null,
) {
    init {
        require(minimumMs > 0 && maximumMs >= minimumMs && maximumMs <= 30_000)
        require(gapSafetyMarginMs >= 0)
    }
}

/** Optional startup reserve for 24kHz mono float PCM; it never inserts silence. */
internal object LamiPcmPrebuffer {
    private const val SAMPLES_PER_MS = 24
    private const val MAXIMUM_SAMPLES = 24_000 * 30

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun collect(
        chunks: ReceiveChannel<FloatArray>,
        config: LamiPcmPrebufferConfig,
        nowMs: () -> Long,
        onChunk: (FloatArray) -> Unit,
    ): FloatArray {
        val buffered = mutableListOf<FloatArray>()
        var totalSamples = 0
        var requiredMs = config.minimumMs.toLong()
        var previousArrivalMs: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val reserveReady = totalSamples > 0 && totalSamples >= requiredMs * SAMPLES_PER_MS
            if (reserveReady && (config.startupReady?.invoke(totalSamples, nowMs()) != false)) break
            // Codec EOS / decoder progress can make an existing reserve safe
            // without producing another PCM chunk. Re-evaluate that gate while
            // still receiving chunks normally; select never drops a winner.
            val next = if (reserveReady && config.startupReady != null) select<ChannelResult<FloatArray>?> {
                chunks.onReceiveCatching { it }
                onTimeout(20L) { null }
            } else chunks.receiveCatching()
            if (next == null) continue
            if (next.isClosed) {
                // Failed generation must not be mistaken for a short successful EOS.
                next.exceptionOrNull()?.let { throw it }
                require(totalSamples > 0) { "No PCM was generated for startup playback" }
                break
            }
            val arrivalMs = nowMs()
            val samples = next.getOrThrow()
            require(samples.isNotEmpty()) { "Empty PCM chunk" }
            require(samples.size.toLong() + totalSamples <= MAXIMUM_SAMPLES) { "Startup PCM exceeds 30 seconds" }
            require(samples.all { it.isFinite() && it in -1f..1f }) { "Invalid startup PCM" }
            val sourceRequiredMs = maxOf(config.minimumMs, config.sourceMinimumMs())
                .coerceAtMost(config.maximumMs)
            requiredMs = maxOf(requiredMs, sourceRequiredMs.toLong())
            previousArrivalMs?.let { previous ->
                val gapMs = (arrivalMs - previous).coerceAtLeast(0)
                val observedMs = (gapMs.coerceAtMost(config.maximumMs.toLong()) + config.gapSafetyMarginMs)
                    .coerceIn(config.minimumMs.toLong(), config.maximumMs.toLong())
                // A burst after a long gap must not erase the gap that caused the
                // reserve to grow. This target is local to the current playback.
                requiredMs = maxOf(requiredMs, observedMs)
            }
            previousArrivalMs = arrivalMs
            // Retain our own bytes while awaiting later chunks. Progress still
            // reports each original chunk exactly once, never the combined buffer.
            buffered += samples.copyOf()
            totalSamples += samples.size
            currentCoroutineContext().ensureActive()
            onChunk(samples)
        }
        currentCoroutineContext().ensureActive()
        return FloatArray(totalSamples).also { output ->
            var offset = 0
            for (samples in buffered) {
                samples.copyInto(output, offset)
                offset += samples.size
            }
        }
    }
}
