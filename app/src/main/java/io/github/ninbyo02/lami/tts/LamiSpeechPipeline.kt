package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** One producer and one player. Rendezvous transport permits only one prepared clip ahead. */
internal class LamiSpeechPipeline<T>(
    private val scope: CoroutineScope,
    private val onBusy: (Boolean) -> Unit,
    private val prepare: suspend (String) -> T,
    private val play: suspend (T) -> Unit,
) {
    private val lock = Any()
    private var generation = 0L
    private var outstanding = 0
    private var input: Channel<String>? = null
    private var worker: Job? = null
    private val activeJobs = linkedSetOf<Job>()

    fun enqueue(text: String): Unit = synchronized(lock) {
        if (text.isBlank()) return@synchronized
        if (worker == null) startWorker()
        check(checkNotNull(input).trySend(text).isSuccess)
        if (outstanding++ == 0) onBusy(true)
    }

    private fun startWorker() {
        val token = generation
        val previous = activeJobs.toList()
        val incoming = Channel<String>(Channel.UNLIMITED)
        input = incoming
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            val ready = Channel<T>(Channel.RENDEZVOUS)
            try {
                previous.forEach { it.join() }
                coroutineScope {
                    launch {
                        for (text in incoming) ready.send(prepare(text))
                    }
                    for (clip in ready) {
                        play(clip)
                        synchronized(lock) {
                            if (token == generation && --outstanding == 0) onBusy(false)
                        }
                    }
                }
            } finally {
                incoming.cancel()
                ready.cancel()
                synchronized(lock) {
                    if (token == generation && worker === self) {
                        worker = null
                        input = null
                        outstanding = 0
                        onBusy(false)
                    }
                }
            }
        }
        worker = next
        activeJobs.add(next)
        next.invokeOnCompletion { synchronized(lock) { activeJobs.remove(next) } }
        next.start()
    }

    fun replace(text: String) = synchronized(lock) {
        if (text.isBlank()) return@synchronized
        stop()
        enqueue(text)
    }

    fun stop() = synchronized(lock) {
        generation++
        worker?.cancel()
        input?.cancel()
        input = null
        worker = null
        outstanding = 0
        onBusy(false)
    }
}
