package io.github.ninbyo02.lami.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ResidentCpSingleStepFixtureTest {
    @Test fun fixedPinnedRowAcceptsValidFiniteBinary() {
        val floats = FloatArray(1024) { (it - 512) / 16.0f }
        val bytes = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach(bytes::putFloat)
        val file = File.createTempFile("resident-cp-fixture", ".f32")
        try {
            file.writeBytes(bytes.array())
            val read = ResidentCpSingleStepFixture.readPinnedRow(
                file, ResidentCpSingleStepFixture.sha256(bytes.array()))
            assertTrue(ResidentCpSingleStepFixture.bitEqual(read, floats))
            read[0] = -read[0]
            assertFalse(ResidentCpSingleStepFixture.bitEqual(read, floats))
        } finally {
            file.delete()
        }
    }

    @Test fun invalidHashAndSizeFailClosed() {
        val file = File.createTempFile("resident-cp-bad", ".f32")
        try {
            file.writeBytes(ByteArray(4096))
            assertTrue(runCatching {
                ResidentCpSingleStepFixture.readPinnedRow(file, "0".repeat(64))
            }.isFailure)
            file.writeBytes(ByteArray(4095))
            assertTrue(runCatching {
                ResidentCpSingleStepFixture.readPinnedRow(
                    file, ResidentCpSingleStepFixture.sha256(file))
            }.isFailure)
        } finally {
            file.delete()
        }
    }
}
