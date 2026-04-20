package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStreamingRunnerChunkAppendTest {

    @Test
    fun `Hello と World の境界では最小 join を入れる`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, "World")

        assertEquals(" ", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `hello dot py と 日本語助詞は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("hello.py", "を"))
    }

    @Test
    fun `Python と 日本語接続は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("Python", "で"))
    }

    @Test
    fun `print 呼び出しトークンは join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("print(", "\"x\")"))
    }

    @Test
    fun `fenced code の開始は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("```python", "\nimport os"))
    }

    @Test
    fun `foo と comma は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("foo", ","))
    }

    @Test
    fun `comma と World は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween(",", "World"))
    }

    @Test
    fun `先頭空白を含む chunk には join を追加しない`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " World")

        assertEquals("", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `空白のみ chunk も streaming chunk として保持対象にする`() {
        assertTrue(shouldPreserveWhitespaceChunk(" "))
        assertTrue(isViableStreamingChunk(" \t"))
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " ")

        assertEquals("", join)
        assertEquals("Hello ", builder.toString())
    }

    @Test
    fun `prose lane は日本語の chunk を壊さず連結する`() {
        val builder = StringBuilder("はい、")
        val context = StreamingAppendContext()

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = " 以下",
            context = context,
        )

        assertEquals("", join)
        assertEquals("はい、 以下", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python と import の連結で code lane に入り不要 join を入れない`() {
        val builder = StringBuilder("以下に")
        val context = StreamingAppendContext()
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "import turtle",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nimport turtle", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `code lane の print トークン連結では join しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("print(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `prose から code へ遷移しても lane ごとの連結規則を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "以下に",
            context = context,
        )
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nprint(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `python 単独タグの後に import が来たら改行で再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "import os", context)

        assertEquals("python\nimport os", builder.toString())
    }

    @Test
    fun `python タグと複数行コードを再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "def main():", context)
        appendStreamingChunk(builder, "    print(\"x\")", context)

        assertEquals("python\ndef main():\n    print(\"x\")", builder.toString())
    }

    @Test
    fun `prose lane は従来どおり自然文を連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "こんにちは、", context)
        appendStreamingChunk(builder, "承知しました。", context)

        assertEquals("こんにちは、承知しました。", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `x equal と空白付き値は 1 行のまま連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "x =", context)
        appendStreamingChunk(builder, " 1", context)

        assertEquals("x = 1", builder.toString())
    }

    @Test
    fun `if の次の print は必要に応じて改行する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "if x > 0:", context)
        appendStreamingChunk(builder, "print(x)", context)

        assertEquals("if x > 0:\nprint(x)", builder.toString())
    }
}
