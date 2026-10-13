package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpBufferedBackendTest {
    private class Fake : ResidentCpBufferedBackend.Driver {
        var opens = 0
        var resets = 0
        var closes = 0
        val ids = mutableSetOf<Int>()
        override fun open() { opens++ }
        override fun clearKv() { resets++ }
        override fun forward(position: Int, input: FloatArray, output: FloatArray) {
            ids += System.identityHashCode(input)
            input.copyInto(output)
            output[0] += position
        }
        override fun close() { closes++ }
    }
    @Test fun reusesHostBuffersAndContext() {
        val fake = Fake()
        val saved = mutableListOf<FloatArray>()
        ResidentCpSession(ResidentCpBufferedBackend(fake, 4)).use { s ->
            repeat(2) {
                s.startFrame()
                repeat(16) { pos ->
                    saved += s.step(floatArrayOf(1f, 2f, 3f, 4f))
                    assertEquals(1f + pos, saved.last()[0])
                }
            }
        }
        assertEquals(1, fake.opens)
        assertEquals(2, fake.resets)
        assertEquals(1, fake.ids.size)
        assertEquals(1, fake.closes)
        assertEquals(1f, saved.first()[0], 0f)
        assertEquals(16f, saved[15][0], 0f)
    }
    @Test fun wrongWidthIsRejected() {
        val backend = ResidentCpBufferedBackend(Fake(), 4)
        backend.loadOnce()
        backend.resetFrameKv()
        try { backend.execute(0, floatArrayOf(1f)); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
        backend.close()
    }
}
