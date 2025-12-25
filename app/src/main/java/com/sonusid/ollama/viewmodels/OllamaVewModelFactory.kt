package com.sonusid.ollama.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sonusid.ollama.db.repository.ChatRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository

class OllamaViewModelFactory(
    private val repository: ChatRepository,
    private val modelPreferenceRepository: ModelPreferenceRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OllamaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OllamaViewModel(repository, modelPreferenceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
