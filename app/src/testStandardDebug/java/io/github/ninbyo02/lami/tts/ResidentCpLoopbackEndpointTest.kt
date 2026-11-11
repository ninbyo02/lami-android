package io.github.ninbyo02.lami.tts

import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ResidentCpLoopbackEndpointTest {
    private class Fake : ResidentCpQnnDriver.NativeApi {
        var resets = 0
        var steps = 0
        override fun loadContext(asset: String) = 1L
        override fun resetKv(context: Long) { resets++ }
        override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {
            steps++
            output.indices.forEach { output[it] = input[it] + position }
        }
        override fun release(context: Long) {}
    }

    @Test fun localhostOneClientAuthenticatedRequest() {
        val fake = Fake()
        val endpoint = ResidentCpLoopbackEndpoint(
            { key -> ResidentCpAuthenticatedChannel(key, ResidentCpWireExecutor(fake, 1L)) }, 3000
        )
        var clientFailure: Throwable? = null
        var clientThread: Thread? = null
        val worker = Thread {
            try {
                endpoint.serveOnce { secret ->
                    clientThread = Thread {
                      try {
                        Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { client ->
                        client.soTimeout = 3000
                        client.getOutputStream().write(secret)
                        client.getOutputStream().write(ResidentCpWire.encode(
                            ResidentCpWire.Packet(ResidentCpWire.RESET, 0, 0, byteArrayOf())))
                        val bytes = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN).apply {
                            repeat(1024) { putFloat(2f) }
                        }.array()
                        client.getOutputStream().write(ResidentCpWire.encode(
                            ResidentCpWire.Packet(ResidentCpWire.STEP, 0, 0, bytes)))
                        client.getOutputStream().flush()
                        val reply = ResidentCpWire.decode(client.getInputStream())
                        assertEquals(ResidentCpWire.RESULT, reply.type)
                        assertEquals(2f, ByteBuffer.wrap(reply.payload).order(ByteOrder.LITTLE_ENDIAN).float)
                        }
                      } catch (e: Throwable) { clientFailure = e }
                    }.also { it.start() }
                }
            } catch (e: Throwable) { clientFailure = e }
        }
        worker.start()
        worker.join(5000)
        clientThread?.join(5000)
        assertFalse(worker.isAlive)
        assertNull(clientFailure)
        assertEquals(1, fake.resets)
        assertEquals(1, fake.steps)
        assertTrue(endpoint.port > 0)
        assertTrue(endpoint.runCatchingClosed())
    }

    private fun ResidentCpLoopbackEndpoint.runCatchingClosed() =
        runCatching { Socket(InetAddress.getLoopbackAddress(), port).use {} }.isFailure
}
