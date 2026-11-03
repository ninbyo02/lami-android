package io.github.ninbyo02.lami.tools.bravia

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

internal data class DiscoveredBravia(val model: String, val name: String, val address: String)

internal object BraviaDiscovery {
    /** SSDP M-SEARCH for Sony's service only. Device descriptions do not require pairing. */
    fun discover(): List<DiscoveredBravia> {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 400
            val query = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                "ST: urn:schemas-sony-com:service:ScalarWebAPI:1\r\n\r\n").toByteArray()
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName("239.255.255.250"), 1900))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (System.nanoTime() < deadline && locations.size < 8) {
                val packet = DatagramPacket(ByteArray(8192), 8192)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                val headers = String(packet.data, 0, packet.length).lineSequence().mapNotNull { line ->
                    val parts = line.split(':', limit = 2)
                    if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
                }.toMap()
                if (headers["st"] != "urn:schemas-sony-com:service:ScalarWebAPI:1") continue
                headers["location"]?.let { locations.add(it) }
            }
        }
        val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        return locations.mapNotNull { location ->
            runCatching {
                val url = location.toHttpUrlOrNull() ?: return@runCatching null
                val endpoint = BraviaEndpoint.parse(url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString())
                val request = Request.Builder().url(location).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val data = response.body?.byteStream()?.use { it.readNBytes(262145) } ?: return@use null
                    if (data.size > 262144) return@use null
                    // The service's control endpoint is HTTP/80; its description uses a separate port.
                    parseDescription(data, endpoint.url.host)
                }
            }.getOrNull()
        }.distinctBy { it.address }
    }

    internal fun parseDescription(data: ByteArray, address: String): DiscoveredBravia? = runCatching {
        if (data.size > 262144) return@runCatching null
        val xml = data.toString(Charsets.UTF_8)
        if (xml.contains("<!DOCTYPE", ignoreCase = true) ||
            xml.contains("<!ENTITY", ignoreCase = true) || xml.contains('\u0000')) return@runCatching null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        // Parse the same UTF-8 characters that were checked above; alternate encodings cannot bypass the checks.
        val document = factory.newDocumentBuilder().apply { setErrorHandler(DefaultHandler()) }
            .parse(InputSource(StringReader(xml)))
        fun text(tag: String): String = document.getElementsByTagNameNS("*", tag).item(0)?.textContent.orEmpty()
        if (!text("manufacturer").contains("Sony", ignoreCase = true)) return@runCatching null
        DiscoveredBravia(text("modelName"), text("friendlyName"), address)
    }.getOrNull()
}
