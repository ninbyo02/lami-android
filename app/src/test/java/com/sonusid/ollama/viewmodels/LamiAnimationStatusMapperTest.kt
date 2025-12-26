package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LamiAnimationStatusMapperTest {

    @Test
    fun `prioritizes talking when tts is playing`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.RESPONDING,
            uiState = UiState.Loading,
            selectedModel = "llama3",
            isTtsPlaying = true,
        )

        assertEquals(LamiStatus.TALKING, result)
    }

    @Test
    fun `returns connecting when loading`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.THINKING,
            uiState = UiState.Loading,
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.CONNECTING, result)
    }

    @Test
    fun `returns error when uiState has error`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.IDLE,
            uiState = UiState.Error("network"),
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.ERROR, result)
    }

    @Test
    fun `returns no models when selection missing`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.IDLE,
            uiState = UiState.Success("ok"),
            selectedModel = null,
        )

        assertEquals(LamiStatus.NO_MODELS, result)
    }

    @Test
    fun `returns ready on normal path`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.IDLE,
            uiState = UiState.Success("ok"),
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.READY, result)
    }
}
