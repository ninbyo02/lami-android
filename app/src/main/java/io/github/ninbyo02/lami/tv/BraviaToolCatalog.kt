package io.github.ninbyo02.lami.tv

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class BraviaToolCall(val name: String, val arguments: JsonObject)

/** OpenAI-style function declarations, independent of the inference backend. Only these
 * typed operations can reach the TV; arbitrary URLs, SOAP bodies and IR codes cannot. */
object BraviaToolCatalog {
    private data class Definition(val name: String, val description: String, val fields: JsonObject, val required: List<String>)
    private fun field(type: String, vararg values: Pair<String, Any?>) = braviaJson("type" to type, *values)
    private val definitions = listOf(
        Definition("tv_status", "登録したテレビの電源・音量・入力の状態を取得する。", JsonObject(), emptyList()),
        Definition("tv_power", "テレビの電源を指定した状態にする。", braviaJson("on" to field("boolean")), listOf("on")),
        Definition("tv_volume", "テレビのスピーカー音量を数値で指定する。", braviaJson("level" to field("integer", "minimum" to 0, "maximum" to 100)), listOf("level")),
        Definition("tv_mute", "テレビの消音状態を指定する。", braviaJson("enabled" to field("boolean")), listOf("enabled")),
        Definition("tv_input", "テレビの入力をHDMI 1〜4に切り替える。", braviaJson("port" to field("integer", "minimum" to 1, "maximum" to 4)), listOf("port")),
        Definition("tv_channel", "テレビの登録済みチャンネルボタン番号を送る。放送局名の推測はしない。", braviaJson(
            "preset" to field("integer", "minimum" to 1, "maximum" to 12),
            "band" to field("string", "enum" to listOf("current", "地デジ", "BS", "CS"))), listOf("preset")),
        Definition("tv_key", "テレビで対応している通常のリモコンキーを送る。", braviaJson("key" to field("string", "enum" to BraviaRemoteKey.entries.map { it.name.lowercase() })), listOf("key")),
        Definition("tv_launch_app", "テレビ自身のアプリ一覧にある名前からアプリを起動する。", braviaJson("name" to field("string", "minLength" to 1, "maxLength" to 40)), listOf("name")),
    )

    fun declarations(): JsonArray = JsonArray().apply {
        definitions.forEach { d -> add(braviaJson("type" to "function", "function" to braviaJson(
            "name" to d.name, "description" to d.description, "parameters" to braviaJson(
                "type" to "object", "properties" to d.fields, "required" to d.required, "additionalProperties" to false)))) }
    }

    fun call(command: BraviaCommand): BraviaToolCall = when (command) {
        BraviaCommand.Status -> BraviaToolCall("tv_status", JsonObject())
        is BraviaCommand.Power -> BraviaToolCall("tv_power", braviaJson("on" to command.on))
        is BraviaCommand.Volume -> BraviaToolCall("tv_volume", braviaJson("level" to command.level))
        is BraviaCommand.Mute -> BraviaToolCall("tv_mute", braviaJson("enabled" to command.enabled))
        is BraviaCommand.Input -> BraviaToolCall("tv_input", braviaJson("port" to command.port))
        is BraviaCommand.Channel -> BraviaToolCall("tv_channel", braviaJson("preset" to command.preset, "band" to (command.band ?: "current")))
        is BraviaCommand.Key -> BraviaToolCall("tv_key", braviaJson("key" to command.key.name.lowercase()))
        is BraviaCommand.LaunchApp -> BraviaToolCall("tv_launch_app", braviaJson("name" to command.name))
    }

    fun command(call: BraviaToolCall): BraviaCommand {
        val d = definitions.singleOrNull { it.name == call.name } ?: throw IllegalArgumentException("不明なテレビ操作です。")
        require(call.arguments.keySet().all { d.fields.has(it) } && d.required.all { call.arguments.has(it) }) { "テレビ操作の引数を確認してください。" }
        fun integer(key: String, range: IntRange): Int {
            val p = call.arguments.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            require(p?.isNumber == true) { "$key は整数で指定してください。" }
            val decimal = p!!.asBigDecimal
            require(decimal.stripTrailingZeros().scale() <= 0 && decimal >= range.first.toBigDecimal() && decimal <= range.last.toBigDecimal()) { "$key の指定範囲を確認してください。" }
            return decimal.toInt()
        }
        fun boolean(key: String): Boolean {
            val p = call.arguments.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            require(p?.isBoolean == true) { "$key はtrueかfalseで指定してください。" }
            return p!!.asBoolean
        }
        fun string(key: String): String {
            val p = call.arguments.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            require(p?.isString == true) { "$key は文字列で指定してください。" }
            return p!!.asString
        }
        return when (call.name) {
            "tv_status" -> BraviaCommand.Status
            "tv_power" -> BraviaCommand.Power(boolean("on"))
            "tv_volume" -> BraviaCommand.Volume(integer("level", 0..100))
            "tv_mute" -> BraviaCommand.Mute(boolean("enabled"))
            "tv_input" -> BraviaCommand.Input(integer("port", 1..4))
            "tv_channel" -> {
                val band = if (call.arguments.has("band")) string("band") else "current"
                require(band in listOf("current", "地デジ", "BS", "CS"))
                BraviaCommand.Channel(integer("preset", 1..12), band.takeUnless { it == "current" })
            }
            "tv_key" -> BraviaCommand.Key(BraviaRemoteKey.entries.singleOrNull { it.name.lowercase() == string("key") }
                ?: throw IllegalArgumentException("対応していないリモコンキーです。"))
            "tv_launch_app" -> BraviaCommand.LaunchApp(string("name").also { require(it.isNotBlank() && it.length <= 40) })
            else -> throw IllegalArgumentException("不明なテレビ操作です。")
        }
    }
}
