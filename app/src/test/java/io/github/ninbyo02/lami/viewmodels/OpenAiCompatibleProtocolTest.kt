package io.github.ninbyo02.lami.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
