package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1ArtifactFreshnessTest {
    private val nowMs = 2_000_000_000_000L

    @Test
    fun `fresh artifact within twenty four hours is fresh`() {
        val result = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = nowMs - DevOnlyNpuPhaseH1ArtifactFreshness.DEFAULT_FRESHNESS_WINDOW_MS,
            nowMs = nowMs,
        )

        assertEquals(DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.FRESH, result.status)
        assertEquals("fresh", result.reasonCode)
        assertTrue(result.artifactFresh)
    }

    @Test
    fun `artifact older than twenty four hours is stale`() {
        val result = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = nowMs - DevOnlyNpuPhaseH1ArtifactFreshness.DEFAULT_FRESHNESS_WINDOW_MS - 1L,
            nowMs = nowMs,
        )

        assertEquals(DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE, result.status)
        assertEquals("stale_artifact", result.reasonCode)
        assertFalse(result.artifactFresh)
    }

    @Test
    fun `missing timestamp is stale or unknown`() {
        val result = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = null,
            nowMs = nowMs,
        )

        assertEquals(DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE_OR_UNKNOWN, result.status)
        assertEquals("stale_or_unknown", result.reasonCode)
        assertFalse(result.artifactFresh)
    }

    @Test
    fun `future timestamp is stale or invalid`() {
        val result = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = nowMs + 1L,
            nowMs = nowMs,
        )

        assertEquals(DevOnlyNpuPhaseH1ArtifactFreshnessResult.Status.STALE_OR_INVALID, result.status)
        assertEquals("stale_or_invalid", result.reasonCode)
        assertFalse(result.artifactFresh)
    }

    @Test
    fun `timestamp can be read from supported artifact keys`() {
        assertEquals(10L, DevOnlyNpuPhaseH1ArtifactFreshness.timestampFrom(mapOf("artifact_timestamp_ms" to "10")))
        assertEquals(11L, DevOnlyNpuPhaseH1ArtifactFreshness.timestampFrom(mapOf("artifact_timestamp" to "11")))
        assertEquals(12L, DevOnlyNpuPhaseH1ArtifactFreshness.timestampFrom(mapOf("synced_at" to "12")))
        assertEquals(13L, DevOnlyNpuPhaseH1ArtifactFreshness.timestampFrom(mapOf("created_at" to "13")))
    }
}
