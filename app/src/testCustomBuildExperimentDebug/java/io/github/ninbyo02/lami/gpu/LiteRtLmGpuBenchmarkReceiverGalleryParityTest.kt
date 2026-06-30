package io.github.ninbyo02.lami.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmGpuBenchmarkReceiverGalleryParityTest {
    @Test
    fun `gallery parity failure row carries required artifact fields`() {
        val row = LiteRtLmGpuBenchmarkRow.failure(
            timestamp = "20260604_120000",
            backendVariant = BenchmarkBackendVariant.GALLERY_CHAT_PARITY,
            closePolicy = BenchmarkClosePolicy.NORMAL,
            phase = BenchmarkPhase.SEND_MESSAGE,
            maxOutputTokensList = "32",
            prompt = "こんにちは",
            maxOutputTokens = 32,
            modelPath = "/data/local/tmp/model.litertlm",
            reason = "test_failure",
            modelExists = true,
            modelLength = 123L,
            timeout = false,
            freshCrash = false,
        )

        assertEquals("GPU", row.backend)
        assertEquals("gallery-chat-parity", row.backendVariant)
        assertEquals("gallery_contents_callback", row.sendApiVariant)
        assertEquals(64, row.samplerTopK)
        assertEquals(0.95, requireNotNull(row.samplerTopP), 0.0)
        assertEquals(1.0, requireNotNull(row.samplerTemperature), 0.0)
        assertTrue(row.conversationConfigUsed)
        assertTrue(row.contentsApiUsed)
    }

    @Test
    fun `gallery parity markdown and csv include required fields`() {
        val row = LiteRtLmGpuBenchmarkRow(
            timestamp = "20260604_120000",
            routeType = "litert_lm_gpu_benchmark",
            backend = "GPU",
            backendVariant = "gallery-chat-parity",
            closePolicy = "normal",
            phase = "send-message",
            prompt = "こんにちは",
            maxOutputTokens = 32,
            maxOutputTokensList = "32",
            modelPath = "/data/local/tmp/model.litertlm",
            modelExists = true,
            modelLength = 123L,
            engineCreateMs = 10L,
            conversationCreateMs = 20L,
            firstTokenMs = 30L,
            ttftMs = 30L,
            decodeMs = 40L,
            totalMs = 70L,
            outputTokens = 3,
            tokensPerSecond = 75.0,
            finishReason = null,
            stopReason = null,
            rawOutput = "こんにちは。",
            sanitizedOutput = "こんにちは。",
            status = "success",
            reason = "completed",
            sendExceptionClass = null,
            sendExceptionMessage = null,
            sendExceptionCauseChain = null,
            intentionallyLeakedForDiagnostic = false,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            sendApiVariant = "gallery_contents_callback",
            samplerTopK = 64,
            samplerTopP = 0.95,
            samplerTemperature = 1.0,
            conversationConfigUsed = true,
            contentsApiUsed = true,
        )

        val markdown = buildGpuBenchmarkMarkdown(
            timestamp = "20260604_120000",
            timeoutMs = 60_000L,
            rows = listOf(row),
        )
        val csv = buildGpuBenchmarkCsv(listOf(row))

        listOf(markdown, csv).forEach { artifact ->
            assertTrue(artifact.contains("gallery-chat-parity"))
            assertTrue(artifact.contains("gallery_contents_callback"))
            assertTrue(artifact.contains("64"))
            assertTrue(artifact.contains("0.95"))
            assertTrue(artifact.contains("1.0"))
            assertTrue(artifact.contains("true"))
        }
        assertTrue(markdown.contains("- conversation_config_used: `true`"))
        assertTrue(markdown.contains("- contents_api_used: `true`"))
        assertTrue(csv.contains("\"send_api_variant\""))
        assertTrue(csv.contains("\"sampler_top_k\""))
        assertTrue(csv.contains("\"sampler_top_p\""))
        assertTrue(csv.contains("\"sampler_temperature\""))
        assertTrue(csv.contains("\"conversation_config_used\""))
        assertTrue(csv.contains("\"contents_api_used\""))
    }
}
