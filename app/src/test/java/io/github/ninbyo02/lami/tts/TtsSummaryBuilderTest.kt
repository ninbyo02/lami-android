package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSummaryBuilderTest {
    @Test
    fun shortGreeting_keepsOriginalSpeechText() {
        val raw = "こんにちは。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals("こんにちは。", actual)
    }

    @Test
    fun shortAck_keepsOriginalSpeechText() {
        val raw = "はい、了解しました。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals("はい、了解しました。", actual)
    }

    @Test
    fun shortNaturalResponse_keepsOriginalSpeechText() {
        val raw = "こんにちは！お手伝いできますか？"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals(speech, actual)
    }

    @Test
    fun listResponse_keepsOriginalSpeechTextWithoutIntro() {
        val raw = "- 手順A\n- 手順B\n- 手順C"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals(speech, actual)
    }

    @Test
    fun codeResponse_keepsOriginalSpeechText() {
        val raw = "```python\nprint('hello')\n```"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals(speech, actual)
    }

    @Test
    fun longExplanation_keepsOriginalSpeechText() {
        val raw = "まず状況を整理します。次に原因候補を確認します。最後に再発防止策を提案します。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech)

        assertEquals(speech, actual)
    }

    @Test
    fun shortError_keepsOriginalSpeechText() {
        val raw = "接続に失敗しました。"
        val speech = SpeechTextBuilder.build(raw)

        val actual = TtsSummaryBuilder.build(rawDisplayText = raw, speechText = speech, isError = true)

        assertEquals("接続に失敗しました。", actual)
    }
}
