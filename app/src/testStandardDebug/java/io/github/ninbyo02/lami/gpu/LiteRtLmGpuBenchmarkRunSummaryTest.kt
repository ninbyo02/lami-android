package io.github.ninbyo02.lami.gpu

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmGpuBenchmarkRunSummaryTest {
    @Test
    fun `atomic UTF-8 publication replaces complete content and removes temporary files`() {
        val directory = Files.createTempDirectory("gpu-report-atomic").toFile()
        try {
            val target = File(directory, "report.csv")
            target.writeText("stale", Charsets.UTF_8)

            writeUtf8Atomically(target, "complete UTF-8 report: 日本語\n")

            assertEquals("complete UTF-8 report: 日本語\n", target.readText(Charsets.UTF_8))
            assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith(".${target.name}.") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `atomic UTF-8 publication removes temporary file when replacement fails`() {
        val directory = Files.createTempDirectory("gpu-report-atomic-failure").toFile()
        try {
            val targetDirectory = File(directory, "report.csv").apply { mkdir() }
            var failed = false

            try {
                writeUtf8Atomically(targetDirectory, "must not publish")
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue("replacement failure must propagate", failed)
            assertTrue("failed replacement must preserve the existing target", targetDirectory.isDirectory)
            assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith(".${targetDirectory.name}.") })
        } finally {
            directory.deleteRecursively()
        }
    }

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
    fun `unsafe resource skips are not counted as completed runs`() {
        val rows = listOf(
            failureRow(
                backendVariant = BenchmarkBackendVariant.GPU,
                reason = "engine_close_timeout",
                timeout = true,
            ),
            failureRow(
                backendVariant = BenchmarkBackendVariant.GPU,
                reason = "skipped_after_unsafe_resource_state",
            ),
        )

        val summary = buildLiteRtLmGpuBenchmarkRunSummary(rows = rows, requestedRunCount = 2)

        assertEquals(1, summary.completedRunCount)
        assertEquals(0, summary.failureCount)
        assertEquals(1, summary.timeoutCount)
        assertEquals("failure", summary.status)
        assertEquals("timeout", summary.reason)
    }

    @Test
    fun `failed attempt followed by unsafe skip counts only the attempted failure`() {
        val summary = buildLiteRtLmGpuBenchmarkRunSummary(
            rows = listOf(
                failureRow(BenchmarkBackendVariant.GPU, reason = "engine_create_failed"),
                failureRow(BenchmarkBackendVariant.GPU, reason = "skipped_after_unsafe_resource_state"),
            ),
            requestedRunCount = 2,
        )

        assertEquals(1, summary.completedRunCount)
        assertEquals(0, summary.successCount)
        assertEquals(1, summary.failureCount)
        assertEquals(0, summary.timeoutCount)
        assertEquals("failure", summary.status)
        assertEquals("engine_create_failed", summary.reason)
    }

    @Test
    fun `successful attempt plus skipped row cannot be accepted as completed success`() {
        val summary = buildLiteRtLmGpuBenchmarkRunSummary(
            rows = listOf(
                successRow(BenchmarkBackendVariant.GPU),
                failureRow(BenchmarkBackendVariant.GPU, reason = "skipped_after_unsafe_resource_state"),
            ),
            requestedRunCount = 2,
        )

        assertEquals(1, summary.completedRunCount)
        assertEquals(1, summary.successCount)
        assertEquals(0, summary.failureCount)
        assertEquals("partial", summary.status)
        assertEquals("partial_success", summary.reason)
    }

    @Test
    fun `atomic benchmark state write replaces content without temporary residue`() {
        val directory = Files.createTempDirectory("lami-benchmark-state-test").toFile()
        try {
            val state = directory.resolve("state.txt")
            state.writeText("old\n")

            writeUtf8Atomically(state, "new\n")

            assertEquals("new\n", state.readText())
            assertTrue(directory.listFiles().orEmpty().none { it.name != state.name })
        } finally {
            directory.deleteRecursively()
        }
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

    @Test
    fun `total context 16 passes with eight generated tokens`() {
        val evidence = DebugTokenBenchmarkResultEvidence(
            status = "success",
            requestedTokens = 16,
            effectiveTokens = 16,
            outputTokens = 8,
            outputTokenSource = "LiteRT benchmarkInfo.lastDecodeTokenCount",
            tokensPerSecond = 1.0,
            totalMs = 1L,
            finishReason = null,
            timeout = false,
            fallback = false,
            freshCrash = false,
            finishEvidence = true,
        )
        assertTrue(evidence.passed)
    }

    @Test
    fun `foreground total context suite includes GPU 16`() {
        assertEquals("GPU 16", DebugTokenBenchmarkCase.GPU_16.label)
        assertEquals(16, DebugTokenBenchmarkCase.GPU_16.requestedTokens)
    }

    @Test
    fun `successful total context run permits unavailable SDK decode token count`() {
        val evidence = DebugTokenBenchmarkResultEvidence(
            status = "success",
            requestedTokens = 32,
            effectiveTokens = 32,
            outputTokens = null,
            outputTokenSource = "unavailable",
            tokensPerSecond = null,
            totalMs = 1L,
            finishReason = "completed",
            timeout = false,
            fallback = false,
            freshCrash = false,
            finishEvidence = true,
        )

        assertTrue(evidence.passed)
    }

    @Test
    fun `csv carries measured prefill flow callback and finish availability evidence`() {
        val row = successRow(BenchmarkBackendVariant.GPU).copy(
            measuredPrefillTokens = 25_840,
            prefillTokenSource = "LiteRT benchmarkInfo.lastPrefillTokenCount",
            outputTokenSource = "LiteRT benchmarkInfo.lastDecodeTokenCount",
            emitCount = 4,
            nonemptyEmitCount = 3,
            rawLength = 12,
            sanitizedLength = 9,
            firstNonemptyMs = 42L,
            flowExceptionType = null,
            finishReasonAvailable = true,
            stopReasonAvailable = false,
            callbackOnMessageCount = 3,
            callbackOnDoneCount = 1,
            callbackOnErrorCount = 0,
            chunkTypeLengthSummary = "Content=3:12",
        )

        val records = DebugTokenBenchmarkCsvParser.records(buildGpuBenchmarkCsv(listOf(row)))
        val values = DebugTokenBenchmarkCsvParser.cells(records.single { it.startsWith("\"") && "20260701_120000" in it })
        val headers = DebugTokenBenchmarkCsvParser.cells(records.first())
        val mapped = headers.zip(values).toMap()

        assertEquals("25840", mapped["measured_prefill_tokens"])
        assertEquals("LiteRT benchmarkInfo.lastPrefillTokenCount", mapped["prefill_token_source"])
        assertEquals("4", mapped["emit_count"])
        assertEquals("3", mapped["callback_on_message_count"])
        assertEquals("true", mapped["finish_reason_available"])
        assertEquals("Content=3:12", mapped["chunk_type_length_summary"])
    }

    @Test
    fun `callback accumulator records thread safe type length and partial evidence`() {
        val accumulator = CallbackObservationAccumulator()
        val workers = (0 until 8).map { index ->
            Thread { accumulator.onMessage("Text", "chunk-$index", index.toLong()) }
        }
        workers.forEach(Thread::start)
        workers.forEach(Thread::join)
        accumulator.onError()

        val snapshot = accumulator.snapshot()
        assertEquals(8, snapshot.emitCount)
        assertEquals(8, snapshot.nonemptyEmitCount)
        assertEquals(1, snapshot.callbackOnErrorCount)
        assertTrue(snapshot.chunkTypeLengthSummary.contains("Text:7"))
        assertTrue(snapshot.rawOutput.isNotBlank())
    }

    @Test
    fun `callback accumulator freezes the first terminal snapshot and ignores late callbacks`() {
        val accumulator = CallbackObservationAccumulator()
        accumulator.onMessage("Text", "before", 7L)

        val doneWon = accumulator.onDone()
        val errorLost = accumulator.onError()
        val lateMessageWasFirst = accumulator.onMessage("Text", "after", 9L)
        val snapshot = accumulator.snapshot()

        assertTrue(doneWon)
        assertTrue(!errorLost)
        assertTrue(!lateMessageWasFirst)
        assertEquals("before", snapshot.rawOutput)
        assertEquals(1, snapshot.callbackOnMessageCount)
        assertEquals(1, snapshot.callbackOnDoneCount)
        assertEquals(0, snapshot.callbackOnErrorCount)
    }

    @Test
    fun `callback accumulator allows exactly one terminal winner under race`() {
        repeat(40) {
            val accumulator = CallbackObservationAccumulator()
            val done = Thread { accumulator.onDone() }
            val error = Thread { accumulator.onError() }
            done.start()
            error.start()
            done.join()
            error.join()

            val snapshot = accumulator.snapshot()
            assertEquals(1, snapshot.callbackOnDoneCount + snapshot.callbackOnErrorCount)
        }
    }

    @Test
    fun `callback timeout competes as an atomic terminal winner`() {
        repeat(40) {
            val accumulator = CallbackObservationAccumulator()
            val done = Thread { accumulator.onDone() }
            val timeout = Thread { accumulator.onTimeout() }
            done.start()
            timeout.start()
            done.join()
            timeout.join()

            assertTrue(accumulator.terminalKind() in setOf(CallbackTerminalKind.DONE, CallbackTerminalKind.TIMEOUT))
            assertEquals(1, accumulator.snapshot().callbackOnDoneCount + if (accumulator.terminalKind() == CallbackTerminalKind.TIMEOUT) 1 else 0)
        }
    }

    @Test
    fun `real flow failure preserves partial observation and actual collect cause`() {
        val failure = try {
            runBlocking {
                collectStringFlowForBenchmark(
                    chunks = flow {
                        emit("partial")
                        throw IllegalStateException("boom")
                    },
                    elapsedMs = { 11L },
                )
            }
            null
        } catch (throwable: SendObservationException) {
            throwable
        }

        assertEquals("partial", failure?.observation?.rawOutput)
        assertEquals(1, failure?.observation?.emitCount)
        assertEquals(IllegalStateException::class.java, failure?.cause?.javaClass)
        assertEquals("boom", failure?.cause?.message)
    }

    @Test
    fun `blocking fallback keeps successful output and durable partial flow evidence`() {
        val partial = SendObservation(
            rawOutput = "partial",
            emitCount = 2,
            nonemptyEmitCount = 1,
            firstNonemptyMs = 11L,
            callbackOnMessageCount = 0,
            callbackOnDoneCount = 0,
            callbackOnErrorCount = 0,
            chunkTypeLengthSummary = "not_callback",
        )
        val blocking = SendObservation.blocking("complete", 25L)
        val merged = blocking.withFlowPartialEvidence(partial)

        assertEquals("complete", merged.rawOutput)
        assertEquals("partial", merged.flowPartialRawOutput)
        assertEquals(2, merged.flowPartialEmitCount)
        assertEquals(1, merged.flowPartialNonemptyEmitCount)
        assertEquals(11L, merged.flowPartialFirstNonemptyMs)
    }

    @Test
    fun `blocking fallback failure keeps dedicated partial flow evidence`() {
        val partial = SendObservation(
            rawOutput = "partial",
            emitCount = 2,
            nonemptyEmitCount = 1,
            firstNonemptyMs = 11L,
            callbackOnMessageCount = 0,
            callbackOnDoneCount = 0,
            callbackOnErrorCount = 0,
            chunkTypeLengthSummary = "not_callback",
        )

        val failure = mergeFlowFailureWithBlockingFailure(partial, IllegalStateException("blocking"))

        assertEquals("partial", failure.observation.flowPartialRawOutput)
        assertEquals(2, failure.observation.flowPartialEmitCount)
        assertEquals(IllegalStateException::class.java, failure.cause?.javaClass)
    }

    @Test
    fun `blocking fallback failure does not wrap interruption`() {
        Thread.interrupted()
        val interruption = InterruptedException("stop")
        try {
            mergeFlowFailureWithBlockingFailure(null, interruption)
            throw AssertionError("interruption was wrapped")
        } catch (actual: InterruptedException) {
            assertTrue(actual === interruption)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `cancel command matches only the active run timestamp`() {
        assertTrue(cancelTimestampMatches("20260724_220000", "20260724_220000"))
        assertTrue(!cancelTimestampMatches("20260724_220000", "20260724_220001"))
        assertTrue(!cancelTimestampMatches(null, "20260724_220000"))
    }

    @Test
    fun `cancellation and interruption remain control flow`() {
        val cancellation = java.util.concurrent.CancellationException("cancel")
        try {
            rethrowCancellationOrInterrupt(cancellation)
            throw AssertionError("cancellation was swallowed")
        } catch (actual: java.util.concurrent.CancellationException) {
            assertTrue(actual === cancellation)
        }

        Thread.interrupted()
        val interruption = InterruptedException("interrupt")
        try {
            rethrowCancellationOrInterrupt(interruption)
            throw AssertionError("interruption was swallowed")
        } catch (actual: InterruptedException) {
            assertTrue(actual === interruption)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `csv preserves public zero decode token count`() {
        val records = DebugTokenBenchmarkCsvParser.records(
            buildGpuBenchmarkCsv(listOf(successRow(BenchmarkBackendVariant.GPU).copy(outputTokens = 0))),
        )
        val headers = DebugTokenBenchmarkCsvParser.cells(records.first())
        val values = DebugTokenBenchmarkCsvParser.cells(records.drop(1).single())
        assertEquals("0", headers.zip(values).toMap()["output_tokens"])
    }

    @Test
    fun `csv writes unavailable literally for missing public token measurements`() {
        val records = DebugTokenBenchmarkCsvParser.records(
            buildGpuBenchmarkCsv(
                listOf(
                    successRow(BenchmarkBackendVariant.GPU).copy(
                        outputTokens = null,
                        measuredPrefillTokens = null,
                        outputTokenSource = "unavailable",
                        prefillTokenSource = "unavailable",
                    ),
                ),
            ),
        )
        val headers = DebugTokenBenchmarkCsvParser.cells(records.first())
        val values = DebugTokenBenchmarkCsvParser.cells(records.drop(1).single())
        val mapped = headers.zip(values).toMap()

        assertEquals("unavailable", mapped["output_tokens"])
        assertEquals("unavailable", mapped["measured_prefill_tokens"])
        assertEquals("unavailable", mapped["output_token_source"])
        assertEquals("unavailable", mapped["prefill_token_source"])
    }

    @Test
    fun `public benchmark measurement rejects negative sentinel values`() {
        val evidence = BenchmarkMeasurementEvidence.fromPublicApi(
            prefillTokens = -1,
            decodeTokens = -1,
        )

        assertNull(evidence.measuredPrefillTokens)
        assertNull(evidence.outputTokens)
        assertEquals("unavailable", evidence.prefillTokenSource)
        assertEquals("unavailable", evidence.outputTokenSource)
    }

    @Test
    fun `public benchmark measurement preserves zero and names exact SDK fields`() {
        val evidence = BenchmarkMeasurementEvidence.fromPublicApi(
            prefillTokens = 0,
            decodeTokens = 0,
        )

        assertEquals(0, evidence.measuredPrefillTokens)
        assertEquals(0, evidence.outputTokens)
        assertEquals("LiteRT benchmarkInfo.lastPrefillTokenCount", evidence.prefillTokenSource)
        assertEquals("LiteRT benchmarkInfo.lastDecodeTokenCount", evidence.outputTokenSource)
    }

    @Test
    fun `flow exception evidence is not attributed to typed callback failures`() {
        val failure = IllegalStateException("boom")

        assertEquals(
            IllegalStateException::class.java.name,
            flowExceptionType(BenchmarkSendApiMode.FLOW_STRING, failure),
        )
        assertNull(flowExceptionType(BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK, failure))
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
