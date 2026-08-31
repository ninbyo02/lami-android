package io.github.ninbyo02.lami.db.dao

import androidx.room.Room
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MessageLifecycleDaoTest {
    private lateinit var database: ChatDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ChatDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `completion wins once and terminal state rejects stale cancellation`() = runTest {
        val id = dao.insertMessageAndReturnId(
            assistantMessage(status = MessageStatus.PENDING),
        ).toInt()

        assertEquals(
            1,
            dao.transitionAssistantMessageStatus(
                messageId = id,
                expectedStatuses = listOf(MessageStatus.PENDING),
                newStatus = MessageStatus.GENERATING,
                errorCode = null,
                updatedAtEpochMs = 200L,
            ),
        )
        assertEquals(
            1,
            dao.updateAssistantMessageContentIfStatus(
                messageId = id,
                expectedStatus = MessageStatus.GENERATING,
                message = "partial",
                updatedAtEpochMs = 300L,
            ),
        )
        assertEquals(
            1,
            dao.completeInFlightAssistantMessage(
                messageId = id,
                message = "final",
                updatedAtEpochMs = 400L,
            ),
        )
        assertEquals(
            0,
            dao.transitionAssistantMessageStatus(
                messageId = id,
                expectedStatuses = MessageStatus.IN_FLIGHT.toList(),
                newStatus = MessageStatus.CANCELLED,
                errorCode = MessageErrorCode.USER_CANCELLED,
                updatedAtEpochMs = 500L,
            ),
        )
        assertEquals(
            0,
            dao.updateAssistantMessageContentIfStatus(
                messageId = id,
                expectedStatus = MessageStatus.GENERATING,
                message = "stale",
                updatedAtEpochMs = 600L,
            ),
        )

        val saved = dao.getMessageById(id)!!
        assertEquals("final", saved.message)
        assertEquals(MessageStatus.COMPLETED, saved.status)
        assertNull(saved.errorCode)
        assertEquals(400L, saved.updatedAtEpochMs)
    }

    @Test
    fun `direct completion is allowed only for in flight assistant rows`() = runTest {
        val pendingId = dao.insertMessageAndReturnId(
            assistantMessage(status = MessageStatus.PENDING),
        ).toInt()
        val completedId = dao.insertMessageAndReturnId(
            assistantMessage(status = MessageStatus.COMPLETED),
        ).toInt()
        val userId = dao.insertMessageAndReturnId(
            Message(
                chatId = 1,
                message = "user",
                isSendbyMe = true,
                status = MessageStatus.PENDING,
            ),
        ).toInt()

        assertEquals(
            1,
            dao.completeInFlightAssistantMessage(
                messageId = pendingId,
                message = "direct final",
                updatedAtEpochMs = 150L,
            ),
        )
        assertEquals(
            0,
            dao.transitionAssistantMessageStatus(
                messageId = completedId,
                expectedStatuses = listOf(MessageStatus.PENDING),
                newStatus = MessageStatus.GENERATING,
                errorCode = null,
                updatedAtEpochMs = 160L,
            ),
        )
        assertEquals(
            0,
            dao.transitionAssistantMessageStatus(
                messageId = userId,
                expectedStatuses = listOf(MessageStatus.PENDING),
                newStatus = MessageStatus.GENERATING,
                errorCode = null,
                updatedAtEpochMs = 170L,
            ),
        )
        assertEquals(MessageStatus.COMPLETED, dao.getMessageById(pendingId)!!.status)
        assertEquals(MessageStatus.COMPLETED, dao.getMessageById(completedId)!!.status)
        assertEquals(MessageStatus.PENDING, dao.getMessageById(userId)!!.status)
    }

    @Test
    fun `message defaults to completed with matching timestamps`() {
        val message = Message(
            chatId = 1,
            message = "legacy-compatible",
            isSendbyMe = false,
            createdAtEpochMs = 123L,
        )

        assertEquals(MessageStatus.COMPLETED, message.status)
        assertNull(message.errorCode)
        assertEquals(123L, message.updatedAtEpochMs)
    }

    @Test
    fun `recovery interrupts only in flight assistant messages`() = runTest {
        val pendingId = dao.insertMessageAndReturnId(
            assistantMessage(
                status = MessageStatus.PENDING,
                updatedAtEpochMs = 100L,
            ),
        ).toInt()
        val generatingId = dao.insertMessageAndReturnId(
            assistantMessage(
                status = MessageStatus.GENERATING,
                updatedAtEpochMs = 200L,
            ),
        ).toInt()
        val completedId = dao.insertMessageAndReturnId(
            assistantMessage(status = MessageStatus.COMPLETED),
        ).toInt()
        val newGeneratingId = dao.insertMessageAndReturnId(
            assistantMessage(
                status = MessageStatus.GENERATING,
                updatedAtEpochMs = 650L,
            ),
        ).toInt()
        val userId = dao.insertMessageAndReturnId(
            Message(
                chatId = 1,
                message = "user",
                isSendbyMe = true,
                status = MessageStatus.GENERATING,
            ),
        ).toInt()

        assertEquals(
            2,
            dao.interruptInFlightAssistantMessagesAfterRestart(
                processStartedAtEpochMs = 600L,
                updatedAtEpochMs = 700L,
            ),
        )

        listOf(pendingId, generatingId).forEach { id ->
            val saved = dao.getMessageById(id)!!
            assertEquals(MessageStatus.INTERRUPTED, saved.status)
            assertEquals(MessageErrorCode.PROCESS_INTERRUPTED, saved.errorCode)
            assertEquals(700L, saved.updatedAtEpochMs)
        }
        assertEquals(MessageStatus.COMPLETED, dao.getMessageById(completedId)!!.status)
        assertEquals(MessageStatus.GENERATING, dao.getMessageById(newGeneratingId)!!.status)
        assertEquals(MessageStatus.GENERATING, dao.getMessageById(userId)!!.status)
    }

    @Test
    fun `failure writes display text atomically and rejects stale completion`() = runTest {
        val id = dao.insertMessageAndReturnId(
            assistantMessage(
                status = MessageStatus.PENDING,
                message = "",
            ),
        ).toInt()

        assertEquals(
            1,
            dao.failInFlightAssistantMessage(
                messageId = id,
                message = "Generation failed",
                errorCode = MessageErrorCode.GENERATION_FAILED,
                updatedAtEpochMs = 800L,
            ),
        )
        assertEquals(
            0,
            dao.completeInFlightAssistantMessage(
                messageId = id,
                message = "stale final",
                updatedAtEpochMs = 900L,
            ),
        )

        val saved = dao.getMessageById(id)!!
        assertEquals("Generation failed", saved.message)
        assertEquals(MessageStatus.FAILED, saved.status)
        assertEquals(MessageErrorCode.GENERATION_FAILED, saved.errorCode)
        assertEquals(800L, saved.updatedAtEpochMs)
    }

    private fun assistantMessage(
        status: String,
        message: String = "assistant",
        updatedAtEpochMs: Long = 100L,
    ): Message {
        return Message(
            chatId = 1,
            message = message,
            isSendbyMe = false,
            status = status,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }
}
