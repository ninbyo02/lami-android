package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope

/** Bounded debug speech transport. Natural EOS flushes the last cumulative prefix. */
internal object LamiPcmStreamingPipeline {
    data class Result<C, P>(val codes: C, val pcm: FloatArray, val playback: P)

    suspend fun <C, P> run(
        frames: (C) -> Int,
        generate: suspend (suspend (C) -> Unit) -> C,
        decode: suspend (C) -> FloatArray,
        play: suspend (ReceiveChannel<FloatArray>) -> P,
    ): Result<C, P> = coroutineScope {
        val codes = Channel<C>(1)
        val chunks = Channel<FloatArray>(1)
        val producer = async {
            var sent = 0
            val full = generate { prefix ->
                codes.send(prefix)
                sent = frames(prefix)
            }
            if (frames(full) > sent) codes.send(full)
            codes.close()
            full
        }
        val decoder = async {
            var previous = FloatArray(0)
            for (prefix in codes) {
                val next = decode(prefix)
                chunks.send(LamiVoiceChunkProbeMath.appendedPcm(previous, next))
                previous = next
            }
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
