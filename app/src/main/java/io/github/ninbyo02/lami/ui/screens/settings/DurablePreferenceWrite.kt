package io.github.ninbyo02.lami.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Complete an explicitly requested small preference write after its screen is removed. */
internal fun CoroutineScope.writePreferenceDurably(
    write: suspend () -> Unit,
    onFailure: () -> Unit,
    onComplete: () -> Unit,
) = launch(start = CoroutineStart.UNDISPATCHED) {
    withContext(NonCancellable) {
        try { write() } catch (_: Exception) { onFailure() } finally { onComplete() }
    }
}
