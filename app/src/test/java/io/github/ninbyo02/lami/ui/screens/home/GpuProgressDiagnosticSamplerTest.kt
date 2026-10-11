package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class GpuProgressDiagnosticSamplerTest {
    @Test fun `burst keeps first and periodic progress snapshots`() {
        val sampler = GpuProgressDiagnosticSampler()
        assertTrue(sampler.shouldEmit("generate_callback_invoked", 100))
        assertFalse(sampler.shouldEmit("generate_callback_invoked", 101))
        assertFalse(sampler.shouldEmit("generate_callback_invoked", 1099))
        assertTrue(sampler.shouldEmit("generate_callback_invoked", 1100))
        assertTrue(sampler.shouldEmit("generate_ui_append_finished", 1100))
    }
    @Test fun `terminal failure and first token are never suppressed`() {
        val sampler = GpuProgressDiagnosticSampler()
        repeat(2) {
            for (stage in listOf("first_token_received", "generate_streaming_completed", "generate_exception", "conversation_create_finished")) {
                assertTrue(sampler.shouldEmit(stage, 100))
            }
        }
    }
}
