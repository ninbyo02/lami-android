package com.sonusid.ollama.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sonusid.ollama.db.entity.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

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

    @Delete
    suspend fun deleteChat(chat: Chat)
}
