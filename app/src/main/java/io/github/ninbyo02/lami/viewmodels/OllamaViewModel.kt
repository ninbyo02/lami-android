package io.github.ninbyo02.lami.viewmodels
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ninbyo02.lami.UiState
import io.github.ninbyo02.lami.api.OllamaRequest
import io.github.ninbyo02.lami.api.RetrofitClient
import io.github.ninbyo02.lami.db.dao.ChatLatestMessage
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.ErrorCause
import io.github.ninbyo02.lami.ui.screens.settings.LemonadeAutoUnloadMode
import io.github.ninbyo02.lami.ui.screens.settings.PendingLemonadeAutoUnload
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import io.github.ninbyo02.lami.ui.text.processEdgeGalleryCompatibleMarkdown
import io.github.ninbyo02.lami.util.RuntimeFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import kotlin.math.min
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
data class ModelInfo(val name: String)

private const val LEMONADE_UNLOAD_EVENT_URL = "http://192.168.52.101:8650/lemonade/unloaded"

internal fun fetchAvailableModelsFromServer(
    baseUrl: String,
    provider: RemoteProvider = RemoteProvider.OLLAMA,
): List<ModelInfo> {
    if (provider.usesOpenAiCompatibleApi()) {
        return fetchOpenAiCompatibleModelsFromServer(baseUrl, provider)
    }
    val url = URL("${baseUrl.trimEnd('/')}/api/tags")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        val responseCode = connection.responseCode
        val responseStream =
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: throw IOException("Failed to read response stream (HTTP $responseCode)")
        val response = responseStream.bufferedReader().use { it.readText() }
        if (responseCode !in 200..299) {
            throw IOException("Failed to load models (HTTP $responseCode): $response")
        }
        val jsonArray = JSONObject(response).getJSONArray("models")
        val availableModels = mutableListOf<ModelInfo>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val name = jsonObject.getString("name")
            availableModels.add(ModelInfo(name))
        }
        return availableModels
    } finally {
        connection.disconnect()
    }
}

internal fun fetchOpenAiCompatibleModelsFromServer(baseUrl: String, provider: RemoteProvider): List<ModelInfo> {
    val config = provider.toOpenAiCompatibleConfig(baseUrl)
    val url = URL("${config.baseUrl}models")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        val apiKey = config.defaultApiKey
        if (!apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            ?: throw IOException("Failed to read response stream (HTTP $responseCode)")
        val response = responseStream.bufferedReader().use { it.readText() }
        if (responseCode !in 200..299) {
            throw IOException("Failed to load OpenAI compatible models (HTTP $responseCode): $response")
        }
        return parseOpenAiCompatibleModels(response)
    } finally {
        connection.disconnect()
    }
}

internal fun buildLemonadeUnloadEventJson(modelName: String, source: String = "lami-android"): String =
    JSONObject()
        .put("model_name", modelName)
        .put("source", source)
        .toString()

internal fun notifyLemonadeUnloadEvent(
    modelName: String,
    eventUrl: String = LEMONADE_UNLOAD_EVENT_URL,
): Boolean {
    if (modelName.isBlank() || eventUrl.isBlank()) return false
    val connection = runCatching {
        (URL(eventUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2000
            readTimeout = 3000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
    }.getOrElse { error ->
        Log.w("LemonadeUnload", "Failed to open unload event connection: ${error.message}")
        return false
    }
    return try {
        val requestBody = buildLemonadeUnloadEventJson(modelName)
        connection.outputStream.use { output ->
            output.write(requestBody.toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        responseStream?.bufferedReader()?.use { it.readText() }
        if (responseCode in 200..299) {
            Log.i("LemonadeUnload", "Sent unload event to bridge for model=${sanitizeLemonadeLogValue(modelName)}")
            true
        } else {
            Log.w("LemonadeUnload", "Unload event bridge returned HTTP $responseCode")
            false
        }
    } catch (error: Exception) {
        Log.w("LemonadeUnload", "Failed to notify unload event bridge: ${error.message}")
        false
    } finally {
        connection.disconnect()
    }
}

private fun sanitizeLemonadeLogValue(value: String): String =
    URLEncoder.encode(value.take(120), Charsets.UTF_8.name())

internal fun resolveLoadedLemonadeModelName(
    baseUrl: String,
): String? {
    val config = RemoteProvider.LEMONADE.toOpenAiCompatibleConfig(baseUrl)
    val url = URL("${config.baseUrl}health")
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299 || response.isBlank()) {
            Log.w("LemonadeUnload", "Failed to resolve loaded Lemonade model: HTTP $responseCode")
            return null
        }
        val json = JSONObject(response)
        json.optString("model_loaded").takeIf { it.isNotBlank() && it != "null" }
            ?: json.optJSONArray("all_models_loaded")
                ?.optJSONObject(0)
                ?.optString("model_name")
                ?.takeIf { it.isNotBlank() }
    } catch (error: Exception) {
        Log.w("LemonadeUnload", "Failed to resolve loaded Lemonade model: ${error.message}")
        null
    } finally {
        connection.disconnect()
    }
}

internal fun unloadLoadedLemonadeModelFromServer(
    baseUrl: String,
    fallbackModelName: String,
    unloadEventUrl: String = LEMONADE_UNLOAD_EVENT_URL,
): Boolean {
    val loadedModelName = resolveLoadedLemonadeModelName(baseUrl)
    val targetModelName = loadedModelName?.takeIf { it.isNotBlank() } ?: fallbackModelName
    Log.i(
        "LemonadeUnload",
        "unload target resolved loaded=${loadedModelName?.let(::sanitizeLemonadeLogValue) ?: "none"} " +
            "fallback=${sanitizeLemonadeLogValue(fallbackModelName)} target=${sanitizeLemonadeLogValue(targetModelName)}"
    )
    return unloadLemonadeModelFromServer(
        baseUrl = baseUrl,
        modelName = targetModelName,
        unloadEventUrl = unloadEventUrl,
    )
}

internal fun unloadLemonadeModelFromServer(
    baseUrl: String,
    modelName: String,
    unloadEventUrl: String = LEMONADE_UNLOAD_EVENT_URL,
): Boolean {
    if (modelName.isBlank()) return false
    val config = RemoteProvider.LEMONADE.toOpenAiCompatibleConfig(baseUrl)
    val url = URL("${config.baseUrl}unload")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val apiKey = config.defaultApiKey
        if (!apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val requestBody = JSONObject()
            .put("model_name", modelName)
            .toString()
        connection.outputStream.use { output ->
            output.write(requestBody.toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw IOException(response.ifEmpty { "Lemonade unload failed (HTTP $responseCode)" })
        }
        notifyLemonadeUnloadEvent(modelName = modelName, eventUrl = unloadEventUrl)
        return true
    } finally {
        connection.disconnect()
    }
}

class OllamaViewModel(
    private val chatRepository: ChatRepository,
    private val modelPreferenceRepository: ModelPreferenceRepository,
    private val settingsPreferences: SettingsPreferences,
    private val initialSelectedModel: String?,
    baseUrlFlow: StateFlow<String>,
    private val shouldAutoLoadModels: Boolean = true,
    private val availableModelsFetcher: suspend (String, RemoteProvider) -> List<ModelInfo> = { baseUrl, provider ->
        withContext(Dispatchers.IO) { fetchAvailableModelsFromServer(baseUrl, provider) }
    },
) : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()
    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()
    private val _lamiUiState =
        MutableStateFlow(LamiUiState(state = mapToLamiState(_uiState.value, _selectedModel.value)))
    val lamiUiState: StateFlow<LamiUiState> = _lamiUiState.asStateFlow()
    private val _lamiState = MutableStateFlow(_lamiUiState.value.state)
    val lamiState: StateFlow<LamiState> = _lamiState.asStateFlow()
    private val _lamiAnimationStatus =
        MutableStateFlow(mapToAnimationLamiStatus(_lamiState.value, _uiState.value, _selectedModel.value))
    val lamiAnimationStatus: StateFlow<LamiStatus> = _lamiAnimationStatus.asStateFlow()
    private val _animationEpochMs = MutableStateFlow(SystemClock.uptimeMillis())
    val animationEpochMs: StateFlow<Long> = _animationEpochMs.asStateFlow()
    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying.asStateFlow()
    private val _latestInferenceStats = MutableStateFlow<InferenceStats?>(null)
    val latestInferenceStats: StateFlow<InferenceStats?> = _latestInferenceStats.asStateFlow()
    @Volatile
    private var activeRemoteCall: Call<ResponseBody>? = null
    @Volatile
    private var activeOpenAiCompatibleConnection: HttpURLConnection? = null
    @Volatile
    private var remoteRequestGeneration: Long = 0L
    @Volatile
    private var markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT
    @Volatile
    private var remoteProvider: RemoteProvider = RemoteProvider.OLLAMA
    @Volatile
    private var lemonadeAutoUnloadMode: LemonadeAutoUnloadMode = LemonadeAutoUnloadMode.OFF
    private var scheduledLemonadeUnloadJob: Job? = null
    private val effectiveContextWindowCache = mutableMapOf<String, Int?>()
    private val effectiveContextWindowRequestState = mutableMapOf<String, ContextWindowResolutionState>()

    private fun buildContextWindowCacheKey(modelName: String): String {
        val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
        return "$baseUrl|${modelName.trim()}"
    }

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats
    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()
    val baseUrl: StateFlow<String> = baseUrlFlow
    // These model/runtime selections belong to the activity-scoped runtime, not to a
    // conversation route. Eager StateFlows retain the latest DataStore values while
    // Home is recreated for /chat/{id}, avoiding transient null/default availability.
    val localBaseModelFilePath = settingsPreferences.localBaseModelFilePathFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    val localBaseModelDisplayName = settingsPreferences.localBaseModelDisplayNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    val localGenericModelFilePath = settingsPreferences.localGenericModelFilePathFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    val localGenericModelDisplayName = settingsPreferences.localGenericModelDisplayNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    val preferredBackendDryRunSetting = settingsPreferences.preferredBackendDryRunSettingFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PreferredBackendDryRunSetting.DEFAULT,
    )
    val inferenceTarget = settingsPreferences.inferenceTargetFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InferenceTarget.LOCAL,
    )

    init {
        applyInitialSelectedModel(initialSelectedModel)
        viewModelScope.launch {
            lamiUiState.collect { state ->
                _lamiState.value = state.state
            }
        }
        viewModelScope.launch {
            lamiUiState
                .map { it.state }
                .distinctUntilChanged()
                .collect {
                    if (!RuntimeFlags.shouldDisableContinuousAnimations()) {
                        _animationEpochMs.value = SystemClock.uptimeMillis()
                    }
                }
        }
        viewModelScope.launch {
            combine(lamiUiState, _uiState, _selectedModel, _isTtsPlaying) { lamiUiState, uiState, selectedModel, isTtsPlaying ->
                mapToAnimationLamiStatus(
                    lamiState = lamiUiState.state,
                    uiState = uiState,
                    selectedModel = selectedModel,
                    isTtsPlaying = isTtsPlaying,
                )
            }.collect { mappedStatus ->
                if (mappedStatus != _lamiAnimationStatus.value && !RuntimeFlags.shouldDisableContinuousAnimations()) {
                    _animationEpochMs.value = SystemClock.uptimeMillis()
                }
                _lamiAnimationStatus.value = mappedStatus
            }
        }
        viewModelScope.launch {
            chatRepository.allChats.collect {
                _chats.value = it
            }
        }
        viewModelScope.launch {
            chatRepository.cleanupEmptyTempPlaceholderChats()
        }
        viewModelScope.launch {
            settingsPreferences.markdownStreamingModeFlow
                .distinctUntilChanged()
                .collect { mode ->
                    markdownStreamingMode = mode
                }
        }
        viewModelScope.launch {
            settingsPreferences.remoteProviderFlow
                .distinctUntilChanged()
                .collect { provider ->
                    remoteProvider = provider
                    if (provider != RemoteProvider.LEMONADE) {
                        cancelScheduledLemonadeUnload()
                        settingsPreferences.clearPendingLemonadeAutoUnload()
                    } else {
                        restorePendingLemonadeAutoUnloadIfNeeded()
                    }
                }
        }
        viewModelScope.launch {
            settingsPreferences.lemonadeAutoUnloadModeFlow
                .distinctUntilChanged()
                .collect { mode ->
                    lemonadeAutoUnloadMode = mode
                    if (mode == LemonadeAutoUnloadMode.OFF) {
                        cancelScheduledLemonadeUnload()
                        settingsPreferences.clearPendingLemonadeAutoUnload()
                    } else {
                        restorePendingLemonadeAutoUnloadIfNeeded()
                    }
                }
        }
        if (shouldAutoLoadModels) {
            viewModelScope.launch {
                combine(baseUrl, settingsPreferences.inferenceTargetFlow, settingsPreferences.remoteProviderFlow) { url, target, provider ->
                    Triple(url, target, provider)
                }
                    .distinctUntilChanged()
                    .collectLatest { (url, target, provider) ->
                        remoteProvider = provider
                        if (target == InferenceTarget.SERVER && url.isNotBlank()) {
                            loadAvailableModels()
                        } else {
                            _isLoadingModels.value = false
                        }
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

    suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage> =
        chatRepository.getLatestMessagesByChatIds(chatIds)

    fun insert(message: Message) {
        viewModelScope.launch {
            if (message.isSendbyMe) {
                chatRepository.insert(message)
            } else {
                chatRepository.insertAssistantMessageAndAutoTitle(message)
            }
        }
    }

    suspend fun insertAssistantMessageAndReturnId(message: Message): Long {
        return if (message.isSendbyMe) {
            chatRepository.insert(message)
            message.messageID.toLong()
        } else {
            chatRepository.insertAssistantMessageAndAutoTitleAndReturnId(message)
        }
    }

    suspend fun updateMessage(message: Message) {
        chatRepository.updateMessage(message)
    }

    suspend fun getMessageById(messageId: Int): Message? {
        return chatRepository.getMessageById(messageId)
    }

    fun insertChat(chat: Chat) {
        viewModelScope.launch {
            insertChatAndReturnId(chat)
        }
    }

    suspend fun insertChatAndReturnId(chat: Chat): Int {
        val chatId = chatRepository.newChat(chat)
        if (chat.titleSource == TitleSource.TEMP && isPlaceholderTitle(chat.title) && chatId > 0) {
            scheduleAutoDeleteForEmptyTempChat(chatId)
        }
        return chatId
    }

    private fun scheduleAutoDeleteForEmptyTempChat(chatId: Int) {
        viewModelScope.launch {
            delay(AUTO_DELETE_DELAY_MS)
            chatRepository.deleteChatIfStillEmptyTempPlaceholder(chatId)
        }
    }

    private fun isPlaceholderTitle(title: String): Boolean {
        val normalizedTitle = title.trim().lowercase()
        return normalizedTitle.isEmpty() || normalizedTitle == "new chat" || normalizedTitle == "newchat"
    }

    fun resetUiState() {
        _uiState.value = UiState.Initial
    }

    fun onUserInteraction() {
        _lamiUiState.update {
            it.copy(lastInteractionTimeMs = System.currentTimeMillis())
        }
    }

    fun onPromptSubmitted() {
        _isTtsPlaying.value = false
        val now = System.currentTimeMillis()
        _lamiUiState.value = LamiUiState(state = LamiState.Thinking, lastInteractionTimeMs = now)
    }

    fun onTtsPlaybackChanged(isPlaying: Boolean) {
        _isTtsPlaying.value = isPlaying
    }

    fun stopTtsPlayback() {
        _isTtsPlaying.value = false
    }

    fun onResponseReceived(textLength: Int) {
        val now = System.currentTimeMillis()
        _lamiUiState.value = LamiUiState(state = LamiState.Speaking(textLength), lastInteractionTimeMs = now)
    }

    fun moveToIdleIfStale(referenceTimeMs: Long, idleTimeoutMs: Long) {
        val snapshot = _lamiUiState.value
        if (snapshot.state is LamiState.Thinking) {
            return
        }
        val elapsed = System.currentTimeMillis() - referenceTimeMs
        if (snapshot.lastInteractionTimeMs == referenceTimeMs && elapsed >= idleTimeoutMs) {
            _lamiUiState.value =
                LamiUiState(state = LamiState.Idle, lastInteractionTimeMs = System.currentTimeMillis())
        }
    }

    fun sendPrompt(prompt: String, model: String?, attachmentUri: Uri? = null, context: Context? = null, onAttachmentPrepared: ((String?) -> Unit)? = null) {
        sendPrompt(
            prompt = prompt,
            model = model,
            attachmentUris = listOfNotNull(attachmentUri),
            context = context,
            onAttachmentPrepared = { prepared -> onAttachmentPrepared?.invoke(prepared?.firstOrNull()) },
        )
    }

    fun sendPrompt(
        prompt: String,
        model: String?,
        attachmentUris: List<Uri> = emptyList(),
        context: Context? = null,
        onAttachmentPrepared: ((List<String>?) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            var encodedImages: List<String> = emptyList()
            var savedAttachmentUriStrings: List<String> = emptyList()
            if (attachmentUris.isNotEmpty()) {
                Log.d("ChatAttachment", "Attachment selected count: ${attachmentUris.size}")
                if (context == null) {
                    updateErrorState("Attachment context missing")
                    return@launch
                }
                val encodedAttachments = encodeAttachmentImagesToBase64(context, attachmentUris) ?: return@launch
                encodedImages = encodedAttachments.base64Images
                savedAttachmentUriStrings = encodedAttachments.savedUriStrings
            }
            onAttachmentPrepared?.invoke(savedAttachmentUriStrings.takeIf { it.isNotEmpty() })
            onPromptSubmitted()
            cancelScheduledLemonadeUnload()
            val requestGeneration = beginRemoteRequestGeneration()
            _uiState.value = UiState.Loading
            _latestInferenceStats.value = null
            val generationStartedAtMs = SystemClock.elapsedRealtime()

            val effectivePrompt = if (prompt.isBlank() && attachmentUris.isNotEmpty()) {
                "この画像について説明して。"
            } else {
                prompt
            }
            val request = OllamaRequest(
                model = model.toString(),
                prompt = effectivePrompt,
                stream = true,
                images = encodedImages.ifEmpty { null },
            )
            val effectiveContextWindow = model?.let { getCachedEffectiveContextWindow(it) }
            val contextWindowFetchState = resolveContextWindowFetchState(model)
            prefetchEffectiveContextWindow(model)

            if (model != null) {
                try {
                    val activeRemoteProvider = remoteProvider
                    val activeBaseUrl = RetrofitClient.currentBaseUrl()
                    val streamingResult = withContext(Dispatchers.IO) {
                        if (activeRemoteProvider.usesOpenAiCompatibleApi()) {
                            collectOpenAiCompatibleStreamingResponse(
                                baseUrl = activeBaseUrl,
                                provider = activeRemoteProvider,
                                model = model,
                                prompt = effectivePrompt,
                                requestStartedAtMs = generationStartedAtMs,
                                requestGeneration = requestGeneration,
                            )
                        } else {
                            collectStreamingResponse(request, generationStartedAtMs, requestGeneration)
                        }
                    }
                    ensureRemoteRequestGenerationActive(requestGeneration)
                    val finalText = streamingResult.text
                    if (finalText.isBlank()) {
                        onResponseReceived(0)
                        updateErrorState("Empty response")
                    } else {
                        val generationTimeMs = (SystemClock.elapsedRealtime() - generationStartedAtMs).coerceAtLeast(0L)
                        val finalChunk = streamingResult.finalChunk
                        val inputTokens = finalChunk?.promptEvalCount
                        val outputTokens = finalChunk?.evalCount
                        val totalTokens = if (inputTokens != null && outputTokens != null) {
                            inputTokens + outputTokens
                        } else {
                            null
                        }
                        val tokensPerSecond = finalChunk?.evalDurationNs
                            ?.takeIf { it > 0L }
                            ?.let { evalDurationNs ->
                                outputTokens?.toDouble()?.div(evalDurationNs)?.times(1_000_000_000)
                            }
                        val inferenceTimeSec = finalChunk?.totalDurationNs
                            ?.takeIf { it > 0L }
                            ?.div(1_000_000_000.0)
                            ?: (generationTimeMs / 1000.0)

                        ensureRemoteRequestGenerationActive(requestGeneration)
                        _latestInferenceStats.value = InferenceStats(
                            modelName = finalChunk?.model ?: model,
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            totalTokens = totalTokens,
                            tokensPerSecond = tokensPerSecond,
                            inferenceTimeSec = inferenceTimeSec,
                            generationTimeMs = generationTimeMs,
                            modelLoadDurationNs = finalChunk?.loadDurationNs,
                            promptEvalDurationNs = finalChunk?.promptEvalDurationNs,
                            generationDurationNs = finalChunk?.evalDurationNs,
                            evalDurationNs = finalChunk?.evalDurationNs,
                            finishReason = finalChunk?.doneReason,
                            // アプリ側計測値。Ollama usage の load_duration とは別指標として扱う。
                            timeToFirstTokenMs = streamingResult.timeToFirstTokenMs,
                            imageInputCount = attachmentUris.size,
                            contextTokensUsed = totalTokens,
                            contextWindow = effectiveContextWindow,
                            contextWindowFetchState = contextWindowFetchState,
                            contextUsageRatio = if (effectiveContextWindow != null && effectiveContextWindow > 0 && totalTokens != null) {
                                totalTokens.toDouble() / effectiveContextWindow.toDouble()
                            } else {
                                null
                            },
                            // 旧命名互換（段階的移行用）。
                            model = finalChunk?.model ?: model,
                            modelLabel = finalChunk?.model ?: model,
                            completionTokens = outputTokens,
                            assistantUpdateCount = streamingResult.assistantUpdateCount,
                        )
                        ensureRemoteRequestGenerationActive(requestGeneration)
                        _uiState.value = UiState.Success(finalText)
                        if (activeRemoteProvider != RemoteProvider.LEMONADE || lemonadeAutoUnloadMode.delayMs != 0L) {
                            scheduleLemonadeAutoUnloadIfNeeded(
                                provider = activeRemoteProvider,
                                baseUrl = activeBaseUrl,
                                modelName = finalChunk?.model ?: model,
                                requestGeneration = requestGeneration,
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    Log.i("OllamaCancel", "Remote request cancelled: ${e.message}")
                    if (isRemoteRequestGenerationActive(requestGeneration)) {
                        _latestInferenceStats.value = null
                    }
                } catch (e: Exception) {
                    if (!isRemoteRequestGenerationActive(requestGeneration)) {
                        Log.i("OllamaCancel", "Ignoring stale remote failure after stop: ${e.message}")
                        return@launch
                    }
                    Log.e("OllamaError", "Request failed: ${e.message}")
                    onResponseReceived(e.message?.length ?: 0)
                    _latestInferenceStats.value = null
                    updateErrorState(e.message ?: "Unknown error")
                }
            } else {
                onResponseReceived("Please Choose A model".length)
                _latestInferenceStats.value = null
                _uiState.value = UiState.Success("Please Choose A model")
            }
        }
    }

    fun cancelRemoteRequest() {
        remoteRequestGeneration += 1
        val call = activeRemoteCall
        val connection = activeOpenAiCompatibleConnection
        activeRemoteCall = null
        activeOpenAiCompatibleConnection = null
        call?.cancel()
        connection?.disconnect()
        _latestInferenceStats.value = null
    }

    private fun beginRemoteRequestGeneration(): Long {
        remoteRequestGeneration += 1
        return remoteRequestGeneration
    }

    private fun isRemoteRequestGenerationActive(requestGeneration: Long): Boolean {
        return remoteRequestGeneration == requestGeneration
    }

    private fun ensureRemoteRequestGenerationActive(requestGeneration: Long) {
        if (!isRemoteRequestGenerationActive(requestGeneration)) {
            throw CancellationException("remote request stopped")
        }
    }

    private fun collectStreamingResponse(
        request: OllamaRequest,
        requestStartedAtMs: Long,
        requestGeneration: Long,
    ): StreamingResult {
        val call = RetrofitClient.instance.generateTextStream(request)
        activeRemoteCall = call
        try {
            ensureRemoteRequestGenerationActive(requestGeneration)
            val response = call.execute()
            ensureRemoteRequestGenerationActive(requestGeneration)
            if (!response.isSuccessful) {
                val error = response.errorBody()?.string().orEmpty()
                throw IOException(error.ifEmpty { "Failed to generate response" })
            }

            val body = response.body() ?: throw IOException("Empty response")
            val activeMarkdownStreamingMode = markdownStreamingMode
            val streamAssembler = when (activeMarkdownStreamingMode) {
                MarkdownStreamingMode.LAMI_RECOVERY_V1 -> SafeMarkdownStreamAssembler()
                MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> null
            }
            val edgeGalleryTextBuilder = StringBuilder()
            var doneReceived = false
            val streamingUiUpdateIntervalMs = 80L
            val priorityFlushChars = setOf('。', '、', '！', '？', '\n')
            var lastUiUpdateAtMs = 0L
            var latestFlushedText: String? = null
            var finalChunk: StreamChunk? = null
            var timeToFirstTokenMs: Long? = null
            var assistantUpdateCount = 0

            body.charStream().buffered().use { reader ->
                while (true) {
                    ensureRemoteRequestGenerationActive(requestGeneration)
                    val rawLine = reader.readLine() ?: break
                    ensureRemoteRequestGenerationActive(requestGeneration)
                    val line = rawLine.trim()
                    if (line.isEmpty()) {
                        continue
                    }
                    val chunk = parseStreamingChunk(line)
                    if (shouldCaptureFirstAssistantToken(timeToFirstTokenMs, chunk.text)) {
                        // assistant 本文の最初の非空トークン受信時刻をアプリ側で確定する。
                        timeToFirstTokenMs = (SystemClock.elapsedRealtime() - requestStartedAtMs).coerceAtLeast(0L)
                    }
                    val chunkText = chunk.text
                    val shouldAppendChunk = when (activeMarkdownStreamingMode) {
                        MarkdownStreamingMode.LAMI_RECOVERY_V1 -> !chunkText.isNullOrBlank()
                        MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> !chunkText.isNullOrEmpty()
                    }
                    if (shouldAppendChunk && chunkText != null) {
                        val currentText = when (activeMarkdownStreamingMode) {
                            // Streaming Markdown Recovery Engine v1: legacy safe markdown recovery path.
                            MarkdownStreamingMode.LAMI_RECOVERY_V1 -> {
                                val assembler = checkNotNull(streamAssembler)
                                assembler.appendChunk(chunkText)
                                assembler.buildDisplayText()
                            }
                            MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> {
                                edgeGalleryTextBuilder.append(processEdgeGalleryCompatibleMarkdown(chunkText))
                                edgeGalleryTextBuilder.toString()
                            }
                        }
                        val nowMs = System.currentTimeMillis()
                        val isIntervalElapsed = nowMs - lastUiUpdateAtMs >= streamingUiUpdateIntervalMs
                        val endsWithPriorityChar =
                            chunkText.lastOrNull() in priorityFlushChars ||
                                currentText.lastOrNull() in priorityFlushChars
                        if ((isIntervalElapsed || endsWithPriorityChar) && latestFlushedText != currentText) {
                            ensureRemoteRequestGenerationActive(requestGeneration)
                            onResponseReceived(currentText.length)
                            _uiState.value = UiState.Streaming(currentText)
                            assistantUpdateCount += 1
                            lastUiUpdateAtMs = nowMs
                            latestFlushedText = currentText
                        }
                    }
                    if (chunk.done) {
                        finalChunk = chunk
                        val currentText = when (activeMarkdownStreamingMode) {
                            MarkdownStreamingMode.LAMI_RECOVERY_V1 -> checkNotNull(streamAssembler).buildDisplayText()
                            MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> edgeGalleryTextBuilder.toString()
                        }
                        if (currentText.isNotEmpty() && latestFlushedText != currentText) {
                            onResponseReceived(currentText.length)
                            _uiState.value = UiState.Streaming(currentText)
                            assistantUpdateCount += 1
                            latestFlushedText = currentText
                        }
                        doneReceived = true
                        break
                    }
                }
            }
            if (!doneReceived) {
                throw IOException("Streaming response ended before done=true")
            }
            val finalizedTextForPersist = when (activeMarkdownStreamingMode) {
                // SafeMarkdownStreamAssembler の finalizeResult() は、保存に使ってよい最終本文を返す。
                MarkdownStreamingMode.LAMI_RECOVERY_V1 -> checkNotNull(streamAssembler).finalizeResult()
                MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> edgeGalleryTextBuilder.toString().trim()
            }
            if (finalizedTextForPersist.isEmpty()) {
                throw IOException("Empty response")
            }
            if (latestFlushedText != finalizedTextForPersist) {
                ensureRemoteRequestGenerationActive(requestGeneration)
                onResponseReceived(finalizedTextForPersist.length)
                _uiState.value = UiState.Streaming(finalizedTextForPersist)
                assistantUpdateCount += 1
            }
            return StreamingResult(
                text = finalizedTextForPersist,
                finalChunk = finalChunk,
                timeToFirstTokenMs = timeToFirstTokenMs,
                assistantUpdateCount = assistantUpdateCount,
            )
        } finally {
            if (activeRemoteCall === call) {
                activeRemoteCall = null
            }
        }
    }

    private fun collectOpenAiCompatibleStreamingResponse(
        baseUrl: String,
        provider: RemoteProvider,
        model: String,
        prompt: String,
        requestStartedAtMs: Long,
        requestGeneration: Long,
    ): StreamingResult {
        val config = provider.toOpenAiCompatibleConfig(baseUrl)
        val url = URL("${config.baseUrl}chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        activeOpenAiCompatibleConnection = connection
        try {
            ensureRemoteRequestGenerationActive(requestGeneration)
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val apiKey = config.defaultApiKey
            if (!apiKey.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val requestBody = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )
                .put("stream", true)
                .toString()
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            ensureRemoteRequestGenerationActive(requestGeneration)
            val responseCode = connection.responseCode
            ensureRemoteRequestGenerationActive(requestGeneration)
            val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                ?: throw IOException("Empty OpenAI compatible response (HTTP $responseCode)")
            if (responseCode !in 200..299) {
                val error = responseStream.bufferedReader().use { it.readText() }
                throw IOException(error.ifEmpty { "OpenAI compatible request failed (HTTP $responseCode)" })
            }

            val textBuilder = StringBuilder()
            var doneReceived = false
            var finishReason: String? = null
            var responseModel: String? = null
            var timeToFirstTokenMs: Long? = null
            var assistantUpdateCount = 0
            var latestFlushedText: String? = null
            var lastUiUpdateAtMs = 0L
            val streamingUiUpdateIntervalMs = 80L
            val priorityFlushChars = setOf('。', '、', '！', '？', '\n')

            responseStream.bufferedReader().use { reader ->
                while (true) {
                    ensureRemoteRequestGenerationActive(requestGeneration)
                    val rawLine = reader.readLine() ?: break
                    ensureRemoteRequestGenerationActive(requestGeneration)
                    val chunk = parseOpenAiCompatibleStreamingLine(rawLine) ?: continue
                    if (chunk.done && chunk.text == null) {
                        doneReceived = true
                        finishReason = finishReason ?: chunk.finishReason ?: "stop"
                        responseModel = responseModel ?: chunk.model
                        break
                    }
                    responseModel = responseModel ?: chunk.model
                    finishReason = finishReason ?: chunk.finishReason
                    val chunkText = chunk.text
                    if (!chunkText.isNullOrEmpty()) {
                        if (timeToFirstTokenMs == null) {
                            timeToFirstTokenMs = (SystemClock.elapsedRealtime() - requestStartedAtMs).coerceAtLeast(0L)
                        }
                        textBuilder.append(processEdgeGalleryCompatibleMarkdown(chunkText))
                        val currentText = textBuilder.toString()
                        val nowMs = System.currentTimeMillis()
                        val isIntervalElapsed = nowMs - lastUiUpdateAtMs >= streamingUiUpdateIntervalMs
                        val endsWithPriorityChar =
                            chunkText.lastOrNull() in priorityFlushChars || currentText.lastOrNull() in priorityFlushChars
                        if ((isIntervalElapsed || endsWithPriorityChar) && latestFlushedText != currentText) {
                            ensureRemoteRequestGenerationActive(requestGeneration)
                            onResponseReceived(currentText.length)
                            _uiState.value = UiState.Streaming(currentText)
                            assistantUpdateCount += 1
                            latestFlushedText = currentText
                            lastUiUpdateAtMs = nowMs
                        }
                    }
                    if (chunk.finishReason != null) {
                        doneReceived = true
                        break
                    }
                }
            }
            if (!doneReceived) {
                throw IOException("OpenAI compatible streaming response ended before done")
            }
            val finalText = textBuilder.toString().trim()
            if (finalText.isEmpty()) {
                throw IOException("Empty OpenAI compatible response")
            }
            if (latestFlushedText != finalText) {
                ensureRemoteRequestGenerationActive(requestGeneration)
                onResponseReceived(finalText.length)
                _uiState.value = UiState.Streaming(finalText)
                assistantUpdateCount += 1
            }
            if (provider == RemoteProvider.LEMONADE && lemonadeAutoUnloadMode.delayMs == 0L) {
                Log.i("LemonadeUnload", "inline immediate auto-unload after Lemonade stream for model=${sanitizeLemonadeLogValue(responseModel ?: model)}")
                runCatching {
                    unloadLoadedLemonadeModelFromServer(baseUrl = baseUrl, fallbackModelName = responseModel ?: model)
                }.onSuccess { unloaded ->
                    Log.i("LemonadeUnload", "Inline immediate Lemonade unload result=$unloaded model=${sanitizeLemonadeLogValue(responseModel ?: model)}")
                }.onFailure { error ->
                    Log.w("LemonadeUnload", "Inline immediate Lemonade unload failed: ${error.message}")
                }
            }
            return StreamingResult(
                text = finalText,
                finalChunk = StreamChunk(
                    text = null,
                    done = true,
                    model = responseModel ?: model,
                    doneReason = finishReason ?: "stop",
                ),
                timeToFirstTokenMs = timeToFirstTokenMs,
                assistantUpdateCount = assistantUpdateCount,
            )
        } finally {
            if (activeOpenAiCompatibleConnection === connection) {
                activeOpenAiCompatibleConnection = null
            }
            connection.disconnect()
        }
    }

    private fun cancelScheduledLemonadeUnload() {
        scheduledLemonadeUnloadJob?.cancel()
        scheduledLemonadeUnloadJob = null
    }

    private fun restorePendingLemonadeAutoUnloadIfNeeded() {
        viewModelScope.launch {
            val pending = settingsPreferences.getPendingLemonadeAutoUnloadOrNull() ?: return@launch
            if (remoteProvider != RemoteProvider.LEMONADE || lemonadeAutoUnloadMode == LemonadeAutoUnloadMode.OFF) {
                settingsPreferences.clearPendingLemonadeAutoUnload()
                return@launch
            }
            schedulePendingLemonadeAutoUnload(pending, requestGeneration = remoteRequestGeneration)
        }
    }

    private fun schedulePendingLemonadeAutoUnload(
        pending: PendingLemonadeAutoUnload,
        requestGeneration: Long,
    ) {
        cancelScheduledLemonadeUnload()
        val remainingMs = pending.deadlineEpochMs - System.currentTimeMillis()
        scheduledLemonadeUnloadJob = viewModelScope.launch {
            if (remainingMs > 0L) {
                Log.i("LemonadeUnload", "restored auto-unload in ${remainingMs}ms for model=${sanitizeLemonadeLogValue(pending.targetModel)}")
                delay(remainingMs)
            } else {
                Log.i("LemonadeUnload", "running overdue auto-unload for model=${sanitizeLemonadeLogValue(pending.targetModel)}")
            }
            if (!isRemoteRequestGenerationActive(requestGeneration)) {
                Log.i("LemonadeUnload", "skip restored auto-unload: stale generation=$requestGeneration current=$remoteRequestGeneration")
                return@launch
            }
            runLemonadeAutoUnload(baseUrl = pending.baseUrl, targetModel = pending.targetModel, mode = pending.mode)
        }
    }

    private fun scheduleLemonadeAutoUnloadIfNeeded(
        provider: RemoteProvider,
        baseUrl: String,
        modelName: String?,
        requestGeneration: Long,
    ) {
        val mode = lemonadeAutoUnloadMode
        val delayMs = mode.delayMs
        val targetModel = modelName?.takeIf { it.isNotBlank() }
        Log.i(
            "LemonadeUnload",
            "schedule check provider=$provider mode=${mode.storageValue} delayMs=$delayMs " +
                "model=${targetModel ?: "blank"} baseUrl=$baseUrl generation=$requestGeneration"
        )
        if (delayMs == null) {
            Log.i("LemonadeUnload", "skip auto-unload: mode=${mode.storageValue}")
            viewModelScope.launch { settingsPreferences.clearPendingLemonadeAutoUnload() }
            return
        }
        if (targetModel == null) {
            Log.w("LemonadeUnload", "skip auto-unload: model is blank")
            return
        }
        if (provider != RemoteProvider.LEMONADE) {
            Log.i("LemonadeUnload", "skip auto-unload: provider=$provider")
            return
        }
        cancelScheduledLemonadeUnload()
        val pending = PendingLemonadeAutoUnload(
            baseUrl = baseUrl,
            targetModel = targetModel,
            mode = mode,
            deadlineEpochMs = System.currentTimeMillis() + delayMs,
        )
        viewModelScope.launch { settingsPreferences.savePendingLemonadeAutoUnload(pending) }
        if (delayMs == 0L) {
            Log.i("LemonadeUnload", "immediate auto-unload for model=$targetModel")
            scheduledLemonadeUnloadJob = viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                runLemonadeAutoUnload(baseUrl = baseUrl, targetModel = targetModel, mode = mode)
            }
            return
        }
        schedulePendingLemonadeAutoUnload(pending, requestGeneration = requestGeneration)
    }

    private suspend fun runLemonadeAutoUnload(
        baseUrl: String,
        targetModel: String,
        mode: LemonadeAutoUnloadMode,
    ) {
        runCatching {
            withContext(Dispatchers.IO) {
                unloadLoadedLemonadeModelFromServer(baseUrl = baseUrl, fallbackModelName = targetModel)
            }
        }.onSuccess { unloaded ->
                if (unloaded) {
                    settingsPreferences.clearPendingLemonadeAutoUnload()
                    Log.i("LemonadeUnload", "Auto-unloaded Lemonade model: $targetModel mode=${mode.storageValue}")
                } else {
                    Log.w("LemonadeUnload", "Auto-unload returned false: $targetModel mode=${mode.storageValue}")
                }
        }.onFailure { error ->
            Log.w("LemonadeUnload", "Failed to auto-unload Lemonade model: ${error.message}")
        }
    }

    override fun onCleared() {
        cancelScheduledLemonadeUnload()
        cancelRemoteRequest()
        super.onCleared()
    }

    private class SafeMarkdownStreamAssembler(
        private val streamingCodePlaceholder: String = "コード生成中…",
    ) {
        companion object {
            private const val PY_REPAIR_LOG_TAG = "PY_REPAIR"
        }
        private val confirmedText = StringBuilder()
        private val pendingCodeBlock = StringBuilder()
        private val pendingLine = StringBuilder()
        private var insideCodeBlock = false

        fun appendChunk(rawChunk: String) {
            val normalizedChunk = normalizeChunk(rawChunk)
            if (normalizedChunk.isEmpty()) return
            pendingLine.append(normalizedChunk)
            drainCompleteLines()
            flushSafeInlineText()
        }

        fun buildDisplayText(): String {
            if (!insideCodeBlock) return confirmedText.toString()
            val base = confirmedText.toString().trimEnd('\n')
            return if (base.isEmpty()) streamingCodePlaceholder else "$base\n$streamingCodePlaceholder"
        }

        fun finalizeResult(): String {
            if (insideCodeBlock) {
                if (pendingLine.isNotEmpty()) {
                    pendingCodeBlock.append(pendingLine)
                    pendingLine.clear()
                }
                if (pendingCodeBlock.isNotEmpty() && !pendingCodeBlock.endsWith("\n")) {
                    pendingCodeBlock.append('\n')
                }
                pendingCodeBlock.append("```")
                confirmedText.append(pendingCodeBlock)
                pendingCodeBlock.clear()
                insideCodeBlock = false
            } else if (pendingLine.isNotEmpty()) {
                confirmedText.append(pendingLine)
                pendingLine.clear()
            }
            val preRepairText = confirmedText.toString()
            val preStats = analyzeMarkdownForRepair(preRepairText)
            Log.d(
                PY_REPAIR_LOG_TAG,
                "PY_REPAIR pre: len=${preRepairText.length}, containsFence=${preStats.containsFence}, " +
                    "containsBareFencePythonPattern=${preStats.bareFencePythonMatchCount > 0}, " +
                    "containsPythonFenceOpening=${preStats.pythonFenceMatchCount > 0}, " +
                    "containsPythonImport=${preRepairText.contains("python\nimport")}, " +
                    "containsPygameMerge=${preRepairText.contains("import pygameimport sys#")}, " +
                    "containsScreenMerge=${preRepairText.contains("SCREEN_WIDTH =80SCREEN_HEIGHT")}, " +
                    "preview=${toSingleLinePreview(preRepairText)}",
            )
            val repairedText = repairPythonCodeBlocks(preRepairText)
            val postStats = analyzeMarkdownForRepair(repairedText)
            val changed = repairedText != preRepairText
            Log.d(
                PY_REPAIR_LOG_TAG,
                "PY_REPAIR post: len=${repairedText.length}, changed=$changed, " +
                    "pythonFenceMatchCount=${postStats.pythonFenceMatchCount}, " +
                    "bareFencePythonMatchCount=${postStats.bareFencePythonMatchCount}, " +
                    "containsPythonImport=${repairedText.contains("python\nimport")}, " +
                    "containsPygameMerge=${repairedText.contains("import pygameimport sys#")}, " +
                    "containsScreenMerge=${repairedText.contains("SCREEN_WIDTH =80SCREEN_HEIGHT")}, " +
                    "preview=${toSingleLinePreview(repairedText)}",
            )
            Log.d(
                PY_REPAIR_LOG_TAG,
                "PY_REPAIR final: len=${repairedText.length}, changedFromPreRepair=$changed",
            )
            return repairedText
        }

        private fun repairPythonCodeBlocks(markdown: String): String {
            if (!markdown.contains("```")) return markdown
            val lines = markdown.split('\n')
            if (lines.isEmpty()) return markdown

            val rebuilt = StringBuilder(markdown.length + 32)
            var index = 0
            while (index < lines.size) {
                val pythonFenceMatch = resolvePythonFenceOpening(lines, index)
                if (pythonFenceMatch == null) {
                    val currentLine = lines[index]
                    rebuilt.append(currentLine)
                    if (index < lines.lastIndex) rebuilt.append('\n')
                    index += 1
                    continue
                }
                val currentLine = lines[index]
                val openingFenceLine = if (pythonFenceMatch.fromBareFencePattern) {
                    normalizeBarePythonFenceLine(currentLine)
                } else {
                    currentLine
                }
                rebuilt.append(openingFenceLine)
                if (index < lines.lastIndex) rebuilt.append('\n')
                index = pythonFenceMatch.bodyStartIndex

                val bodyBuilder = StringBuilder()
                while (index < lines.size && !isFenceLine(lines[index])) {
                    bodyBuilder.append(lines[index])
                    if (index < lines.lastIndex) bodyBuilder.append('\n')
                    index += 1
                }
                rebuilt.append(repairPythonBlockBody(bodyBuilder.toString()))

                if (index < lines.size) {
                    rebuilt.append(lines[index])
                    if (index < lines.lastIndex) rebuilt.append('\n')
                    index += 1
                }
            }
            return rebuilt.toString()
        }

        private data class RepairDetectionStats(
            val containsFence: Boolean,
            val pythonFenceMatchCount: Int,
            val bareFencePythonMatchCount: Int,
        )

        private data class PythonFenceMatch(
            val bodyStartIndex: Int,
            val fromBareFencePattern: Boolean,
        )

        private fun resolvePythonFenceOpening(lines: List<String>, index: Int): PythonFenceMatch? {
            val currentLine = lines[index]
            if (isPythonFenceOpeningLine(currentLine)) {
                return PythonFenceMatch(bodyStartIndex = index + 1, fromBareFencePattern = false)
            }
            if (!isBareFenceLine(currentLine)) return null
            val nextIndex = index + 1
            if (nextIndex >= lines.size) return null
            if (!isPythonLanguageOnlyLine(lines[nextIndex])) return null
            return PythonFenceMatch(bodyStartIndex = nextIndex + 1, fromBareFencePattern = true)
        }

        private fun analyzeMarkdownForRepair(markdown: String): RepairDetectionStats {
            if (!markdown.contains("```")) {
                return RepairDetectionStats(
                    containsFence = false,
                    pythonFenceMatchCount = 0,
                    bareFencePythonMatchCount = 0,
                )
            }
            val lines = markdown.split('\n')
            var pythonFenceMatchCount = 0
            var bareFencePythonMatchCount = 0
            var index = 0
            while (index < lines.size) {
                val match = resolvePythonFenceOpening(lines, index)
                if (match != null) {
                    pythonFenceMatchCount += 1
                    if (match.fromBareFencePattern) {
                        bareFencePythonMatchCount += 1
                    }
                }
                index += 1
            }
            return RepairDetectionStats(
                containsFence = true,
                pythonFenceMatchCount = pythonFenceMatchCount,
                bareFencePythonMatchCount = bareFencePythonMatchCount,
            )
        }

        private fun toSingleLinePreview(value: String, maxLength: Int = 320): String {
            return value
                .replace("\n", "\\n")
                .take(maxLength)
        }

        private fun isPythonFenceOpeningLine(line: String): Boolean {
            val withoutIndent = line.trimStart(' ', '\t')
            if (!withoutIndent.startsWith("```")) return false
            val rawSuffix = withoutIndent.removePrefix("```").trim()
            if (rawSuffix.isEmpty()) return false
            val languageToken = rawSuffix.substringBefore(' ').lowercase(Locale.ROOT)
            return languageToken == "python" || languageToken == "py"
        }

        private fun isBareFenceLine(line: String): Boolean {
            val withoutIndent = line.trimStart(' ', '\t')
            if (!withoutIndent.startsWith("```")) return false
            return withoutIndent.removePrefix("```").trim().isEmpty()
        }

        private fun isPythonLanguageOnlyLine(line: String): Boolean {
            val trimmed = line.trim().lowercase(Locale.ROOT)
            return trimmed == "python" || trimmed == "py"
        }

        private fun normalizeBarePythonFenceLine(line: String): String {
            val withoutIndent = line.trimStart(' ', '\t')
            val indentLength = line.length - withoutIndent.length
            val indent = line.substring(0, indentLength)
            return "${indent}```python"
        }

        private fun repairPythonBlockBody(body: String): String {
            if (body.isEmpty()) return body
            val lines = body.split('\n')
            if (lines.isEmpty()) return body
            val repairedLines = mutableListOf<String>()
            var index = 0
            while (index < lines.size) {
                var line = lines[index]
                val nextLine = lines.getOrNull(index + 1)
                val splitResult = splitCommentFragmentAndCode(line)
                line = splitResult.line
                if (splitResult.extractedComment != null) {
                    repairedLines.add(line)
                    repairedLines.add(splitResult.extractedComment)
                    index += 1
                    continue
                }
                if (line.trimStart().startsWith("#")) {
                    val merged = mergeCommentBlocks(lines, index)
                    repairedLines.addAll(normalizeCommentLines(merged.comments))
                    index = merged.nextIndex
                    continue
                }
                val repairedLine = repairPythonCodeLine(line, nextLine)
                repairedLines.add(repairedLine)
                index += 1
            }
            return repairedLines.joinToString("\n")
        }

        private fun repairPythonCodeLine(line: String, nextLine: String?): String {
            var repaired = line
            repaired = repaired.replace(
                Regex("^\\s*\\(([^)]{1,20})\\)([A-Za-z_][A-Za-z0-9_]*\\s*=.*)$"),
                "# （$1）\n$2",
            )
            if (repaired.trimStart().startsWith("---") && repaired.contains("pygame.")) {
                repaired = repaired.trimStart().removePrefix("---").trimStart()
            }
            repaired = repaired.replace(Regex("(?<!\\S)(import\\s+[\\w.]+)import\\s+"), "$1\nimport ")
            repaired = repaired.replace(
                Regex("(?<=[\\]\"'A-Za-z_0-9\\)])\\s*#\\s*(\\S.*)$"),
                "\n# $1",
            )
            repaired = repaired.replace(
                Regex("(\\b(?:True|False|None)\\b|\\d)([A-Za-z_][A-Za-z0-9_]*\\s*=)"),
                "$1\n$2",
            )
            repaired = repaired.replace(
                Regex("(?<=\\))(?=(?:[A-Za-z_][A-Za-z0-9_]*\\s*=|pygame\\.))"),
                "\n",
            )
            repaired = repaired.replace(
                Regex("(?<=\\])(for|if|while|with|return|pass|break|continue)\\b"),
                "\n$1",
            )
            repaired = repaired.replace(
                Regex("(?<=[^\\s=<>!])=(?=[^=\\s])"),
                " = ",
            )
            repaired = repaired.replace(Regex("(?<=\\d),(?=\\d)"), ", ")
            repaired = repaired.replace(Regex("(?<=\\S),(?=\\S)"), ", ")
            if (nextLine != null && isCommentFragment(nextLine.trim())) {
                repaired = repaired.replace(Regex("\\s+#\\s*$"), "")
            }
            return repaired
        }

        private data class MergedCommentResult(
            val comments: List<String>,
            val nextIndex: Int,
        )

        private data class SplitCommentCodeResult(
            val line: String,
            val extractedComment: String? = null,
        )

        private fun mergeCommentBlocks(lines: List<String>, startIndex: Int): MergedCommentResult {
            val comments = mutableListOf<String>()
            var index = startIndex
            while (index < lines.size) {
                val current = lines[index]
                val trimmed = current.trim()
                if (trimmed.isEmpty()) break
                if (trimmed.startsWith("#")) {
                    comments.add(current)
                    index += 1
                    continue
                }
                if (!isCommentFragment(trimmed)) break
                comments.add("# $trimmed")
                index += 1
            }
            return MergedCommentResult(comments = comments, nextIndex = index)
        }

        private fun splitCommentFragmentAndCode(line: String): SplitCommentCodeResult {
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("#")) return SplitCommentCodeResult(line = line)
            val match = Regex("^(\\s*#\\s*[^=]+?)([A-Za-z_][A-Za-z0-9_]*\\s*=.*)$").find(line)
            if (match != null) {
                val commentLine = normalizePlainComment(match.groupValues[1])
                val codeLine = match.groupValues[2].trimStart()
                return SplitCommentCodeResult(line = commentLine, extractedComment = codeLine)
            }
            return SplitCommentCodeResult(line = line)
        }

        private fun normalizeCommentLines(lines: List<String>): List<String> {
            if (lines.isEmpty()) return lines
            val rebuilt = mutableListOf<String>()
            var index = 0
            while (index < lines.size) {
                val current = lines[index]
                val trimmed = current.trim()
                if (!trimmed.startsWith("#")) {
                    rebuilt.add(current)
                    index += 1
                    continue
                }
                val rawContent = trimmed.removePrefix("#").trim()
                if (rawContent.replace(" ", "").contains("パラメータ---パドル")) {
                    rebuilt.add("# --- ゲームオブジェクトのパラメータ ---")
                    rebuilt.add("# パドル（プレイヤー）")
                    index += 1
                    continue
                }
                if (!rawContent.contains("---") && isCommentFragment(rawContent)) {
                    val mergedContent = StringBuilder(rawContent)
                    var cursor = index + 1
                    while (cursor < lines.size) {
                        val nextContent = lines[cursor].trim().removePrefix("#").trim()
                        if (nextContent.isEmpty() || nextContent.contains("---") || !isCommentFragment(nextContent)) break
                        mergedContent.append(nextContent)
                        cursor += 1
                    }
                    rebuilt.add(normalizePlainComment("# ${mergedContent.toString()}"))
                    index = cursor
                    continue
                }
                val normalized = if (trimmed.contains("---")) normalizeDashComment(current) else normalizePlainComment(current)
                rebuilt.add(normalized)
                index += 1
            }
            return rebuilt
        }

        private fun normalizeDashComment(line: String): String {
            var normalized = line.replace(Regex("^(\\s*)#\\s*"), "$1# ")
            normalized = normalized.replace(Regex("\\s*---\\s*"), " --- ")
            normalized = normalized.replace(Regex("\\s{2,}"), " ").trimEnd()
            if (!normalized.trimStart().startsWith("#")) {
                normalized = "# ${normalized.trim()}"
            }
            return normalized
        }

        private fun normalizePlainComment(line: String): String {
            val trimmed = line.trim()
            val content = trimmed.removePrefix("#").trim()
            val merged = content
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\(\\s*"), "（")
                .replace(Regex("\\s*\\)"), "）")
            return "# $merged"
        }

        private fun isCommentFragment(text: String): Boolean {
            if (text.isEmpty()) return false
            if (looksLikeCodeLine(text)) return false
            if (text.length > 18) return false
            return containsJapanese(text) || text.all { it == '-' || it.isWhitespace() }
        }

        private fun containsJapanese(text: String): Boolean {
            return text.any {
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HIRAGANA ||
                    Character.UnicodeBlock.of(it) == Character.UnicodeBlock.KATAKANA ||
                    Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            }
        }

        private fun looksLikeCodeLine(text: String): Boolean {
            if (text.contains(Regex("[=\\[\\]{}():]"))) return true
            if (text.startsWith("import ")) return true
            if (text.startsWith("from ")) return true
            if (text.startsWith("for ") || text.startsWith("if ") || text.startsWith("while ")) return true
            return false
        }

        private fun drainCompleteLines() {
            while (true) {
                val newlineIndex = pendingLine.indexOf("\n")
                if (newlineIndex < 0) break
                val line = pendingLine.substring(0, newlineIndex)
                pendingLine.delete(0, newlineIndex + 1)
                appendLine(line, hasTrailingNewline = true)
            }
        }

        private fun appendLine(line: String, hasTrailingNewline: Boolean) {
            val isFence = isFenceLine(line)
            if (!insideCodeBlock) {
                if (isFence) {
                    insideCodeBlock = true
                    pendingCodeBlock.clear()
                    pendingCodeBlock.append(normalizeOpeningFenceLine(line))
                    if (hasTrailingNewline) pendingCodeBlock.append('\n')
                    return
                }
                confirmedText.append(line)
                if (hasTrailingNewline) confirmedText.append('\n')
                return
            }
            pendingCodeBlock.append(line)
            if (hasTrailingNewline) pendingCodeBlock.append('\n')
            if (isFence) {
                insideCodeBlock = false
                confirmedText.append(pendingCodeBlock)
                pendingCodeBlock.clear()
            }
        }

        private fun flushSafeInlineText() {
            if (insideCodeBlock || pendingLine.isEmpty()) return
            val partialLine = pendingLine.toString()
            if (isPossibleFencePrefix(partialLine)) return
            confirmedText.append(partialLine)
            pendingLine.clear()
        }

        private fun isFenceLine(line: String): Boolean {
            val withoutIndent = line.trimStart(' ', '\t')
            return withoutIndent.startsWith("```")
        }

        private fun normalizeOpeningFenceLine(line: String): String {
            val withoutIndent = line.trimStart(' ', '\t')
            if (!withoutIndent.startsWith("```")) return line
            val indentLength = line.length - withoutIndent.length
            val indent = line.substring(0, indentLength)
            val rawSuffix = withoutIndent.removePrefix("```").trim()
            if (rawSuffix.isEmpty()) return "${indent}```"
            val languageToken = rawSuffix.substringBefore(' ')
            val suffixRemainder = rawSuffix.removePrefix(languageToken).trimStart()
            val normalizedLanguage = normalizeLanguageToken(languageToken)
            return if (suffixRemainder.isEmpty()) {
                "${indent}```$normalizedLanguage"
            } else {
                "${indent}```$normalizedLanguage $suffixRemainder"
            }
        }

        private fun normalizeLanguageToken(token: String): String {
            return when (token.lowercase(Locale.ROOT)) {
                "kt" -> "kotlin"
                "py" -> "python"
                "js" -> "javascript"
                "ts" -> "typescript"
                "sh" -> "bash"
                else -> token
            }
        }

        private fun isPossibleFencePrefix(text: String): Boolean {
            val trimmed = text.trimStart(' ', '\t')
            return "```".startsWith(trimmed)
        }

        private fun normalizeChunk(chunk: String): String {
            return chunk
                .replace("\r\n", "\n")
                .replace("\r", "\n")
        }
    }

    private fun parseStreamingChunk(line: String): StreamChunk {
        val json = runCatching { JSONObject(line) }
            .getOrElse { throw IOException("Failed to parse streaming chunk: $line") }
        val responseText = json.optString("response").takeIf { it.isNotEmpty() }
        val messageText = json.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotEmpty() }
        return StreamChunk(
            text = responseText ?: messageText,
            done = json.optBoolean("done", false),
            model = json.optNullableString("model"),
            evalCount = json.optNullableInt("eval_count"),
            evalDurationNs = json.optNullableLong("eval_duration"),
            loadDurationNs = json.optNullableLong("load_duration"),
            promptEvalCount = json.optNullableInt("prompt_eval_count"),
            promptEvalDurationNs = json.optNullableLong("prompt_eval_duration"),
            totalDurationNs = json.optNullableLong("total_duration"),
            doneReason = json.optNullableString("done_reason") ?: json.optNullableString("finish_reason"),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) runCatching { getInt(name) }.getOrNull() else null

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) runCatching { getLong(name) }.getOrNull() else null


    private data class StreamingResult(
        val text: String,
        val finalChunk: StreamChunk? = null,
        val timeToFirstTokenMs: Long? = null,
        val assistantUpdateCount: Int = 0,
    )

    private data class StreamChunk(
        val text: String?,
        val done: Boolean,
        val model: String? = null,
        val evalCount: Int? = null,
        val evalDurationNs: Long? = null,
        val loadDurationNs: Long? = null,
        val promptEvalCount: Int? = null,
        val promptEvalDurationNs: Long? = null,
        val totalDurationNs: Long? = null,
        val doneReason: String? = null,
    )

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    fun loadAvailableModels() {

        viewModelScope.launch {
            _isLoadingModels.value = true
            val baseUrl = this@OllamaViewModel.baseUrl.value.trimEnd('/')
            if (baseUrl.isBlank()) {
                _availableModels.value = emptyList()
                _isLoadingModels.value = false
                return@launch
            }
            try {
                val models = availableModelsFetcher(baseUrl, remoteProvider)
                _availableModels.value = models
                refreshSelectedModel(models)
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e("OllamaError", "Error loading models: ${e.message}")
                _availableModels.value = emptyList()
                val message = e.message ?: "Unknown error"
                updateErrorState("Failed to load models: $message")
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
                prefetchEffectiveContextWindow(savedModelAvailable)
                withContext(Dispatchers.IO) {
                    modelPreferenceRepository.setSelectedModel(baseUrl, savedModelAvailable)
                }
            }

            currentSelection != null -> {
                _selectedModel.value = currentSelection
                prefetchEffectiveContextWindow(currentSelection)
            }

            else -> {
                clearSelectedModelForBaseUrl(baseUrl)
            }
        }
    }

    private suspend fun encodeAttachmentImagesToBase64(context: Context, attachmentUris: List<Uri>): EncodedAttachments? {
        return withContext(Dispatchers.IO) {
            try {
                val base64Images = mutableListOf<String>()
                val savedUriStrings = mutableListOf<String>()
                val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
                val targetUris = attachmentUris.take(min(attachmentUris.size, MAX_COMPOSER_ATTACHMENTS))
                targetUris.forEachIndexed { index, attachmentUri ->
                    val bytes = context.contentResolver.openInputStream(attachmentUri)?.use { input ->
                        input.readBytes()
                    } ?: run {
                        updateErrorState("Failed to read attachment image")
                        return@withContext null
                    }

                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                    val maxLongEdgePx = 1280
                    var sampleSize = 1
                    val longEdge = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                    while (longEdge / sampleSize > maxLongEdgePx) {
                        sampleSize *= 2
                    }

                    val bitmapOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions) ?: run {
                        updateErrorState("Failed to read attachment image")
                        return@withContext null
                    }

                    val outputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                    bitmap.recycle()

                    val jpegBytes = outputStream.toByteArray()
                    val attachmentFile = File(attachmentsDir, "att_${System.currentTimeMillis()}_${index}.jpg")
                    attachmentFile.writeBytes(jpegBytes)
                    base64Images += Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                    savedUriStrings += attachmentFile.toURI().toString()
                }

                EncodedAttachments(
                    base64Images = base64Images,
                    savedUriStrings = savedUriStrings,
                )
            } catch (e: Exception) {
                Log.e("ChatAttachment", "Failed to encode attachment", e)
                updateErrorState("Failed to read attachment image")
                null
            }
        }
    }

    private fun updateErrorState(message: String) {
        _uiState.value = UiState.Error(message)
        persistErrorCause(message)
    }

    private fun persistErrorCause(message: String?) {
        val cause = inferErrorCause(message)
        val storedCause = cause?.takeUnless { it == ErrorCause.UNKNOWN }
        viewModelScope.launch {
            settingsPreferences.saveErrorCause(storedCause)
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
            prefetchEffectiveContextWindow(modelName)
            // 永続化はユーザーが明示的に updateSelectedModel を呼び出した場合のみ行う
            withContext(Dispatchers.IO) {
                modelPreferenceRepository.setSelectedModel(baseUrl, modelName)
            }
        }
    }

    private fun resolveContextWindowFetchState(modelName: String?): ContextWindowFetchState {
        val normalizedModel = modelName?.trim().orEmpty()
        if (normalizedModel.isBlank()) {
            return ContextWindowFetchState.UNAVAILABLE
        }
        val cacheKey = buildContextWindowCacheKey(normalizedModel)
        val cachedWindow = effectiveContextWindowCache[cacheKey]
        if (cachedWindow != null && cachedWindow > 0) {
            return ContextWindowFetchState.AVAILABLE
        }
        return when (effectiveContextWindowRequestState[cacheKey]) {
            ContextWindowResolutionState.LOADING -> ContextWindowFetchState.LOADING
            ContextWindowResolutionState.RESOLVED_WITH_VALUE -> ContextWindowFetchState.AVAILABLE
            ContextWindowResolutionState.RESOLVED_WITHOUT_VALUE -> ContextWindowFetchState.UNAVAILABLE
            null -> ContextWindowFetchState.LOADING
        }
    }

    private fun getCachedEffectiveContextWindow(modelName: String): Int? {
        return effectiveContextWindowCache[buildContextWindowCacheKey(modelName)]
    }

    private fun prefetchEffectiveContextWindow(modelName: String?) {
        val normalizedModel = modelName?.trim().orEmpty()
        if (normalizedModel.isBlank()) {
            return
        }
        val cacheKey = buildContextWindowCacheKey(normalizedModel)
        if (effectiveContextWindowCache.containsKey(cacheKey)) {
            effectiveContextWindowRequestState[cacheKey] = if (effectiveContextWindowCache[cacheKey] != null) {
                ContextWindowResolutionState.RESOLVED_WITH_VALUE
            } else {
                ContextWindowResolutionState.RESOLVED_WITHOUT_VALUE
            }
            return
        }
        if (effectiveContextWindowRequestState[cacheKey] == ContextWindowResolutionState.LOADING) {
            return
        }
        effectiveContextWindowRequestState[cacheKey] = ContextWindowResolutionState.LOADING
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching { fetchEffectiveContextWindow(normalizedModel) }
                    .onFailure { error ->
                        Log.d("OllamaViewModel", "Failed to resolve effective context window for $normalizedModel: ${error.message}")
                    }
                    .getOrNull()
            }
            effectiveContextWindowCache[cacheKey] = resolved
            effectiveContextWindowRequestState[cacheKey] = if (resolved != null && resolved > 0) {
                ContextWindowResolutionState.RESOLVED_WITH_VALUE
            } else {
                ContextWindowResolutionState.RESOLVED_WITHOUT_VALUE
            }
            _latestInferenceStats.update { current ->
                if (current == null) {
                    return@update null
                }
                val statsModel = current.modelName ?: current.model
                val normalizedStatsModel = statsModel?.trim().orEmpty()
                if (normalizedStatsModel.isBlank() || buildContextWindowCacheKey(normalizedStatsModel) != cacheKey) {
                    return@update current
                }
                val totalTokens = current.totalTokens
                current.copy(
                    contextWindow = resolved,
                    contextWindowFetchState = if (resolved != null && resolved > 0) {
                        ContextWindowFetchState.AVAILABLE
                    } else {
                        ContextWindowFetchState.UNAVAILABLE
                    },
                    contextUsageRatio = if (resolved != null && resolved > 0 && totalTokens != null) {
                        totalTokens.toDouble() / resolved.toDouble()
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun fetchEffectiveContextWindow(modelName: String): Int? {
        val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')
        val url = URL("$baseUrl/api/show")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val requestBody = JSONObject()
            .put("model", modelName)
            .toString()
        connection.outputStream.use { output ->
            output.write(requestBody.toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return null
        val response = responseStream.bufferedReader().use { it.readText() }
        if (responseCode !in 200..299) {
            throw IOException("Failed to load model details (HTTP $responseCode): $response")
        }
        return extractEffectiveContextWindowFromShowResponse(response)
    }

    private enum class ContextWindowResolutionState {
        LOADING,
        RESOLVED_WITH_VALUE,
        RESOLVED_WITHOUT_VALUE,
    }

    private data class EncodedAttachments(
        val base64Images: List<String>,
        val savedUriStrings: List<String>,
    )

    companion object {
        private const val AUTO_DELETE_DELAY_MS = 10 * 60 * 1000L
        private const val MAX_COMPOSER_ATTACHMENTS = 10
    }

}

private val NUM_CTX_PATTERN = Regex("""(^|[\s\\n])num_ctx\s+(\d+)""", RegexOption.MULTILINE)
private val OPTIONS_NUM_CTX_PATTERN = Regex(""""options"\s*:\s*\{[^}]*"num_ctx"\s*:\s*(\d+)""")
private val JSON_PARAMETERS_PATTERN = Regex(""""parameters"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL)
private val MODEL_INFO_CONTEXT_PATTERN = Regex(""""model_info"\s*:\s*\{[^}]*"[^"]*context_length"\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
private val CONTEXT_WINDOW_PATTERN = Regex(""""context_window"\s*:\s*(\d+)""")
private val ROOT_CONTEXT_LENGTH_PATTERN = Regex(""""context_length"\s*:\s*(\d+)""")
private val DETAILS_CONTEXT_LENGTH_PATTERN = Regex(""""details"\s*:\s*\{[^}]*"context_length"\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)

private fun JSONObject.optNullableIntCompat(name: String): Int? =
    if (has(name) && !isNull(name)) runCatching { getInt(name) }.getOrNull() else null

@VisibleForTesting
internal fun extractEffectiveContextWindowFromShowResponse(response: String): Int? {
    extractFirstPositiveInt(OPTIONS_NUM_CTX_PATTERN, response)?.let { return it }

    JSON_PARAMETERS_PATTERN.find(response)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::unescapeJsonStringForContextWindow)
        ?.let { parameters ->
            NUM_CTX_PATTERN.find(parameters)
                ?.groupValues
                ?.getOrNull(2)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }
        }

    extractFirstPositiveInt(MODEL_INFO_CONTEXT_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(CONTEXT_WINDOW_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(ROOT_CONTEXT_LENGTH_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(DETAILS_CONTEXT_LENGTH_PATTERN, response)?.let { return it }

    return null
}

private fun extractFirstPositiveInt(pattern: Regex, text: String): Int? {
    return pattern.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun unescapeJsonStringForContextWindow(value: String): String {
    return value
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
