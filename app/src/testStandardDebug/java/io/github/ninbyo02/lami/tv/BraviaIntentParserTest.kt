package io.github.ninbyo02.lami.tv

import org.junit.Assert.*
import org.junit.Test

class BraviaIntentParserTest {
    @Test fun explicitJapaneseCommandsHaveTypedArguments() {
        assertEquals(BraviaCommand.Power(true), BraviaIntentParser.parse("ラミィ、テレビをつけて。"))
        assertEquals(BraviaCommand.Power(false), BraviaIntentParser.parse("テレビを消してください"))
        assertEquals(BraviaCommand.Volume(10), BraviaIntentParser.parse("テレビの音量を１０にして"))
        assertEquals(BraviaCommand.Input(1), BraviaIntentParser.parse("テレビをHDMI1に切り替えて"))
        assertEquals(BraviaCommand.Input(4), BraviaIntentParser.parse("テレビの入力をHDMI4にして"))
        assertEquals(BraviaCommand.Channel(1, "地デジ"), BraviaIntentParser.parse("テレビを地デジの1チャンネルにして"))
        assertEquals(BraviaCommand.Key(BraviaRemoteKey.PAUSE), BraviaIntentParser.parse("テレビで一時停止して"))
        assertEquals(BraviaCommand.LaunchApp("netflix"), BraviaIntentParser.parse("テレビでnetflixを開いて"))
        assertEquals(BraviaCommand.Status, BraviaIntentParser.parse("テレビの状態を教えて"))
    }

    @Test fun discussionNegationQuestionsAndCompoundInstructionsNeverBecomeDeviceActions() {
        listOf("音量を10にして", "テレビのおすすめを教えて", "テレビを消さないで",
            "テレビを消してもいい？", "「テレビを消して」を英訳して", "テレビを消して、それからつけて",
            "明日テレビをつけて", "テレビを消して\nテレビをつけて", "テレビをつけてと言ったらどうなる",
            "テレビで動画を観たい", "テレビを買いたい").forEach { assertNull(it, BraviaIntentParser.parse(it)) }
    }

    @Test fun schemasRejectUnexpectedArgumentsAndCoercionsBeforeDispatch() {
        fun invalid(call: BraviaToolCall) {
            try { BraviaToolCatalog.command(call); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        }
        invalid(BraviaToolCall("tv_power", braviaJson("on" to "false")))
        invalid(BraviaToolCall("tv_volume", braviaJson("level" to 10.5)))
        invalid(BraviaToolCall("tv_volume", braviaJson("level" to 101)))
        invalid(BraviaToolCall("tv_input", braviaJson("port" to 5)))
        invalid(BraviaToolCall("tv_power", braviaJson("on" to false, "url" to "http://public.example")))
        invalid(BraviaToolCall("tv_key", braviaJson("key" to "factory_reset")))
        invalid(BraviaToolCall("tv_channel", braviaJson("preset" to 1, "band" to "invented")))
        invalid(BraviaToolCall("arbitrary_http", braviaJson("url" to "http://public.example")))
        assertEquals(BraviaCommand.Power(false), BraviaToolCatalog.command(BraviaToolCatalog.call(BraviaCommand.Power(false))))
    }

    @Test fun onlyPrivateNumericLanAddressesAreAccepted() {
        listOf("192.168.1.50", "10.0.0.2", "172.16.0.10", "172.31.1.2").forEach { assertEquals(it, BraviaAddress.parse(it).host) }
        listOf("8.8.8.8", "172.32.0.1", "http://192.168.1.50", "localhost", "192.168.1.50:80", "192.168.1.999", "192.168.01.2").forEach {
            try { BraviaAddress.parse(it); fail(it) } catch (_: IllegalArgumentException) { }
        }
    }
}
