package io.github.ninbyo02.lami.ui.screens.home

/** One resource; use and disposal share a lock so native calls cannot race close. */
internal class IdleCloseableResource<K, V : AutoCloseable>(
    private val scheduleExpiry: (() -> Unit) -> AutoCloseable,
) : AutoCloseable {
    private var key: K? = null
    private var value: V? = null
    private var expiry: AutoCloseable? = null
    private var generation = 0L

    @Synchronized
    fun <R> use(key: K, create: () -> V?, block: (V, Boolean) -> R): R? {
        expiry?.close()
        expiry = null
        generation++
        if (this.key != key) close()
        val reused = value != null
        val resource = value ?: create()?.also { this.key = key; value = it } ?: return null
        return try {
            block(resource, reused)
        } catch (failure: Throwable) {
            close()
            throw failure
        } finally {
            if (value === resource) {
                val expectedGeneration = generation
                expiry = scheduleExpiry { expire(expectedGeneration) }
            }
        }
    }

    @Synchronized
    private fun expire(expectedGeneration: Long) {
        if (generation == expectedGeneration) close()
    }

    @Synchronized
    override fun close() {
        generation++
        expiry?.close()
        expiry = null
        val previous = value
        value = null
        key = null
        previous?.close()
    }
}
