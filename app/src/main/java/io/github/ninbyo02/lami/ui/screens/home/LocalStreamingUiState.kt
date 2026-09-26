package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalStreamingUiState(
    val responseText: String? = null,
    val showDelayedPlaceholder: Boolean = false,
) {
    fun clear(): LocalStreamingUiState = LocalStreamingUiState()

    fun onPartial(text: String): LocalStreamingUiState = copy(
        responseText = text,
        showDelayedPlaceholder = false,
    )

    fun withDelayedPlaceholder(show: Boolean): LocalStreamingUiState = copy(
        showDelayedPlaceholder = show,
    )
}
