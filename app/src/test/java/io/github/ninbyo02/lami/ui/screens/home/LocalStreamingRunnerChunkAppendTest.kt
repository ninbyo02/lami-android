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
}
