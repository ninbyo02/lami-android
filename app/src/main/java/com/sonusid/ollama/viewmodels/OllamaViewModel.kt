package com.sonusid.ollama.viewmodels
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonusid.ollama.UiState
import com.sonusid.ollama.api.OllamaRequest
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.dao.ChatLatestMessage
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.TitleSource
import com.sonusid.ollama.db.repository.ChatRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import com.sonusid.ollama.ui.model.InferenceStats
import com.sonusid.ollama.ui.screens.settings.ErrorCause
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.util.RuntimeFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.min
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
data class ModelInfo(val name: String)
class OllamaViewModel(
    private val chatRepository: ChatRepository,
    private val modelPreferenceRepository: ModelPreferenceRepository,
    private val settingsPreferences: SettingsPreferences,
    private val initialSelectedModel: String?,
    baseUrlFlow: StateFlow<String>,
    private val shouldAutoLoadModels: Boolean = true,
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

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats
    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()
    val baseUrl: StateFlow<String> = baseUrlFlow

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

            if (model != null) {
                try {
                    val streamingResult = withContext(Dispatchers.IO) {
                        collectStreamingResponse(request, generationStartedAtMs)
                    }
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
                            // 旧命名互換（段階的移行用）。
                            model = finalChunk?.model ?: model,
                            modelLabel = finalChunk?.model ?: model,
                            completionTokens = outputTokens,
                        )
                        _uiState.value = UiState.Success(finalText)
                    }
                } catch (e: Exception) {
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

    private fun collectStreamingResponse(request: OllamaRequest, requestStartedAtMs: Long): StreamingResult {
        val response = RetrofitClient.instance.generateTextStream(request).execute()
        if (!response.isSuccessful) {
            val error = response.errorBody()?.string().orEmpty()
            throw IOException(error.ifEmpty { "Failed to generate response" })
        }

        val body = response.body() ?: throw IOException("Empty response")
        val resultBuilder = StringBuilder()
        var doneReceived = false
        val streamingUiUpdateIntervalMs = 80L
        val priorityFlushChars = setOf('。', '、', '！', '？', '\n')
        var lastUiUpdateAtMs = 0L
        var latestFlushedLength = 0
        var finalChunk: StreamChunk? = null
        var timeToFirstTokenMs: Long? = null

        body.charStream().buffered().use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    continue
                }
                val chunk = parseStreamingChunk(line)
                if (shouldCaptureFirstAssistantToken(timeToFirstTokenMs, chunk.text)) {
                    // assistant 本文の最初の非空トークン受信時刻をアプリ側で確定する。
                    timeToFirstTokenMs = (SystemClock.elapsedRealtime() - requestStartedAtMs).coerceAtLeast(0L)
                }
                if (!chunk.text.isNullOrBlank()) {
                    resultBuilder.append(chunk.text)
                    val currentText = resultBuilder.toString()
                    val nowMs = System.currentTimeMillis()
                    val isIntervalElapsed = nowMs - lastUiUpdateAtMs >= streamingUiUpdateIntervalMs
                    val endsWithPriorityChar =
                        chunk.text.lastOrNull() in priorityFlushChars ||
                            currentText.lastOrNull() in priorityFlushChars
                    if (isIntervalElapsed || endsWithPriorityChar) {
                        onResponseReceived(currentText.length)
                        _uiState.value = UiState.Streaming(currentText)
                        lastUiUpdateAtMs = nowMs
                        latestFlushedLength = currentText.length
                    }
                }
                if (chunk.done) {
                    finalChunk = chunk
                    val currentText = resultBuilder.toString()
                    if (currentText.isNotEmpty() && latestFlushedLength != currentText.length) {
                        onResponseReceived(currentText.length)
                        _uiState.value = UiState.Streaming(currentText)
                        latestFlushedLength = currentText.length
                    }
                    doneReceived = true
                    break
                }
            }
        }

        if (!doneReceived) {
            throw IOException("Streaming response ended before done=true")
        }
        if (resultBuilder.isEmpty()) {
            throw IOException("Empty response")
        }
        return StreamingResult(
            text = resultBuilder.toString(),
            finalChunk = finalChunk,
            timeToFirstTokenMs = timeToFirstTokenMs,
        )
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
                updateErrorState("Failed to load models: $message")
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
            // 永続化はユーザーが明示的に updateSelectedModel を呼び出した場合のみ行う
            withContext(Dispatchers.IO) {
                modelPreferenceRepository.setSelectedModel(baseUrl, modelName)
            }
        }
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
