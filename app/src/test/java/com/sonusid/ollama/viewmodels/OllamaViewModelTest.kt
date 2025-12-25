package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.dao.ChatDao
import com.sonusid.ollama.db.dao.MessageDao
import com.sonusid.ollama.db.dao.ModelPreferenceDao
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.SelectedModel
import com.sonusid.ollama.db.repository.ChatRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class OllamaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeChatDao = FakeChatDao()
    private val fakeMessageDao = FakeMessageDao()
    private val fakeModelPreferenceDao = FakeModelPreferenceDao()

    private val chatRepository = ChatRepository(fakeMessageDao, fakeChatDao)
    private val modelPreferenceRepository = ModelPreferenceRepository(fakeModelPreferenceDao)

    private val baseUrl = RetrofitClient.currentBaseUrl().trimEnd('/')

    @Test
    fun `refreshSelectedModel preserves saved selection when list shrinks to single model`() =
        runTest {
            val savedModel = "llama2"
            modelPreferenceRepository.setSelectedModel(baseUrl, savedModel)
            val viewModel = OllamaViewModel(chatRepository, modelPreferenceRepository)

            viewModel.refreshSelectedModel(listOf(ModelInfo(savedModel), ModelInfo("qwen")))
            assertEquals(savedModel, viewModel.selectedModel.value)
            assertEquals(savedModel, fakeModelPreferenceDao.getByBaseUrl(baseUrl)?.modelName)

            val singleModel = "mistral"
            viewModel.refreshSelectedModel(listOf(ModelInfo(singleModel)))
            assertEquals(singleModel, viewModel.selectedModel.value)
            assertEquals(savedModel, fakeModelPreferenceDao.getByBaseUrl(baseUrl)?.modelName)

            viewModel.refreshSelectedModel(listOf(ModelInfo(savedModel), ModelInfo("qwen2")))
            assertEquals(savedModel, viewModel.selectedModel.value)
            assertEquals(savedModel, fakeModelPreferenceDao.getByBaseUrl(baseUrl)?.modelName)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) {
        super.starting(description)
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}

private class FakeModelPreferenceDao : ModelPreferenceDao {
    private val storage = mutableMapOf<String, SelectedModel>()

    override suspend fun getByBaseUrl(baseUrl: String): SelectedModel? = storage[baseUrl]

    override suspend fun upsert(model: SelectedModel) {
        storage[model.baseUrl] = model
    }

    override suspend fun deleteByBaseUrl(baseUrl: String) {
        storage.remove(baseUrl)
    }
}

private class FakeChatDao : ChatDao {
    private val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())

    override suspend fun insertChat(chat: Chat) {
        chatsFlow.value = chatsFlow.value + chat
    }

    override fun getAllChats(): Flow<List<Chat>> = chatsFlow

    override suspend fun deleteChat(chat: Chat) {
        chatsFlow.value = chatsFlow.value.filterNot { it.chatId == chat.chatId }
    }
}

private class FakeMessageDao : MessageDao {
    override suspend fun insertMessage(message: Message) = Unit

    override fun getAllMessages(chatId: Int): Flow<List<Message>> = MutableStateFlow(emptyList())

    override suspend fun deleteMessage(message: Message) = Unit
}
