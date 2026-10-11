package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One executing count and one waiting caller across all normal/fallback routes.
 * Cancellation belongs to the caller. Native cleanup remains inside the count operation.
 */
internal class TokenRecountExecutionCoordinator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val slots = Semaphore(2)
    private val mutex = Mutex()

    suspend fun <T> execute(fallback: T, count: suspend () -> T): T = withContext(dispatcher) {
        if (!slots.tryAcquire()) return@withContext fallback
        try {
            mutex.withLock { count() }
        } finally {
            slots.release()
        }
    }
}

// Process-wide admission even when screens/updaters are recreated.
internal val localTokenRecountCoordinator = TokenRecountExecutionCoordinator()
