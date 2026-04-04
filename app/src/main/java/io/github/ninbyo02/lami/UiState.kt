package io.github.ninbyo02.lami


sealed interface UiState {

    object Initial : UiState

    object Loading : UiState

    data class Streaming(val partialText: String) : UiState

    data class Success(val outputText: String) : UiState

    data class Error(val errorMessage: String) : UiState

    data class ModelsLoaded(val models: List<String>) : UiState
}
