package io.github.ninbyo02.lami.asr.foundation.model

import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.testing.fixtureVerifiedModel
import java.nio.file.Files
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArtifactManifestTest {
    @Test
    fun `approved exact four role manifest is installable`() {
        val manifest = fixtureManifest()

        manifest.requireInstallable()

        assertEquals(ModelFileRole.values().toSet(), manifest.filesByRole.keys)
        assertTrue(manifest.contentFingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `license HOLD fails closed without evidence payload`() {
        val canary = "PRIVATE_LICENSE_EVIDENCE_CANARY"
        val manifest = fixtureManifest(
            license = fixtureLicense(
                review = DistributionReview.HOLD,
                evidenceUrl = "https://example.invalid/$canary",
            ),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            manifest.requireInstallable()
        }

        assertEquals("model distribution is not approved", failure.message)
        assertFalse(failure.message.orEmpty().contains(canary))
    }

    @Test
    fun `manifest rejects duplicate roles and unsafe paths without payload`() {
        val canary = "PRIVATE_PATH_CANARY"
        val duplicate = fixtureFiles().toMutableList().apply {
            add(ModelArtifactFile(ModelFileRole.ENCODER, "second.onnx", 1, digest("duplicate")))
        }
        assertSafeManifestFailure(canary) {
            fixtureManifest(files = duplicate)
        }
        assertSafeManifestFailure(canary) {
            fixtureManifest(
                files = fixtureFiles().map {
                    if (it.role == ModelFileRole.ENCODER) {
                        ModelArtifactFile(it.role, "../$canary.onnx", it.sizeBytes, it.sha256)
                    } else {
                        it
                    }
                },
            )
        }
    }

    @Test
    fun `manifest snapshots caller collections and fingerprint is order stable`() {
        val files = fixtureFiles().toMutableList()
        val languages = mutableListOf("ja", "en")
        val first = fixtureManifest(files = files, languages = languages)
        val second = fixtureManifest(files = files.reversed(), languages = languages.reversed())

        files.clear()
        languages.clear()

        assertEquals(ModelFileRole.values().toSet(), first.filesByRole.keys)
        assertEquals(listOf("en", "ja"), first.languages)
        assertEquals(first.contentFingerprint, second.contentFingerprint)
    }

    @Test
    fun `content fingerprint changes with verified bytes but not compliance evidence`() {
        val original = fixtureManifest()
        val changedBytes = fixtureManifest(
            files = fixtureFiles().map {
                if (it.role == ModelFileRole.JOINER) {
                    ModelArtifactFile(it.role, it.relativePath, it.sizeBytes, digest("changed"))
                } else {
                    it
                }
            },
        )
        val changedEvidence = fixtureManifest(
            license = fixtureLicense(evidenceUrl = "https://other.invalid/license"),
        )

        assertNotEquals(original.contentFingerprint, changedBytes.contentFingerprint)
        assertEquals(original.contentFingerprint, changedEvidence.contentFingerprint)
        assertNotEquals(original.approvalFingerprint, changedEvidence.approvalFingerprint)
        assertTrue(PinnedModelApprovalPolicy(setOf(original.approvalFingerprint)).isApproved(original))
        assertFalse(PinnedModelApprovalPolicy(setOf(original.approvalFingerprint)).isApproved(changedEvidence))
    }

    @Test
    fun `VerifiedModel is a verified path free non data capability`() {
        val verified = fixtureVerifiedModel()
        val root = verified.fileFor(ModelFileRole.ENCODER).parent

        assertFalse(
            VerifiedModel::class.java.declaredConstructors.any {
                !it.isSynthetic && Modifier.isPublic(it.modifiers)
            },
        )
        assertFalse(VerifiedModel::class.java.declaredMethods.any { it.name == "copy" })
        assertFalse(verified.toString().contains(root.toString()))
        assertEquals(root.resolve("encoder.onnx"), verified.fileFor(ModelFileRole.ENCODER))

        val failure = assertThrows(IllegalStateException::class.java) {
            VerifiedModel.fromVerifiedInstallation(
                fixtureManifest(),
                Files.createTempDirectory("unverified-model-test"),
            )
        }
        assertFalse(failure.message.orEmpty().contains(root.toString()))
    }

    private fun assertSafeManifestFailure(canary: String, block: () -> Unit) {
        val failure = assertThrows(IllegalArgumentException::class.java, block)
        assertFalse(failure.message.orEmpty().contains(canary))
    }

    private fun fixtureManifest(
        files: List<ModelArtifactFile> = fixtureFiles(),
        languages: List<String> = listOf("ja", "en"),
        license: ModelLicenseEvidence = fixtureLicense(),
    ) = ModelArtifactManifest(
        id = "synthetic-streaming-model",
        version = "1.0.0",
        engine = "sherpa-onnx",
        runtimeVersion = "1.13.4",
        sampleRateHz = 16_000,
        languages = languages,
        sourceUrl = "https://example.invalid/synthetic-model",
        files = files,
        license = license,
    )

    private fun fixtureLicense(
        review: DistributionReview = DistributionReview.APPROVED,
        evidenceUrl: String = "https://example.invalid/license",
    ) = ModelLicenseEvidence(
        spdxId = "Apache-2.0",
        evidenceUrl = evidenceUrl,
        digestEvidenceUrl = "https://example.invalid/digests",
        decisionOwner = "synthetic-test-owner",
        reviewedAt = "2026-07-21",
        distributionReview = review,
    )

    private fun fixtureFiles() = listOf(
        ModelArtifactFile(ModelFileRole.ENCODER, "encoder.onnx", 7, digest("encoder")),
        ModelArtifactFile(ModelFileRole.DECODER, "decoder.onnx", 7, digest("decoder")),
        ModelArtifactFile(ModelFileRole.JOINER, "joiner.onnx", 6, digest("joiner")),
        ModelArtifactFile(ModelFileRole.TOKENS, "tokens.txt", 6, digest("tokens")),
    )

    private fun digest(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
