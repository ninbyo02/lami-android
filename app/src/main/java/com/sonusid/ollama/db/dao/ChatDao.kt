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

    @Query("UPDATE user_table SET title = :title, titleSource = :titleSource WHERE chatId = :chatId")
    suspend fun updateChatTitle(chatId: Int, title: String, titleSource: String)

    @Delete
    suspend fun deleteChat(chat: Chat)
}
