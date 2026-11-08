package io.github.ninbyo02.lami.asr.foundation.wake

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Local ASR-backed prototype. Nonmatching text is never emitted to the chat. */
class WakeWordSession(
    private val matcher: WakeWordMatcher,
    private val timeoutMillis: Long = 60_000,
    private val maximumUtterances: Int = 30,
) {
    init {
        require(timeoutMillis in 1..60_000)
        require(maximumUtterances in 1..30)
    }

    suspend fun awaitDetection(readUtterance: suspend () -> String?): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            repeat(maximumUtterances) {
                currentCoroutineContext().ensureActive()
                val text = readUtterance()
                currentCoroutineContext().ensureActive()
                if (text != null && matcher.matches(text)) return@withTimeoutOrNull true
            }
            false
        } ?: false
}
