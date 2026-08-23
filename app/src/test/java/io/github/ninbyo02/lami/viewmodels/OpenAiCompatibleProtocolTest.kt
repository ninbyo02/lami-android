package io.github.ninbyo02.lami.viewmodels

import io.github.ninbyo02.lami.ui.screens.settings.LemonadeAutoUnloadMode

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
    fun `Lemonade unload event bridge is opt in and rejects public cleartext URLs`() {
        assertFalse(notifyLemonadeUnloadEvent(modelName = "Gemma-4"))
        assertFalse(
            notifyLemonadeUnloadEvent(
                modelName = "Gemma-4",
                eventUrl = "http://example.com:8650/lemonade/unloaded",
            ),
        )
    }

    @Test
    fun `builds Lemonade unload event payload`() {
        val payload = JSONObject(buildLemonadeUnloadEventJson("Gemma-4"))

        assertEquals("Gemma-4", payload.getString("model_name"))
        assertEquals("lami-android", payload.getString("source"))
    }


}
