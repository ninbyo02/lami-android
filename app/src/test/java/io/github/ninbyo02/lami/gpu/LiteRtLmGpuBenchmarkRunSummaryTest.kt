package io.github.ninbyo02.lami.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmGpuBenchmarkRunSummaryTest {
    @Test
    fun `backend parser treats default as automatic`() {
        assertEquals(BenchmarkBackendVariant.AUTOMATIC, BenchmarkBackendVariant.parse("automatic"))
        assertEquals(BenchmarkBackendVariant.AUTOMATIC, BenchmarkBackendVariant.parse("default"))
        assertEquals("automatic", BenchmarkBackendVariant.parse("DEFAULT").wireValue)
        assertEquals("Automatic", BenchmarkBackendVariant.parse("default").backendLabel)
    }

    @Test
    fun `automatic engine config marker does not map to explicit GPU`() {
        val configParts = LiteRtLmGpuBenchmarkReceiver().resolveEngineConfigPartsForBenchmark(
            cacheDirPath = "/cache",
            backendVariant = BenchmarkBackendVariant.AUTOMATIC,
            maxOutputTokens = 32,
        )
        val markerDetail = configParts.markerDetail(BenchmarkBackendVariant.AUTOMATIC, 32)

        assertEquals(null, configParts.backend)
        assertEquals(null, configParts.visionBackend)
        assertEquals(null, configParts.audioBackend)
        assertEquals(true, configParts.useConstructorDefaultBackend)
        assertEquals(null, configParts.cacheDir)
        assertTrue(markerDetail.contains("backend_variant=automatic"))
        assertTrue(markerDetail.contains("backend=Automatic"))
        assertTrue(markerDetail.contains("engine_backend=Automatic"))
        assertTrue(markerDetail.contains("vision_backend=default"))
        assertTrue(markerDetail.contains("audio_backend=default"))
        assertTrue(markerDetail.contains("config_style=automatic"))
        assertTrue(!markerDetail.contains("engine_backend=GPU"))
        assertTrue(!markerDetail.contains("vision_backend=GPU"))
        assertTrue(!markerDetail.contains("config_style=explicit_gpu"))
    }

    @Test
    fun `GPU and CPU variants retain explicit config styles`() {
        assertEquals("explicit_gpu", BenchmarkBackendVariant.GPU.configStyle)
        assertEquals("explicit_cpu", BenchmarkBackendVariant.CPU.configStyle)
        assertEquals("GPU", BenchmarkBackendVariant.GPU.backendLabel)
        assertEquals("CPU", BenchmarkBackendVariant.CPU.backendLabel)
    }

    @Test
    fun `summary counts generic fallback automatic 20 run result`() {
        val rows = List(20) { successRow(backendVariant = BenchmarkBackendVariant.AUTOMATIC) }

        val summary = buildLiteRtLmGpuBenchmarkRunSummary(
            rows = rows,
            requestedRunCount = 20,
            modelPathSource = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
            genericFallbackModelConfigured = true,
        )

        assertEquals("Automatic", summary.backend)
        assertEquals(20, summary.requestedRunCount)
        assertEquals(20, summary.completedRunCount)
        assertEquals(20, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals(0, summary.timeoutCount)
        assertEquals(0, summary.fallbackCount)
        assertEquals("generic_fallback", summary.modelPathSource)
        assertEquals(true, summary.genericFallbackModelConfigured)
        assertEquals("success", summary.status)
        assertEquals("completed", summary.reason)
    }

    @Test
    fun `automatic generic fallback markdown contains required summary fields`() {
        val markdown = buildGpuBenchmarkMarkdown(
            timestamp = "20260701_120000",
            timeoutMs = 60_000L,
            rows = List(20) { successRow(backendVariant = BenchmarkBackendVariant.AUTOMATIC) },
        )

        assertTrue(markdown.contains("- backend: `Automatic`"))
        assertTrue(markdown.contains("- requested_run_count: `20`"))
        assertTrue(markdown.contains("- completed_run_count: `20`"))
        assertTrue(markdown.contains("- success_count: `20`"))
        assertTrue(markdown.contains("- failure_count: `0`"))
        assertTrue(markdown.contains("- timeout_count: `0`"))
        assertTrue(markdown.contains("- fallback_count: `0`"))
        assertTrue(markdown.contains("- model_path_source: `generic_fallback`"))
        assertTrue(markdown.contains("- backend_variants: `automatic`"))
    }

    @Test
    fun `summary counts generic fallback GPU 20 run result`() {
        val rows = List(18) { successRow(backendVariant = BenchmarkBackendVariant.GPU) } +
            listOf(
                failureRow(backendVariant = BenchmarkBackendVariant.GPU, reason = "blank_output"),
                failureRow(backendVariant = BenchmarkBackendVariant.GPU, reason = "case_timeout_60000ms", timeout = true),
            )

        val summary = buildLiteRtLmGpuBenchmarkRunSummary(
            rows = rows,
            requestedRunCount = 20,
            modelPathSource = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
            genericFallbackModelConfigured = true,
        )

        assertEquals("GPU", summary.backend)
        assertEquals(20, summary.requestedRunCount)
        assertEquals(20, summary.completedRunCount)
        assertEquals(18, summary.successCount)
        assertEquals(1, summary.failureCount)
        assertEquals(1, summary.timeoutCount)
        assertEquals(0, summary.fallbackCount)
        assertEquals("generic_fallback", summary.modelPathSource)
        assertEquals(true, summary.genericFallbackModelConfigured)
        assertEquals("failure", summary.status)
        assertEquals("timeout", summary.reason)
    }

    @Test
    fun `generic fallback missing is reported clearly in markdown`() {
        val rows = List(20) {
            failureRow(
                backendVariant = BenchmarkBackendVariant.CPU,
                reason = "generic_fallback_model_missing",
                modelPath = "",
                modelPathSource = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
                genericFallbackModelConfigured = false,
            )
        }

        val markdown = buildGpuBenchmarkMarkdown(
            timestamp = "20260701_120000",
            timeoutMs = 60_000L,
            rows = rows,
        )

        assertTrue(markdown.contains("- backend: `CPU`"))
        assertTrue(markdown.contains("- requested_run_count: `20`"))
        assertTrue(markdown.contains("- completed_run_count: `20`"))
        assertTrue(markdown.contains("- success_count: `0`"))
        assertTrue(markdown.contains("- failure_count: `20`"))
        assertTrue(markdown.contains("- timeout_count: `0`"))
        assertTrue(markdown.contains("- fallback_count: `0`"))
        assertTrue(markdown.contains("- model_path_source: `generic_fallback`"))
        assertTrue(markdown.contains("- generic_fallback_model_configured: `false`"))
        assertTrue(markdown.contains("- reason: `generic_fallback_model_missing`"))
    }

    @Test
    fun `csv includes model source columns`() {
        val csv = buildGpuBenchmarkCsv(
            listOf(
                successRow(
                    backendVariant = BenchmarkBackendVariant.CPU,
                    modelPathSource = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
                    genericFallbackModelConfigured = true,
                ),
            ),
        )

        assertTrue(csv.contains("\"model_path_source\""))
        assertTrue(csv.contains("\"generic_fallback_model_configured\""))
        assertTrue(csv.contains("\"generic_fallback\""))
    }

    private fun successRow(
        backendVariant: BenchmarkBackendVariant,
        modelPathSource: String = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
        genericFallbackModelConfigured: Boolean = true,
    ): LiteRtLmGpuBenchmarkRow =
        LiteRtLmGpuBenchmarkRow(
            timestamp = "20260701_120000",
            routeType = "litert_lm_gpu_benchmark",
            backend = backendVariant.backendLabel,
            backendVariant = backendVariant.wireValue,
            closePolicy = BenchmarkClosePolicy.NORMAL.wireValue,
            phase = BenchmarkPhase.SEND_MESSAGE.wireValue,
            prompt = "こんにちは",
            maxOutputTokens = 32,
            maxOutputTokensList = "32",
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/generic.litertlm",
            modelExists = true,
            modelLength = 123L,
            engineCreateMs = 10L,
            conversationCreateMs = 20L,
            firstTokenMs = 30L,
            ttftMs = 30L,
            decodeMs = 40L,
            totalMs = 80L,
            outputTokens = 4,
            tokensPerSecond = 100.0,
            finishReason = null,
            stopReason = null,
            rawOutput = "ok",
            sanitizedOutput = "ok",
            status = "success",
            reason = "completed",
            sendExceptionClass = null,
            sendExceptionMessage = null,
            sendExceptionCauseChain = null,
            intentionallyLeakedForDiagnostic = false,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            sendApiVariant = "flow_string_with_blocking_fallback",
            samplerTopK = null,
            samplerTopP = null,
            samplerTemperature = null,
            conversationConfigUsed = false,
            contentsApiUsed = false,
            modelPathSource = modelPathSource,
            genericFallbackModelConfigured = genericFallbackModelConfigured,
        )

    private fun failureRow(
        backendVariant: BenchmarkBackendVariant,
        reason: String,
        timeout: Boolean = false,
        modelPath: String = "/data/user/0/io.github.ninbyo02.lami/files/generic.litertlm",
        modelPathSource: String = BenchmarkModelPathSource.GENERIC_FALLBACK.wireValue,
        genericFallbackModelConfigured: Boolean = true,
    ): LiteRtLmGpuBenchmarkRow =
        LiteRtLmGpuBenchmarkRow.failure(
            timestamp = "20260701_120000",
            backendVariant = backendVariant,
            closePolicy = BenchmarkClosePolicy.NORMAL,
            phase = BenchmarkPhase.SEND_MESSAGE,
            maxOutputTokensList = "32",
            prompt = "こんにちは",
            maxOutputTokens = 32,
            modelPath = modelPath,
            reason = reason,
            modelExists = modelPath.isNotBlank(),
            modelLength = if (modelPath.isBlank()) 0L else 123L,
            timeout = timeout,
            freshCrash = false,
            modelPathSource = modelPathSource,
            genericFallbackModelConfigured = genericFallbackModelConfigured,
        )
}
