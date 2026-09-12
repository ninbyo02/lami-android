package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull

internal data class GpuExperimentalTimeoutOperationResult<T>(val value: T?, val timedOut: Boolean)

/** Preserve the non-blocking timeout while forwarding caller cancellation to native cleanup. */
internal suspend fun <T> runCancellableGpuOperation(
    timeoutMs: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T?,
): GpuExperimentalTimeoutOperationResult<T> {
    val workerScope = CoroutineScope(SupervisorJob() + dispatcher)
    val worker = workerScope.async { block() }
    return try {
        withTimeoutOrNull(timeoutMs) {
            GpuExperimentalTimeoutOperationResult(worker.await(), timedOut = false)
        } ?: GpuExperimentalTimeoutOperationResult(value = null, timedOut = true)
    } finally {
        // Includes user stop and cancellation of the outer request, not only watchdog expiry.
        workerScope.cancel()
    }
}
