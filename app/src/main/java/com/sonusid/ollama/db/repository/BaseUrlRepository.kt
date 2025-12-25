package com.sonusid.ollama.db.repository

import com.sonusid.ollama.db.dao.BaseUrlDao
import com.sonusid.ollama.db.entity.BaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface BaseUrlProvider {
    suspend fun getActiveOrFirst(): BaseUrl?

    suspend fun getAll(): List<BaseUrl>

    suspend fun replaceAll(baseUrls: List<BaseUrl>)
}

class BaseUrlRepository(private val baseUrlDao: BaseUrlDao) : BaseUrlProvider {
    private val _activeBaseUrl = MutableStateFlow(DEFAULT_BASE_URL.trimEnd('/'))
    val activeBaseUrl: StateFlow<String> = _activeBaseUrl.asStateFlow()

    override suspend fun getAll(): List<BaseUrl> = baseUrlDao.getAll()

    override suspend fun getActiveOrFirst(): BaseUrl? = baseUrlDao.getActive() ?: baseUrlDao.getAll().firstOrNull()

    override suspend fun replaceAll(baseUrls: List<BaseUrl>) {
        baseUrlDao.replaceBaseUrls(baseUrls)
    }

    suspend fun setActive(id: Int) {
        baseUrlDao.setActive(id)
    }

    suspend fun refreshActiveBaseUrl() {
        val activeUrl = getActiveOrFirst()?.url ?: DEFAULT_BASE_URL
        updateActiveBaseUrl(activeUrl)
    }

    fun updateActiveBaseUrl(baseUrl: String) {
        _activeBaseUrl.update { baseUrl.trimEnd('/') }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434/"
    }
}
