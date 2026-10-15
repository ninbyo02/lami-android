package io.github.ninbyo02.lami.tts

import java.io.InputStream
import java.io.OutputStream

/**
 * Single-connection diagnostic protocol handler. No socket is opened here.
 * The caller must enforce loopback binding, single client and a bounded lifetime.
 * Authentication is exactly 32 raw bytes before any framed command.
 */
internal class ResidentCpLocalSessionHandler(
    private val channel: ResidentCpAuthenticatedChannel
) {
    fun serve(input: InputStream, output: OutputStream) {
        channel.use { session ->
            val provided = ByteArray(32)
            try {
                var read = 0
                while (read < provided.size) {
                    val count = input.read(provided, read, provided.size - read)
                    if (count < 0) return
                    if (count == 0) continue
                    read += count
                }
                if (!session.authorize(provided)) return
                // Protocol permits at most 256 frames, 17 packets per frame.
                var requests = 0
                while (requests < 256 * 17) {
                    val packet = try {
                        ResidentCpWire.decode(input)
                    } catch (e: java.io.EOFException) {
                        return
                    }
                    val result = session.process(packet)
                    if (result != null) {
                        output.write(ResidentCpWire.encode(result))
                        output.flush()
                    }
                    requests++
                }
            } finally {
                provided.fill(0)
            }
        }
    }
}
