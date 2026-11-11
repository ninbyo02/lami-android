package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpAuthenticatedChannelTest {
    private class Fake : ResidentCpQnnDriver.NativeApi {
        var resets = 0
        override fun loadContext(asset: String) = 1L
        override fun resetKv(context: Long) { resets++ }
        override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {}
        override fun release(context: Long) {}
    }

    @Test fun validSecretAllowsOneAuthorizedSession() {
        val api = Fake()
        val expected = ByteArray(32) { it.toByte() }
        val channel = ResidentCpAuthenticatedChannel(expected, ResidentCpWireExecutor(api, 1L))
        assertTrue(runCatching { channel.process(ResidentCpWire.Packet(1, 0, 0, byteArrayOf())) }.isFailure)
        assertTrue(channel.authorize(expected.copyOf()))
        assertFalse(channel.authorize(expected.copyOf()))
        assertNull(channel.process(ResidentCpWire.Packet(1, 0, 0, byteArrayOf())))
        assertEquals(1, api.resets)
        channel.close()
        assertTrue(runCatching { channel.process(ResidentCpWire.Packet(1, 1, 0, byteArrayOf())) }.isFailure)
    }

    @Test fun incorrectSecretFailsClosed() {
        val api = Fake()
        val channel = ResidentCpAuthenticatedChannel(ByteArray(32) { 7 }, ResidentCpWireExecutor(api, 1L))
        assertFalse(channel.authorize(ByteArray(32) { 8 }))
        assertTrue(runCatching { channel.authorize(ByteArray(32) { 7 }) }.isFailure)
        assertEquals(0, api.resets)
    }
}
