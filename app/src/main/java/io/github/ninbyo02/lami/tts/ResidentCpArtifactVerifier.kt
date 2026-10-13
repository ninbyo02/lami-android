package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest

/** Host-verifiable gate. No model load or NPU call until byte integrity is proven. */
internal object ResidentCpArtifactVerifier {
    fun verify(file: File, spec: ResidentCpContextSpec): Long {
        require(file.isFile && file.length() > 0) { "Missing or empty CP context" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(actual == spec.sha256) { "CP context SHA-256 mismatch" }
        return file.length()
    }
}
