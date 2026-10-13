package io.github.ninbyo02.lami.tts

/**
 * Host-side ownership boundary for a future JNI/QNN implementation.
 * NativeApi must map loadContext to a single resident QNN context and graph.
 * No QNN symbols or phone hardware are invoked by this adapter.
 */
internal class ResidentCpQnnDriver(
    private val api: NativeApi,
    private val contextAsset: String,
) : ResidentCpBufferedBackend.Driver {
    interface NativeApi {
        fun loadContext(asset: String): Long
        fun resetKv(context: Long)
        fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray)
        fun release(context: Long)
    }

    init { require(contextAsset.isNotBlank()) }

    private var handle: Long = 0
    private var closed = false
    private var faulted = false

    override fun open() {
        check(!closed && !faulted) { "QNN driver unavailable" }
        if (handle == 0L) {
            val opened = api.loadContext(contextAsset)
            check(opened != 0L) { "QNN returned invalid context" }
            handle = opened
        }
    }

    override fun clearKv() = guarded { api.resetKv(it) }

    override fun forward(position: Int, input: FloatArray, output: FloatArray) {
        require(position in 0..15)
        guarded { api.execute(it, position, input, output) }
    }

    private inline fun guarded(operation: (Long) -> Unit) {
        check(!closed && !faulted && handle != 0L) { "No healthy resident context" }
        try {
            operation(handle)
        } catch (failure: Throwable) {
            faulted = true
            throw failure
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            val previous = handle
            handle = 0L
            if (previous != 0L) api.release(previous)
        }
    }
}
