package io.github.ninbyo02.lami.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.ninbyo02.lami.db.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId ")
    fun getAllMessages(chatId: Int): Flow<List<Message>>


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
