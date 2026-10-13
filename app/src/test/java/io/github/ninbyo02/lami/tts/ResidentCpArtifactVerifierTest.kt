package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ResidentCpArtifactVerifierTest {
    private fun spec(bytes: ByteArray): ResidentCpContextSpec {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return ResidentCpContextSpec(hash, 1024, 16, 32, "cp-test")
    }
    @Test fun matchingFileIsAccepted() {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        val file = File.createTempFile("cp-artifact", ".bin")
        try {
            file.writeBytes(bytes)
            assertEquals(bytes.size.toLong(), ResidentCpArtifactVerifier.verify(file, spec(bytes)))
        } finally { file.delete() }
    }
    @Test fun tamperedFileIsRejected() {
        val original = "model".toByteArray()
        val file = File.createTempFile("cp-artifact", ".bin")
        try {
            file.writeBytes("other".toByteArray())
            try { ResidentCpArtifactVerifier.verify(file, spec(original)); fail("tampered") }
            catch (_: IllegalArgumentException) {}
        } finally { file.delete() }
    }
    @Test fun missingOrEmptyFileRejected() {
        val file = File.createTempFile("cp-artifact", ".bin")
        try {
            try { ResidentCpArtifactVerifier.verify(file, spec(byteArrayOf(1))); fail("empty") }
            catch (_: IllegalArgumentException) {}
        } finally { file.delete() }
    }
}
