package io.github.ninbyo02.lami.tools.bravia

import java.text.Normalizer

internal sealed interface BraviaCommand {
    data class Power(val on: Boolean) : BraviaCommand
    data class Volume(val level: Int) : BraviaCommand
    data class Mute(val muted: Boolean) : BraviaCommand
    data class Hdmi(val port: Int) : BraviaCommand
    data class Channel(val preset: Int) : BraviaCommand
    data class Key(val name: String, val label: String) : BraviaCommand
    data object Status : BraviaCommand
}

/**
 * Only whole, explicitly addressed commands are executed. Questions, quotations, negations,
 * attachments and compound requests are not converted to device operations by an LLM.
 */
internal object BraviaCommandParser {
    fun parse(input: String): BraviaCommand? {
        var text = Normalizer.normalize(input, Normalizer.Form.NFKC).trim()
        text = text.replace(Regex("^(?:ラミィ|ラミー|ラミ|LAMI)[、,\\s]+", RegexOption.IGNORE_CASE), "")
        if (text.contains('\n')) return null
        text = text.trimEnd('。', '！', '!', ' ', '　')
        text = text.replace(Regex("(?:ください|下さい|お願い(?:します)?|くれる|もらえる)$"), "")
            .trimEnd('、', ',', ' ', '　')
        val prefix = Regex("^(?:テレビ|TV|ブラビア|BRAVIA)(?:の|を|で)?[ 　]*", RegexOption.IGNORE_CASE)
        val match = prefix.find(text) ?: return null
        val command = text.substring(match.range.last + 1).replace(" ", "").replace("　", "")
        return when (command) {
            "つけて", "付けて", "点けて", "電源をつけて", "電源を入れて", "電源オン", "電源をオンにして" ->
                BraviaCommand.Power(true)
            "消して", "切って", "電源を消して", "電源を切って", "電源オフ", "電源をオフにして" ->
                BraviaCommand.Power(false)
            "ミュートして", "消音して", "音を消して" -> BraviaCommand.Mute(true)
            "ミュートを解除して", "消音を解除して", "音を戻して" -> BraviaCommand.Mute(false)
            "音量を上げて", "音を大きくして" -> BraviaCommand.Key("VolumeUp", "音量を1つ上げる")
            "音量を下げて", "音を小さくして" -> BraviaCommand.Key("VolumeDown", "音量を1つ下げる")
            "チャンネルを上げて", "次のチャンネルにして" -> BraviaCommand.Key("ChannelUp", "次のチャンネル")
            "チャンネルを下げて", "前のチャンネルにして" -> BraviaCommand.Key("ChannelDown", "前のチャンネル")
            "ホームに戻って", "ホームを開いて", "ホームにして" -> BraviaCommand.Key("Home", "ホーム")
            "戻って", "戻るを押して" -> BraviaCommand.Key("Back", "戻る")
            "上を押して", "上に移動して" -> BraviaCommand.Key("Up", "上")
            "下を押して", "下に移動して" -> BraviaCommand.Key("Down", "下")
            "左を押して", "左に移動して" -> BraviaCommand.Key("Left", "左")
            "右を押して", "右に移動して" -> BraviaCommand.Key("Right", "右")
            "決定して", "決定を押して" -> BraviaCommand.Key("Confirm", "決定")
            "再生して" -> BraviaCommand.Key("Play", "再生")
            "一時停止して" -> BraviaCommand.Key("Pause", "一時停止")
            "停止して" -> BraviaCommand.Key("Stop", "停止")
            "状態を教えて", "状態を確認して", "今の音量を教えて", "音量を教えて" -> BraviaCommand.Status
            else -> parseNumberCommand(command)
        }
    }

    private fun parseNumberCommand(text: String): BraviaCommand? {
        Regex("音量(?:を)?([0-9]{1,3})に(?:して|設定して)").matchEntire(text)?.let {
            return BraviaCommand.Volume(it.groupValues[1].toInt())
        }
        Regex("(?:入力(?:を)?(?:HDMI|hdmi)|(?:HDMI|hdmi))([1-4])に(?:して|切り替えて|切り換えて)")
            .matchEntire(text)?.let { return BraviaCommand.Hdmi(it.groupValues[1].toInt()) }
        Regex("(?:チャンネル(?:を)?([0-9]{1,2})|([0-9]{1,2})チャンネル)に(?:して|切り替えて|切り換えて)")
            .matchEntire(text)?.let {
                val number = (it.groupValues[1].ifBlank { it.groupValues[2] }).toInt()
                if (number in 1..12) return BraviaCommand.Channel(number)
            }
        return null
    }
}
