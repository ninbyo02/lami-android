package com.sonusid.ollama.api

import com.sonusid.ollama.db.entity.BaseUrl
import com.sonusid.ollama.db.repository.BaseUrlProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeBaseUrlProvider(var baseUrls: List<BaseUrl>) : BaseUrlProvider {
    override suspend fun getActiveOrFirst(): BaseUrl? {
        return baseUrls.firstOrNull { it.isActive } ?: baseUrls.firstOrNull()
    }

    override suspend fun getAll(): List<BaseUrl> = baseUrls

    override suspend fun replaceAll(baseUrls: List<BaseUrl>) {
        this.baseUrls = baseUrls
    }
}

class RetrofitClientTest {
    @Test
    fun `initialize and refresh picks active base url`() = runBlocking {
        val provider = FakeBaseUrlProvider(
            listOf(
                BaseUrl(id = 1, url = "server-one:11434", isActive = true),
                BaseUrl(id = 2, url = "server-two:11434", isActive = false)
            )
        )

        RetrofitClient.initialize(provider)
        assertEquals("http://server-one:11434/", RetrofitClient.currentBaseUrl())

        provider.baseUrls = listOf(
            BaseUrl(id = 1, url = "server-one:11434", isActive = false),
            BaseUrl(id = 2, url = "server-two:11434", isActive = true)
        )

        RetrofitClient.refreshBaseUrl(provider)
        assertEquals("http://server-two:11434/", RetrofitClient.currentBaseUrl())
    }

    @Test
    fun `initialize normalizes base url without scheme`() = runBlocking {
        val provider = FakeBaseUrlProvider(
            listOf(
                BaseUrl(id = 1, url = "example.com:1234", isActive = true)
            )
        )

        RetrofitClient.initialize(provider)
        assertEquals("http://example.com:1234/", RetrofitClient.currentBaseUrl())
    }

    @Test
    fun `initialize falls back to default when stored url is invalid`() = runBlocking {
        val provider = FakeBaseUrlProvider(
            listOf(
                BaseUrl(id = 1, url = "not a url", isActive = false)
            )
        )

        val state = RetrofitClient.initialize(provider)

        assertEquals("http://localhost:11434/", RetrofitClient.currentBaseUrl())
        assertTrue(state.usedFallback)
        assertEquals("http://localhost:11434/", state.baseUrl)
        assertEquals(1, provider.baseUrls.size)
        val savedUrl = provider.baseUrls.first()
        assertEquals("http://localhost:11434/", savedUrl.url)
        assertTrue(savedUrl.isActive)
    }

    @Test
    fun `initialize normalizes full width port and colon`() = runBlocking {
        val provider = FakeBaseUrlProvider(
            listOf(
                BaseUrl(id = 1, url = "http://localhost：１１４３４", isActive = true),
                BaseUrl(id = 2, url = "https：／／example．com：９９９９", isActive = false)
            )
        )

        val state = RetrofitClient.initialize(provider)

        assertEquals("http://localhost:11434/", RetrofitClient.currentBaseUrl())
        assertEquals("http://localhost:11434/", state.baseUrl)
        assertTrue(provider.baseUrls.first { it.id == 1 }.isActive)
        assertEquals("http://localhost:11434/", provider.baseUrls.first { it.id == 1 }.url)
        assertEquals("https://example.com:9999/", provider.baseUrls.first { it.id == 2 }.url)
    }
}
