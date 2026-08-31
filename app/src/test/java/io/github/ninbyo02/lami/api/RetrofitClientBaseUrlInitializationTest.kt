package io.github.ninbyo02.lami.api

import io.github.ninbyo02.lami.db.entity.BaseUrl
import io.github.ninbyo02.lami.db.repository.BaseUrlProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RetrofitClientBaseUrlInitializationTest {
    @Test
    fun `empty server list does not create default localhost`() = runTest {
        val provider = FakeBaseUrlProvider(emptyList())

        val state = RetrofitClient.initialize(provider)

        assertEquals("", state.baseUrl)
        assertFalse(state.usedFallback)
        assertNull(state.errorMessage)
        assertEquals(emptyList<BaseUrl>(), provider.baseUrls)
        assertNull(provider.lastReplaceAll)
        assertEquals("", RetrofitClient.currentBaseUrl())
    }

    @Test
    fun `existing active server is preserved`() = runTest {
        val existing = BaseUrl(id = 7, url = "http://server.local:11434/", isActive = true)
        val provider = FakeBaseUrlProvider(listOf(existing))

        val state = RetrofitClient.initialize(provider)

        assertEquals("http://server.local:11434/", state.baseUrl)
        assertFalse(state.usedFallback)
        assertEquals(listOf(existing), provider.baseUrls)
        assertNull(provider.lastReplaceAll)
    }

    @Test
    fun `all invalid saved servers are removed without restoring localhost`() = runTest {
        val provider = FakeBaseUrlProvider(
            listOf(BaseUrl(id = 1, url = "not a url", isActive = true))
        )

        val state = RetrofitClient.initialize(provider)

        assertEquals("", state.baseUrl)
        assertEquals(emptyList<BaseUrl>(), provider.baseUrls)
        assertEquals(emptyList<BaseUrl>(), provider.lastReplaceAll)
        assertEquals("", RetrofitClient.currentBaseUrl())
    }
}

private class FakeBaseUrlProvider(initialBaseUrls: List<BaseUrl>) : BaseUrlProvider {
    var baseUrls: List<BaseUrl> = initialBaseUrls
    var lastReplaceAll: List<BaseUrl>? = null

    override suspend fun getActiveOrFirst(): BaseUrl? =
        baseUrls.firstOrNull { it.isActive } ?: baseUrls.firstOrNull()

    override suspend fun getAll(): List<BaseUrl> = baseUrls

    override suspend fun replaceAll(baseUrls: List<BaseUrl>, refreshActive: Boolean) {
        this.baseUrls = baseUrls
        lastReplaceAll = baseUrls
    }
}
