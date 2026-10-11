package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Progress belongs to the current creation attempt, never to saved UI state. */
internal suspend fun createChatWithProgress(
    setCreating: (Boolean) -> Unit,
    createChat: suspend () -> Int,
    onCreated: (Int) -> Unit,
) {
    setCreating(true)
    try {
        val id = createChat()
        currentCoroutineContext().ensureActive()
        onCreated(id)
    } finally {
        setCreating(false)
    }
}
