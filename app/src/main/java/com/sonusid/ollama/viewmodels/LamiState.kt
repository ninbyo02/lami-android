package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState

enum class LamiState {
    IDLE,
    THINKING,
    ERROR,
}

fun mapToLamiState(uiState: UiState, selectedModel: String?): LamiState {
    if (selectedModel.isNullOrBlank()) {
        return LamiState.ERROR
    }
    return when (uiState) {
        UiState.Loading -> LamiState.THINKING
        is UiState.Error -> LamiState.IDLE
        is UiState.Success -> LamiState.IDLE
        UiState.Initial -> LamiState.IDLE
    }
}
