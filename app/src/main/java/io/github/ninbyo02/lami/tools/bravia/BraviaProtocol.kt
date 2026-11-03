package io.github.ninbyo02.lami.tools.bravia

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Cookie
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The TV speaks plain HTTP; credentials must stay on the selected local IPv4 endpoint. */
internal data class BraviaEndpoint(val url: HttpUrl) {
    val address: String get() = url.host + if (url.port == 80) "" else ":" + url.port

    companion object {
        fun parse(address: String): BraviaEndpoint {
            val raw = address.trim()
            val url = (if (raw.contains("://")) raw else "http://" + raw).toHttpUrlOrNull()
                ?: throw BraviaException("テレビのIPv4アドレスを入力してください。")
            val octets = url.host.split('.').mapNotNull { it.toIntOrNull() }
            val local = octets.size == 4 && octets.all { it in 0..255 } &&
                (octets[0] == 10 || octets[0] == 127 ||
                    (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 172 && octets[1] in 16..31))
            if (!local || url.scheme != "http" || url.username.isNotEmpty() ||
                url.password.isNotEmpty() || url.encodedPath != "/" ||
                url.query != null || url.fragment != null) {
                throw BraviaException("家庭内LANのテレビのIPv4アドレスを指定してください。")
            }
            return BraviaEndpoint(url)
        }
    }
}

internal open class BraviaException(message: String) : IOException(message)
internal class BraviaAuthenticationRequired :
    BraviaException("テレビとの認証が必要です。設定の「テレビ操作」から登録してください。")

internal data class BraviaTvInfo(val model: String, val name: String, val mac: String)
internal data class BraviaVolume(val value: Int, val minimum: Int, val maximum: Int, val muted: Boolean)

internal interface BraviaTransport {
    fun post(url: HttpUrl, body: String, headers: Map<String, String>): BraviaHttpResponse
}

internal data class BraviaHttpResponse(
    val status: Int,
    val body: String,
    val setCookies: List<String> = emptyList(),
)

internal class BraviaHttpTransport : BraviaTransport {
    // A lost response must never cause an automatic replay of a key press or volume change.
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun post(url: HttpUrl, body: String, headers: Map<String, String>): BraviaHttpResponse {
        val contentType = headers["Content-Type"] ?: "application/json"
        val request = Request.Builder().url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        return http.newCall(request).execute().use { response ->
            val responseBody = response.body
            if ((responseBody?.contentLength() ?: 0L) > MAX_RESPONSE_BYTES) {
                throw BraviaException("テレビの応答が大きすぎます。")
            }
            val bytes = responseBody?.byteStream()?.use { it.readNBytes((MAX_RESPONSE_BYTES + 1).toInt()) }
                ?: byteArrayOf()
            if (bytes.size > MAX_RESPONSE_BYTES) throw BraviaException("テレビの応答が大きすぎます。")
            BraviaHttpResponse(response.code, bytes.toString(Charsets.UTF_8), response.headers("Set-Cookie"))
        }
    }

    companion object { private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024 }
}

/** Pure JVM protocol client. It does not log PINs, cookie headers, or TV responses. */
internal class BraviaProtocol(
    private val endpoint: BraviaEndpoint,
    cookie: String = "",
    private val transport: BraviaTransport = BraviaHttpTransport(),
    private val saveCookie: (String) -> Unit = {},
) {
    private var sessionCookie = cookie
    private var remoteKeys: Map<String, String>? = null

    fun beginPairing(clientId: String): Boolean {
        // Sony's normal pairing trigger, also used by pybravia. This is not a PIN search.
        return try {
            register(clientId, "0000")
            true
        } catch (_: BraviaAuthenticationRequired) {
            false
        }
    }

    fun completePairing(clientId: String, pin: String): BraviaTvInfo {
        if (!Regex("[0-9]{4}").matches(pin)) throw BraviaException("テレビに表示された4桁の番号を入力してください。")
        register(clientId, pin)
        return information()
    }

    private fun register(clientId: String, pin: String) {
        val identity = JsonObject().apply {
            addProperty("clientid", clientId)
            addProperty("nickname", "LAMI")
            addProperty("level", "private")
        }
        val wol = JsonArray().apply {
            add(JsonObject().apply { addProperty("value", "yes"); addProperty("function", "WOL") })
        }
        rpc("accessControl", "actRegister", JsonArray().apply { add(identity); add(wol) },
            mapOf("Authorization" to Credentials.basic("", pin)))
    }

    fun information(): BraviaTvInfo {
        val value = rpc("system", "getSystemInformation")[0].asJsonObject
        return BraviaTvInfo(value.text("model"), value.text("name"), value.text("macAddr"))
    }

    fun isPoweredOn(): Boolean = rpc("system", "getPowerStatus")[0].asJsonObject.text("status") == "active"

    fun setPower(on: Boolean) {
        rpc("system", "setPowerStatus", params("status" to on))
    }

    fun volume(): BraviaVolume {
        val entries = rpc("audio", "getVolumeInformation")[0].asJsonArray
        val value = entries.map { it.asJsonObject }.firstOrNull { it.text("target") == "speaker" }
            ?: throw BraviaException("テレビのスピーカー音量を取得できませんでした。")
        return BraviaVolume(value.number("volume"), value.number("minVolume"), value.number("maxVolume"),
            value.get("mute")?.asBoolean ?: false)
    }

    fun setVolume(level: Int) {
        val range = volume()
        if (level !in range.minimum..range.maximum) {
            throw BraviaException("音量は" + range.minimum + "〜" + range.maximum + "で指定してください。")
        }
        rpc("audio", "setAudioVolume", params("target" to "speaker", "volume" to level.toString()))
    }

    fun setMuted(muted: Boolean) { rpc("audio", "setAudioMute", params("status" to muted)) }

    fun selectHdmi(port: Int) {
        if (port !in 1..4) throw BraviaException("HDMIは1〜4で指定してください。")
        // Use the URI actually advertised by the TV, not an arbitrary URL supplied by a model.
        val values = rpc("avContent", "getCurrentExternalInputsStatus")[0].asJsonArray
        val uri = values.map { it.asJsonObject.text("uri") }.firstOrNull {
            it == "extInput:hdmi?port=" + port || it.startsWith("extInput:hdmi?port=" + port + "&")
        }
        if (uri != null) rpc("avContent", "setPlayContent", params("uri" to uri))
        else pressKey("Hdmi" + port)
    }

    fun pressKey(name: String) {
        if (name !in ALLOWED_KEYS) throw BraviaException("このリモコン操作には対応していません。")
        val code = keys()[name] ?: throw BraviaException("テレビがこの操作に対応していません：" + name)
        // Codes are read from the selected TV. Do not accept arbitrary SOAP or remote-control codes.
        if (!Regex("[A-Za-z0-9+/=]+").matches(code)) throw BraviaException("テレビの操作コードが不正です。")
        val xml = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>" +
            code + "</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"
        val headers = authenticationHeaders() + mapOf(
            "Content-Type" to "text/xml; charset=UTF-8",
            "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"",
        )
        val response = transport.post(endpoint.url.resolve("/sony/ircc")!!, xml, headers)
        checkHttp(response)
        if (response.body.contains(":Fault", ignoreCase = true) || response.body.contains("<Fault")) {
            throw BraviaException("テレビがリモコン操作を受け付けませんでした。")
        }
    }

    fun selectChannelPreset(number: Int) {
        if (number !in 1..12) throw BraviaException("チャンネルはリモコンの1〜12で指定してください。")
        val name = if (number == 10 && "Num10" !in keys()) "Num0" else "Num" + number
        pressKey(name)
    }

    fun keys(): Map<String, String> {
        remoteKeys?.let { return it }
        val result = rpc("system", "getRemoteControllerInfo")
        val keys = result[1].asJsonArray.associate {
            val item = it.asJsonObject
            item.text("name") to item.text("value")
        }
        remoteKeys = keys
        return keys
    }

    private fun authenticationHeaders(): Map<String, String> =
        if (sessionCookie.isBlank()) emptyMap() else mapOf("Cookie" to sessionCookie)

    private fun rpc(
        service: String,
        method: String,
        parameters: JsonArray = JsonArray(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonArray {
        val url = endpoint.url.resolve("/sony/" + service)!!
        val request = JsonObject().apply {
            addProperty("method", method)
            addProperty("id", 1)
            addProperty("version", "1.0")
            add("params", parameters)
        }
        val headers = (if (service == "accessControl") emptyMap() else authenticationHeaders()) + extraHeaders
        val response = transport.post(url, request.toString(), headers)
        checkHttp(response)
        val result = try { JsonParser.parseString(response.body).asJsonObject }
        catch (_: Exception) { throw BraviaException("テレビから正しい応答を取得できませんでした。") }
        result.getAsJsonArray("error")?.let { error ->
            val code = error.firstOrNull()?.let { runCatching { it.asInt }.getOrNull() }
            if (code == 401 || code == 403) throw BraviaAuthenticationRequired()
            throw BraviaException("テレビが操作に対応していないか、現在実行できません（コード " + code + "）。")
        }
        val array = result.getAsJsonArray("result") ?: throw BraviaException("テレビの応答に結果がありません。")
        if (service == "accessControl") {
            val cookies = response.setCookies.mapNotNull { Cookie.parse(url, it) }
                .filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
            if (cookies.isEmpty()) throw BraviaException("テレビの認証情報を取得できませんでした。")
            sessionCookie = cookies.joinToString("; ") { it.name + "=" + it.value }
            saveCookie(sessionCookie)
        }
        return array
    }

    private fun checkHttp(response: BraviaHttpResponse) {
        if (response.status == 401 || response.status == 403) throw BraviaAuthenticationRequired()
        if (response.status !in 200..299) throw BraviaException("テレビとの通信に失敗しました（HTTP " + response.status + "）。")
    }

    companion object {
        val ALLOWED_KEYS = setOf(
            "Home", "Back", "Up", "Down", "Left", "Right", "Confirm", "Options", "Tv", "TvPower",
            "VolumeUp", "VolumeDown", "ChannelUp", "ChannelDown", "Play", "Pause", "Stop",
            "Next", "Prev", "Forward", "Rewind", "Hdmi1", "Hdmi2", "Hdmi3", "Hdmi4",
        ) + (0..12).map { "Num" + it }

        private fun params(vararg values: Pair<String, Any>): JsonArray = JsonArray().apply {
            add(JsonObject().apply {
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> addProperty(key, value)
                        is Number -> addProperty(key, value)
                        else -> addProperty(key, value.toString())
                    }
                }
            })
        }
    }
}

private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.number(key: String): Int =
    get(key)?.let { runCatching { it.asInt }.getOrNull() } ?: throw BraviaException("音量情報を取得できませんでした。")
