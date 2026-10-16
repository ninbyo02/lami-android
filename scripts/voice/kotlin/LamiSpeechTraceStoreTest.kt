package io.github.ninbyo02.lami.tts

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LamiSpeechTraceStoreTest {
    @Test fun unfinishedCapturesCountAgainstQuotaAndCannotBeOverwritten() {
        val parent = Files.createTempDirectory("lami-store").toFile()
        try {
            val root = parent.resolve("store")
            val store = LamiSpeechTraceStore(root, "a".repeat(64), "b".repeat(64)) { 0L }
            repeat(8) { store.capture(1L, "same request number") }
            assertEquals(8, root.listFiles()!!.size)
            assertThrows(IllegalArgumentException::class.java) { store.capture(2L, "") }
            assertEquals(8, root.listFiles()!!.size)
        } finally { parent.deleteRecursively() }
    }
    @Test fun oversizedPcmIsRejectedBeforeWriting() {
        val parent = Files.createTempDirectory("lami-store").toFile()
        try {
            val directory = parent.resolve("capture")
            val trace = LamiSpeechTraceCapture(directory, "test", "", "a".repeat(64), "b".repeat(64)) { 0L }
            trace.event("synthesis_started")
            trace.event("pcm_ready")
            assertThrows(IllegalArgumentException::class.java) {
                trace.success(FloatArray(LamiSpeechTraceCapture.MAX_PCM_BYTES / 4 + 1))
            }
            assertFalse(directory.resolve("output.f32").exists())
            trace.failure("cancelled")
        } finally { parent.deleteRecursively() }
    }
}
