package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ChatScreenGateTest {
    @Test
    fun `gate off preserves existing route`() {
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = false,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
    }

    @Test
    fun `gate on enters bridge path only for local text prompt`() {
        assertTrue(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.SERVER,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = true,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "",
            ),
        )
    }

    @Test
    fun `bridge path keeps side effects disconnected`() {
        val result = NpuStandardRouteS1Bridge().run()

        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }
}
