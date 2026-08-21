package io.github.ninbyo02.lami.db.repository

import androidx.room.Room
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ChatRepositoryMessageLifecycleTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ChatDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database.messageDao(), database.chatDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `cancel preserves partial text and rejects stale completion`() = runTest {
        val id = repository.insertAssistantMessageAndAutoTitleAndReturnId(
            Message(
                chatId = 1,
                message = "",
                isSendbyMe = false,
                status = MessageStatus.PENDING,
                createdAtEpochMs = 100L,
                updatedAtEpochMs = 100L,
            ),
        ).toInt()

        assertTrue(repository.markAssistantMessageGenerating(id, updatedAtEpochMs = 200L))
        assertTrue(
            repository.updateGeneratingAssistantMessageContent(
                messageId = id,
                message = "partial answer",
                updatedAtEpochMs = 300L,
            ),
        )
        assertTrue(repository.cancelAssistantMessage(id, updatedAtEpochMs = 400L))
        assertFalse(
            repository.completeAssistantMessage(
                messageId = id,
                message = "stale final answer",
                updatedAtEpochMs = 500L,
            ),
        )

        val saved = repository.getMessageById(id)!!
        assertEquals("partial answer", saved.message)
        assertEquals(MessageStatus.CANCELLED, saved.status)
        assertEquals(MessageErrorCode.USER_CANCELLED, saved.errorCode)
        assertEquals(400L, saved.updatedAtEpochMs)
    }

    @Test
    fun `failure uses a stable default error code and stays terminal`() = runTest {
        val id = repository.insertAssistantMessageAndAutoTitleAndReturnId(
            Message(
                chatId = 1,
                message = "partial",
                isSendbyMe = false,
                status = MessageStatus.GENERATING,
            ),
        ).toInt()

        assertTrue(
            repository.failAssistantMessage(
                messageId = id,
                message = "Generation failed",
                errorCode = "",
                updatedAtEpochMs = 600L,
            ),
        )
        assertFalse(repository.cancelAssistantMessage(id, updatedAtEpochMs = 700L))

        val saved = repository.getMessageById(id)!!
        assertEquals("Generation failed", saved.message)
        assertEquals(MessageStatus.FAILED, saved.status)
        assertEquals(MessageErrorCode.GENERATION_FAILED, saved.errorCode)
        assertEquals(600L, saved.updatedAtEpochMs)
    }

    @Test
    fun `cancel wins against delayed failure and completion`() = runTest {
        val id = repository.insertAssistantMessageAndAutoTitleAndReturnId(
            Message(
                chatId = 1,
                message = "partial answer",
                isSendbyMe = false,
                status = MessageStatus.GENERATING,
                createdAtEpochMs = 100L,
                updatedAtEpochMs = 200L,
            ),
        ).toInt()

        assertTrue(repository.cancelAssistantMessage(id, updatedAtEpochMs = 300L))
        assertFalse(
            repository.failAssistantMessage(
                messageId = id,
                message = "late server error",
                updatedAtEpochMs = 400L,
            ),
        )
        assertFalse(
            repository.completeAssistantMessage(
                messageId = id,
                message = "late final answer",
                updatedAtEpochMs = 500L,
            ),
        )

        val saved = repository.getMessageById(id)!!
        assertEquals("partial answer", saved.message)
        assertEquals(MessageStatus.CANCELLED, saved.status)
        assertEquals(MessageErrorCode.USER_CANCELLED, saved.errorCode)
        assertEquals(300L, saved.updatedAtEpochMs)
    }

    @Test
    fun `restart recovery respects process start cutoff`() = runTest {
        val oldId = repository.insertAssistantMessageAndAutoTitleAndReturnId(
            Message(
                chatId = 1,
                message = "old partial",
                isSendbyMe = false,
                status = MessageStatus.GENERATING,
                updatedAtEpochMs = 100L,
            ),
        ).toInt()
        val newId = repository.insertAssistantMessageAndAutoTitleAndReturnId(
            Message(
                chatId = 1,
                message = "new partial",
                isSendbyMe = false,
                status = MessageStatus.GENERATING,
                updatedAtEpochMs = 300L,
            ),
        ).toInt()

        assertEquals(
            1,
            repository.interruptInFlightAssistantMessagesAfterRestart(
                processStartedAtEpochMs = 200L,
                updatedAtEpochMs = 400L,
            ),
        )
        assertEquals(MessageStatus.INTERRUPTED, repository.getMessageById(oldId)!!.status)
        assertEquals(MessageStatus.GENERATING, repository.getMessageById(newId)!!.status)
    }
}
