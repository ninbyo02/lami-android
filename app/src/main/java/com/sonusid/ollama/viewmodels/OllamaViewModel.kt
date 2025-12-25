package com.sonusid.ollama.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonusid.ollama.UiState
import com.sonusid.ollama.api.OllamaRequest
import com.sonusid.ollama.api.OllamaResponse
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.URL

data class ModelInfo(val name: String)

class OllamaViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    init {
        viewModelScope.launch {
            repository.allChats.collect {
                _chats.value = it
            }
        }
    }

    fun allMessages(chatId: Int): Flow<List<Message>> = repository.getMessages(chatId)


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
                            _uiState.value = UiState.Initial
                        }

                        override fun onFailure(call: Call<OllamaResponse>, t: Throwable) {
                            Log.e("OllamaError", "Request failed: ${t.message}")
                            _uiState.value = UiState.Error(t.message ?: "Unknown error")
                            _uiState.value = UiState.Initial
                        }
                    })
            } else {
                _uiState.value = UiState.Success("Please Choose A model")
                _uiState.value = UiState.Initial
            }
        }
    }

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    fun loadAvailableModels() {

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val models = withContext(Dispatchers.IO) {
                    val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
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
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e("OllamaError", "Error loading models: ${e.message}")
                _availableModels.value = emptyList()
                val message = e.message ?: "Unknown error"
                _uiState.value = UiState.Error("Failed to load models: $message")
            }
        }
    }


    fun insertChat(chat: Chat) = viewModelScope.launch {
        repository.newChat(chat)
    }

    fun insert(message: Message) = viewModelScope.launch {
        repository.insert(message)
    }

    fun delete(message: Message) = viewModelScope.launch {
        repository.delete(message)
    }

}
