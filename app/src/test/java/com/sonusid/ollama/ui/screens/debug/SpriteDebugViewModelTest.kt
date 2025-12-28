package com.sonusid.ollama.ui.screens.debug

import androidx.lifecycle.SavedStateHandle
import com.sonusid.ollama.viewmodels.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpriteDebugViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initializeState ignores corrupted saved state and falls back to default`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(KEY_STATE to "corrupted"))

        val viewModel = SpriteDebugViewModel(savedStateHandle)

        advanceUntilIdle()

        val currentState = viewModel.uiState.value
        val defaultState = SpriteDebugState()
        assertNotNull(currentState)
        assertEquals(defaultState.selectedBoxIndex, currentState.selectedBoxIndex)
        assertEquals(defaultState.boxes, currentState.boxes)
        assertEquals(defaultState.spriteSheetConfig, currentState.spriteSheetConfig)
        assertEquals(currentState, savedStateHandle.get<SpriteDebugState>(KEY_STATE))
    }
}

private const val KEY_STATE = "sprite_debug_state"
