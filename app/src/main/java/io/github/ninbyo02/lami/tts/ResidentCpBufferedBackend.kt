package io.github.ninbyo02.lami.tts

/**
 * Adapter boundary for an in-process QNN/HTP driver.
 * Owns reusable host tensors; does not claim a device-resident graph exists yet.
 * Driver implementation must retain the native QNN context until close().
 */
internal class ResidentCpBufferedBackend(
    private val driver: Driver,
    private val width: Int,
) : ResidentCpSession.Backend {
    interface Driver : AutoCloseable {
        fun open()
        fun clearKv()
        /** Must synchronously fill output without retaining the caller's mutable buffers. */
        fun forward(position: Int, input: FloatArray, output: FloatArray)
    }
    init { require(width > 0) }
    private val inputBuffer = FloatArray(width)
    private val outputBuffer = FloatArray(width)
    private var opened = false
    private var closed = false

    override fun loadOnce() {
        check(!closed)
        if (!opened) {
            driver.open()
            opened = true
        }
    }
    override fun resetFrameKv() {
        check(opened && !closed)
        driver.clearKv()
        inputBuffer.fill(0f)
        outputBuffer.fill(0f)
    }
    override fun execute(position: Int, input: FloatArray): FloatArray {
        check(opened && !closed)
        require(position in 0..15 && input.size == width)
        input.copyInto(inputBuffer)
        outputBuffer.fill(0f)
        driver.forward(position, inputBuffer, outputBuffer)
        return outputBuffer.copyOf() // caller cannot alias next NPU output
    }
    override fun close() {
        if (!closed) {
            closed = true
            driver.close()
            inputBuffer.fill(0f)
            outputBuffer.fill(0f)
        }
    }
}
