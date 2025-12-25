package com.sonusid.ollama.db.repository

import com.sonusid.ollama.db.dao.BaseUrlDao
import com.sonusid.ollama.db.entity.BaseUrl

interface BaseUrlProvider {
    suspend fun getActiveOrFirst(): BaseUrl?

    suspend fun getAll(): List<BaseUrl>

    suspend fun replaceAll(baseUrls: List<BaseUrl>)
}

class BaseUrlRepository(private val baseUrlDao: BaseUrlDao) : BaseUrlProvider {
    override suspend fun getAll(): List<BaseUrl> = baseUrlDao.getAll()

    override suspend fun getActiveOrFirst(): BaseUrl? = baseUrlDao.getActive() ?: baseUrlDao.getAll().firstOrNull()

    override suspend fun replaceAll(baseUrls: List<BaseUrl>) {
        baseUrlDao.replaceBaseUrls(baseUrls)
    }

    suspend fun setActive(id: Int) {
        baseUrlDao.setActive(id)
    }
}
