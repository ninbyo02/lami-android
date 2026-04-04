package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.entity.Chat
import kotlinx.coroutines.flow.Flow

class UserRepository(private val chatDao: ChatDao) {

    val allUsers: Flow<List<Chat>> = chatDao.getAllChats()

    suspend fun insert(chat: Chat) {
        chatDao.insertChat(chat)
    }

    suspend fun delete(chat: Chat) {
        chatDao.deleteChat(chat)
    }
}