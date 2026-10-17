package io.github.ninbyo02.lami.tts

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ResidentCpWireTest {
    /** Reference vectors created by scripts/voice/resident_cp_wire.py on the host. */
    @Test fun matchesPythonWireVectors() {
        val reset = ResidentCpWire.encode(
            ResidentCpWire.Packet(ResidentCpWire.RESET, 7, 0, byteArrayOf()))
        val expectedReset = "4c4350310107000000000000"
        assertEquals(expectedReset, reset.joinToString("") { "%02x".format(it.toInt() and 255) })
        val payload = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(1024) { putFloat(((it % 17) - 8) / 16f) }
        }.array()
        val step = ResidentCpWire.encode(
            ResidentCpWire.Packet(ResidentCpWire.STEP, 12, 15, payload))
        val digest = MessageDigest.getInstance("SHA-256").digest(step)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("8330bb90c484444418b9f9d1132ea8208a8713f657bfffe01177106f0e4d28cf", digest)
    }

    @Test fun packetRoundtrip() {
        for (packet in listOf(
            ResidentCpWire.Packet(ResidentCpWire.RESET, 0, 0, byteArrayOf()),
            ResidentCpWire.Packet(ResidentCpWire.STEP, 12, 15, ByteArray(4096) { 5 })
        )) {
            val decoded = ResidentCpWire.decode(ByteArrayInputStream(ResidentCpWire.encode(packet)))
            assertEquals(packet.type, decoded.type)
            assertEquals(packet.frame, decoded.frame)
            assertEquals(packet.position, decoded.position)
            assertArrayEquals(packet.payload, decoded.payload)
        }
    }

    @Test fun malformedInputsRejected() {
        assertTrue(runCatching { ResidentCpWire.encode(ResidentCpWire.Packet(2, 0, 16, ByteArray(4096))) }.isFailure)
        assertTrue(runCatching { ResidentCpWire.encode(ResidentCpWire.Packet(2, 0, 0, ByteArray(4095))) }.isFailure)
        assertTrue(runCatching { ResidentCpWire.decode(ByteArrayInputStream(byteArrayOf(0, 1))) }.isFailure)
        val corrupted = ResidentCpWire.encode(ResidentCpWire.Packet(1, 0, 0, byteArrayOf()))
        corrupted[0] = 0
        assertTrue(runCatching { ResidentCpWire.decode(ByteArrayInputStream(corrupted)) }.isFailure)
    }
}
