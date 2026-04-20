package io.github.ninbyo02.lami.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FencedCodeParserTest {

    @Test
    fun normalTextOnly_returnsSingleTextSegment() {
        val input = "ただのテキストです"

        val result = parseFencedCodeSegments(input)

        assertEquals(listOf(Segment.Text("ただのテキストです")), result)
    }

    @Test
    fun singleFencedCodeWithLanguage_splitsToTextCodeText() {
        val input = "前文\n```kotlin\nval a = 1\n```\n後文"

        val result = parseFencedCodeSegments(input)

        assertEquals(3, result.size)
        assertEquals(Segment.Text("前文"), result[0])
        assertEquals(Segment.Code("kotlin", "val a = 1"), result[1])
        assertEquals(Segment.Text("後文"), result[2])
    }

    @Test
    fun fencedCodeWithoutLanguage_setsLangNull() {
        val input = "```\nconsole.log('x')\n```"

        val result = parseFencedCodeSegments(input)

        assertEquals(1, result.size)
        val codeSegment = result[0] as Segment.Code
        assertEquals(null, codeSegment.lang)
        assertEquals("console.log('x')", codeSegment.code)
    }

    @Test
    fun multipleFencedCodeBlocks_extractsMultipleCodes() {
        val input = "A\n```kotlin\nval a = 1\n```\nB\n```python\nprint('x')\n```\nC"

        val result = parseFencedCodeSegments(input)

        val codeSegments = result.filterIsInstance<Segment.Code>()
        assertEquals(2, codeSegments.size)
        assertEquals("kotlin", codeSegments[0].lang)
        assertEquals("val a = 1", codeSegments[0].code)
        assertEquals("python", codeSegments[1].lang)
        assertEquals("print('x')", codeSegments[1].code)
    }

    @Test
    fun missingClosingFence_returnsUnclosedCodeSegment() {
        val input = "開始\n```kotlin\nval a = 1"

        val result = parseFencedCodeSegments(input)

        assertEquals(2, result.size)
        assertTrue(result[0] is Segment.Text)
        assertEquals("開始", (result[0] as Segment.Text).text)
        val code = result[1] as Segment.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val a = 1", code.code)
        assertTrue(!code.isClosed)
    }
}
