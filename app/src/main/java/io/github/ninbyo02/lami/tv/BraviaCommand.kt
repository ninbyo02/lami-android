package io.github.ninbyo02.lami.tv

import com.google.gson.JsonObject
import java.text.Normalizer

sealed interface BraviaCommand {
    data object Status : BraviaCommand
    data class Power(val on: Boolean) : BraviaCommand
    data class Volume(val level: Int) : BraviaCommand
    data class Mute(val enabled: Boolean) : BraviaCommand
    data class Input(val port: Int) : BraviaCommand
    data class Channel(val preset: Int, val band: String? = null) : BraviaCommand
    data class Key(val key: BraviaRemoteKey) : BraviaCommand
    data class LaunchApp(val name: String) : BraviaCommand
}

enum class BraviaRemoteKey(val sonyName: String, val label: String) {
    HOME("Home", "ホーム"), BACK("Back", "戻る"), UP("Up", "上"), DOWN("Down", "下"),
    LEFT("Left", "左"), RIGHT("Right", "右"), CONFIRM("Confirm", "決定"),
    PLAY("Play", "再生"), PAUSE("Pause", "一時停止"), STOP("Stop", "停止"),
    VOLUME_UP("VolumeUp", "音量＋"), VOLUME_DOWN("VolumeDown", "音量－"),
    CHANNEL_UP("ChannelUp", "チャンネル＋"), CHANNEL_DOWN("ChannelDown", "チャンネル－")
}

/** Entire utterance matching: quoted examples, questions, negation and compound requests
 * are not sent to a physical device. Unqualified volume requests may refer to LAMI itself. */
object BraviaIntentParser {
    fun parse(utterance: String): BraviaCommand? {
        var t = Normalizer.normalize(utterance, Normalizer.Form.NFKC).trim()
            .replace(Regex("^(?:ラミィ|ラミー|ラミ|LAMI)[、,\\s]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[。!！]+$"), "").trim()
        if (t.length > 100 || t.contains('\n') || t.any { it in "「」『』\"" }) return null
        t = t.replace(Regex("^(?:テレビ|TV|ブラビア)[、,\\s]*", RegexOption.IGNORE_CASE), "テレビ")
        if (!t.startsWith("テレビ")) return null
        t = t.replace(Regex("(?:ください|下さい)$"), "").trim()
        val query = t.trimEnd('?', '？')
        if (query.matches(Regex("テレビ(?:の)?(?:状態|音量|電源(?:の状態)?)(?:を教えて|を確認して|は)"))) return BraviaCommand.Status
        if (t.any { it == '?' || it == '？' }) return null
        if (t.matches(Regex("テレビの電源(?:を)?(?:入れて|つけて|点けて|オンにして)"))) return BraviaCommand.Power(true)
        if (t.matches(Regex("テレビの電源(?:を)?(?:切って|消して|オフにして)"))) return BraviaCommand.Power(false)
        if (t.matches(Regex("テレビ(?:を)?(?:つけて|点けて|オンにして|電源を入れて)"))) return BraviaCommand.Power(true)
        if (t.matches(Regex("テレビ(?:を)?(?:消して|切って|オフにして|電源を切って)"))) return BraviaCommand.Power(false)
        if (t.matches(Regex("テレビ(?:の)?(?:状態|音量|電源)(?:を教えて|を確認して|は)"))) return BraviaCommand.Status
        if (t.matches(Regex("テレビ(?:を)?(?:ミュートして|消音して)"))) return BraviaCommand.Mute(true)
        if (t.matches(Regex("テレビ(?:の)?(?:ミュート|消音)(?:を)?解除して"))) return BraviaCommand.Mute(false)
        Regex("テレビ(?:の)?音量(?:を)?([0-9]{1,3})(?:にして|に設定して)").matchEntire(t)?.let {
            return BraviaCommand.Volume(it.groupValues[1].toInt())
        }
        Regex("テレビ(?:の入力|を|の入力を)?HDMI\\s*([1-4])(?:にして|に切り替えて|へ切り替えて)", RegexOption.IGNORE_CASE).matchEntire(t)?.let {
            return BraviaCommand.Input(it.groupValues[1].toInt())
        }
        Regex("テレビ(?:を|のチャンネルを)(?:(地デジ|BS|CS)(?:の)?)?([1-9]|1[0-2])(?:チャンネル|番)?にして", RegexOption.IGNORE_CASE).matchEntire(t)?.let {
            return BraviaCommand.Channel(it.groupValues[2].toInt(), it.groupValues[1].ifBlank { null })
        }
        val keys = mapOf("ホームに戻して" to BraviaRemoteKey.HOME, "ホームを開いて" to BraviaRemoteKey.HOME,
            "戻って" to BraviaRemoteKey.BACK, "決定して" to BraviaRemoteKey.CONFIRM,
            "上に動かして" to BraviaRemoteKey.UP, "下に動かして" to BraviaRemoteKey.DOWN,
            "左に動かして" to BraviaRemoteKey.LEFT, "右に動かして" to BraviaRemoteKey.RIGHT,
            "再生して" to BraviaRemoteKey.PLAY, "一時停止して" to BraviaRemoteKey.PAUSE, "停止して" to BraviaRemoteKey.STOP,
            "音量を上げて" to BraviaRemoteKey.VOLUME_UP, "音量を下げて" to BraviaRemoteKey.VOLUME_DOWN,
            "チャンネルを上げて" to BraviaRemoteKey.CHANNEL_UP, "チャンネルを下げて" to BraviaRemoteKey.CHANNEL_DOWN)
        keys[t.removePrefix("テレビ").replace(Regex("^(?:の|を|で)"), "")]?.let { return BraviaCommand.Key(it) }
        Regex("テレビで([^、,。\\n]{1,40})(?:を開いて|を起動して)").matchEntire(t)?.let {
            return BraviaCommand.LaunchApp(it.groupValues[1].trim())
        }
        return null
    }
}

data class BraviaToolResult(val ok: Boolean, val reply: String, val verified: Boolean = false, val details: JsonObject = JsonObject())

class BraviaControlTool(private val client: BraviaClient) {
    fun execute(command: BraviaCommand): BraviaToolResult = when (command) {
        BraviaCommand.Status -> {
            val on = client.powerStatus()
            val volume = if (on) client.optionalVolume() else null
            val input = if (on) client.optionalInputUri() else null
            val reply = buildList {
                add("テレビの電源：${if (on) "オン" else "待機中"}")
                volume?.let { add("音量：${it.value}${if (it.muted) "（消音中）" else ""}") }
                Regex("extInput:hdmi\\?port=([1-4])").find(input.orEmpty())?.let { add("入力：HDMI ${it.groupValues[1]}") }
            }.joinToString("\n")
            BraviaToolResult(true, reply, true, braviaJson("power" to on, "volume" to volume?.value, "muted" to volume?.muted, "input" to input))
        }
        is BraviaCommand.Power -> {
            client.setPower(command.on)
            val actual = try { client.powerStatus() } catch (_: BraviaException) { null }
            val verified = actual == command.on
            BraviaToolResult(true, if (verified) "テレビの電源を${if (command.on) "入れました" else "切りました"}。"
                else "テレビへ電源${if (command.on) "オン" else "オフ"}の要求を送りました。状態はまだ確認できていません。", verified)
        }
        is BraviaCommand.Volume -> {
            require(command.level in 0..100) { "音量は0〜100で指定してください。" }
            client.setVolume(command.level)
            val actual = try { client.volume() } catch (_: BraviaException) { null }
            if (actual == null) BraviaToolResult(true, "テレビへ音量${command.level}の設定要求を送りました。現在の音量はまだ確認できていません。")
            else BraviaToolResult(actual.value == command.level, "テレビの現在の音量は${actual.value}です。", actual.value == command.level,
                braviaJson("volume" to actual.value, "muted" to actual.muted))
        }
        is BraviaCommand.Mute -> {
            client.setMute(command.enabled)
            val actual = try { client.volume() } catch (_: BraviaException) { null }
            if (actual == null) BraviaToolResult(true, "テレビへ消音${if (command.enabled) "オン" else "解除"}の要求を送りました。状態はまだ確認できていません。")
            else BraviaToolResult(actual.muted == command.enabled, "テレビは${if (actual.muted) "消音中" else "消音解除の状態"}です。", actual.muted == command.enabled)
        }
        is BraviaCommand.Input -> {
            require(command.port in 1..4) { "HDMIは1〜4で指定してください。" }
            client.sendKnownKey("Hdmi${command.port}")
            val actual = try { client.optionalInputUri() } catch (_: BraviaException) { null }
            val verified = actual?.let { Regex("extInput:hdmi\\?port=${command.port}(?:&|$)").containsMatchIn(it) } ?: false
            BraviaToolResult(true, if (verified) "テレビの入力はHDMI ${command.port}です。" else "テレビへHDMI ${command.port}の切り替えキーを送りました。", verified)
        }
        is BraviaCommand.Channel -> {
            require(command.preset in 1..12) { "チャンネルのボタン番号は1〜12で指定してください。" }
            require(command.band == null || command.band.lowercase() in setOf("地デジ", "bs", "cs"))
            val keys = client.keys()
            val bandKey = when (command.band?.lowercase()) { "地デジ" -> "Digital"; "bs" -> "BS"; "cs" -> "CS"; else -> "Tv" }
            // Validate the entire sequence before changing anything on the TV.
            if (keys.none { it.equals(bandKey, true) } || keys.none { it.equals("Num${command.preset}", true) }) {
                throw BraviaUnsupported("このテレビから、指定した放送・チャンネルの操作コードを取得できませんでした。")
            }
            client.sendKnownKey(bandKey)
            client.sendKnownKey("Num${command.preset}")
            BraviaToolResult(true, "テレビへ${command.band.orEmpty()}の${command.preset}番の選局キーを送りました。放送局の確認はまだできていません。")
        }
        is BraviaCommand.Key -> {
            client.sendKnownKey(command.key.sonyName)
            BraviaToolResult(true, "テレビへ「${command.key.label}」キーを送りました。")
        }
        is BraviaCommand.LaunchApp -> {
            require(command.name.isNotBlank() && command.name.length <= 40)
            val name = client.launchApplication(command.name)
            BraviaToolResult(true, "テレビへ「$name」の起動要求を送りました。")
        }
    }
}
