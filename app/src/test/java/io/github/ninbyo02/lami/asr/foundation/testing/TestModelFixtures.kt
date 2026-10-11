package io.github.ninbyo02.lami.asr.foundation.testing

import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.model.DistributionReview
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactFile
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactManifest
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.model.ModelLicenseEvidence
import io.github.ninbyo02.lami.asr.foundation.model.toHex
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

private val fixtureBytes = mapOf(
    "encoder.onnx" to "encoder".toByteArray(),
    "decoder.onnx" to "decoder".toByteArray(),
    "joiner.onnx" to "joiner".toByteArray(),
    "tokens.txt" to "tokens".toByteArray(),
)

private val fixtureManifest: ModelArtifactManifest by lazy {
    ModelArtifactManifest(
        id = "fixture-ja",
        version = "1.0.0",
        engine = "sherpa-onnx",
        runtimeVersion = "1.13.4",
        sampleRateHz = 16_000,
        languages = listOf("ja"),
        sourceUrl = "https://example.invalid/fixture-model",
        files = listOf(
            fixtureFile(ModelFileRole.ENCODER, "encoder.onnx"),
            fixtureFile(ModelFileRole.DECODER, "decoder.onnx"),
            fixtureFile(ModelFileRole.JOINER, "joiner.onnx"),
            fixtureFile(ModelFileRole.TOKENS, "tokens.txt"),
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
}

private val fixtureModel: VerifiedModel by lazy {
    val root = Files.createTempDirectory("verified-model-fixture")
    fixtureBytes.forEach { (path, bytes) -> Files.write(root.resolve(path), bytes) }
    Files.write(
        root.resolve(".install-complete"),
        fixtureManifest.contentFingerprint.toByteArray(StandardCharsets.US_ASCII),
    )
    VerifiedModel.fromVerifiedInstallation(fixtureManifest, root)
}

internal fun fixtureVerifiedModel(): VerifiedModel = fixtureModel

internal fun fixtureModelManifest(): ModelArtifactManifest = fixtureManifest

private fun fixtureFile(role: ModelFileRole, path: String): ModelArtifactFile {
    val bytes = fixtureBytes.getValue(path)
    return ModelArtifactFile(
        role = role,
        relativePath = path,
        sizeBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
    )
}
