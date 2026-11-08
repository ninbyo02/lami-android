package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Keep every retiring owner, not only the latest job waiting for its cleanup. */
internal class AssistantCaptureSerialJobs {
    private val lock = Any()
    private val jobs = linkedSetOf<Job>()

    fun replace(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job = synchronized(lock) {
        val previous = jobs.toList()
        previous.forEach { it.cancel() }
        val next = scope.launch(start = CoroutineStart.LAZY) {
            previous.forEach { it.join() }
            ensureActive()
            block()
        }
        jobs.add(next)
        next.invokeOnCompletion { synchronized(lock) { jobs.remove(next) } }
        next
    }
}
