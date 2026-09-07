package io.github.ninbyo02.lami.viewmodels

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProviderDiscoveryTest {
    @Test
    fun `falls back to Ollama when the saved Lemonade provider does not match the server`() {
        val attempted = mutableListOf<RemoteProvider>()

        val result = discoverRemoteModels(RemoteProvider.LEMONADE) { provider ->
            attempted += provider
            if (provider == RemoteProvider.OLLAMA) {
                listOf(ModelInfo("qwen3.8:27b"))
            } else {
                throw IOException("Failed to load models (HTTP 404): ignored response")
            }
        }

        assertEquals(RemoteProvider.OLLAMA, result.provider)
        assertEquals(listOf(ModelInfo("qwen3.8:27b")), result.models)
        assertEquals(
            listOf(RemoteProvider.LEMONADE, RemoteProvider.OLLAMA),
            attempted,
        )
    }

    @Test
    fun `keeps the configured provider when its model endpoint succeeds`() {
        val result = discoverRemoteModels(RemoteProvider.LEMONADE) { provider ->
            assertEquals(RemoteProvider.LEMONADE, provider)
            listOf(ModelInfo("Gemma-4-26B-A4B-it-GGUF"))
        }

        assertEquals(RemoteProvider.LEMONADE, result.provider)
    }

    @Test
    fun `failure summary exposes status codes but not response bodies`() {
        val failure = runCatching {
            discoverRemoteModels(RemoteProvider.OLLAMA) {
                throw IOException("Request failed (HTTP 503): private server response")
            }
        }.exceptionOrNull()

        val message = requireNotNull(failure).message.orEmpty()
        assertTrue(message.contains("HTTP 503"))
        assertFalse(message.contains("private server response"))
    }
}
