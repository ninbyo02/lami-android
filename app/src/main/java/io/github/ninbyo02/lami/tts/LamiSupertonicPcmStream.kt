package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Bridges synchronous sherpa JNI callbacks to a suspending PCM consumer. The
 * single waiting chunk bounds memory and applies backpressure to native decoding.
 * Cancellation discards callbacks but waits for JNI to finish before returning.
 */
internal suspend fun <T> streamSupertonicNativePcm(
    nativeDispatcher: CoroutineDispatcher,
    generate: suspend (callback: (FloatArray) -> Unit) -> T,
    consume: suspend (FloatArray) -> Unit,
): T = coroutineScope {
    data class Chunk(val samples: FloatArray, val consumed: CompletableDeferred<Unit>)
    val chunks = Channel<Chunk>(capacity = 1, onUndeliveredElement = { it.consumed.cancel() })
    val producer = async(nativeDispatcher) {
        try {
            generate { samples ->
                if (samples.isNotEmpty()) {
                    // JNI owns the callback array only until return. Never throw
                    // through JNI, including when cancellation closes the channel.
                    runCatching {
                        val chunk = Chunk(samples.copyOf(), CompletableDeferred())
                        runBlocking {
                            chunks.send(chunk)
                            // The consumer acknowledges after its suspending emit.
                            // Its first emit waits for AudioTrack.start, so native
                            // decoding cannot finish before the first audio starts.
                            chunk.consumed.await()
                        }
                    }
                }
            }
        } finally {
            chunks.close()
        }
    }
    try {
        for (chunk in chunks) {
            try {
                consume(chunk.samples)
            } finally {
                chunk.consumed.complete(Unit)
            }
        }
        producer.await()
    } finally {
        // Release a producer blocked in send before waiting for the native call.
        chunks.cancel()
        withContext(NonCancellable) { producer.join() }
    }
}
