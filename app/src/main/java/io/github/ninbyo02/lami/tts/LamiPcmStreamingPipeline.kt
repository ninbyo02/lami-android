package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

/** Bounded cumulative-prefix transport. Only new, prefix-stable PCM reaches the player. */
internal object LamiPcmStreamingPipeline {
    data class Result<C, P>(val codes: C, val pcm: FloatArray, val playback: P)

    suspend fun <C, P> run(
        frames: (C) -> Int,
        generate: suspend (suspend (C) -> Unit) -> C,
        decode: suspend (C) -> FloatArray,
        play: suspend (ReceiveChannel<FloatArray>) -> P,
        coalescePendingPrefixes: Boolean = false,
        onGenerationComplete: (C) -> Unit = {},
        onPcmComplete: (FloatArray) -> Unit = {},
        onPrefixMismatch: (C, FloatArray, FloatArray) -> Unit = { _, _, _ -> },
        prefixTolerance: LamiVoiceChunkProbeMath.PrefixTolerance = LamiVoiceChunkProbeMath.PrefixTolerance(),
    ): Result<C, P> = coroutineScope {
        // A newer cumulative prefix contains every older frame. Keep just the
        // latest waiting prefix in live mode rather than re-decoding stale work.
        val codes = Channel<C>(if (coalescePendingPrefixes) Channel.CONFLATED else 1)
        val chunks = Channel<FloatArray>(1)
        val firstReceived = CompletableDeferred<Unit>()
        val producer = async {
            var sent = 0
            val full = generate { prefix ->
                val count = frames(prefix)
                require(count > sent) { "Codec prefix did not advance" }
                codes.send(prefix)
                val first = sent == 0
                sent = count
                if (coalescePendingPrefixes) {
                    // Do not let a fast producer overwrite the startup prefix
                    // before the decoder has received it, even on one thread.
                    if (first) firstReceived.await()
                    yield()
                }
            }
            val finalFrames = frames(full)
            require(finalFrames > 0 && finalFrames >= sent) { "Natural EOS truncated codec prefix" }
            onGenerationComplete(full)
            if (finalFrames > sent) codes.send(full)
            codes.close()
            full
        }
        val decoder = async {
            var previous = FloatArray(0)
            for (prefix in codes) {
                firstReceived.complete(Unit)
                val next = decode(prefix)
                val appended = try {
                    LamiVoiceChunkProbeMath.appendedPcm(previous, next, prefixTolerance)
                } catch (failure: IllegalArgumentException) {
                    runCatching { onPrefixMismatch(prefix, previous, next) }
                    throw failure
                }
                chunks.send(appended)
                // Keep samples actually released to playback as the canonical
                // prefix. Per-step tolerance must not accumulate hidden drift.
                previous = if (prefixTolerance.maxAbsolute == 0.0 && prefixTolerance.rms == 0.0) next else previous + appended
            }
            onPcmComplete(previous)
            chunks.close()
            previous
        }
        val player = async { play(chunks) }
        try {
            Result(producer.await(), decoder.await(), player.await())
        } finally {
            codes.cancel()
            chunks.cancel()
            producer.cancel()
            decoder.cancel()
            player.cancel()
        }
    }
}
