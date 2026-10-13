package io.github.ninbyo02.lami.tts

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ResidentCpRustNativeApiTest {
    private class Fake : ResidentCpRustNativeApi.Bridge {
        var libraries = 0; var loads = 0; var releases = 0; var resets = 0; var executions = 0
        var fail = false
        var rpcLibrary = ""
        override fun loadLibrary(file: File?) { libraries++ }
        override fun create(bytes: ByteArray, backend: String, system: String, rpc: String): Long { loads++; rpcLibrary = rpc; return loads.toLong() }
        override fun reset(handle: Long) { resets++ }
        override fun execute(handle: Long, position: Int, input: FloatArray, output: FloatArray) {
            if (fail) error("native failure")
            executions++; input.copyInto(output)
        }
        override fun cancel(handle: Long) {}
        override fun release(handle: Long) { releases++ }
        override fun stats(handle: Long) = longArrayOf(1, executions.toLong(), 24, resets.toLong(), 0, 0, 0)
    }
    private fun fixture(block: (File, ResidentCpContextSpec, Fake, ResidentCpRustNativeApi) -> Unit) {
        val root = Files.createTempDirectory("cp-jni-test").toFile()
        try {
            val file = File(root, "context.bin").apply { writeBytes("model".toByteArray()) }
            for (name in listOf("libQnnHtp.so", "libQnnSystem.so", "libcdsprpc.so")) File(root, name).writeBytes(byteArrayOf(0))
            val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val spec = ResidentCpContextSpec(sha, 1024, 16, 32, "diagnostic-test")
            val fake = Fake()
            ResidentCpRustNativeApi(root, spec, bridge = fake).use { api -> block(file, spec, fake, api) }
        } finally { root.deleteRecursively() }
    }
    @Test fun reusedFramesAndCancellationReleaseOnce() = fixture { file, spec, fake, api ->
        ResidentCpSession(ResidentCpBufferedBackend(ResidentCpQnnDriver(api, file, spec), 1024)).use { session ->
            repeat(2) { session.startFrame(); repeat(16) { session.step(FloatArray(1024) { 1f }) } }
            session.startFrame(); session.step(FloatArray(1024)); session.cancelFrame()
            session.startFrame(); repeat(16) { session.step(FloatArray(1024)) }
        }
        assertEquals(1, fake.loads); assertEquals(1, fake.libraries)
        assertEquals("libcdsprpc.so", fake.rpcLibrary)
        assertEquals(4, fake.resets); assertEquals(49, fake.executions); assertEquals(1, fake.releases)
        api.close(); assertEquals(1, fake.releases)
    }
    @Test fun failedDriverReleasedAndFreshDriverCanRecover() = fixture { file, spec, fake, api ->
        val broken = ResidentCpQnnDriver(api, file, spec)
        broken.open(); broken.clearKv(); fake.fail = true
        reject { broken.forward(0, FloatArray(1024), FloatArray(1024)) }
        reject { broken.clearKv() }; broken.close()
        fake.fail = false
        ResidentCpQnnDriver(api, file, spec).use { fresh ->
            fresh.open(); fresh.clearKv(); fresh.forward(0, FloatArray(1024), FloatArray(1024))
        }
        assertEquals(2, fake.loads); assertEquals(2, fake.releases)
    }
    @Test fun tamperingRejectedBeforeNativeLibraryOrContextLoad() = fixture { file, _, fake, api ->
        file.writeText("tampered")
        try { api.loadContext(file.absolutePath); fail("Expected hash rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(0, fake.libraries); assertEquals(0, fake.loads)
    }
    @Test fun staleTokensAndMalformedBuffersRejectedBeforeBridge() = fixture { file, _, fake, api ->
        val handle = api.loadContext(file.absolutePath)
        try { api.execute(handle, 0, FloatArray(1023), FloatArray(1024)); fail("Expected shape rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(0, fake.executions)
        api.release(handle); api.release(handle)
        reject { api.resetKv(handle) }; reject { api.stats(handle) }
        assertEquals(1, fake.releases)
    }
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: IllegalStateException) {}
    }
}
