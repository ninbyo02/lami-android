package io.github.ninbyo02.lami.tts

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class ResidentCpWireTest {
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
