package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.security.MessageDigest

private const val MAX_ELF_NOTE_SCAN_BYTES = 1024 * 1024

internal fun sha256ForFileSafely(file: File): String? {
    return runCatching {
        if (!file.isFile || file.length() <= 0L) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }.getOrNull()
}

internal fun readElfBuildIdSafely(file: File): String? {
    return runCatching {
        if (!file.isFile || file.length() <= 0L) return null
        val bytes = file.inputStream().use { input ->
            input.readBytes().let { data ->
                if (data.size <= MAX_ELF_NOTE_SCAN_BYTES) data else data.copyOf(MAX_ELF_NOTE_SCAN_BYTES)
            }
        }
        extractGnuBuildId(bytes)
    }.getOrNull()
}

private fun extractGnuBuildId(bytes: ByteArray): String? {
    val gnuName = byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0)
    var index = 12
    while (index <= bytes.size - gnuName.size) {
        if (matchesBytes(bytes, index, gnuName)) {
            val headerOffset = index - 12
            val nameSize = readLittleEndianInt(bytes, headerOffset)
            val descSize = readLittleEndianInt(bytes, headerOffset + 4)
            val type = readLittleEndianInt(bytes, headerOffset + 8)
            if (nameSize == 4 && type == 3 && descSize in 4..64) {
                val descOffset = index + align4(nameSize)
                if (descOffset >= 0 && descOffset + descSize <= bytes.size) {
                    return bytes.copyOfRange(descOffset, descOffset + descSize)
                        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                }
            }
        }
        index += 1
    }
    return null
}

private fun matchesBytes(bytes: ByteArray, offset: Int, expected: ByteArray): Boolean {
    if (offset < 0 || offset + expected.size > bytes.size) return false
    return expected.indices.all { index -> bytes[offset + index] == expected[index] }
}

private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset + 4 > bytes.size) return -1
    return (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

private fun align4(value: Int): Int = (value + 3) and -4
