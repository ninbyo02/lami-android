package io.github.ninbyo02.lami.asr.foundation.model

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArtifactVerifierTest {
    @Test
    fun `verifier opens and verifies every declared role exactly once`() {
        val bytes = fixtureBytes()
        val source = RecordingSource(bytes)
        val manifest = manifestFor(bytes)

        ModelArtifactVerifier().verify(manifest, source)

        assertEquals(ModelFileRole.entries.map { manifest.filesByRole.getValue(it).relativePath }, source.opened)
    }

    @Test
    fun `verifier rejects digest mismatch without source payload`() {
        val bytes = fixtureBytes()
        val canary = "SECRET"
        val source = RecordingSource(bytes + ("joiner.onnx" to canary.toByteArray()))
        val manifest = manifestFor(bytes)

        val failure = assertThrows(ModelArtifactVerificationException::class.java) {
            ModelArtifactVerifier().verify(manifest, source)
        }

        assertEquals("model verification failed: role=joiner reason=digest", failure.message)
        assertFalse(failure.message.orEmpty().contains(canary))
    }

    @Test
    fun `verifier uses a bounded reusable read buffer`() {
        val bytes = fixtureBytes().mapValues { (_, value) -> value + ByteArray(200_000) { 7 } }
        val manifest = manifestFor(bytes)
        var largestRead = 0
        val source = ModelArtifactSource { relativePath ->
            object : ByteArrayInputStream(bytes.getValue(relativePath)) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    largestRead = maxOf(largestRead, length)
                    check(length <= 64 * 1024) { "oversized read" }
                    return super.read(buffer, offset, length)
                }
            }
        }

        ModelArtifactVerifier().verify(manifest, source)

        assertTrue(largestRead in 1..(64 * 1024))
    }

    @Test
    fun `source exception is reduced to operation role and class`() {
        val canary = "PRIVATE_SOURCE_EXCEPTION_CANARY"
        val manifest = manifestFor(fixtureBytes())
        val source = ModelArtifactSource { throw IOException(canary) }

        val failure = assertThrows(ModelArtifactVerificationException::class.java) {
            ModelArtifactVerifier().verify(manifest, source)
        }

        assertEquals("model verification failed: role=encoder type=IOException", failure.message)
        assertFalse(failure.message.orEmpty().contains(canary))
        assertEquals(null, failure.cause)
    }

    @Test
    fun `cancellation closes source and rethrows sanitized cancellation`() {
        val bytes = fixtureBytes()
        val manifest = manifestFor(bytes)
        var closed = false
        val source = ModelArtifactSource { relativePath ->
            object : ByteArrayInputStream(bytes.getValue(relativePath)) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }
        val canary = "PRIVATE_CANCELLATION_CANARY"
        var checks = 0

        val failure = assertThrows(CancellationException::class.java) {
            ModelArtifactVerifier().verify(
                manifest = manifest,
                source = source,
                cancellation = CopyCancellationCheck {
                    checks += 1
                    if (checks == 2) throw CancellationException(canary)
                },
            )
        }

        assertTrue(closed)
        assertEquals("model verification cancelled", failure.message)
        assertFalse(failure.message.orEmpty().contains(canary))
        assertEquals(null, failure.cause)
    }

    @Test
    fun `cancellation discards close failure payload and suppressed exceptions`() {
        val bytes = fixtureBytes()
        val manifest = manifestFor(bytes)
        val canary = "PRIVATE_CLOSE_FAILURE_CANARY"
        val source = ModelArtifactSource { path ->
            object : ByteArrayInputStream(bytes.getValue(path)) {
                override fun close() {
                    throw IOException(canary)
                }
            }
        }
        var checks = 0

        val failure = assertThrows(CancellationException::class.java) {
            ModelArtifactVerifier().verify(
                manifest,
                source,
                CopyCancellationCheck {
                    checks += 1
                    if (checks == 2) throw CancellationException("PRIVATE_CANCEL_CANARY")
                },
            )
        }

        assertEquals("model verification cancelled", failure.message)
        assertEquals(null, failure.cause)
        assertTrue(failure.suppressed.isEmpty())
        assertFalse(failure.toString().contains(canary))
    }

    @Test
    fun `digest failure discards close failure payload and suppressed exceptions`() {
        val bytes = fixtureBytes()
        val manifest = manifestFor(bytes)
        val canary = "PRIVATE_DIGEST_CLOSE_CANARY"
        val source = ModelArtifactSource { path ->
            val payload = if (path == "encoder.onnx") "ENCODER".toByteArray() else bytes.getValue(path)
            object : ByteArrayInputStream(payload) {
                override fun close() {
                    throw IOException(canary)
                }
            }
        }

        val failure = assertThrows(ModelArtifactVerificationException::class.java) {
            ModelArtifactVerifier().verify(manifest, source)
        }

        assertEquals("model verification failed: role=encoder reason=digest", failure.message)
        assertEquals(null, failure.cause)
        assertTrue(failure.suppressed.isEmpty())
        assertFalse(failure.toString().contains(canary))
    }

    @Test
    fun `non fatal source Error becomes payload free verification failure`() {
        val manifest = manifestFor(fixtureBytes())
        val canary = "PRIVATE_ASSERTION_CANARY"
        val source = ModelArtifactSource { throw AssertionError(canary) }

        val failure = assertThrows(ModelArtifactVerificationException::class.java) {
            ModelArtifactVerifier().verify(manifest, source)
        }

        assertTrue(failure.message.orEmpty().contains("type=AssertionError"))
        assertFalse(failure.message.orEmpty().contains(canary))
        assertEquals(null, failure.cause)
    }

    private class RecordingSource(
        private val bytes: Map<String, ByteArray>,
    ) : ModelArtifactSource {
        val opened = mutableListOf<String>()

        override fun open(relativePath: String): InputStream {
            opened += relativePath
            return ByteArrayInputStream(bytes.getValue(relativePath))
        }
    }

    private fun fixtureBytes() = mapOf(
        "encoder.onnx" to "encoder".toByteArray(),
        "decoder.onnx" to "decoder".toByteArray(),
        "joiner.onnx" to "joiner".toByteArray(),
        "tokens.txt" to "tokens".toByteArray(),
    )

    private fun manifestFor(bytes: Map<String, ByteArray>) = ModelArtifactManifest(
        id = "synthetic-streaming-model",
        version = "1.0.0",
        engine = "sherpa-onnx",
        runtimeVersion = "1.13.4",
        sampleRateHz = 16_000,
        languages = listOf("ja"),
        sourceUrl = "https://example.invalid/synthetic-model",
        files = listOf(
            file(ModelFileRole.ENCODER, "encoder.onnx", bytes),
            file(ModelFileRole.DECODER, "decoder.onnx", bytes),
            file(ModelFileRole.JOINER, "joiner.onnx", bytes),
            file(ModelFileRole.TOKENS, "tokens.txt", bytes),
        ),
        license = ModelLicenseEvidence(
            spdxId = "Apache-2.0",
            evidenceUrl = "https://example.invalid/license",
            digestEvidenceUrl = "https://example.invalid/digests",
                decisionOwner = "synthetic-test-owner",
            reviewedAt = "2026-07-21",
            distributionReview = DistributionReview.APPROVED,
        ),
    )

    private fun file(
        role: ModelFileRole,
        path: String,
        bytes: Map<String, ByteArray>,
    ): ModelArtifactFile {
        val content = bytes.getValue(path)
        return ModelArtifactFile(
            role = role,
            relativePath = path,
            sizeBytes = content.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(content).toHex(),
        )
    }
}
