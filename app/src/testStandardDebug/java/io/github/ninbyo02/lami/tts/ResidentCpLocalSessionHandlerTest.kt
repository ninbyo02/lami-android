package io.github.ninbyo02.lami.tts

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ResidentCpLocalSessionHandlerTest {
    private class Fake : ResidentCpQnnDriver.NativeApi {
        var resets = 0
        var steps = 0
        override fun loadContext(asset: String) = 1L
        override fun resetKv(context: Long) { resets++ }
        override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {
            steps++
            for (i in output.indices) output[i] = input[i] + position
        }
        override fun release(context: Long) {}
    }
    private fun handler(fake: Fake, key: ByteArray) =
        ResidentCpLocalSessionHandler(ResidentCpAuthenticatedChannel(key.copyOf(), ResidentCpWireExecutor(fake, 1L)))

    @Test fun authenticatedStreamHandlesTwoFrames() {
        val fake = Fake()
        val key = ByteArray(32) { it.toByte() }
        val bytes = ByteArrayOutputStream()
        bytes.write(key)
        repeat(2) { frame ->
            bytes.write(ResidentCpWire.encode(ResidentCpWire.Packet(ResidentCpWire.RESET, frame, 0, byteArrayOf())))
            repeat(16) { position ->
                val body = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(1024) { putFloat(1f) } }.array()
                bytes.write(ResidentCpWire.encode(ResidentCpWire.Packet(ResidentCpWire.STEP, frame, position, body)))
            }
        }
        val output = ByteArrayOutputStream()
        handler(fake, key).serve(ByteArrayInputStream(bytes.toByteArray()), output)
        assertEquals(2, fake.resets)
        assertEquals(32, fake.steps)
        val frames = ByteArrayInputStream(output.toByteArray())
        repeat(32) { index ->
            val reply = ResidentCpWire.decode(frames)
            assertEquals(ResidentCpWire.RESULT, reply.type)
            assertEquals(index / 16, reply.frame)
            assertEquals(index % 16, reply.position)
        }
        assertEquals(0, frames.available())
    }

    @Test fun wrongKeyCannotResetOrRun() {
        val fake = Fake()
        val response = ByteArrayOutputStream()
        handler(fake, ByteArray(32) { 7 }).serve(ByteArrayInputStream(ByteArray(32) { 8 }), response)
        assertEquals(0, fake.resets)
        assertEquals(0, fake.steps)
        assertEquals(0, response.size())
    }
}
