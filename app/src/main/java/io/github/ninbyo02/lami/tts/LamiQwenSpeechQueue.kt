package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal enum class LamiQwenRequestMode { DIRECT, QUEUED_PREPARED }
internal data class LamiQwenSpeechRequest(val text: String, val mode: LamiQwenRequestMode,
    val generation: Long, val startsBurst: Boolean)

/** One typed input owner and one global current-plus-one-ahead PCM pipeline. */
internal class LamiQwenSpeechQueue<T>(
    private val scope: CoroutineScope,
    private val onBusy: (Boolean) -> Unit,
    private val prepareRequest: suspend (LamiQwenSpeechRequest) -> T,
    private val remaining: (T) -> List<String>,
    private val prepareText: suspend (String, T) -> T,
    private val makeReady: suspend (T) -> T,
    private val play: suspend (T, () -> Unit) -> Boolean,
    private val onFailure: (Exception) -> Unit,
) {
    private val lock = Any()
    private var generation = 0L
    private var outstanding = 0
    private var input: Channel<LamiQwenSpeechRequest>? = null
    private var worker: Job? = null
    private val activeJobs = linkedSetOf<Job>()

    fun enqueue(text: String): Unit = synchronized(lock) { submit(text, LamiQwenRequestMode.QUEUED_PREPARED) }

    fun replace(text: String): Unit = synchronized(lock) {
        if (text.isBlank()) return@synchronized
        stop()
        submit(text, LamiQwenRequestMode.DIRECT)
    }

    private fun submit(text: String, mode: LamiQwenRequestMode) {
        if (text.isBlank()) return
        if (worker == null) startWorker()
        val token = generation
        val incoming = checkNotNull(input)
        val startsBurst = outstanding == 0
        // Reserve before publication, including an unconfined/immediate player.
        if (outstanding++ == 0) onBusy(true)
        if (token != generation) return
        check(incoming.trySend(LamiQwenSpeechRequest(text, mode, token, startsBurst)).isSuccess)
    }

    private fun startWorker() {
        val token = generation
        val previous = activeJobs.toList()
        val incoming = Channel<LamiQwenSpeechRequest>(Channel.UNLIMITED)
        input = incoming
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            try {
                previous.forEach { it.join() }
                LamiQwenSegmentPipeline.runRequests(incoming,
                    prepareRequest = { request ->
                        check(request.generation == token)
                        prepareRequest(request)
                    }, remaining = remaining, prepareText = prepareText, makeReady = makeReady, play = play,
                    onRequestCompleted = {
                        synchronized(lock) {
                            if (token == generation && --outstanding == 0) onBusy(false)
                        }
                    })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) { if (token == generation) onFailure(failure) }
            } finally {
                incoming.cancel()
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

    fun stop() = synchronized(lock) {
        generation++
        worker?.cancel()
        input?.cancel()
        worker = null
        input = null
        outstanding = 0
        onBusy(false)
    }

    suspend fun awaitCleanup() {
        val previous = synchronized(lock) { activeJobs.toList() }
        previous.forEach { it.join() }
    }
}
