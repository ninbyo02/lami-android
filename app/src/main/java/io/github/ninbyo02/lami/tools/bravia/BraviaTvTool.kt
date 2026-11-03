package io.github.ninbyo02.lami.tools.bravia

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

internal data class BraviaConfiguration(
    val address: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val paired: Boolean = false,
)

/**
 * Credentials remain in app-private storage (application backup is disabled).
 * The short-lived PIN is never stored. A cookie is always bound to one selected endpoint.
 */
internal class BraviaTvRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("bravia_tv", Context.MODE_PRIVATE)

    fun configuration(): BraviaConfiguration = BraviaConfiguration(
        preferences.getString("address", "").orEmpty(),
        preferences.getString("model", "").orEmpty(),
        preferences.getBoolean("enabled", false),
        !preferences.getString("cookie", "").isNullOrBlank(),
    )

    private fun clientId(): String {
        preferences.getString("client_id", null)?.let { return it }
        val id = "LAMI:" + UUID.randomUUID()
        check(preferences.edit().putString("client_id", id).commit())
        return id
    }

    private fun selectAddress(address: String): BraviaEndpoint {
        val endpoint = BraviaEndpoint.parse(address)
        if (endpoint.address != configuration().address) {
            check(preferences.edit().putString("address", endpoint.address)
                .remove("cookie").remove("mac").remove("model").putBoolean("enabled", false).commit())
        }
        return endpoint
    }

    private fun protocol(endpoint: BraviaEndpoint): BraviaProtocol =
        BraviaProtocol(endpoint, preferences.getString("cookie", "").orEmpty()) { cookie ->
            if (configuration().address != endpoint.address) {
                throw BraviaException("接続先が変更されました。もう一度登録してください。")
            }
            check(preferences.edit().putString("cookie", cookie).commit())
        }

    /** Returns true when the TV needs the PIN displayed on its screen. */
    suspend fun beginPairing(address: String): Boolean = withContext(Dispatchers.IO) {
        val endpoint = selectAddress(address)
        val client = protocol(endpoint)
        if (configuration().paired) {
            try {
                rememberTv(client.information())
                return@withContext false
            } catch (_: BraviaAuthenticationRequired) {
                preferences.edit().remove("cookie").putBoolean("enabled", false).commit()
            }
        }
        if (client.beginPairing(clientId())) {
            rememberTv(client.information())
            false
        } else true
    }

    suspend fun completePairing(address: String, pin: String): BraviaConfiguration = withContext(Dispatchers.IO) {
        val endpoint = BraviaEndpoint.parse(address)
        if (configuration().address != endpoint.address) throw BraviaException("接続先が変わりました。登録をやり直してください。")
        val client = protocol(endpoint)
        rememberTv(client.completePairing(clientId(), pin))
        configuration()
    }

    private fun rememberTv(info: BraviaTvInfo) {
        if (info.model.isBlank()) throw BraviaException("テレビの機種情報を確認できませんでした。")
        check(preferences.edit().putString("model", info.model).putString("mac", info.mac)
            .putBoolean("enabled", true).commit())
    }

    fun setEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("enabled", enabled && configuration().paired).commit())
    }

    fun forget() {
        check(preferences.edit().remove("cookie").remove("mac").remove("model")
            .putBoolean("enabled", false).commit())
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        val config = configuration()
        if (!config.paired) throw BraviaAuthenticationRequired()
        describeStatus(protocol(BraviaEndpoint.parse(config.address)))
    }

    suspend fun execute(command: BraviaCommand): String = executionMutex.withLock {
        withContext(Dispatchers.IO) {
            val config = configuration()
            if (!config.paired || !config.enabled) {
                return@withContext "設定の「テレビ操作」でテレビを登録し、チャットからの操作を有効にしてください。"
            }
            val client = protocol(BraviaEndpoint.parse(config.address))
            try {
                when (command) {
                    is BraviaCommand.Power -> {
                        try {
                            client.setPower(command.on)
                            "テレビに電源" + (if (command.on) "オン" else "オフ") + "の操作を送りました。"
                        } catch (error: BraviaException) {
                            throw error
                        } catch (error: IOException) {
                            if (!command.on) throw error
                            val mac = preferences.getString("mac", "").orEmpty()
                            sendWakeOnLan(mac)
                            "テレビに起動信号を送りました。起動を確認できない場合は、テレビのリモートスタート設定を確認してください。"
                        }
                    }
                    is BraviaCommand.Volume -> {
                        client.setVolume(command.level)
                        "テレビに音量" + command.level + "の設定を送りました。"
                    }
                    is BraviaCommand.Mute -> {
                        client.setMuted(command.muted)
                        "テレビに" + (if (command.muted) "消音" else "消音解除") + "の設定を送りました。"
                    }
                    is BraviaCommand.Hdmi -> {
                        client.selectHdmi(command.port)
                        "テレビにHDMI " + command.port + "への切り替えを送りました。"
                    }
                    is BraviaCommand.Channel -> {
                        client.selectChannelPreset(command.preset)
                        "テレビにリモコンの" + command.preset + "番の選局を送りました。"
                    }
                    is BraviaCommand.Key -> {
                        client.pressKey(command.name)
                        "テレビに「" + command.label + "」の操作を送りました。"
                    }
                    BraviaCommand.Status -> describeStatus(client)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: BraviaException) {
                error.message ?: "テレビの操作を完了できませんでした。"
            } catch (_: IOException) {
                "テレビから応答を確認できませんでした。接続や電源状態を確認してください。操作は自動再送していません。"
            } catch (_: Exception) {
                "テレビの応答を読み取れませんでした。状態を確認してから、もう一度操作してください。"
            }
        }
    }

    companion object {
        private val executionMutex = Mutex()

        private fun describeStatus(client: BraviaProtocol): String {
            if (!client.isPoweredOn()) return "テレビは待機中です。"
            val volume = client.volume()
            return "テレビは電源オン、音量は" + volume.value + "です。" +
                if (volume.muted) "消音中です。" else ""
        }

        private fun sendWakeOnLan(mac: String) {
            val normalized = mac.replace(":", "").replace("-", "")
            if (!Regex("[0-9a-fA-F]{12}").matches(normalized)) {
                throw BraviaException("テレビの起動情報がありません。テレビをつけて再登録してください。")
            }
            val bytes = normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val packet = ByteArray(102) { index -> if (index < 6) 0xff.toByte() else bytes[(index - 6) % 6] }
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) { socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), 9)) }
            }
        }
    }
}
