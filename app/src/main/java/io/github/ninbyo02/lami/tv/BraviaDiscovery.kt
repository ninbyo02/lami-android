package io.github.ninbyo02.lami.tv

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class BraviaDiscoveredDevice(val address: String, val model: String, val name: String)

object BraviaDiscovery {
    suspend fun find(context: Context): List<BraviaDiscoveredDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("lami-bravia-discovery")?.apply { setReferenceCounted(false); acquire() }
        try {
            val locations = linkedSetOf<String>()
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val st = "urn:schemas-sony-com:service:ScalarWebAPI:1"
                val body = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n".toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(body, body.size, InetAddress.getByName("239.255.255.250"), 1900))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                while (System.nanoTime() < deadline) {
                    val data = ByteArray(16 * 1024)
                    val packet = DatagramPacket(data, data.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val headers = String(packet.data, 0, packet.length, Charsets.UTF_8).lineSequence()
                        .filter { ':' in it }.map { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }.toMap()
                    if (!headers["st"].orEmpty().contains("schemas-sony-com", true)) continue
                    headers["location"]?.let { locations.add(it) }
                }
            }
            val http = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false)
                .proxy(java.net.Proxy.NO_PROXY).callTimeout(4, TimeUnit.SECONDS).build()
            locations.take(8).mapNotNull { location ->
                try {
                    val url = location.toHttpUrl()
                    if (url.scheme != "http" || !BraviaAddress.isPrivateIpv4(url.host) || url.username.isNotEmpty() || url.password.isNotEmpty()) return@mapNotNull null
                    val xml = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@mapNotNull null
                        val source = response.body?.source() ?: return@mapNotNull null
                        source.request(128L * 1024 + 1)
                        if (source.buffer.size > 128L * 1024) return@mapNotNull null
                        source.readByteArray()
                    }
                    val factory = DocumentBuilderFactory.newInstance().apply {
                        isNamespaceAware = true
                        isExpandEntityReferences = false
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        setFeature("http://xml.org/sax/features/external-general-entities", false)
                        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    }
                    val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
                    fun text(name: String) = doc.getElementsByTagNameNS("*", name).item(0)?.textContent.orEmpty()
                    if (!text("manufacturer").contains("Sony", true) || text("modelName").isBlank()) return@mapNotNull null
                    BraviaDiscoveredDevice(url.host, text("modelName"), text("friendlyName"))
                } catch (_: Exception) { null }
            }.distinctBy { it.address }
        } finally { if (lock?.isHeld == true) lock.release() }
    }
}
