package com.sonusid.ollama.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sonusid.ollama.db.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId ")
    fun getAllMessages(chatId: Int): Flow<List<Message>>

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId AND isSendbyMe = 1 AND TRIM(message) != '' ORDER BY messageID ASC LIMIT 1")
    suspend fun getFirstUserMessage(chatId: Int): Message?

    @Query("SELECT * FROM chat_table WHERE chatId = :chatId AND TRIM(message) != '' ORDER BY messageID ASC LIMIT 1")
    suspend fun getFirstNonEmptyMessage(chatId: Int): Message?

    @Delete
    suspend fun deleteMessage(message: Message)
}
