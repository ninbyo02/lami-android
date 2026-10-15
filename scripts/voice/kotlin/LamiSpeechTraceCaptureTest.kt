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
            System.getProperty("lami.capture.export")?.let { target ->
                val output = java.io.File(target)
                require(!output.exists())
                require(directory.copyRecursively(output, overwrite = false))
            }
            assertThrows(IllegalStateException::class.java) { trace.failure("load") }
        } finally { parent.deleteRecursively() }
    }
    @Test fun failureStoresStageWithoutPcm() {
        val parent = Files.createTempDirectory("lami-trace").toFile()
        try {
            val directory = parent.resolve("capture")
            var clock = 0L
            val trace = LamiSpeechTraceCapture(directory, "failed", "", "a".repeat(64), "b".repeat(64)) { clock }
            clock = 25
            trace.failure("load")
            assertTrue(directory.resolve("trace.json").readText().contains("\"failure_stage\":\"load\""))
            assertFalse(directory.resolve("output.f32").exists())
            System.getProperty("lami.capture.export")?.let { target ->
                require(directory.copyRecursively(java.io.File(target + "-failure"), overwrite = false))
            }
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
            System.getProperty("lami.capture.export")?.let { target ->
                require(parent.resolve("capture").copyRecursively(java.io.File(target + "-cancelled"), overwrite = false))
            }
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
    @Test fun unfinishedCaptureKeepsLastCompletedStage() {
        val parent = Files.createTempDirectory("lami-journal").toFile()
        try {
            var clock = 100L
            val directory = parent.resolve("capture")
            val trace = LamiSpeechTraceCapture(directory, "unfinished", "", "a".repeat(64), "b".repeat(64)) { clock }
            clock = 125
            trace.event("synthesis_started")
            assertFalse(directory.resolve("trace.json").exists())
            val lines = directory.resolve("events.jsonl").readLines()
            assertEquals(2, lines.size)
            assertTrue(lines.last().contains("synthesis_started"))
            assertTrue(lines.last().contains("25"))
            assertTrue(directory.resolve("request.properties").readText().contains("request_id=unfinished"))
        } finally { parent.deleteRecursively() }
    }
}
