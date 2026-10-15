package io.github.ninbyo02.lami.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ResidentCpWireExecutorTest {
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
    private fun req(type: Int, frame: Int, position: Int, input: Float = 1f) =
        ResidentCpWire.Packet(type, frame, position, if (type == ResidentCpWire.RESET) byteArrayOf()
            else ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(1024) { putFloat(input) }
            }.array())

    @Test fun twoFramesOneContext() {
        val fake = Fake()
        ResidentCpWireExecutor(fake, 1L).use { engine ->
            repeat(2) { frame ->
                assertNull(engine.process(req(ResidentCpWire.RESET, frame, 0)))
                repeat(16) { pos ->
                    val result = engine.process(req(ResidentCpWire.STEP, frame, pos))!!
                    assertEquals(ResidentCpWire.RESULT, result.type)
                    assertEquals(pos, result.position)
                    assertEquals(1f + pos, ByteBuffer.wrap(result.payload).order(ByteOrder.LITTLE_ENDIAN).float)
                }
            }
        }
        assertEquals(2, fake.resets)
        assertEquals(32, fake.steps)
    }

    @Test fun rejectsInvalidInputBeforeNativeStep() {
        val fake = Fake()
        ResidentCpWireExecutor(fake, 1L).use { engine ->
            engine.process(req(ResidentCpWire.RESET, 0, 0))
            assertTrue(runCatching { engine.process(req(ResidentCpWire.STEP, 0, 0, Float.NaN)) }.isFailure)
            assertEquals(0, fake.steps)
        }
    }
}
