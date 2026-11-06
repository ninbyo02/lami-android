package io.github.ninbyo02.lami.skills.web

import org.junit.Assert.*
import org.junit.Test

class WebSearchIntentParserTest {
    @Test fun acceptsOnlyExplicitCommandsAndExtractsOnlyTheQuery() {
        listOf("Webで新しいLLMを検索して", "DuckDuckGoで新しいLLMを検索して", "/web 新しいLLM").forEach {
            assertEquals(WebSearchIntent.Search("新しいLLM"), WebSearchIntentParser.parse(it))
        }
        assertEquals(WebSearchIntent.Search("Android 最新情報"), WebSearchIntentParser.parse(" /web Android 最新情報 "))
    }

    @Test fun ordinaryPromptsAreNotSearchRequests() {
        listOf("こんにちは", "新しいLLMを説明して", "ネット検索機能はありますか", "この文章を翻訳してください").forEach {
            assertNull(WebSearchIntentParser.parse(it))
        }
    }

    @Test fun quotedNegatedExampleQuestionsAttachmentsAndCompoundRequestsAreLocallyRejected() {
        val rejected = listOf("「WebでLLMを検索して」", "例: /web LLM", "例えばWebでLLMを検索して",
            "WebでLLMを検索しないで", "WebでLLMを検索して？", "/web LLM?", "/web\nLLM",
            "WebでLLMを検索してニュースを読んで", "WebでAを検索してBを検索して",
            "/web ", "Webでを検索して", "/web " + "a".repeat(201))
        rejected.forEach { assertEquals(it, WebSearchIntent.Rejected, WebSearchIntentParser.parse(it)) }
        assertEquals(WebSearchIntent.Rejected, WebSearchIntentParser.parse("/web LLM", hasAttachments = true))
    }
}
