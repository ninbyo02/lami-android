package io.github.ninbyo02.lami.viewmodels

import io.github.ninbyo02.lami.ui.screens.settings.LemonadeAutoUnloadMode
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OpenAiCompatibleProtocolTest {
    @Test
    fun `parses OpenAI compatible models response ids`() {
        val json = """
            {
              "object": "list",
              "data": [
                {"id": "Qwen3-0.6B-GGUF", "object": "model"},
                {"id": "Gemma-4-E2B-it-GGUF", "object": "model"}
              ]
            }
        """.trimIndent()

        val models = parseOpenAiCompatibleModels(json)

        assertEquals(
            listOf(ModelInfo("Qwen3-0.6B-GGUF"), ModelInfo("Gemma-4-E2B-it-GGUF")),
            models,
        )
    }

    @Test
    fun `parses OpenAI compatible streaming content delta`() {
        val chunk = requireNotNull(parseOpenAiCompatibleStreamingLine(
            "data: {\"choices\":[{\"delta\":{\"content\":\"こんにちは\"},\"finish_reason\":null}]}"
        ))

        assertEquals("こんにちは", chunk.text)
        assertFalse(chunk.done)
        assertNull(chunk.finishReason)
    }

    @Test
    fun `parses Lemonade reasoning content separately from assistant content`() {
        val chunk = requireNotNull(parseOpenAiCompatibleStreamingLine(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"考え中\",\"content\":\"答え\"},\"finish_reason\":null}]}"
        ))

        assertEquals("答え", chunk.text)
        assertEquals("考え中", chunk.reasoningText)
        assertFalse(chunk.done)
    }

    @Test
    fun `parses OpenAI compatible done sentinel`() {
        val chunk = requireNotNull(parseOpenAiCompatibleStreamingLine("data: [DONE]"))

        assertTrue(chunk.done)
        assertNull(chunk.text)
    }

    @Test
    fun `resolves Lemonade provider as OpenAI compatible preset`() {
        val config = RemoteProvider.LEMONADE.toOpenAiCompatibleConfig("http://192.168.52.99:13305")

        assertEquals("http://192.168.52.99:13305/api/v1/", config.baseUrl)
        assertEquals("lemonade", config.defaultApiKey)
    }

    @Test
    fun `Lemonade auto unload modes expose Ollama-like idle delays`() {
        assertEquals(LemonadeAutoUnloadMode.OFF, LemonadeAutoUnloadMode.fromStorage(null))
        assertEquals(LemonadeAutoUnloadMode.AFTER_15_MIN, LemonadeAutoUnloadMode.fromStorage("after_15_min"))
        assertEquals(15 * 60 * 1000L, LemonadeAutoUnloadMode.AFTER_15_MIN.delayMs)
        assertNull(LemonadeAutoUnloadMode.OFF.delayMs)
    }
    @Test
    fun `builds Lemonade unload event payload`() {
        val payload = JSONObject(buildLemonadeUnloadEventJson("Gemma-4"))

        assertEquals("Gemma-4", payload.getString("model_name"))
        assertEquals("lami-android", payload.getString("source"))
    }

    @Test
    fun `unload Lemonade notifies bridge after successful unload`() {
        val unloadBody = AtomicReference<String>()
        val eventBody = AtomicReference<String>()
        val unloadServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val eventServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        unloadServer.createContext("/api/v1/unload") { exchange ->
            unloadBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = "{}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        eventServer.createContext("/lemonade/unloaded") { exchange ->
            eventBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = "{\"status\":\"ok\"}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        unloadServer.start()
        eventServer.start()
        try {
            val unloadBaseUrl = "http://127.0.0.1:${unloadServer.address.port}"
            val eventUrl = "http://127.0.0.1:${eventServer.address.port}/lemonade/unloaded"

            assertTrue(unloadLemonadeModelFromServer(unloadBaseUrl, "Gemma-4", eventUrl))

            assertEquals("Gemma-4", JSONObject(unloadBody.get()).getString("model_name"))
            val eventJson = JSONObject(eventBody.get())
            assertEquals("Gemma-4", eventJson.getString("model_name"))
            assertEquals("lami-android", eventJson.getString("source"))
        } finally {
            unloadServer.stop(0)
            eventServer.stop(0)
        }
    }

}
