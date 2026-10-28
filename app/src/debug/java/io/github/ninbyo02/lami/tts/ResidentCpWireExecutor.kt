package io.github.ninbyo02.lami.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure, non-networked binding to an already owned resident driver. */
internal class ResidentCpWireExecutor(
    private val driver: ResidentCpQnnDriver.NativeApi,
    private val handle: Long
) : AutoCloseable {
    private val session = ResidentCpWireSession()
    private var closed = false

    fun process(packet: ResidentCpWire.Packet): ResidentCpWire.Packet? {
        check(!closed && handle > 0)
        // Validate framing first, even when callers construct packets directly.
        ResidentCpWire.encode(packet)
        val reset = session.accept(packet)
        if (reset) {
            driver.resetKv(handle)
            return null
        }
        val input = FloatArray(1024)
        val bb = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN)
        for (i in input.indices) input[i] = bb.float
        require(input.all(Float::isFinite)) { "Non-finite input" }
        val output = FloatArray(1024)
        driver.execute(handle, packet.position, input, output)
        check(output.all(Float::isFinite)) { "Non-finite output" }
        val bytes = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        output.forEach(bytes::putFloat)
        return ResidentCpWire.Packet(ResidentCpWire.RESULT, packet.frame, packet.position, bytes.array())
    }

    override fun close() { closed = true; session.close() }
}
