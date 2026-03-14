package com.sonusid.ollama.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class OllamaModelContextWindowParserTest {
    @Test
    fun `extractEffectiveContextWindowFromShowResponse prioritizes options num_ctx`() {
        val response = """
            {
              "model": "qwen2.5",
              "options": { "num_ctx": 4096 },
              "parameters": "num_ctx 8192"
            }
        """.trimIndent()

        val actual = extractEffectiveContextWindowFromShowResponse(response)

        assertEquals(4096, actual)
    }

    @Test
    fun `extractEffectiveContextWindowFromShowResponse falls back to parameters num_ctx`() {
        val response = """
            {
              "model": "qwen2.5",
              "parameters": "temperature 0.7\nnum_ctx 12288\nnum_predict -1"
            }
        """.trimIndent()

        val actual = extractEffectiveContextWindowFromShowResponse(response)

        assertEquals(12288, actual)
    }

    @Test
    fun `extractEffectiveContextWindowFromShowResponse falls back to model_info context_length`() {
        val response = """
            {
              "model_info": {
                "llama.context_length": 32768
              }
            }
        """.trimIndent()

        val actual = extractEffectiveContextWindowFromShowResponse(response)

        assertEquals(32768, actual)
    }

    @Test
    fun `extractEffectiveContextWindowFromShowResponse returns null when unavailable`() {
        val response = """
            {
              "model": "qwen2.5",
              "details": { "family": "llama" }
            }
        """.trimIndent()

        val actual = extractEffectiveContextWindowFromShowResponse(response)

        assertEquals(null, actual)
    }
}
