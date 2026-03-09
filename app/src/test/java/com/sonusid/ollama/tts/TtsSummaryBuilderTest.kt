package com.sonusid.ollama.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSummaryBuilderTest {
    @Test
    fun normalResponse_addsDefaultIntro() {
        val raw = "おすすめの方法は3つあります。設定確認、ログ確認、再起動の順で試してください。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertTrue(actual.contains("結論からお伝えしますね。"))
    }

    @Test
    fun listResponse_addsListIntro() {
        val raw = "- 手順A\n- 手順B\n- 手順C"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertTrue(actual.contains("順番に説明しますね。"))
    }

    @Test
    fun codeResponse_addsCodeIntro() {
        val raw = "```python\nprint('hello')\n```"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertTrue(actual.contains("コード例があります。ポイントをお話ししますね。"))
    }

    @Test
    fun shortNaturalSentence_keepsOriginalSpeechText() {
        val raw = "はい、了解しました。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals("はい、了解しました。", actual)
    }

    @Test
    fun shortGreeting_keepsOriginalSpeechText() {
        val raw = "こんにちは。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals("こんにちは。", actual)
    }

    @Test
    fun errorResponse_addsErrorIntroWhenLong() {
        val raw = "接続に失敗しました。ネットワーク設定を確認してから再試行してください。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech, isError = true)

        assertTrue(actual.contains("状況を確認しますね。"))
    }
}
