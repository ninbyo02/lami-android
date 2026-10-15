package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpWireSessionTest {
    private fun packet(type: Int, frame: Int, position: Int) =
        ResidentCpWire.Packet(type, frame, position,
            if (type == ResidentCpWire.STEP) ByteArray(4096) else byteArrayOf())

    @Test fun allowsTwoCompleteFramesWithOneResetEach() {
        val session = ResidentCpWireSession()
        repeat(2) { frame ->
            assertTrue(session.accept(packet(ResidentCpWire.RESET, frame, 0)))
            repeat(16) { position ->
                assertFalse(session.accept(packet(ResidentCpWire.STEP, frame, position)))
            }
        }
        session.close()
        assertTrue(runCatching { session.accept(packet(ResidentCpWire.RESET, 2, 0)) }.isFailure)
    }

    @Test fun rejectsUnresetRepeatedAndSkippedPositions() {
        val session = ResidentCpWireSession()
        assertTrue(runCatching { session.accept(packet(ResidentCpWire.STEP, 0, 0)) }.isFailure)
        assertTrue(session.accept(packet(ResidentCpWire.RESET, 0, 0)))
        assertTrue(runCatching { session.accept(packet(ResidentCpWire.RESET, 0, 0)) }.isFailure)
        assertTrue(runCatching { session.accept(packet(ResidentCpWire.STEP, 0, 1)) }.isFailure)
        assertFalse(session.accept(packet(ResidentCpWire.STEP, 0, 0)))
        assertTrue(runCatching { session.accept(packet(ResidentCpWire.STEP, 0, 0)) }.isFailure)
    }
}
