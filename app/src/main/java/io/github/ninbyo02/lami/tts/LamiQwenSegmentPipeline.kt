package io.github.ninbyo02.lami.tts

import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** One EOS-validated segment playing, at most one being prepared or waiting. */
internal object LamiQwenSegmentPipeline {
    private data class Delivery<T>(val clip: T, val lastInRequest: Boolean,
        val started: CompletableDeferred<Unit> = CompletableDeferred())

    suspend fun <T> run(
        first: T,
        remaining: (T) -> List<String>,
        prepareText: suspend (String) -> T,
        makeReady: suspend (T) -> T,
        play: suspend (T, () -> Unit) -> Boolean,
    ): Boolean {
        val requests = Channel<Unit>(1)
        requests.trySend(Unit).getOrThrow()
        requests.close()
        return try {
            runRequests(requests, prepareRequest = { first }, remaining = remaining,
                prepareText = { text, _ -> prepareText(text) }, makeReady = makeReady, play = play)
        } finally { requests.cancel() }
    }

    /** A single producer flattens consecutive requests, without nested lookahead. */
    suspend fun <R, T> runRequests(
        requests: ReceiveChannel<R>,
        prepareRequest: suspend (R) -> T,
        remaining: (T) -> List<String>,
        prepareText: suspend (String, T) -> T,
        makeReady: suspend (T) -> T,
        play: suspend (T, () -> Unit) -> Boolean,
        onRequestCompleted: () -> Unit = {},
    ): Boolean = coroutineScope {
        // A completed next segment cannot queue here while another preparation
        // starts: its rendezvous send waits for the single player to take it.
        val ready = Channel<Result<Delivery<T>>>(Channel.RENDEZVOUS)
        val producer = launch {
            try {
                for (request in requests) {
                    currentCoroutineContext().ensureActive()
                    val first = prepareRequest(request)
                    var current = first
                    val pending = ArrayDeque<String>()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        for (text in remaining(current).asReversed()) pending.addFirst(text)
                        val delivery = Delivery(makeReady(current), lastInRequest = pending.isEmpty())
                        ready.send(Result.success(delivery))
                        // Prioritize route / AudioTrack startup over model work.
                        // This same handshake spans request boundaries as well.
                        delivery.started.await()
                        if (pending.isEmpty()) break
                        current = prepareText(pending.removeFirst(), first)
                    }
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                // A private native/IPC timeout is a request failure. A cancelled
                // producer must not leave the player waiting on an open channel.
                val error = if (failure is CancellationException) IllegalStateException(
                    "Qwen segment preparation was cancelled before completion", failure) else failure
                ready.send(Result.failure(error))
            } finally {
                ready.close()
            }
        }
        try {
            for (result in ready) {
                val delivery = result.getOrThrow()
                currentCoroutineContext().ensureActive()
                if (!play(delivery.clip) { delivery.started.complete(Unit) }) return@coroutineScope false
                // A completed player without a start callback is still safe to
                // advance; no next synthesis could have overlapped its startup.
                delivery.started.complete(Unit)
                if (delivery.lastInRequest) onRequestCompleted()
            }
            true
        } finally {
            producer.cancel()
            ready.cancel()
            // coroutineScope joins native preparation/decoder cleanup before
            // returning, so microphone handoff and FLUSH retain their barrier.
        }
    }
}
