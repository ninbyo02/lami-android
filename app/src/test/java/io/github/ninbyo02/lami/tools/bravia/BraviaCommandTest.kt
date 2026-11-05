package io.github.ninbyo02.lami.tools.bravia

import org.junit.Assert.*
import org.junit.Test

class BraviaCommandTest {
    @Test fun explicitCommandsAndFullWidthNumbers() {
        assertEquals(BraviaCommand.Power(true), BraviaCommandParser.parse("テレビをつけて"))
        assertEquals(BraviaCommand.Power(false), BraviaCommandParser.parse("ラミィ、テレビの電源を切ってください。"))
        assertEquals(BraviaCommand.Volume(10), BraviaCommandParser.parse("テレビの音量を１０にして"))
        assertEquals(BraviaCommand.Mute(false), BraviaCommandParser.parse("TVのミュートを解除して"))
        assertEquals(BraviaCommand.Hdmi(1), BraviaCommandParser.parse("テレビをHDMI 1にして"))
        assertEquals(BraviaCommand.Channel(12), BraviaCommandParser.parse("テレビを12チャンネルにして"))
        assertEquals(BraviaCommand.Status, BraviaCommandParser.parse("テレビの状態を教えて"))
    }

    @Test fun conversationNeverExecutesAQuotedOrNegatedCommand() {
        listOf(
            "テレビを消さないで", "「テレビを消して」と言われました", "テレビを消してから音量を10にして",
            "テレビを消していいですか", "テレビを消して？", "テレビをつける方法を教えて",
            "テレビの電源を切るコードを書いて", "テレビをつけて\nそれから消して", "音量を10にして",
            "テレビを100チャンネルにして", "テレビをHDMI 5にして",
        ).forEach { assertNull(it, BraviaCommandParser.parse(it)) }
    }

    @Test fun navigationNamesAreRestrictedToKnownRemoteKeys() {
        listOf("テレビの上を押して", "テレビのホームに戻って", "テレビを一時停止して").forEach {
            val key = BraviaCommandParser.parse(it) as BraviaCommand.Key
            assertTrue(key.name in BraviaProtocol.ALLOWED_KEYS)
        }
    }
}
