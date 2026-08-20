package io.github.ninbyo02.lami.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.ninbyo02.lami.db.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageAndReturnId(message: Message): Long

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId ")
    fun getAllMessages(chatId: Int): Flow<List<Message>>

    @Query("SELECT * FROM chat_table WHERE messageID = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: Int): Message?

    @Update
    suspend fun updateMessage(message: Message)

    @Query(
        """
        UPDATE chat_table
        SET status = :newStatus,
            errorCode = :errorCode,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE messageID = :messageId
          AND isSendbyMe = 0
          AND status IN (:expectedStatuses)
        """
    )
    suspend fun transitionAssistantMessageStatus(
        messageId: Int,
        expectedStatuses: List<String>,
        newStatus: String,
        errorCode: String?,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE chat_table
        SET message = :message,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE messageID = :messageId
          AND isSendbyMe = 0
          AND status = :expectedStatus
        """
    )
    suspend fun updateAssistantMessageContentIfStatus(
        messageId: Int,
        expectedStatus: String,
        message: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE chat_table
        SET message = :message,
            status = 'COMPLETED',
            errorCode = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE messageID = :messageId
          AND isSendbyMe = 0
          AND status IN ('PENDING', 'GENERATING')
        """
    )
    suspend fun completeInFlightAssistantMessage(
        messageId: Int,
        message: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE chat_table
        SET message = COALESCE(:message, message),
            status = 'FAILED',
            errorCode = :errorCode,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE messageID = :messageId
          AND isSendbyMe = 0
          AND status IN ('PENDING', 'GENERATING')
        """
    )
    suspend fun failInFlightAssistantMessage(
        messageId: Int,
        message: String?,
        errorCode: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE chat_table
        SET status = 'INTERRUPTED',
            errorCode = 'PROCESS_INTERRUPTED',
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE isSendbyMe = 0
          AND status IN ('PENDING', 'GENERATING')
          AND updatedAtEpochMs < :processStartedAtEpochMs
        """
    )
    suspend fun interruptInFlightAssistantMessagesAfterRestart(
        processStartedAtEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Int


    @Query("SELECT COUNT(*) FROM chat_table WHERE chatId = :chatId")
    suspend fun countMessages(chatId: Int): Int

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId AND isSendbyMe = 1 AND TRIM(message) != '' ORDER BY messageID ASC LIMIT 1")
    suspend fun getFirstUserMessage(chatId: Int): Message?

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId AND TRIM(message) != '' ORDER BY messageID ASC LIMIT 1")
    suspend fun getFirstNonEmptyMessage(chatId: Int): Message?

    @Query(
        """
        SELECT m.chatId AS chatId, m.message AS message
        FROM chat_table AS m
        INNER JOIN (
            SELECT chatId, MAX(messageID) AS latestMessageId
            FROM chat_table
            WHERE chatId IN (:chatIds)
            GROUP BY chatId
        ) AS latest
        ON m.chatId = latest.chatId AND m.messageID = latest.latestMessageId
        """
    )
    suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage>

    @Delete
    suspend fun deleteMessage(message: Message)
}

data class ChatLatestMessage(
    val chatId: Int,
    val message: String,
)
