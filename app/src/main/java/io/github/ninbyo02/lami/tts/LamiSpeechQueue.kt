package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** Serial utterances; a replacement waits for cancelled playback cleanup before starting. */
internal class LamiSpeechQueue(
    private val scope: CoroutineScope,
    private val onBusy: (Boolean) -> Unit,
    private val consume: suspend (String) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var generation = 0L
    private var worker: Job? = null
    private val activeJobs = linkedSetOf<Job>()

    fun enqueue(text: String): Unit = synchronized(lock) {
        if (text.isBlank()) return@synchronized
        pending.addLast(text)
        if (worker != null) return@synchronized
        val token = generation
        val previous = activeJobs.toList()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            try {
                previous.forEach { it.join() }
                while (true) {
                    val utterance = synchronized(lock) {
                        if (token != generation) null
                        else if (pending.isEmpty()) {
                            worker = null
                            onBusy(false)
                            null
                        } else pending.removeFirst()
                    } ?: break
                    consume(utterance)
                }
            } finally {
                synchronized(lock) {
                    if (token == generation && worker === self) {
                        worker = null
                        pending.clear()
                        onBusy(false)
                    }
                }
            }
        }
        worker = next
        activeJobs.add(next)
        next.invokeOnCompletion { synchronized(lock) { activeJobs.remove(next) } }
        onBusy(true)
        next.start()
        Unit
    }

    fun replace(text: String) = synchronized(lock) {
        if (text.isBlank()) return@synchronized
        stop()
        enqueue(text)
    }

    fun stop() = synchronized(lock) {
        generation++
        pending.clear()
        worker?.cancel()
        worker = null
        onBusy(false)
    }
}
