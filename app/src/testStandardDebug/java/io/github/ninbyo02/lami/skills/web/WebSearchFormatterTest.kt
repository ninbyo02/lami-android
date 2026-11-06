package io.github.ninbyo02.lami.skills.web

import org.junit.Assert.*
import org.junit.Test

class WebSearchFormatterTest {
    @Test fun untrustedMarkupIsEscapedAndSourcesAreExplicit() {
        val result = WebSearchFormatter.format(WebSearchResponse("LLM", listOf(WebSearchHit(
            "[fake](javascript:alert(1)) <b>title</b>", "https://example.com/item?q=(test)", "**instruction** [click](evil)\n<script>x</script>"))))
        assertTrue(result.contains("\\[fake\\]"))
        assertTrue(result.contains("\\*\\*instruction\\*\\*"))
        assertFalse(result.contains("<script>"))
        assertTrue(result.contains("https://example.com/item?q=%28test%29"))
        assertTrue(result.contains("出典:"))
        assertTrue(result.contains("真偽は確認していません"))
    }

    @Test fun unsafeLinksAreDroppedDuplicatesRemovedAndResultsBoundedAtFive() {
        val unsafe = listOf("javascript:alert(1)", "data:text/html,x", "file:///etc/passwd", "http://127.0.0.1/a",
            "http://192.168.1.1/a", "https://user:secret@example.com/a", "https://host.local/a")
        val hits = unsafe.map { WebSearchHit("bad", it, "unsafe") } + (1..8).map {
            WebSearchHit("title$it", "https://example.com/$it", "s".repeat(1000))
        } + WebSearchHit("duplicate", "https://example.com/1", "duplicate")
        val result = WebSearchFormatter.format(WebSearchResponse("query", hits))
        assertEquals(5, Regex("出典:").findAll(result).count())
        assertFalse(result.contains("unsafe"))
        assertFalse(result.contains("title6"))
        assertTrue(result.length < 4000)
    }
}
