package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.BaseUrlDao
import io.github.ninbyo02.lami.db.entity.BaseUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaseUrlRepositoryEmptyListTest {
    @Test
    fun `refresh active URL with empty list keeps active URL empty`() = runTest {
        val repository = BaseUrlRepository(FakeBaseUrlDao(emptyList()))

        repository.refreshActiveBaseUrl()

        assertEquals("", repository.activeBaseUrl.value)
        assertNull(repository.getActiveOrFirst())
    }

    @Test
    fun `last server deletion leaves list empty and does not restore localhost`() = runTest {
        val dao = FakeBaseUrlDao(
            listOf(BaseUrl(id = 1, url = "http://localhost:13511/", isActive = true))
        )
        val repository = BaseUrlRepository(dao)

        repository.replaceAll(emptyList())

        assertEquals(emptyList<BaseUrl>(), repository.getAll())
        assertEquals("", repository.activeBaseUrl.value)
    }

    @Test
    fun `existing saved servers are kept when replacing with explicit list`() = runTest {
        val saved = BaseUrl(id = 1, url = "http://server.local:11434/", isActive = true)
        val repository = BaseUrlRepository(FakeBaseUrlDao(emptyList()))

        repository.replaceAll(listOf(saved))

        assertEquals(listOf(saved.copy(id = 1)), repository.getAll())
        assertEquals("http://server.local:11434", repository.activeBaseUrl.value)
    }
}

private class FakeBaseUrlDao(initialBaseUrls: List<BaseUrl>) : BaseUrlDao {
    private var nextId = 1
    private val rows = mutableListOf<BaseUrl>()

    init {
        rows += initialBaseUrls
        nextId = (initialBaseUrls.maxOfOrNull { it.id } ?: 0) + 1
    }

    override suspend fun getAll(): List<BaseUrl> = rows.toList()

    override suspend fun getActive(): BaseUrl? = rows.firstOrNull { it.isActive }

    override suspend fun insert(baseUrl: BaseUrl): Long {
        val id = if (baseUrl.id > 0) baseUrl.id else nextId++
        rows += baseUrl.copy(id = id)
        return id.toLong()
    }

    override suspend fun insertAll(baseUrls: List<BaseUrl>): List<Long> =
        baseUrls.map { insert(it) }

    override suspend fun update(baseUrl: BaseUrl) {
        val index = rows.indexOfFirst { it.id == baseUrl.id }
        if (index >= 0) rows[index] = baseUrl
    }

    override suspend fun delete(baseUrl: BaseUrl) {
        rows.removeAll { it.id == baseUrl.id }
    }

    override suspend fun deleteById(id: Int) {
        rows.removeAll { it.id == id }
    }

    override suspend fun clear() {
        rows.clear()
    }

    override suspend fun clearActive() {
        rows.replaceAll { it.copy(isActive = false) }
    }

    override suspend fun activateById(id: Int) {
        rows.replaceAll { it.copy(isActive = it.id == id) }
    }
}
