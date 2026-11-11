package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class AndroidTtsTextChunksTest {
    @Test fun `input at the API limit stays in one request and longer input retains every character`() {
        assertEquals(listOf("あ".repeat(4000)), splitAndroidTtsText("あ".repeat(4000), 4000))
        val input = "あ".repeat(8001)
        val chunks = splitAndroidTtsText(input, 4000)
        assertEquals(listOf(4000, 4000, 1), chunks.map { it.length })
        assertEquals(input, chunks.joinToString(""))
    }

    @Test fun `nearby sentence or whitespace boundaries preserve separators`() {
        assertEquals(listOf("前半です。", "後半も続きます"), splitAndroidTtsText("前半です。後半も続きます", 8))
        assertEquals(listOf("hello ", "world ", "again"), splitAndroidTtsText("hello world again", 8))
    }

    @Test fun `hard boundary never splits a supplementary code point`() {
        val input = "あ".repeat(3999) + "😀" + "い".repeat(4000)
        val chunks = splitAndroidTtsText(input, 4000)
        assertEquals(input, chunks.joinToString(""))
        assertEquals(3999, chunks.first().length)
        assertTrue(chunks.all { it.length <= 4000 && it.isNotEmpty() })
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
        assertEquals(listOf("😀", "😺", "あ"), splitAndroidTtsText("😀😺あ", 2))
    }

    @Test fun `empty input has no requests and a limit too small for a code point is rejected`() {
        assertTrue(splitAndroidTtsText("", 4000).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { splitAndroidTtsText("😀", 1) }
    }
}
