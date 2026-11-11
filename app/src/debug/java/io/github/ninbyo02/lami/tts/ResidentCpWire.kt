package io.github.ninbyo02.lami.tts

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Strict, debug-only codec. Deliberately no server or network listener. */
internal object ResidentCpWire {
    const val RESET = 1
    const val STEP = 2
    const val RESULT = 3
    const val ERROR = 4
    private val magic = byteArrayOf(76, 67, 80, 49)
    private const val MAX_PAYLOAD = 4096

    data class Packet(val type: Int, val frame: Int, val position: Int, val payload: ByteArray)

    fun encode(packet: Packet): ByteArray {
        require(packet.type in RESET..ERROR)
        require(packet.frame in 0..255 && packet.position in 0..15)
        require(when (packet.type) {
            STEP, RESULT -> packet.payload.size == MAX_PAYLOAD
            RESET -> packet.payload.isEmpty()
            else -> packet.payload.size <= 256
        })
        return ByteBuffer.allocate(12 + packet.payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(magic)
            put(packet.type.toByte())
            put(packet.frame.toByte())
            putShort(packet.position.toShort())
            putInt(packet.payload.size)
            put(packet.payload)
        }.array()
    }

    private fun exact(input: InputStream, size: Int): ByteArray {
        require(size in 0..MAX_PAYLOAD)
        val data = ByteArray(size)
        var offset = 0
        while (offset < data.size) {
            val got = input.read(data, offset, data.size - offset)
            if (got < 0) throw EOFException("Truncated CP wire message")
            if (got == 0) continue
            offset += got
        }
        return data
    }

    fun decode(input: InputStream): Packet {
        val header = ByteBuffer.wrap(exact(input, 12)).order(ByteOrder.LITTLE_ENDIAN)
        val prefix = ByteArray(4)
        header.get(prefix)
        require(prefix.contentEquals(magic)) { "Incorrect protocol magic" }
        val type = header.get().toInt() and 255
        val frame = header.get().toInt() and 255
        val position = header.short.toInt() and 65535
        val length = header.int
        require(length in 0..MAX_PAYLOAD)
        val packet = Packet(type, frame, position, exact(input, length))
        encode(packet) // full shape validation
        return packet
    }
}
