package io.github.ninbyo02.lami.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.flow.StateFlow

class OllamaViewModelFactory(
    private val repository: ChatRepository,
    private val modelPreferenceRepository: ModelPreferenceRepository,
    private val settingsPreferences: SettingsPreferences,
    private val initialSelectedModel: String?,
    private val baseUrlFlow: StateFlow<String>,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OllamaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OllamaViewModel(
                repository,
                modelPreferenceRepository,
                settingsPreferences,
                initialSelectedModel,
                baseUrlFlow
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
