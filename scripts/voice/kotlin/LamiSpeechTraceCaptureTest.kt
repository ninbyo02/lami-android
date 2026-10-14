package io.github.ninbyo02.lami.tts

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LamiSpeechTraceCaptureTest {
    @Test fun successStoresSameRequestAndPcm() {
        val parent = Files.createTempDirectory("lami-trace").toFile()
        try {
            var clock = 100L
            val directory = parent.resolve("capture")
            val trace = LamiSpeechTraceCapture(directory, "request1", "こんにちは。", "a".repeat(64), "b".repeat(64)) { clock }
            trace.event("synthesis_started")
            clock = 150
            trace.event("pcm_ready")
            clock = 170
            trace.success(floatArrayOf(0.1f, -0.2f))
            val json = directory.resolve("trace.json").readText()
            assertTrue(json.contains("\"request_id\":\"request1\""))
            assertTrue(json.contains("\"elapsed_ms\":70"))
            assertEquals(8L, directory.resolve("output.f32").length())
            assertThrows(IllegalStateException::class.java) { trace.failure("load") }
        } finally { parent.deleteRecursively() }
    }
    @Test fun failureStoresStageWithoutPcm() {
        val parent = Files.createTempDirectory("lami-trace").toFile()
        try {
            val directory = parent.resolve("capture")
            val trace = LamiSpeechTraceCapture(directory, "failed", "", "a".repeat(64), "b".repeat(64)) { 0L }
            trace.failure("load")
            assertTrue(directory.resolve("trace.json").readText().contains("\"failure_stage\":\"load\""))
            assertFalse(directory.resolve("output.f32").exists())
        } finally { parent.deleteRecursively() }
    }
    @Test fun rejectsBackwardsClockAndInvalidPcm() {
        val parent = Files.createTempDirectory("lami-trace").toFile()
        try {
            var clock = 10L
            val trace = LamiSpeechTraceCapture(parent.resolve("capture"), "test", "", "a".repeat(64), "b".repeat(64)) { clock }
            clock = 9
            assertThrows(IllegalArgumentException::class.java) { trace.event("pcm_ready") }
            clock = 10
            trace.event("synthesis_started")
            trace.event("pcm_ready")
            assertThrows(IllegalArgumentException::class.java) { trace.success(floatArrayOf(Float.NaN)) }
            trace.failure("cancelled")
        } finally { parent.deleteRecursively() }
    }
    @Test fun refusesExistingCaptureDirectory() {
        val parent = Files.createTempDirectory("lami-trace").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                LamiSpeechTraceCapture(parent, "test", "", "a".repeat(64), "b".repeat(64)) { 0L }
            }
        } finally { parent.deleteRecursively() }
    }
}
