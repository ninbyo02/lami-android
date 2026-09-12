package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class ExactTokenCountCacheTest {
    private fun key(text: String) = ExactTokenCountKey("model", 100, 1, tokenTextDigest(text), tokenTextDigest("答え"))

    @Test fun separatesModelIdentityAndExactUnicodeText() {
        val cache = ExactTokenCountCache<Int>(16)
        val first = key("こんにちは")
        cache.put(first, 7)
        assertEquals(7, cache.get(key("こんにちは")))
        assertNull(cache.get(key("こんにちは ")))
        assertNull(cache.get(first.copy(modelPath = "other")))
        assertNull(cache.get(first.copy(modelSize = 101)))
        assertNull(cache.get(first.copy(modelModified = 2)))
        assertNull(cache.get(first.copy(responseDigest = tokenTextDigest("別の答え"))))
    }

    @Test fun evictsLeastRecentlyUsedAndRetainsZeroCount() {
        val cache = ExactTokenCountCache<Int>(2)
        cache.put(key("a"), 0)
        cache.put(key("b"), 2)
        assertEquals(0, cache.get(key("a")))
        cache.put(key("c"), 3)
        assertNull(cache.get(key("b")))
        assertEquals(0, cache.get(key("a")))
        assertEquals(3, cache.get(key("c")))
    }

    @Test fun postTerminalMetadataPreservesRouteAndCompletionTiming() {
        val source = "source_summary=effective_backend=GPU fallback_used=false\ngeneration_finished_at_elapsed_ms=50\ntokenizer_count_duration_ms=unavailable\nstats_final_display_used_tokenizer_tokens=false"
        val updated = mergePostTerminalTokenCountDiagnostics(source, LocalInferenceMeasuredTokenSnapshot(
            inputTokens = 10, outputTokens = 2, totalTokens = 12,
            tokenizerCountStartedAtElapsedMs = 60, tokenizerCountFinishedAtElapsedMs = 80,
            tokenizerCountDurationMs = 20,
        ))
        assertTrue(updated.contains("source_summary=effective_backend=GPU fallback_used=false"))
        assertTrue(updated.contains("generation_finished_at_elapsed_ms=50"))
        assertTrue(updated.contains("stats_final_display_used_tokenizer_tokens=true"))
        assertTrue(updated.contains("tokenizer_count_duration_ms=20"))
        assertFalse(updated.contains("tokenizer_count_duration_ms=unavailable"))
    }

    @Test fun failedRecountDoesNotClaimExactTokens() {
        val updated = mergePostTerminalTokenCountDiagnostics("", LocalInferenceMeasuredTokenSnapshot())
        assertTrue(updated.contains("stats_final_display_used_tokenizer_tokens=false"))
        assertTrue(updated.contains("stats_token_metrics_final_source=estimated_tokens"))
    }
}
