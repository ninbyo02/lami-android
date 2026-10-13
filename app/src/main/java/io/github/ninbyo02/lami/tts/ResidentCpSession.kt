package io.github.ninbyo02.lami.tts

/** Lifecycle guard; does not execute or promote a hardware backend itself. */
internal class ResidentCpSession(private val backend: Backend) : AutoCloseable {
    interface Backend : AutoCloseable {
        fun loadOnce()
        fun resetFrameKv()
        fun execute(position: Int, input: FloatArray): FloatArray
    }
    private var closed = false
    private var initialized = false
    private var position = 16
    var loads: Int = 0
        private set
    var executions: Int = 0
        private set
    var completedFrames: Int = 0
        private set

    fun startFrame() {
        check(!closed) { "Session closed" }
        check(position == 16) { "Previous frame incomplete" }
        if (!initialized) {
            backend.loadOnce()
            loads++
            initialized = true
        }
        backend.resetFrameKv()
        position = 0
    }

    fun step(input: FloatArray): FloatArray {
        check(!closed && initialized && position < 16) { "Frame not active" }
        require(input.isNotEmpty() && input.all(Float::isFinite)) { "Invalid input" }
        val output = backend.execute(position, input)
        require(output.isNotEmpty() && output.all(Float::isFinite)) { "Invalid output" }
        executions++
        position++
        if (position == 16) completedFrames++
        return output
    }

    fun cancelFrame() {
        check(!closed)
        position = 16
    }

    override fun close() {
        if (!closed) {
            closed = true
            backend.close()
        }
    }
}
