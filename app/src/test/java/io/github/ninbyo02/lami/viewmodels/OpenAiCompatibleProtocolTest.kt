package io.github.ninbyo02.lami.viewmodels

import com.google.gson.Gson
import io.github.ninbyo02.lami.api.OllamaApiService
import io.github.ninbyo02.lami.api.OllamaChatMessage
import io.github.ninbyo02.lami.api.OllamaOptions
import io.github.ninbyo02.lami.api.OllamaRequest
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
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
import retrofit2.http.POST

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
    fun `only Ollama and Lemonade expose Ollama model details`() {
        assertTrue(RemoteProvider.OLLAMA.supportsOllamaModelDetails())
        assertTrue(RemoteProvider.LEMONADE.supportsOllamaModelDetails())
        assertFalse(RemoteProvider.OPENAI_COMPATIBLE.supportsOllamaModelDetails())
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

    @Test
    fun `Ollama streaming uses chat endpoint`() {
        val method = OllamaApiService::class.java.getMethod(
            "generateTextStream",
            OllamaRequest::class.java,
        )

        assertEquals("api/chat", method.getAnnotation(POST::class.java)?.value)
    }

    @Test
    fun `Ollama request sends structured user message and no app template`() {
        val request = OllamaRequest(
            model = "qwen3.8:27b",
            messages = listOf(
                OllamaChatMessage(
                    role = "user",
                    content = "画像を説明して",
                    images = listOf("base64-image"),
                ),
            ),
            stream = true,
            options = OllamaOptions(numPredict = 8_192),
        )

        val json = JSONObject(Gson().toJson(request))
        val message = json.getJSONArray("messages").getJSONObject(0)

        assertEquals("qwen3.8:27b", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        assertEquals(8_192, json.getJSONObject("options").getInt("num_predict"))
        assertEquals("user", message.getString("role"))
        assertEquals("画像を説明して", message.getString("content"))
        assertEquals("base64-image", message.getJSONArray("images").getString(0))
        listOf("prompt", "template", "chat_template", "raw").forEach { forbidden ->
            assertFalse("Ollama request must not contain app-owned field: $forbidden", json.has(forbidden))
        }
    }

    @Test
    fun `Ollama chat response maps assistant message content`() {
        val response = Gson().fromJson(
            """{"message":{"role":"assistant","content":"こんにちは。"}}""",
            io.github.ninbyo02.lami.api.OllamaResponse::class.java,
        )

        assertEquals("assistant", response.message.role)
        assertEquals("こんにちは。", response.message.content)
        assertNull(response.message.images)
    }

    @Test
    fun `Ollama chat stream extracts message content`() {
        val chunk = JSONObject(
            """{"model":"qwen3.8:27b","message":{"role":"assistant","content":"了解"},"done":false}""",
        )

        assertEquals("了解", parseOllamaChatAssistantContent(chunk))
    }

    @Test
    fun `remote chat history keeps users and completed assistants only`() {
        val messages = buildRemoteChatMessages(
            history = listOf(
                Message(chatId = 7, message = "最初の質問", isSendbyMe = true),
                Message(chatId = 7, message = "最初の回答", isSendbyMe = false),
                Message(
                    chatId = 7,
                    message = "失敗した途中回答",
                    isSendbyMe = false,
                    status = MessageStatus.FAILED,
                ),
                Message(
                    chatId = 7,
                    message = "生成途中",
                    isSendbyMe = false,
                    status = MessageStatus.GENERATING,
                ),
                Message(chatId = 7, message = "   ", isSendbyMe = true),
            ),
            currentContent = "続けて説明して",
            currentImages = listOf("current-image"),
            inputTokenBudget = 6_144,
        )

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.role })
        assertEquals(listOf("最初の質問", "最初の回答", "続けて説明して"), messages.map { it.content })
        assertNull(messages[0].images)
        assertNull(messages[1].images)
        assertEquals(listOf("current-image"), messages[2].images)
    }

    @Test
    fun `remote chat history limit keeps newest history and always appends current user`() {
        val messages = buildRemoteChatMessages(
            history = listOf(
                Message(chatId = 9, message = "古い質問", isSendbyMe = true),
                Message(chatId = 9, message = "新しい質問", isSendbyMe = true),
                Message(chatId = 9, message = "新しい回答", isSendbyMe = false),
            ),
            currentContent = "現在の質問",
            inputTokenBudget = 6_144,
            historyLimit = 2,
        )

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.role })
        assertEquals(listOf("新しい質問", "新しい回答", "現在の質問"), messages.map { it.content })
    }

    @Test
    fun `remote chat estimates conservatively and reserves output context`() {
        assertEquals(2, estimateRemoteChatContentTokens("abcd"))
        assertEquals(2, estimateRemoteChatContentTokens("あ"))
    }

    @Test
    fun `remote output budget keeps requested 8192 when context has room`() {
        val budget = resolveRemoteChatTokenBudget(
            contextWindow = 32_768,
            currentContent = "abcd",
        )

        assertEquals(32_768, budget.contextWindow)
        assertEquals(8_192, budget.requestedOutputTokens)
        assertEquals(8_192, budget.effectiveOutputTokens)
        assertEquals(24_512, budget.inputTokenBudget)
        assertEquals(22, budget.estimatedCurrentInputTokens)
    }

    @Test
    fun `remote output budget clamps to context after current input and safety reserve`() {
        val budget = resolveRemoteChatTokenBudget(
            contextWindow = 4_096,
            currentContent = "abcd",
        )

        assertEquals(8_192, budget.requestedOutputTokens)
        assertEquals(4_010, budget.effectiveOutputTokens)
        assertEquals(22, budget.inputTokenBudget)
        assertEquals(4_032, budget.inputTokenBudget + budget.effectiveOutputTokens)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `remote output budget rejects a current input that cannot fit context`() {
        resolveRemoteChatTokenBudget(
            contextWindow = 80,
            currentContent = "x".repeat(100),
        )
    }

    @Test
    fun `remote chat token budget keeps newest complete turn`() {
        val messages = buildRemoteChatMessages(
            history = listOf(
                Message(chatId = 10, message = "a".repeat(20), isSendbyMe = true),
                Message(chatId = 10, message = "b".repeat(20), isSendbyMe = false),
                Message(chatId = 10, message = "c".repeat(20), isSendbyMe = true),
                Message(chatId = 10, message = "d".repeat(20), isSendbyMe = false),
            ),
            currentContent = "now",
            inputTokenBudget = 72,
        )

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.role })
        assertEquals(listOf("c".repeat(20), "d".repeat(20), "now"), messages.map { it.content })
    }

    @Test
    fun `remote chat reserves context for current images`() {
        val messages = buildRemoteChatMessages(
            history = listOf(
                Message(chatId = 11, message = "previous", isSendbyMe = true),
                Message(chatId = 11, message = "answer", isSendbyMe = false),
            ),
            currentContent = "now",
            currentImages = listOf("base64-image"),
            inputTokenBudget = 1_050,
        )

        assertEquals(listOf("user"), messages.map { it.role })
        assertEquals(listOf("now"), messages.map { it.content })
        assertEquals(listOf("base64-image"), messages.single().images)
    }

    @Test
    fun `remote chat never sends orphan assistant when its user exceeds budget`() {
        val messages = buildRemoteChatMessages(
            history = listOf(
                Message(chatId = 11, message = "u".repeat(40), isSendbyMe = true),
                Message(chatId = 11, message = "short", isSendbyMe = false),
            ),
            currentContent = "now",
            inputTokenBudget = 48,
        )

        assertEquals(listOf("user"), messages.map { it.role })
        assertEquals(listOf("now"), messages.map { it.content })
    }

    @Test
    fun `OpenAI compatible request sends the same structured history without templates`() {
        val json = JSONObject(
            buildOpenAiCompatibleChatRequestJson(
                model = "Qwen3.8-27B-GGUF",
                messages = listOf(
                    OllamaChatMessage(role = "user", content = "質問"),
                    OllamaChatMessage(role = "assistant", content = "回答"),
                    OllamaChatMessage(role = "user", content = "続き", images = listOf("ollama-only")),
                ),
                maxOutputTokens = 8_192,
            ),
        )
        val messages = json.getJSONArray("messages")

        assertEquals("Qwen3.8-27B-GGUF", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        assertEquals(8_192, json.getInt("max_tokens"))
        assertEquals(3, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("続き", messages.getJSONObject(2).getString("content"))
        assertFalse(messages.getJSONObject(2).has("images"))
        listOf("prompt", "template", "chat_template", "raw").forEach { forbidden ->
            assertFalse(json.has(forbidden))
        }
    }
}
