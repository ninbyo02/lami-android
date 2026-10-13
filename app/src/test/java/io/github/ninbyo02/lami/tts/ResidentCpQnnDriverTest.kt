package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import org.junit.Test

class ResidentCpQnnDriverTest {
    private class Fake : ResidentCpQnnDriver.NativeApi {
        var loads = 0
        var resets = 0
        var executes = 0
        var releases = 0
        var failExecution = false
        override fun loadContext(asset: String): Long { loads++; return 12L }
        override fun resetKv(context: Long) { assertEquals(12L, context); resets++ }
        override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {
            if (failExecution) error("native failure")
            assertEquals(12L, context)
            executes++
            input.copyInto(output)
        }
        override fun release(context: Long) { assertEquals(12L, context); releases++ }
    }

    @Test fun loadedOnceAcrossFrames() {
        val native = Fake()
        ResidentCpSession(ResidentCpBufferedBackend(ResidentCpQnnDriver(native, "context.bin"), 4)).use { session ->
            repeat(3) {
                session.startFrame()
                repeat(16) { session.step(floatArrayOf(1f, 2f, 3f, 4f)) }
            }
        }
        assertEquals(1, native.loads)
        assertEquals(3, native.resets)
        assertEquals(48, native.executes)
        assertEquals(1, native.releases)
    }

    @Test fun nativeFailurePoisonsHandleUntilReleased() {
        val native = Fake()
        val driver = ResidentCpQnnDriver(native, "context.bin")
        driver.open()
        driver.clearKv()
        native.failExecution = true
        reject { driver.forward(0, floatArrayOf(1f), floatArrayOf(0f)) }
        reject { driver.clearKv() }
        reject { driver.open() }
        driver.close()
        driver.close()
        assertEquals(1, native.releases)
    }

    @Test fun refusesInvalidHandle() {
        val native = object : ResidentCpQnnDriver.NativeApi {
            override fun loadContext(asset: String) = 0L
            override fun resetKv(context: Long) {}
            override fun execute(context: Long, position: Int, input: FloatArray, output: FloatArray) {}
            override fun release(context: Long) { fail("Invalid handle must not be released") }
        }
        reject { ResidentCpQnnDriver(native, "context.bin").open() }
    }

    @Test fun verifiedLoadRejectsTamperingBeforeNativeOpen() {
        val native = Fake()
        val file = File.createTempFile("cp-context", ".bin")
        try {
            val original = "compiled-context".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(original)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val spec = ResidentCpContextSpec(digest, 1024, 16, 32, "cp-test")
            file.writeBytes("corrupted-context".toByteArray())
            val driver = ResidentCpQnnDriver(native, file, spec)
            try { driver.open(); fail("Expected hash rejection") }
            catch (_: IllegalArgumentException) {}
            assertEquals(0, native.loads)
            driver.close()
            file.writeBytes(original)
            val good = ResidentCpQnnDriver(native, file, spec)
            good.open()
            good.close()
            assertEquals(1, native.loads)
            assertEquals(1, native.releases)
        } finally { file.delete() }
    }
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected failure") }
        catch (_: IllegalStateException) {}
    }
}
