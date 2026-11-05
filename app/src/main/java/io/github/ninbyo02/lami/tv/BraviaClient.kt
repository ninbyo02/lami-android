package io.github.ninbyo02.lami.tv

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal val braviaGson = Gson()
internal fun braviaJson(vararg fields: Pair<String, Any?>): JsonObject = JsonObject().apply {
    fields.forEach { (name, value) -> add(name, braviaGson.toJsonTree(value)) }
}
internal fun JsonObject.text(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

/** The user's configured LAN address, never an arbitrary URL supplied by a language model. */
class BraviaAddress private constructor(val host: String, internal val baseUrl: HttpUrl) {
    companion object {
        fun parse(value: String): BraviaAddress {
            val host = value.trim()
            require(isPrivateIpv4(host)) { "テレビのIPv4アドレスを入力してください。" }
            return BraviaAddress(host, "http://$host/".toHttpUrl())
        }

        internal fun isPrivateIpv4(host: String): Boolean {
            if (!host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return false
            val p = host.split('.').map { it.toInt() }
            if (p.any { it !in 0..255 } || host.split('.').any { it.length > 1 && it[0] == '0' }) return false
            return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
        }

        internal fun forTest(baseUrl: HttpUrl) = BraviaAddress(baseUrl.host, baseUrl)
    }
}

open class BraviaException(message: String) : IOException(message)
class BraviaAuthenticationRequired : BraviaException("テレビの認証が必要です。設定の「テレビ操作」から登録してください。")
class BraviaApiException(val code: Int) : BraviaException("テレビがこの操作を受け付けませんでした（コード $code）。")
class BraviaUnavailable : BraviaException("テレビに接続できません。電源と同じネットワークへの接続を確認してください。")
class BraviaUnsupported(message: String) : BraviaException(message)

data class BraviaPairingReply(val challengeRequired: Boolean, val cookie: String?) {
    override fun toString() = "BraviaPairingReply(challengeRequired=$challengeRequired, cookie=<redacted>)"
}

data class BraviaDeviceInfo(val model: String, val name: String, val macAddress: String?)
data class BraviaVolume(val value: Int, val minimum: Int, val maximum: Int, val muted: Boolean)
data class BraviaApplication(val name: String, val uri: String)

/** Blocking wire client; Android callers run it on Dispatchers.IO. No mutation is retried. */
class BraviaClient(
    private val address: BraviaAddress,
    private val cookie: String? = null,
    private val http: OkHttpClient = defaultHttp,
) {
    private val ids = AtomicInteger()
    private var remoteKeys: Map<String, String>? = null

    companion object {
        private val defaultHttp = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        private val jsonType = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_BYTES = 1024L * 1024L
    }

    private data class WireReply(val status: Int, val body: String, val cookies: List<String>)

    private fun post(path: String, body: String, contentType: String? = null, authorization: String? = null, soap: Boolean = false): WireReply {
        val request = Request.Builder()
            .url(address.baseUrl.newBuilder().encodedPath(path).build())
            .post(body.toRequestBody(contentType?.toMediaType() ?: jsonType))
            .header("Cache-Control", "no-cache")
            .apply {
                cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                authorization?.let { header("Authorization", it) }
                if (soap) header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            }.build()
        try {
            http.newCall(request).execute().use { response ->
                val responseBody = response.body ?: throw BraviaException("テレビから応答本文がありません。")
                val source = responseBody.source()
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw BraviaException("テレビの応答が大きすぎます。")
                return WireReply(response.code, responseBody.string(), response.headers.values("Set-Cookie"))
            }
        } catch (error: BraviaException) {
            throw error
        } catch (_: IOException) {
            throw BraviaUnavailable()
        }
    }

    private fun decode(reply: WireReply): JsonObject {
        if (reply.status == 401 || reply.status == 403) throw BraviaAuthenticationRequired()
        if (reply.status !in 200..299) throw BraviaException("テレビとの通信に失敗しました（HTTP ${reply.status}）。")
        val data = try { braviaGson.fromJson(reply.body, JsonObject::class.java) } catch (_: RuntimeException) {
            throw BraviaException("テレビの応答形式を確認できませんでした。")
        } ?: throw BraviaException("テレビから空の応答を受信しました。")
        val error = data.get("error")
        if (error != null && !error.isJsonNull) {
            if (!error.isJsonArray || error.asJsonArray.size() == 0) throw BraviaException("テレビの応答形式を確認できませんでした。")
            val code = try { error.asJsonArray[0].asInt } catch (_: RuntimeException) {
                throw BraviaException("テレビの応答形式を確認できませんでした。")
            }
            if (code == 401 || code == 403) throw BraviaAuthenticationRequired()
            throw BraviaApiException(code)
        }
        if (data.get("result")?.isJsonArray != true) throw BraviaException("テレビの操作結果を確認できませんでした。")
        return data
    }

    internal fun rpc(service: String, method: String, params: JsonArray = JsonArray(), version: String = "1.0"): JsonArray {
        require(service in setOf("system", "audio", "avContent", "appControl"))
        val request = braviaJson("method" to method, "params" to params, "id" to ids.incrementAndGet(), "version" to version)
        return decode(post("/sony/$service", request.toString())).getAsJsonArray("result")
    }

    fun pair(clientId: String, pin: String? = null): BraviaPairingReply {
        require(clientId.matches(Regex("LAMI:[a-f0-9-]{36}")))
        require(pin == null || pin.matches(Regex("[0-9]{4}"))) { "テレビ画面の4桁の番号を入力してください。" }
        val params = JsonArray().apply {
            add(braviaJson("clientid" to clientId, "nickname" to "LAMI", "level" to "private"))
            add(JsonArray().apply { add(braviaJson("value" to "yes", "function" to "WOL")) })
        }
        val request = braviaJson("method" to "actRegister", "params" to params, "id" to ids.incrementAndGet(), "version" to "1.0")
        val response = post("/sony/accessControl", request.toString(), authorization = pin?.let { Credentials.basic("", it) })
        if (pin == null && response.status == 401) return BraviaPairingReply(true, null)
        decode(response)
        // Some BRAVIAs set an invalid expires=0000 date. Only cookie name/value pairs are
        // retained, scoped to this fixed address; PIN and response headers are never logged.
        val pairs = response.cookies.map { it.substringBefore(';').trim() }
            .filter { it.matches(Regex("[A-Za-z0-9_-]+=[^;\\r\\n]+")) }
        return BraviaPairingReply(false, pairs.joinToString("; ").ifBlank { null })
    }

    fun deviceInfo(): BraviaDeviceInfo {
        val info = rpc("system", "getSystemInformation")[0].asJsonObject
        return BraviaDeviceInfo(info.text("model").orEmpty(), info.text("name").orEmpty(), info.text("macAddr"))
    }

    fun powerStatus(): Boolean = when (rpc("system", "getPowerStatus")[0].asJsonObject.text("status")) {
        "active" -> true
        "standby" -> false
        else -> throw BraviaException("テレビの電源状態を確認できませんでした。")
    }

    fun setPower(on: Boolean) {
        rpc("system", "setPowerStatus", JsonArray().apply { add(braviaJson("status" to on)) })
    }

    fun volume(): BraviaVolume {
        val values = rpc("audio", "getVolumeInformation")[0].asJsonArray
        val item = values.map { it.asJsonObject }.firstOrNull { it.text("target") == "speaker" }
            ?: throw BraviaUnsupported("テレビのスピーカー音量を取得できません。")
        return BraviaVolume(item.get("volume").asInt, item.get("minVolume")?.asInt ?: 0,
            item.get("maxVolume")?.asInt ?: 100, item.get("mute")?.asBoolean ?: false)
    }

    fun setVolume(level: Int) {
        val range = volume()
        require(level in range.minimum..range.maximum) { "音量は${range.minimum}〜${range.maximum}で指定してください。" }
        rpc("audio", "setAudioVolume", JsonArray().apply { add(braviaJson("target" to "speaker", "volume" to level.toString())) })
    }

    fun setMute(muted: Boolean) {
        rpc("audio", "setAudioMute", JsonArray().apply { add(braviaJson("status" to muted)) })
    }

    fun keys(): Set<String> {
        ensureRemoteKeys()
        return remoteKeys!!.keys
    }

    private fun ensureRemoteKeys() {
        if (remoteKeys != null) return
        val result = rpc("system", "getRemoteControllerInfo")
        if (result.size() < 2 || !result[1].isJsonArray) throw BraviaUnsupported("リモコンの操作一覧を取得できません。")
        remoteKeys = result[1].asJsonArray.mapNotNull {
            val o = it.asJsonObject
            val name = o.text("name") ?: return@mapNotNull null
            val value = o.text("value") ?: return@mapNotNull null
            if (value.length !in 4..256 || !value.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) return@mapNotNull null
            name to value
        }.toMap()
    }

    internal fun sendKnownKey(vararg names: String) {
        ensureRemoteKeys()
        val key = names.firstNotNullOfOrNull { requested -> remoteKeys!!.entries.firstOrNull { it.key.equals(requested, true) } }
            ?: throw BraviaUnsupported("このテレビには指定したリモコン操作がありません。")
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>${key.value}</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
        val response = post("/sony/ircc", body, "text/xml; charset=utf-8", soap = true)
        if (response.status == 401 || response.status == 403) throw BraviaAuthenticationRequired()
        if (response.status !in 200..299 || Regex("<(?:[A-Za-z0-9_]+:)?Fault(?:\\s|>)").containsMatchIn(response.body)) {
            throw BraviaException("テレビがリモコン操作を受け付けませんでした。")
        }
    }

    fun inputUri(): String? = rpc("avContent", "getPlayingContentInfo").firstOrNull()?.asJsonObject?.text("uri")

    fun applications(): List<BraviaApplication> = rpc("appControl", "getApplicationList")[0].asJsonArray.mapNotNull {
        val item = it.asJsonObject
        val name = item.text("title") ?: return@mapNotNull null
        val uri = item.text("uri") ?: return@mapNotNull null
        BraviaApplication(name, uri)
    }

    fun launchApplication(name: String): String {
        val candidates = applications().filter { normalizeAppName(it.name) == normalizeAppName(name) }
        val app = candidates.singleOrNull() ?: throw BraviaUnsupported("テレビのアプリ一覧から「$name」を一意に選べません。設定のアプリ一覧で名前を確認してください。")
        rpc("appControl", "setActiveApp", JsonArray().apply { add(braviaJson("uri" to app.uri)) })
        return app.name
    }

    internal fun optionalVolume(): BraviaVolume? = try { volume() } catch (_: BraviaApiException) { null } catch (_: BraviaUnsupported) { null }

    internal fun optionalInputUri(): String? = try { inputUri() } catch (_: BraviaApiException) { null }

    private fun normalizeAppName(name: String): String = when (val n = name.lowercase().replace(Regex("[\\s・_-]"), "")) {
        "ユーチューブ" -> "youtube"
        "ネットフリックス" -> "netflix"
        else -> n
    }
}
