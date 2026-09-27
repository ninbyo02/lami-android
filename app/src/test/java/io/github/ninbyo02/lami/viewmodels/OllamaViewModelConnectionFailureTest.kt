package io.github.ninbyo02.lami.viewmodels

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import io.github.ninbyo02.lami.UiState
import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.dao.ChatLatestMessage
import io.github.ninbyo02.lami.db.dao.MessageDao
import io.github.ninbyo02.lami.db.dao.ModelPreferenceDao
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.SelectedModel
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OllamaViewModelConnectionFailureTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val viewModels = mutableSetOf<OllamaViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            viewModels.clear()
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `server model load exception becomes error state and keeps selected model`() = runViewModelTest {
        val modelPreferenceDao = FakeModelPreferenceDao().apply {
            selected["http://localhost:13511"] = SelectedModel(
                baseUrl = "http://localhost:13511",
                modelName = "saved-server-model",
            )
        }
        val viewModel = OllamaViewModel(
            ChatRepository(
                messageDao = FakeMessageDao(),
                chatDao = FakeChatDao(),
            ),
            ModelPreferenceRepository(modelPreferenceDao),
            SettingsPreferences(RuntimeEnvironment.getApplication()),
            "saved-server-model",
            MutableStateFlow("http://localhost:13511"),
            false,
        ) { _, _ ->
            throw IOException("server disconnected")
        }

        viewModels += viewModel
        viewModel.loadAvailableModels().join()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState is UiState.Error)
        assertTrue((uiState as UiState.Error).errorMessage.contains("server disconnected"))
        assertEquals("saved-server-model", viewModel.selectedModel.value)
        assertEquals("saved-server-model", modelPreferenceDao.selected["http://localhost:13511"]?.modelName)
    }
    @Test
    fun `provider preferences survive switching to a server supporting both APIs`() = runViewModelTest {
        val preferences = SettingsPreferences(RuntimeEnvironment.getApplication())
        val lemonadeUrl = "http://localhost:13512"
        val ollamaUrl = "http://localhost:13513"
        preferences.saveRemoteProvider(RemoteProvider.LEMONADE, lemonadeUrl + "/")
        preferences.saveRemoteProvider(RemoteProvider.OLLAMA, ollamaUrl)
        assertEquals(RemoteProvider.LEMONADE, preferences.remoteProviderForBaseUrlFlow(lemonadeUrl).first())
        assertEquals(RemoteProvider.OLLAMA, preferences.remoteProviderForBaseUrlFlow(ollamaUrl).first())
        val dao = FakeModelPreferenceDao()
        var receivedProvider: RemoteProvider? = null
        val vm = OllamaViewModel(ChatRepository(FakeMessageDao(), FakeChatDao()),
            ModelPreferenceRepository(dao), preferences, null, MutableStateFlow(lemonadeUrl), false) { _, provider ->
            receivedProvider = provider
            // Both endpoints succeed but expose different identifiers.
            RemoteModelsResult(listOf(ModelInfo(if (provider == RemoteProvider.LEMONADE) "model" else "model:latest")), provider)
        }
        viewModels += vm
        vm.loadAvailableModels().join()
        assertEquals(RemoteProvider.LEMONADE, receivedProvider)
        assertEquals("model", vm.selectedModel.value)
        assertEquals("model", dao.selected[lemonadeUrl]?.modelName)
        assertEquals(RemoteProvider.OLLAMA, preferences.remoteProviderForBaseUrlFlow(ollamaUrl).first())
    }

    @Test
    fun `late discovery cannot overwrite new server models or selection`() = runViewModelTest {
        val preferences = SettingsPreferences(RuntimeEnvironment.getApplication())
        val firstUrl = "http://localhost:13514"
        val secondUrl = "http://localhost:13515"
        preferences.saveRemoteProvider(RemoteProvider.LEMONADE, firstUrl)
        preferences.saveRemoteProvider(RemoteProvider.OLLAMA, secondUrl)
        val urls = MutableStateFlow(firstUrl)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dao = FakeModelPreferenceDao()
        val vm = OllamaViewModel(ChatRepository(FakeMessageDao(), FakeChatDao()),
            ModelPreferenceRepository(dao), preferences, null, urls, false) { url, provider ->
            if (url == firstUrl) {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
            }
            RemoteModelsResult(listOf(ModelInfo(if (url == firstUrl) "old-model" else "new-model")), provider)
        }
        viewModels += vm
        val first = vm.loadAvailableModels()
        entered.await()
        urls.value = secondUrl
        viewModels += vm
        vm.loadAvailableModels().join()
        release.complete(Unit)
        first.join()
        assertEquals(listOf("new-model"), vm.availableModels.value.map { it.name })
        assertEquals("new-model", vm.selectedModel.value)
        assertEquals("new-model", dao.selected[secondUrl]?.modelName)
        assertEquals(null, dao.selected[firstUrl])
        assertTrue(vm.uiState.value !is UiState.Error)
    }

    @Test
    fun `single model selection is persisted and missing selection clears on multiple models`() = runViewModelTest {
        val dao = FakeModelPreferenceDao()
        val url = "http://selection-test.local:13511"
        var names = listOf("single")
        val vm = OllamaViewModel(ChatRepository(FakeMessageDao(), FakeChatDao()),
            ModelPreferenceRepository(dao), SettingsPreferences(RuntimeEnvironment.getApplication()),
            null, MutableStateFlow(url), false) { _, _ ->
            RemoteModelsResult(names.map(::ModelInfo), RemoteProvider.LEMONADE)
        }
        viewModels += vm
        vm.loadAvailableModels().join()
        assertEquals("single", vm.selectedModel.value)
        assertEquals("single", dao.selected[url]?.modelName)
        names = listOf("other-a", "other-b")
        viewModels += vm
        vm.loadAvailableModels().join()
        assertEquals(null, vm.selectedModel.value)
        assertEquals(null, dao.selected[url])
    }

    @Test
    fun `server switching restores saved selection from multiple models`() = runViewModelTest {
        val firstUrl = "http://selection-one.local:13511"
        val secondUrl = "http://selection-two.local:13511"
        val dao = FakeModelPreferenceDao().apply { selected[secondUrl] = SelectedModel(secondUrl, "saved") }
        val urls = MutableStateFlow(firstUrl)
        val preferences = SettingsPreferences(RuntimeEnvironment.getApplication())
        // Both fake servers are Lemonade. Seed both URLs before starting collectors,
        // so this selection test cannot fall through to Ollama model-detail prefetch.
        preferences.saveRemoteProvider(RemoteProvider.LEMONADE, firstUrl)
        preferences.saveRemoteProvider(RemoteProvider.LEMONADE, secondUrl)
        val vm = OllamaViewModel(ChatRepository(FakeMessageDao(), FakeChatDao()),
            ModelPreferenceRepository(dao), preferences,
            null, urls, false) { url, _ ->
            RemoteModelsResult((if (url == firstUrl) listOf("only") else listOf("saved", "other")).map(::ModelInfo), RemoteProvider.LEMONADE)
        }
        viewModels += vm
        vm.loadAvailableModels().join()
        urls.value = secondUrl
        viewModels += vm
        vm.loadAvailableModels().join()
        assertEquals("saved", vm.selectedModel.value)
        assertEquals("only", dao.selected[firstUrl]?.modelName)
        assertEquals("saved", dao.selected[secondUrl]?.modelName)
        urls.value = firstUrl
        viewModels += vm
        vm.loadAvailableModels().join()
        assertEquals("only", vm.selectedModel.value)
    }

}

private class FakeChatDao : ChatDao {
    override suspend fun insertChat(chat: Chat): Long = 1L
    override fun getAllChats() = flowOf(emptyList<Chat>())
    override suspend fun getChatById(chatId: Int): Chat? = null
    override suspend fun updateChatTitle(chatId: Int, title: String, newSource: String, expectedSource: String): Int = 0
    override suspend fun deleteChatIfStillEmptyTempPlaceholder(chatId: Int, expectedSource: String): Int = 0
    override suspend fun deleteEmptyTempPlaceholderChats(expectedSource: String): Int = 0
    override suspend fun deleteChat(chat: Chat) = Unit
}

private class FakeMessageDao : MessageDao {
    override suspend fun insertMessage(message: Message) = Unit
    override suspend fun insertMessageAndReturnId(message: Message): Long = 1L
    override fun getAllMessages(chatId: Int) = flowOf(emptyList<Message>())
    override suspend fun getMessageById(messageId: Int): Message? = null
    override suspend fun updateMessage(message: Message) = Unit
    override suspend fun transitionAssistantMessageStatus(
        messageId: Int,
        expectedStatuses: List<String>,
        newStatus: String,
        errorCode: String?,
        updatedAtEpochMs: Long,
    ): Int = 0
    override suspend fun updateAssistantMessageContentIfStatus(
        messageId: Int,
        expectedStatus: String,
        message: String,
        updatedAtEpochMs: Long,
    ): Int = 0
    override suspend fun completeInFlightAssistantMessage(
        messageId: Int,
        message: String,
        updatedAtEpochMs: Long,
    ): Int = 0
    override suspend fun failInFlightAssistantMessage(
        messageId: Int,
        message: String?,
        errorCode: String,
        updatedAtEpochMs: Long,
    ): Int = 0
    override suspend fun interruptInFlightAssistantMessagesAfterRestart(
        processStartedAtEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int = 0
    override suspend fun countMessages(chatId: Int): Int = 0
    override suspend fun getFirstUserMessage(chatId: Int): Message? = null
    override suspend fun getFirstNonEmptyMessage(chatId: Int): Message? = null
    override suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage> = emptyList()
    override suspend fun deleteMessage(message: Message) = Unit
}

private class FakeModelPreferenceDao : ModelPreferenceDao {
    val selected = linkedMapOf<String, SelectedModel>()

    override suspend fun getByBaseUrl(baseUrl: String): SelectedModel? = selected[baseUrl]
    override suspend fun upsert(model: SelectedModel) {
        selected[model.baseUrl] = model
    }

    override suspend fun deleteByBaseUrl(baseUrl: String) {
        selected.remove(baseUrl)
    }

    override suspend fun getAllBaseUrls(): List<String> = selected.keys.toList()
    override suspend fun deleteAllExcept(baseUrls: List<String>) {
        selected.keys.retainAll(baseUrls.toSet())
    }

    override suspend fun clearAll() {
        selected.clear()
    }
}
