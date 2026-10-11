package io.github.ninbyo02.lami.ui.screens.home

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
}
