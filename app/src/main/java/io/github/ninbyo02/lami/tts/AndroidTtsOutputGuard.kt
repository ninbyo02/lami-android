package io.github.ninbyo02.lami.tts

/** Serializes the emergency mute with writes/play/release so a racing write cannot restart output. */
internal class AndroidTtsOutputGuard(
    private val silenceImmediately: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val lock = Any()
    private var failed: String? = null
    private var closed = false

    fun ensureHealthy(): Unit = synchronized(lock) {
        check(!closed) { "Android TTS output is closed" }
        failed?.let { throw IllegalStateException(it) }
    }

    fun <T> mutate(action: () -> T): T = synchronized(lock) {
        ensureHealthy()
        action()
    }

    fun fail(reason: String) {
        val notify = synchronized(lock) {
            if (closed || failed != null) false else {
                failed = reason
                runCatching { silenceImmediately() }
                true
            }
        }
        // Wake the source consumer only after queued private audio has been muted.
        if (notify) runCatching { onFailure(reason) }
    }

    fun close(release: () -> Unit): Unit = synchronized(lock) {
        if (!closed) {
            closed = true
            release()
        }
    }
}
