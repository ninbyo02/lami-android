package io.github.ninbyo02.lami.tts

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only in-process authentication gate for the resident CP executor.
 * No sockets, Android exported components, or listening service are created.
 */
internal class ResidentCpAuthenticatedChannel(
    private val expectedSecret: ByteArray,
    private val executor: ResidentCpWireExecutor
) : AutoCloseable {
    private val authorized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    init {
        require(expectedSecret.size == 32) { "Use a fresh 256-bit session secret" }
    }

    @Synchronized fun authorize(providedSecret: ByteArray): Boolean {
        check(!closed.get()) { "Session is closed" }
        if (authorized.get()) return false
        if (providedSecret.size != 32 || !MessageDigest.isEqual(expectedSecret, providedSecret)) {
            close()
            return false
        }
        authorized.set(true)
        return true
    }

    @Synchronized fun process(packet: ResidentCpWire.Packet): ResidentCpWire.Packet? {
        check(!closed.get() && authorized.get()) { "Unauthorized CP session" }
        return try {
            executor.process(packet)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    @Synchronized override fun close() {
        if (closed.compareAndSet(false, true)) {
            expectedSecret.fill(0)
            executor.close()
        }
    }
}
