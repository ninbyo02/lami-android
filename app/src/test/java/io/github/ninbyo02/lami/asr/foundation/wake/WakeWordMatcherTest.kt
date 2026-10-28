package io.github.ninbyo02.lami.asr.foundation.wake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherTest {
    @Test
    fun kanaWidthWhitespaceAndPunctuationAreNormalized() {
        val matcher = WakeWordMatcher("ラミ")
        listOf("らみ", "ラミ。", "ﾗﾐ", " ラ ミ！ ").forEach { assertTrue(matcher.matches(it)) }
    }

    @Test
    fun latinCaseAndFullWidthAreNormalized() {
        val matcher = WakeWordMatcher("Hey LAMI")
        assertTrue(matcher.matches("ｈｅｙ　ＬＡＭＩ。"))
        assertTrue(matcher.matches("Hey Lami"))
    }

    @Test
    fun wordsInsideOrdinaryConversationCannotWake() {
        val matcher = WakeWordMatcher("ラミ")
        listOf("ラミネート", "ラミ、一年は何ヶ月ですか", "私はラミです", "", "はい").forEach {
            assertFalse(matcher.matches(it))
        }
    }

    @Test
    fun arbitraryPhraseDoesNotAddImplicitAliases() {
        val matcher = WakeWordMatcher("ねえ助手")
        assertTrue(matcher.matches("ねえ助手。"))
        assertFalse(matcher.matches("ラミ"))
        assertFalse(matcher.matches("ねえじょしゅ"))
    }

    @Test
    fun invalidOrExcessivelyShortPhrasesAreRejected() {
        listOf("", "あ", "！", "a".repeat(25), "ラミ+", "ラミ😀").forEach {
            assertThrows(IllegalArgumentException::class.java) { WakeWordMatcher(it) }
        }
    }
}
