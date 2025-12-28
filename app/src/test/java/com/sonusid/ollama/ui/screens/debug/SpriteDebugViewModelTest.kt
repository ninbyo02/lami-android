package com.sonusid.ollama.ui.screens.debug

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.sonusid.ollama.util.SpriteAnalysis
import com.sonusid.ollama.viewmodels.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpriteDebugViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initializeState ignores corrupted saved state and falls back to default`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(KEY_STATE to "corrupted"))
        val dataStore = FakeSpriteDebugDataStore()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val viewModel = SpriteDebugViewModel(
            savedStateHandle = savedStateHandle,
            dataStore = dataStore,
            defaultDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            gson = Gson(),
        )

        advanceUntilIdle()

        val currentState = viewModel.uiState.value
        val defaultState = SpriteDebugState()
        assertNotNull(currentState)
        assertEquals(defaultState.selectedBoxIndex, currentState.selectedBoxIndex)
        assertEquals(defaultState.boxes, currentState.boxes)
        assertEquals(defaultState.spriteSheetConfig, currentState.spriteSheetConfig)
        assertEquals(currentState, savedStateHandle.get<SpriteDebugState>(KEY_STATE))
    }

    @Test
    fun `autoSearchAll runs analysis asynchronously and updates scores`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val dataStore = FakeSpriteDebugDataStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SpriteDebugViewModel(
            savedStateHandle = savedStateHandle,
            dataStore = dataStore,
            defaultDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            gson = Gson(),
        )
        val sprite = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        viewModel.setSpriteSheet(sprite)
        advanceUntilIdle()

        viewModel.autoSearchAll()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(8, state.matchScores.size)
        assertNotNull(dataStore.storedAnalysisResult)
    }

    @Test
    fun `cancelAnalysis clears loading flag`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val dataStore = FakeSpriteDebugDataStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SpriteDebugViewModel(
            savedStateHandle = savedStateHandle,
            dataStore = dataStore,
            defaultDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            gson = Gson(),
        )
        val sprite = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        viewModel.setSpriteSheet(sprite)
        advanceUntilIdle()

        viewModel.autoSearchAll()
        viewModel.cancelAnalysis()
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
    }
}

private const val KEY_STATE = "sprite_debug_state"

private class FakeSpriteDebugDataStore : SpriteDebugDataStore {
    var storedState: SpriteDebugState? = null
    var storedAnalysisResult: SpriteAnalysis.SpriteAnalysisResult? = null

    override suspend fun readState(): SpriteDebugState? = storedState

    override suspend fun saveState(state: SpriteDebugState) {
        storedState = state
    }

    override suspend fun readAnalysisResult(): SpriteAnalysis.SpriteAnalysisResult? = storedAnalysisResult

    override suspend fun saveAnalysisResult(result: SpriteAnalysis.SpriteAnalysisResult) {
        storedAnalysisResult = result
    }

    override suspend fun clearAnalysis() {
        storedAnalysisResult = null
    }
}
