package io.github.ninbyo02.lami.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Pinned one-position QNN fixture from the production-fp32 SM8750 qnn-net-run actual-check.
 * The reference is an independent QNN result, NOT a CPU accuracy oracle.
 * No utterance, sampling, PCM, runtime fixture generation, or mutable baseline.
 */
internal object ResidentCpSingleStepFixture {
    const val INPUT_SHA256 = "af4986f4525dc536ec3feb552afc2eaad1947b99657c8ac7d69b86b80725bd18"
    const val GOLDEN_SHA256 = "957864d1aef2610fc9c1786f784012f596133d8765d0fce0c0aedf10dafd3ce6"
    const val CONTEXT_SHA256 = "1ebaf036a376c886fe135afbf5f87dfb651be2804dc8675f1035be9ca7a92136"
    const val SKELETON_SHA256 = "dd8987c17928e53877e51ce5739c550d3e74393bd5b1b5ccf311326eddb08612"
    private const val WIDTH = 1024

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun readPinnedRow(path: File, expectedSha256: String): FloatArray {
        require(path.isFile && path.length() == WIDTH * 4L) { "Missing fixed 1024-float fixture" }
        val bytes = path.readBytes()
        require(bytes.size == WIDTH * 4 && sha256(bytes) == expectedSha256) {
            "CP fixture SHA-256 mismatch"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(WIDTH) { buffer.float }.also { row ->
            require(row.all(Float::isFinite)) { "Non-finite CP fixture" }
        }
    }

    fun bitEqual(actual: FloatArray, expected: FloatArray): Boolean =
        actual.size == WIDTH && expected.size == WIDTH &&
            actual.indices.all { actual[it].toRawBits() == expected[it].toRawBits() }
}
