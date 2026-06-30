package io.github.ninbyo02.lami.viewmodels

import io.github.ninbyo02.lami.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LamiAnimationStatusMapperTest {

    @Test
    fun `prioritizes talking when speaking state`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.Speaking(12),
            uiState = UiState.Loading,
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.TALKING, result)
    }

    @Test
    fun `returns connecting when loading`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.Thinking,
            uiState = UiState.Loading,
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.CONNECTING, result)
    }

    @Test
    fun `returns error when uiState has non network error`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.Idle,
            uiState = UiState.Error("validation failed"),
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.ERROR, result)
    }

    @Test
    fun `returns offline when selection missing`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.Idle,
            uiState = UiState.Success("ok"),
            selectedModel = null,
        )

        assertEquals(LamiStatus.OFFLINE, result)
    }

    @Test
    fun `returns ready on normal path`() {
        val result = mapToAnimationLamiStatus(
            lamiState = LamiState.Idle,
            uiState = UiState.Success("ok"),
            selectedModel = "llama3",
        )

        assertEquals(LamiStatus.READY, result)
    }
}
