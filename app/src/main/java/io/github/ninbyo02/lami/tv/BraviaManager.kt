package io.github.ninbyo02.lami.tv

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.IOException
import java.util.UUID

data class BraviaConnectionSummary(val address: String = "", val model: String = "", val paired: Boolean = false, val enabled: Boolean = false)
data class BraviaInspection(val summary: BraviaConnectionSummary, val keys: List<String>, val apps: List<String>)

/** Serializes UI and tool requests; model output alone never triggers pairing or a retry. */
class BraviaManager internal constructor(private val store: BraviaSessionStore) {
    private val mutex = Mutex()
    private var pending: BraviaSession? = null

    companion object {
        @Volatile private var instance: BraviaManager? = null
        fun get(context: Context): BraviaManager = instance ?: synchronized(this) {
            instance ?: BraviaManager(AndroidBraviaSessionStore(context.applicationContext)).also { instance = it }
        }
    }

    suspend fun summary(): BraviaConnectionSummary = withContext(Dispatchers.IO) {
        mutex.withLock { store.load()?.summary() ?: BraviaConnectionSummary() }
    }

    suspend fun beginPairing(address: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val endpoint = BraviaAddress.parse(address)
            val next = pending?.takeIf { it.address == endpoint.host }
                ?: BraviaSession(endpoint.host, "LAMI:${UUID.randomUUID()}").also { pending = it }
            val result = BraviaClient(endpoint).pair(next.clientId)
            if (result.challengeRequired) return@withLock false
            saveVerifiedSession(next.copy(cookie = result.cookie))
            pending = null
            true
        }
    }

    suspend fun completePairing(pin: String): BraviaConnectionSummary = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = pending ?: throw BraviaException("「テレビに認証番号を表示」からやり直してください。")
            val result = BraviaClient(BraviaAddress.parse(next.address)).pair(next.clientId, pin)
            val saved = saveVerifiedSession(next.copy(cookie = result.cookie))
            pending = null
            saved.summary()
        }
    }

    private fun saveVerifiedSession(session: BraviaSession): BraviaSession {
        val info = client(session).deviceInfo()
        require(info.model.isNotBlank()) { "テレビの機種情報を確認できませんでした。" }
        val verified = session.copy(model = info.model, macAddress = info.macAddress)
        store.save(verified)
        return verified
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock { store.load()?.let { store.save(it.copy(enabled = enabled)) } }
    }

    suspend fun forget() = withContext(Dispatchers.IO) { mutex.withLock { store.clear(); pending = null } }

    suspend fun inspect(): BraviaInspection = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = store.load() ?: throw BraviaAuthenticationRequired()
            val client = client(session)
            val info = client.deviceInfo()
            val apps = try { client.applications().map { it.name } } catch (_: BraviaApiException) { emptyList() }
            BraviaInspection(session.copy(model = info.model).summary(), client.keys().sorted(), apps.sorted())
        }
    }

    suspend fun execute(command: BraviaCommand): BraviaToolResult = execute(BraviaToolCatalog.call(command))

    suspend fun execute(call: BraviaToolCall): BraviaToolResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val command = BraviaToolCatalog.command(call)
                val session = store.load() ?: return@withLock BraviaToolResult(false, "設定の「テレビ操作」からテレビを登録してください。")
                if (!session.enabled) return@withLock BraviaToolResult(false, "設定の「テレビ操作」で、チャットからの操作を有効にしてください。")
                val client = client(session)
                try {
                    BraviaControlTool(client).execute(command)
                } catch (error: BraviaUnavailable) {
                    // A standby TV may stop its HTTP server. Only explicit power-on can
                    // send a targeted WoL packet; no other command is replayed or retried.
                    if (command != BraviaCommand.Power(true) || !sendWakeOnLan(session.macAddress)) throw error
                    BraviaToolResult(true, "テレビへネットワーク起動の要求を送りました。電源状態はまだ確認できていません。")
                }
            } catch (error: CancellationException) { throw error
            } catch (error: BraviaException) { BraviaToolResult(false, error.message ?: "テレビの操作結果を確認できませんでした。")
            } catch (error: IllegalArgumentException) { BraviaToolResult(false, error.message ?: "テレビ操作の指定を確認してください。") }
        }
    }

    private fun client(session: BraviaSession) = BraviaClient(BraviaAddress.parse(session.address), session.cookie)
    private fun BraviaSession.summary() = BraviaConnectionSummary(address, model, true, enabled)

    private fun sendWakeOnLan(mac: String?): Boolean {
        if (mac == null || !mac.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"))) return false
        val bytes = mac.split(':').map { it.toInt(16).toByte() }.toByteArray()
        val packet = ByteArray(6) { 0xff.toByte() } + ByteArray(16 * 6) { bytes[it % 6] }
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), 9))
            }
            true
        } catch (_: IOException) { false }
    }
}
