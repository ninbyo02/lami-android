package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.BaseUrlDao
import io.github.ninbyo02.lami.db.entity.BaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface BaseUrlProvider {
    suspend fun getActiveOrFirst(): BaseUrl?

    suspend fun getAll(): List<BaseUrl>

    suspend fun replaceAll(baseUrls: List<BaseUrl>, refreshActive: Boolean = true)
}

class BaseUrlRepository(private val baseUrlDao: BaseUrlDao) : BaseUrlProvider {
    val activeBaseUrl: StateFlow<String> = activeBaseUrlFlow

    override suspend fun getAll(): List<BaseUrl> = baseUrlDao.getAll()

    override suspend fun getActiveOrFirst(): BaseUrl? = baseUrlDao.getActive() ?: baseUrlDao.getAll().firstOrNull()

    override suspend fun replaceAll(baseUrls: List<BaseUrl>, refreshActive: Boolean) {
        baseUrlDao.replaceBaseUrls(baseUrls)
        if (refreshActive) {
            refreshActiveBaseUrl()
        }
    }

    suspend fun setActive(id: Int) {
        baseUrlDao.setActive(id)
        refreshActiveBaseUrl()
    }

    suspend fun refreshActiveBaseUrl() {
        val activeUrl = getActiveOrFirst()?.url.orEmpty()
        updateActiveBaseUrl(activeUrl)
    }

    fun updateActiveBaseUrl(baseUrl: String) {
        activeBaseUrlState.update { baseUrl.trimEnd('/') }
    }

    private companion object {
        val activeBaseUrlState = MutableStateFlow("")
        val activeBaseUrlFlow: StateFlow<String> = activeBaseUrlState.asStateFlow()
    }
}
