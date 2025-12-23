package com.sonusid.ollama.api

import com.sonusid.ollama.db.entity.BaseUrl
import com.sonusid.ollama.db.repository.BaseUrlProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeBaseUrlProvider(var baseUrls: List<BaseUrl>) : BaseUrlProvider {
    override suspend fun getActiveOrFirst(): BaseUrl? {
        return baseUrls.firstOrNull { it.isActive } ?: baseUrls.firstOrNull()
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
}
