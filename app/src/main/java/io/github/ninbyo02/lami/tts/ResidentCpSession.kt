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
    private var poisoned = false
    private var inputSize: Int? = null
    private var outputSize: Int? = null
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
        try { backend.resetFrameKv() } catch (failure: Throwable) {
            poisoned = true
            throw failure
        }
        poisoned = false
        position = 0
    }

    fun step(input: FloatArray): FloatArray {
        check(!closed && initialized && position < 16) { "Frame not active" }
        require(input.isNotEmpty() && input.all(Float::isFinite)) { "Invalid input" }
        require(inputSize == null || input.size == inputSize) { "CP input shape changed" }
        val output = try { backend.execute(position, input) } catch (failure: Throwable) {
            poisoned = true
            position = 16
            throw failure
        }
        if (output.isEmpty() || !output.all(Float::isFinite) ||
            (outputSize != null && output.size != outputSize)) {
            poisoned = true
            position = 16
            throw IllegalStateException("Invalid CP output; frame invalidated")
        }
        inputSize = input.size
        outputSize = output.size
        executions++
        position++
        if (position == 16) completedFrames++
        return output
    }

    fun cancelFrame() {
        check(!closed)
        position = 16
        poisoned = true
    }

    override fun close() {
        if (!closed) {
            closed = true
            backend.close()
        }
    }
}
