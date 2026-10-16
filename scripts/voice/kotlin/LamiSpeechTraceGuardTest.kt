package io.github.ninbyo02.lami.tts

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LamiSpeechTraceGuardTest {
    @Test fun failedFactoryDoesNotInterruptCaller() {
        val guard = LamiSpeechTraceGuard.create { throw java.io.IOException("disk unavailable") }
        guard.event("synthesis_started")
        guard.success(floatArrayOf(0.1f))
        guard.failure("cancelled")
    }
    @Test fun failedSaveDoesNotInterruptCaller() {
        val parent = Files.createTempDirectory("lami-guard").toFile()
        try {
            val directory = parent.resolve("capture")
            val guard = LamiSpeechTraceGuard.create {
                LamiSpeechTraceCapture(directory, "test", "", "a".repeat(64), "b".repeat(64)) { 0L }
            }
            guard.event("synthesis_started")
            guard.event("pcm_ready")
            directory.deleteRecursively()
            directory.writeText("blocks directory recreation")
            guard.success(floatArrayOf(0.1f))
            assertEquals("blocks directory recreation", directory.readText())
            guard.failure("cancelled")
        } finally { parent.deleteRecursively() }
    }
    @Test fun successfulGuardFinalizesOnce() {
        val parent = Files.createTempDirectory("lami-guard").toFile()
        try {
            val directory = parent.resolve("capture")
            val guard = LamiSpeechTraceGuard.create {
                LamiSpeechTraceCapture(directory, "test", "", "a".repeat(64), "b".repeat(64)) { 0L }
            }
            guard.event("synthesis_started")
            guard.event("pcm_ready")
            guard.success(floatArrayOf(0.1f))
            val result = directory.resolve("trace.json").readText()
            guard.failure("cancelled")
            assertEquals(result, directory.resolve("trace.json").readText())
        } finally { parent.deleteRecursively() }
    }
}
