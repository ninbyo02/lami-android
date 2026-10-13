package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpSessionTest {
    private class Fake : ResidentCpSession.Backend {
        var loaded = 0
        var resets = 0
        var closed = 0
        val positions = mutableListOf<Int>()
        override fun loadOnce() { loaded++ }
        override fun resetFrameKv() { resets++ }
        override fun execute(position: Int, input: FloatArray): FloatArray {
            positions += position
            return input.copyOf()
        }
        override fun close() { closed++ }
    }
    @Test fun reuseContext() {
        val backend = Fake()
        ResidentCpSession(backend).use { session ->
            repeat(3) {
                session.startFrame()
                repeat(16) { session.step(floatArrayOf(1f)) }
            }
            assertEquals(1, backend.loaded)
            assertEquals(3, backend.resets)
            assertEquals(48, session.executions)
            assertEquals(3, session.completedFrames)
            assertEquals(List(3) { (0..15).toList() }.flatten(), backend.positions)
        }
        assertEquals(1, backend.closed)
    }
    @Test fun cancelAndReuse() {
        val backend = Fake()
        ResidentCpSession(backend).use { session ->
            session.startFrame()
            session.step(floatArrayOf(1f))
            session.cancelFrame()
            session.startFrame()
            repeat(16) { session.step(floatArrayOf(1f)) }
            assertEquals(1, backend.loaded)
            assertEquals(2, backend.resets)
            assertEquals(1, session.completedFrames)
        }
    }
    @Test fun rejectsInvalidState() {
        val session = ResidentCpSession(Fake())
        assertFails { session.step(floatArrayOf(1f)) }
        session.startFrame()
        assertFails { session.startFrame() }
        assertFails { session.step(floatArrayOf(Float.NaN)) }
        session.close()
        assertFails { session.startFrame() }
    }
    @Test fun backendFailureRequiresResetWithoutReload() {
        val backend = object : ResidentCpSession.Backend {
            var loads = 0
            var resets = 0
            override fun loadOnce() { loads++ }
            override fun resetFrameKv() { resets++ }
            override fun execute(position: Int, input: FloatArray): FloatArray {
                if (resets == 1) error("Simulated NPU failure")
                return input.copyOf()
            }
            override fun close() {}
        }
        ResidentCpSession(backend).use { session ->
            session.startFrame()
            assertFails { session.step(floatArrayOf(1f)) }
            session.startFrame()
            repeat(16) { session.step(floatArrayOf(1f)) }
            assertEquals(1, backend.loads)
            assertEquals(2, backend.resets)
            assertEquals(1, session.completedFrames)
        }
    }
    @Test fun changingTensorShapeIsRejected() {
        ResidentCpSession(Fake()).use { session ->
            session.startFrame()
            session.step(floatArrayOf(1f))
            assertFails { session.step(floatArrayOf(1f, 2f)) }
        }
    }
    private fun assertFails(block: () -> Unit) {
        try { block(); fail("Expected rejection") }
        catch (_: IllegalStateException) { }
        catch (_: IllegalArgumentException) { }
    }
}
