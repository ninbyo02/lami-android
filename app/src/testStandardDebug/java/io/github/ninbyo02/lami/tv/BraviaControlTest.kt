package io.github.ninbyo02.lami.tv

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BraviaControlTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BraviaClient
    private val clientId = "LAMI:00000000-0000-0000-0000-000000000001"

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = BraviaClient(BraviaAddress.forTest(server.url("/")))
    }
    @After fun tearDown() { server.shutdown() }
    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun keys(vararg names: String) = braviaJson("result" to listOf(emptyList<String>(), names.map { mapOf("name" to it, "value" to "AAAAAQAAAAEAAAAVAw==") })).toString()
    private fun volume(value: Int, max: Int = 100, mute: Boolean = false) = braviaJson("result" to listOf(listOf(mapOf("target" to "speaker", "volume" to value, "minVolume" to 0, "maxVolume" to max, "mute" to mute)))).toString()
    private inline fun <reified T : Throwable> fails(action: () -> Unit) {
        try { action(); fail("Expected " + T::class.java.simpleName) } catch (error: Throwable) { if (error !is T) throw error }
    }

    @Test fun pairingChallengeAndCookieDoNotCarryPinIntoSubsequentCalls() {
        server.enqueue(json("""{"error":[401,"Unauthorized"]}""", 401))
        assertTrue(client.pair(clientId).challengeRequired)
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.enqueue(json("""{"result":[]}""").addHeader("Set-Cookie", "auth=private-test-token; expires=Thu, 01 Jan 0000 00:00:00 GMT; path=/"))
        val paired = client.pair(clientId, "1234")
        val registration = server.takeRequest()
        assertEquals("Basic OjEyMzQ=", registration.getHeader("Authorization"))
        assertEquals("auth=private-test-token", paired.cookie)
        assertFalse(paired.toString().contains("private-test-token"))
        server.enqueue(json("""{"result":[{"model":"KJ-55A9F","name":"TV"}]}"""))
        val authenticated = BraviaClient(BraviaAddress.forTest(server.url("/")), paired.cookie)
        assertEquals("KJ-55A9F", authenticated.deviceInfo().model)
        val request = server.takeRequest()
        assertEquals("auth=private-test-token", request.getHeader("Cookie"))
        assertNull(request.getHeader("Authorization"))
        assertFalse(request.body.readUtf8().contains("1234"))
    }

    @Test fun invalidPinIsRejectedBeforeAnyNetworkRequest() {
        fails<IllegalArgumentException> { client.pair(clientId, "12\n4") }
        assertEquals(0, server.requestCount)
    }

    @Test fun jsonRpcAuthenticationErrorOverHttp200StillRequiresRegistration() {
        server.enqueue(json("""{"error":[403,"Forbidden"]}"""))
        fails<BraviaAuthenticationRequired> { client.powerStatus() }
    }

    @Test fun redirectsDoNotForwardCookieOrFollowAnUntrustedTarget() {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal")))
            val authenticated = BraviaClient(BraviaAddress.forTest(server.url("/")), "auth=private-test-token")
            fails<BraviaException> { authenticated.powerStatus() }
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun remoteKeyUsesTheCodeReturnedByThisTv() {
        server.enqueue(json(keys("Home")))
        server.enqueue(MockResponse().setBody(""))
        val result = BraviaControlTool(client).execute(BraviaCommand.Key(BraviaRemoteKey.HOME))
        assertTrue(result.ok); assertFalse(result.verified)
        val query = server.takeRequest()
        assertTrue(query.body.readUtf8().contains("getRemoteControllerInfo"))
        val command = server.takeRequest()
        assertEquals("/sony/ircc", command.path)
        assertTrue(command.body.readUtf8().contains("<IRCCCode>AAAAAQAAAAEAAAAVAw==</IRCCCode>"))
        assertEquals("\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"", command.getHeader("SOAPACTION"))
    }

    @Test fun soapFaultOverHttp200IsNotReportedAsSent() {
        server.enqueue(json(keys("Home")))
        server.enqueue(MockResponse().setBody("""<s:Envelope><s:Body><s:Fault><faultcode>failed</faultcode></s:Fault></s:Body></s:Envelope>"""))
        fails<BraviaException> { BraviaControlTool(client).execute(BraviaCommand.Key(BraviaRemoteKey.HOME)) }
    }

    @Test fun unsupportedKeyCannotFallBackToAGuessedCode() {
        server.enqueue(json(keys("Home")))
        fails<BraviaUnsupported> { BraviaControlTool(client).execute(BraviaCommand.Key(BraviaRemoteKey.PLAY)) }
        assertEquals(1, server.requestCount)
    }

    @Test fun incompleteChannelSequenceIsRejectedBeforeChangingTheBroadcastBand() {
        server.enqueue(json(keys("Digital", "Num1")))
        fails<BraviaUnsupported> { BraviaControlTool(client).execute(BraviaCommand.Channel(12, "地デジ")) }
        assertEquals(1, server.requestCount)
    }

    @Test fun reportedMaximumVolumeIsRespectedBeforeMutation() {
        server.enqueue(json(volume(10, max = 50)))
        fails<IllegalArgumentException> { BraviaControlTool(client).execute(BraviaCommand.Volume(51)) }
        assertEquals(1, server.requestCount)
    }

    @Test fun volumeReplyUsesTheReadBackValueInsteadOfClaimingTheRequestedValue() {
        server.enqueue(json(volume(10)))
        server.enqueue(json("""{"result":[]}"""))
        server.enqueue(json(volume(12)))
        val result = BraviaControlTool(client).execute(BraviaCommand.Volume(20))
        assertFalse(result.ok); assertFalse(result.verified)
        assertTrue(result.reply.contains("12")); assertFalse(result.reply.contains("20"))
    }

    @Test fun acceptedVolumeChangeWithFailedConfirmationIsExplicitlyUnverified() {
        server.enqueue(json(volume(10)))
        server.enqueue(json("""{"result":[]}"""))
        server.enqueue(json("""{"error":[7,"Illegal state"]}"""))
        val result = BraviaControlTool(client).execute(BraviaCommand.Volume(20))
        assertTrue(result.ok); assertFalse(result.verified)
        assertTrue(result.reply.contains("まだ確認できていません"))
    }

    @Test fun droppedMutationResponseDoesNotRetryTheCommand() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        fails<BraviaUnavailable> { client.setPower(false) }
        assertEquals(1, server.requestCount)
    }

    @Test fun unknownPowerStateIsNotMistakenForStandby() {
        server.enqueue(json("""{"result":[{"status":"unknown"}]}"""))
        fails<BraviaException> { client.powerStatus() }
    }

    @Test fun unknownAppDoesNotAcceptAnArbitraryLaunchUri() {
        server.enqueue(json("""{"result":[[{"title":"YouTube","uri":"tv-specific-youtube-uri"}]]}"""))
        fails<BraviaUnsupported> { client.launchApplication("http://example.com") }
        assertEquals(1, server.requestCount)
    }

    @Test fun exactInstalledAppUriIsUsed() {
        server.enqueue(json("""{"result":[[{"title":"YouTube","uri":"tv-specific-youtube-uri"}]]}"""))
        server.enqueue(json("""{"result":[]}"""))
        assertEquals("YouTube", client.launchApplication("ユーチューブ"))
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("tv-specific-youtube-uri"))
    }
}
