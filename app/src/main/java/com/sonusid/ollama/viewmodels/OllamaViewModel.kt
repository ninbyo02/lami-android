package com.sonusid.ollama.viewmodels
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonusid.ollama.UiState
import com.sonusid.ollama.api.OllamaRequest
import com.sonusid.ollama.api.OllamaResponse
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.repository.ChatRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.URL
data class ModelInfo(val name: String)
class OllamaViewModel(
    private val chatRepository: ChatRepository,
    private val modelPreferenceRepository: ModelPreferenceRepository,
    private val initialSelectedModel: String?,
    baseUrlFlow: StateFlow<String>,
    private val shouldAutoLoadModels: Boolean = true,
) : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()
    private val _lamiState = MutableStateFlow(mapToLamiState(_uiState.value, _selectedModel.value))
    val lamiState: StateFlow<LamiState> = _lamiState.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()
    private val _lamiState = MutableStateFlow(mapToLamiState(_uiState.value, _selectedModel.value))
    val lamiState: StateFlow<LamiState> = _lamiState.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats
    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()
    val baseUrl: StateFlow<String> = baseUrlFlow

    init {
        applyInitialSelectedModel(initialSelectedModel)
        viewModelScope.launch {
            combine(_uiState, _selectedModel) { uiState, selectedModel ->
                mapToLamiState(uiState, selectedModel)
            }.collect { mappedState ->
                _lamiState.value = mappedState
            }
        }
        viewModelScope.launch {
            chatRepository.allChats.collect {
                _chats.value = it
            }
        }
        if (shouldAutoLoadModels) {
            viewModelScope.launch {
                baseUrl.collectLatest {
                    loadAvailableModels()
                }
            }
        }
    }

    fun applyInitialSelectedModel(initialModelName: String? = null) {
        if (!initialModelName.isNullOrBlank()) {
            _selectedModel.value = initialModelName
        }
    }

    fun allMessages(chatId: Int): Flow<List<Message>> = chatRepository.getMessages(chatId)

    fun insert(message: Message) {
        viewModelScope.launch {
            chatRepository.insert(message)
        }
    }

    fun insertChat(chat: Chat) {
        viewModelScope.launch {
            chatRepository.newChat(chat)
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Initial
    }


    fun sendPrompt(prompt: String, model: String?) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val request = OllamaRequest(model = model.toString(), prompt = prompt)

            if (model != null) {
                RetrofitClient.instance.generateText(request)
                    .enqueue(object : Callback<OllamaResponse> {
                        override fun onResponse(
                            call: Call<OllamaResponse>,
                            response: Response<OllamaResponse>,
                        ) {
                            if (response.isSuccessful) {
                                response.body()?.response?.let { output ->
                                    _uiState.value = UiState.Success(output)
                                } ?: run {
                                    _uiState.value = UiState.Error("Empty response")
                                }

                            } else {
                                val error =
                                    response.errorBody()?.string().orEmpty()
                                _uiState.value = UiState.Error(
                                    error.ifEmpty { "Failed to generate response" })
                            }
                        }

                        override fun onFailure(call: Call<OllamaResponse>, t: Throwable) {
                            Log.e("OllamaError", "Request failed: ${t.message}")
                            _uiState.value = UiState.Error(t.message ?: "Unknown error")
                        }
                    })
            } else {
                _uiState.value = UiState.Success("Please Choose A model")
            }
        }
    }

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    fun loadAvailableModels() {

        viewModelScope.launch {
            _isLoadingModels.value = true
            val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
            try {
                val models = withContext(Dispatchers.IO) {
                    val url =
                        URL("${baseUrl}/api/tags")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 10000
                    val responseCode = connection.responseCode
                    val responseStream =
                        if (responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        } ?: throw java.io.IOException("Failed to read response stream (HTTP $responseCode)")
                    val response =
                        responseStream.bufferedReader().use { it.readText() }
                    if (responseCode !in 200..299) {
                        throw java.io.IOException("Failed to load models (HTTP $responseCode): $response")
                    }
                    val jsonArray = JSONObject(response).getJSONArray("models")
                    val availableModels = mutableListOf<ModelInfo>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val name = jsonObject.getString("name")
                        availableModels.add(ModelInfo(name))
                    }
                    availableModels
                }
                _availableModels.value = models
                refreshSelectedModel(models)
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e("OllamaError", "Error loading models: ${e.message}")
                _availableModels.value = emptyList()
                val message = e.message ?: "Unknown error"
                _uiState.value = UiState.Error("Failed to load models: $message")
                clearSelectedModelForBaseUrl(baseUrl)
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    @VisibleForTesting
    internal suspend fun refreshSelectedModel(models: List<ModelInfo>) {
        val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
        val savedModel = withContext(Dispatchers.IO) {
            modelPreferenceRepository.getSelectedModel(baseUrl)
        }
        val savedModelAvailable = savedModel?.takeIf { modelName -> models.any { it.name == modelName } }
        val currentSelection = _selectedModel.value?.takeIf { modelName -> models.any { it.name == modelName } }
        if (models.size == 1) {
            val singleModel = models.first().name
            _selectedModel.value = singleModel
            withContext(Dispatchers.IO) {
                modelPreferenceRepository.setSelectedModel(baseUrl, singleModel)
            }
            return
        }

        when {
            savedModelAvailable != null -> {
                _selectedModel.value = savedModelAvailable
                withContext(Dispatchers.IO) {
                    modelPreferenceRepository.setSelectedModel(baseUrl, savedModelAvailable)
                }
            }

            currentSelection != null -> {
                _selectedModel.value = currentSelection
            }

            else -> {
                clearSelectedModelForBaseUrl(baseUrl)
            }
        }
    }

    private suspend fun clearSelectedModelForBaseUrl(baseUrl: String) {
        _selectedModel.value = null
        withContext(Dispatchers.IO) {
            modelPreferenceRepository.clearSelectedModel(baseUrl)
        }
    }

    fun updateSelectedModel(modelName: String) {
        viewModelScope.launch {
            val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
            _selectedModel.value = modelName
            // 永続化はユーザーが明示的に updateSelectedModel を呼び出した場合のみ行う
            withContext(Dispatchers.IO) {
                modelPreferenceRepository.setSelectedModel(baseUrl, modelName)
            }
        }
    }
}
