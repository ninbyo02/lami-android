package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.tts.SpeechTextBuilder
import org.junit.Assert.*
import org.junit.Test

class ResponseSpeechSessionTest {
    @Test fun `completion speaks only unsaid tail and duplicate completion is ignored`() {
        val session = ResponseSpeechSession().apply { begin() }
        assertEquals("こんにちは。", session.take("こんにちは。佐藤さん", false))
        assertEquals("佐藤さん", session.take("こんにちは。佐藤さん", true))
        assertNull(session.take("こんにちは。佐藤さん", true))
        assertNull(session.take("こんにちは。佐藤さん。", false))
    }
    @Test fun `stop before first text blocks partial and final`() {
        val session = ResponseSpeechSession().apply { begin(); stop() }
        assertNull(session.take("こんにちは。", false))
        assertNull(session.take("こんにちは。", true))
    }
    @Test fun `stop while preparing invalidates captured token even after resend`() {
        val session = ResponseSpeechSession().apply { begin() }
        val old = session.generation
        session.stop()
        assertFalse(session.accepts(old))
        session.begin()
        assertFalse(session.accepts(old))
        assertEquals("次の回答。", session.take("次の回答。", true))
    }
    @Test fun `stop after sentence discards remaining tail`() {
        val session = ResponseSpeechSession().apply { begin() }
        assertEquals("最初。", session.take("最初。続き", false))
        session.stop()
        assertNull(session.take("最初。続きです。", true))
    }
    @Test fun `short answer without punctuation is spoken once on completion`() {
        val session = ResponseSpeechSession().apply { begin() }
        assertNull(session.take("赤", false))
        assertEquals("赤", session.take("赤", true))
        assertNull(session.take("赤", true))
    }

    @Test fun `numeric separators wait for the rest of the number before enqueuing speech`() {
        val session = ResponseSpeechSession().apply { begin() }
        assertNull(session.take("集合は４：", false))
        assertNull(session.take("集合は４：２０", false))
        val first = "集合は４：２０です、"
        assertEquals(first, session.take(first + "出力は1,", false))
        assertNull(session.take(first + "出力は1,250", false))
        assertEquals("出力は1,250 Wです。", session.take(first + "出力は1,250 Wです。", true))
    }

    @Test fun `nonnumeric soft boundaries and final trailing separators are still spoken`() {
        val session = ResponseSpeechSession().apply { begin() }
        val prefix = "手順1:"
        assertEquals(prefix, session.take(prefix + "確認", false))
        assertNull(session.take(prefix + "確認は手順2:", false))
        assertEquals("確認は手順2:", session.take(prefix + "確認は手順2:", true))
    }

    @Test fun `shorter display replacement never rewinds speech`() {
        val session = ResponseSpeechSession().apply { begin() }
        assertEquals("こんにちは。", session.take("こんにちは。", false))
        assertNull(session.take("こん", false))
        assertNull(session.take("こんにちは。", true))
    }
    @Test fun `next request starts its own cursor`() {
        val session = ResponseSpeechSession().apply { begin() }
        session.take("長い回答でした。", true)
        session.begin()
        assertEquals("はい。", session.take("はい。", true))
    }

    @Test fun `fenced code does not retire speech and later explanation resumes once`() {
        val session = ResponseSpeechSession().apply { begin() }
        val owner = session.generation
        val prefix = "最初に説明します。\n"
        val code = "```kotlin\nval value = 1\n"
        assertEquals(prefix, session.take(prefix + code, false))
        assertNull(session.take(prefix + code + "println(value)\n", false))
        assertTrue(session.accepts(owner))
        val complete = prefix + code + "println(value)\n```\n続きの説明です。最後の補足"
        assertEquals("続きの説明です。", session.take(complete, false))
        assertEquals("最後の補足", session.take(complete, true))
        assertEquals(owner, session.generation)
        assertNull(session.take(complete, true))
    }

    @Test fun `opening and closing fence split across updates never leak code or repeat prose`() {
        val session = ResponseSpeechSession().apply { begin() }
        val prefix = "前の説明。\n"
        val code = "```python\nprint('読まない')\n"
        val updates = listOf(prefix + "`", prefix + "``", prefix + code,
            prefix + code + "`", prefix + code + "``", prefix + code + "```\n後の説明。")
        val spoken = updates.mapNotNull { session.take(it, false) }.joinToString("")
        assertEquals(prefix + "後の説明。", spoken)
        assertNull(session.take(updates.last(), true))
    }

    @Test fun `generation pause with no new text preserves cursor and owner for resumed speech`() {
        val session = ResponseSpeechSession().apply { begin() }
        val owner = session.generation
        val initial = "最初の文章。次の途中"
        assertEquals("最初の文章。", session.take(initial, false))
        repeat(5) { assertNull(session.take(initial, false)) }
        assertTrue(session.accepts(owner))
        val resumed = initial + "から再開します。補足"
        assertEquals("次の途中から再開します。", session.take(resumed, false))
        assertEquals("補足", session.take(resumed, true))
    }

    @Test fun `an unclosed code block stays omitted on final completion`() {
        val session = ResponseSpeechSession().apply { begin() }
        val text = "説明。\n```python\nprint('読まない')\n"
        assertEquals("説明。\n", session.take(text, false))
        assertNull(session.take(text, true))
        assertFalse(session.accepts())
    }

    @Test fun `tilde fences and longer backtick fences keep their own closing marker`() {
        val session = ResponseSpeechSession().apply { begin() }
        val text = "前。\n~~~~python\n```\n読まない。\n~~~~\n中。\n````kotlin\n```\n読まない。\n````\n後。"
        assertEquals("前。\n中。\n後。", session.take(text, true))
    }

    @Test fun `explicit stop while inside code still blocks later explanation`() {
        val session = ResponseSpeechSession().apply { begin() }
        val partial = "前。\n```python\nprint(1)\n"
        assertEquals("前。\n", session.take(partial, false))
        session.stop()
        assertNull(session.take(partial + "```\n後。", false))
        assertNull(session.take(partial + "```\n後。", true))
    }

    @Test fun `speech formatting changes emitted text without moving the cumulative source cursor`() {
        val session = ResponseSpeechSession().apply { begin() }
        val prefix = "## 手順\n**最初の説明です。**\n```kotlin\nval count = 2\n```\n"
        val complete = prefix + "最後に3 0秒待ちます"
        val first = checkNotNull(session.take(prefix, false))
        assertEquals("手順\n最初の説明です。", SpeechTextBuilder.build(first))
        val tail = checkNotNull(session.take(complete, true))
        assertEquals("最後に3 0秒待ちます", tail)
        assertEquals("最後に30秒待ちます", SpeechTextBuilder.build(tail))
        assertNull(session.take(complete, true))
    }

    @Test fun `trimmed final source preserves all unsaid prose after a code block`() {
        val session = ResponseSpeechSession().apply { begin() }
        val streamed = "\n  ## 見出し\n説明。\n```python\nprint(1)\n```\n最後の補足"
        assertEquals("## 見出し\n説明。\n", session.take(streamed, false))
        assertEquals("最後の補足", session.take(streamed.trim(), true))
        assertNull(session.take(streamed.trim(), true))
    }
}
