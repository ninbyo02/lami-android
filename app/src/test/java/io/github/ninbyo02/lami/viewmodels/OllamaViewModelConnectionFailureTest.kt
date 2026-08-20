package io.github.ninbyo02.lami.viewmodels

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `server model load exception becomes error state and keeps selected model`() = runTest(dispatcher) {
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

        viewModel.loadAvailableModels()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState is UiState.Error)
        assertTrue((uiState as UiState.Error).errorMessage.contains("server disconnected"))
        assertEquals("saved-server-model", viewModel.selectedModel.value)
        assertEquals("saved-server-model", modelPreferenceDao.selected["http://localhost:13511"]?.modelName)
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
