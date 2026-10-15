package io.github.ninbyo02.lami.tts

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom

/**
 * Explicit opt-in one-client loopback transport for the isolated debug app.
 * No Android component starts this automatically. It owns no native handle.
 */
internal class ResidentCpLoopbackEndpoint(
    private val channelFactory: (ByteArray) -> ResidentCpAuthenticatedChannel,
    private val timeoutMillis: Int = 15_000,
) : AutoCloseable {
    init { require(timeoutMillis in 1..60_000) }
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply {
        soTimeout = timeoutMillis
    }
    private var served = false
    val port: Int get() = server.localPort

    fun serveOnce(onSecret: (ByteArray) -> Unit) {
        check(!served) { "One client per endpoint" }
        served = true
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            onSecret(secret.copyOf())
            server.accept().use { client ->
                check(client.inetAddress.isLoopbackAddress) { "Non-loopback caller" }
                client.soTimeout = timeoutMillis
                ResidentCpLocalSessionHandler(channelFactory(secret.copyOf())).serve(
                    client.getInputStream(), client.getOutputStream()
                )
            }
        } catch (_: SocketTimeoutException) {
            // No client or stalled client: fail closed and release resources.
        } finally {
            secret.fill(0)
            close()
        }
    }

    override fun close() { server.close() }
}
