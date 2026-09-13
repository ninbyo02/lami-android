package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StandaloneGpuTokenCounterTest {
    private fun model(test: (File) -> Unit) {
        val file = File.createTempFile("gpu-tokenizer", ".litertlm")
        try { file.writeBytes(byteArrayOf(1)); test(file) } finally { file.delete() }
    }
    @Test fun disabledAndForcedFallbackDoNotReadOrCount() = model { file ->
        val counter = StandaloneGpuTokenCounter(read = { error("read") }, count = { _, _, _ -> error("count") })
        assertEquals("disabled", counter.attempt(false, false, file.path, "a", "b").status)
        assertEquals("forced-fallback", counter.attempt(true, true, file.path, "a", "b").status)
    }
    @Test fun cachedResultSkipsReadingAndCountingButModelOrTextChangesInvalidate() = model { file ->
        var reads = 0; var counts = 0
        val counter = StandaloneGpuTokenCounter(read = { reads++; it.readBytes() }, count = { _, _, _ -> counts++; StandaloneTokenCounts(2, 3) }, fingerprint = { VERIFIED_GPU_TOKENIZER_SHA256 })
        assertFalse(counter.attempt(true, false, file.path, "a", "b").cacheHit)
        assertTrue(counter.attempt(true, false, file.path, "a", "b").cacheHit)
        assertEquals(1, reads); assertEquals(1, counts)
        file.appendBytes(byteArrayOf(2))
        assertFalse(counter.attempt(true, false, file.path, "a", "b").cacheHit)
        assertFalse(counter.attempt(true, false, file.path, "a", "c").cacheHit)
        assertEquals(3, counts)
    }
    @Test fun unsupportedTokenizerNeverEntersNativeCode() = model { file ->
        val counter = StandaloneGpuTokenCounter(read = { byteArrayOf(1) }, count = { _, _, _ -> error("native invoked") }, fingerprint = { "unknown" })
        assertEquals("unsupported-tokenizer", counter.attempt(true, false, file.path, "a", "b").status)
    }
    @Test fun missingLibraryFailureIsNotCachedAndNextCallCanRecover() = model { file ->
        var calls = 0
        val counter = StandaloneGpuTokenCounter(read = { byteArrayOf(1) }, count = { _, _, _ ->
            if (calls++ == 0) throw UnsatisfiedLinkError()
            StandaloneTokenCounts(1, 2)
        }, fingerprint = { VERIFIED_GPU_TOKENIZER_SHA256 })
        assertEquals("native-unavailable", counter.attempt(true, false, file.path, "a", "b").status)
        assertEquals(StandaloneTokenCounts(1, 2), counter.attempt(true, false, file.path, "a", "b").counts)
        assertEquals(2, calls)
    }
    @Test fun invalidCountsAndParseFailuresFallBack() = model { file ->
        val failed = StandaloneGpuTokenCounter(read = { throw IllegalArgumentException() }, count = { _, _, _ -> error("count") })
        assertEquals("failed", failed.attempt(true, false, file.path, "a", "b").status)
        for (value in listOf(StandaloneTokenCounts(-1, 0), StandaloneTokenCounts(Int.MAX_VALUE, 1))) {
            val counter = StandaloneGpuTokenCounter(read = { byteArrayOf(1) }, count = { _, _, _ -> value }, fingerprint = { VERIFIED_GPU_TOKENIZER_SHA256 })
            assertEquals("failed", counter.attempt(true, false, file.path, "a", "b").status)
        }
    }
    @Test fun modelChangedDuringCountIsNotPublishedOrCached() = model { file ->
        val counter = StandaloneGpuTokenCounter(read = { byteArrayOf(1) }, count = { _, _, _ -> file.appendBytes(byteArrayOf(2)); StandaloneTokenCounts(1, 2) }, fingerprint = { VERIFIED_GPU_TOKENIZER_SHA256 })
        assertEquals("model-changed", counter.attempt(true, false, file.path, "a", "b").status)
    }
    @Test fun cancellationIsNotARecoverableFailure() = model { file ->
        val counter = StandaloneGpuTokenCounter(read = { throw CancellationException() }, count = { _, _, _ -> error("count") })
        try { counter.attempt(true, false, file.path, "a", "b"); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun newProviderRemainsTokenizerBasedAndDiagnosticsExposeProvenance() {
        assertTrue(isTokenizerRecountMode(STANDALONE_TOKEN_COUNT_MODE))
        assertFalse(isTokenizerRecountMode("estimated_code_points"))
        val snapshot = LocalInferenceMeasuredTokenSnapshot(inputTokens = 1, outputTokens = 2,
            tokenCountMode = STANDALONE_TOKEN_COUNT_MODE, recountProvider = "standalone_sentencepiece", recountCacheHit = true)
        val summary = mergePostTerminalTokenCountDiagnostics("", snapshot)
        assertTrue(summary.contains("tokenizer_count_provider=standalone_sentencepiece"))
        assertTrue(summary.contains("tokenizer_result_cache_hit=true"))
        assertFalse(summary.contains("stats_token_metrics_final_source=estimated_tokens"))
    }
}
