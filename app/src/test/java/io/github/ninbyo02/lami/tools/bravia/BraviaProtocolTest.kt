package io.github.ninbyo02.lami.tools.bravia

import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class BraviaProtocolTest {
    private val endpoint = BraviaEndpoint.parse("192.168.1.20")
    private data class Sent(val url: HttpUrl, val body: String, val headers: Map<String, String>)
    private class FakeTransport(private val respond: (Sent) -> BraviaHttpResponse) : BraviaTransport {
        val sent = mutableListOf<Sent>()
        override fun post(url: HttpUrl, body: String, headers: Map<String, String>): BraviaHttpResponse {
            val request = Sent(url, body, headers)
            sent.add(request)
            return respond(request)
        }
    }

    @Test fun endpointRejectsPublicAddressesRedirectTargetsAndUrlCredentials() {
        listOf("https://192.168.1.20", "8.8.8.8", "example.com", "http://user:secret@192.168.1.20",
            "http://192.168.1.20/path", "http://192.168.1.20/?x=1", "http://192.168.1.20/#x",
            "169.254.169.254").forEach {
            assertThrows(it, BraviaException::class.java) { BraviaEndpoint.parse(it) }
        }
        assertEquals("192.168.1.20", endpoint.address)
        assertEquals("172.16.0.4:8080", BraviaEndpoint.parse("172.16.0.4:8080").address)
    }

    @Test fun pairingChallengeDoesNotPersistPinOrSendItToOtherApis() {
        val saved = mutableListOf<String>()
        val transport = FakeTransport { request ->
            val method = JsonParser.parseString(request.body).asJsonObject["method"].asString
            if (method == "actRegister") {
                if (request.headers["Authorization"] == "Basic OjEyMzQ=")
                    BraviaHttpResponse(200, """{"result":[],"id":1}""", listOf("auth=paired; Path=/sony; HttpOnly"))
                else BraviaHttpResponse(401, "")
            } else {
                assertNull(request.headers["Authorization"])
                assertEquals("auth=paired", request.headers["Cookie"])
                BraviaHttpResponse(200, """{"result":[{"model":"KJ-55A9F","name":"TV","macAddr":""}],"id":1}""")
            }
        }
        val client = BraviaProtocol(endpoint, transport = transport, saveCookie = { saved.add(it) })
        assertFalse(client.beginPairing("LAMI:test"))
        assertTrue(saved.isEmpty())
        assertEquals("KJ-55A9F", client.completePairing("LAMI:test", "1234").model)
        assertEquals(listOf("auth=paired"), saved)
        assertTrue(transport.sent.all { !it.body.contains("1234") })
    }

    @Test fun authenticationFailureNeverLooksLikeASuccess() {
        for (status in listOf(401, 403)) {
            val client = BraviaProtocol(endpoint, transport = FakeTransport { BraviaHttpResponse(status, "") })
            assertThrows(BraviaAuthenticationRequired::class.java) { client.setPower(false) }
        }
        val error = BraviaProtocol(endpoint, transport = FakeTransport {
            BraviaHttpResponse(200, """{"error":[403,"Forbidden"],"id":1}""")
        })
        assertThrows(BraviaAuthenticationRequired::class.java) { error.isPoweredOn() }
    }

    @Test fun absoluteVolumeHonorsTheRangeReportedByTheTv() {
        val transport = FakeTransport { request ->
            if (request.body.contains("getVolumeInformation"))
                BraviaHttpResponse(200, """{"result":[[{"target":"speaker","volume":10,"minVolume":0,"maxVolume":50,"mute":false}]],"id":1}""")
            else BraviaHttpResponse(200, """{"result":[],"id":1}""")
        }
        val client = BraviaProtocol(endpoint, transport = transport)
        assertThrows(BraviaException::class.java) { client.setVolume(51) }
        assertFalse(transport.sent.any { it.body.contains("setAudioVolume") })
        client.setVolume(12)
        val parameters = JsonParser.parseString(transport.sent.last().body).asJsonObject["params"].asJsonArray[0].asJsonObject
        assertEquals("12", parameters["volume"].asString)
        assertEquals("speaker", parameters["target"].asString)
    }

    @Test fun hdmiUsesTheInputAdvertisedByTheTv() {
        val transport = FakeTransport { request ->
            if (request.body.contains("getCurrentExternalInputsStatus"))
                BraviaHttpResponse(200, """{"result":[[{"uri":"extInput:hdmi?port=2&type=single","title":"HDMI 2"}]],"id":1}""")
            else BraviaHttpResponse(200, """{"result":[],"id":1}""")
        }
        BraviaProtocol(endpoint, transport = transport).selectHdmi(2)
        assertTrue(transport.sent.last().body.contains("extInput:hdmi?port=2"))
        assertTrue(transport.sent.last().body.contains("setPlayContent"))
    }

    @Test fun keysComeFromTheTvAndAMutationIsNeverRetried() {
        val transport = FakeTransport { request ->
            if (request.url.encodedPath == "/sony/ircc") throw IOException("response lost")
            BraviaHttpResponse(200, """{"result":[{},[{"name":"Home","value":"AAAAAQAAAAEAAABgAw=="}]],"id":1}""")
        }
        val client = BraviaProtocol(endpoint, cookie = "auth=secret", transport = transport)
        assertThrows(IOException::class.java) { client.pressKey("Home") }
        assertEquals(1, transport.sent.count { it.url.encodedPath == "/sony/ircc" })
        assertTrue(transport.sent.last().body.contains("AAAAAQAAAAEAAABgAw=="))
        assertEquals("auth=secret", transport.sent.last().headers["Cookie"])
        assertThrows(BraviaException::class.java) { client.pressKey("FactoryReset") }
    }

    @Test fun malformedSoapAndUnknownKeysAreReported() {
        val transport = FakeTransport { request ->
            if (request.url.encodedPath == "/sony/ircc")
                BraviaHttpResponse(200, "<s:Envelope><s:Fault>Failed</s:Fault></s:Envelope>")
            else BraviaHttpResponse(200, """{"result":[{},[{"name":"Home","value":"AAAA"}]],"id":1}""")
        }
        val client = BraviaProtocol(endpoint, transport = transport)
        assertThrows(BraviaException::class.java) { client.pressKey("Back") }
        assertThrows(BraviaException::class.java) { client.pressKey("Home") }
    }

    @Test fun httpTransportReadsSmallBodiesAndDoesNotFollowRedirects() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"result":[],"id":1}"""))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/target"))
        server.enqueue(MockResponse().setBody("should not be reached"))
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val endpoint = BraviaEndpoint.parse("127.0.0.1:" + server.port)
            val transport = BraviaHttpTransport()
            val small = transport.post(endpoint.url.resolve("/small")!!, "{}", emptyMap())
            assertEquals(200, small.status)
            assertTrue(small.body.contains("result"))
            val redirect = transport.post(endpoint.url.resolve("/redirect")!!, "{}", emptyMap())
            assertEquals(302, redirect.status)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
