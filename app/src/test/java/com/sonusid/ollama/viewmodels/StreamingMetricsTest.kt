package com.sonusid.ollama.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMetricsTest {
    @Test
    fun `captures on first non-empty assistant chunk only`() {
        assertTrue(shouldCaptureFirstAssistantToken(existingLatencyMs = null, chunkText = "a"))
        assertFalse(shouldCaptureFirstAssistantToken(existingLatencyMs = 12L, chunkText = "b"))
    }

    @Test
    fun `does not capture on empty chunk`() {
        assertFalse(shouldCaptureFirstAssistantToken(existingLatencyMs = null, chunkText = ""))
        assertFalse(shouldCaptureFirstAssistantToken(existingLatencyMs = null, chunkText = "   "))
        assertFalse(shouldCaptureFirstAssistantToken(existingLatencyMs = null, chunkText = null))
    }
}
