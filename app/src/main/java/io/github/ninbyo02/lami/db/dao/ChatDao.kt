package io.github.ninbyo02.lami.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.ninbyo02.lami.db.entity.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat): Long

    @Query("SELECT * FROM user_table")
    fun getAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM user_table WHERE chatId = :chatId LIMIT 1")
    suspend fun getChatById(chatId: Int): Chat?

    @Query(
        """
        UPDATE user_table
        SET title = :title, titleSource = :newSource
        WHERE chatId = :chatId
          AND titleSource = :expectedSource
          AND (TRIM(title) = '' OR LOWER(TRIM(title)) IN ('new chat', 'newchat'))
        """
    )
    suspend fun updateChatTitle(chatId: Int, title: String, newSource: String, expectedSource: String): Int

    @Query(
        """
        DELETE FROM user_table
        WHERE chatId = :chatId
          AND titleSource = :expectedSource
          AND (TRIM(title) = '' OR LOWER(TRIM(title)) IN ('new chat', 'newchat'))
          AND NOT EXISTS (
              SELECT 1
              FROM chat_table
              WHERE chat_table.chatId = :chatId
          )
        """
    )
    suspend fun deleteChatIfStillEmptyTempPlaceholder(chatId: Int, expectedSource: String): Int

    @Query(
        """
        DELETE FROM user_table
        WHERE titleSource = :expectedSource
          AND (TRIM(title) = '' OR LOWER(TRIM(title)) IN ('new chat', 'newchat'))
          AND NOT EXISTS (
              SELECT 1
              FROM chat_table
              WHERE chat_table.chatId = user_table.chatId
          )
        """
    )
    suspend fun deleteEmptyTempPlaceholderChats(expectedSource: String): Int

    @Delete
    suspend fun deleteChat(chat: Chat)
}
