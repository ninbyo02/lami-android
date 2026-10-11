package io.github.ninbyo02.lami.asr.platform.model

import io.github.ninbyo02.lami.asr.foundation.model.DistributionReview
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactFile
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactManifest
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.model.ModelLicenseEvidence
import io.github.ninbyo02.lami.asr.foundation.model.PinnedModelApprovalPolicy

internal object ReazonSpeechModel {
    const val ID = "reazonspeech-k2-v2"
    const val VERSION = "2024-08-01"
    const val RUNTIME_VERSION = "1.13.6"
    const val APPROVAL_FINGERPRINT =
        "2576f6e72ec5ff83a06f9fdee5d6a525748a282e65b3ef7728ae128db0a3d6bc"

    val manifest = ModelArtifactManifest(
        id = ID,
        version = VERSION,
        engine = "sherpa-onnx-offline-transducer",
        runtimeVersion = RUNTIME_VERSION,
        sampleRateHz = 16_000,
        languages = listOf("ja"),
        sourceUrl = ARCHIVE_URL,
        files = listOf(
            artifact(
                ModelFileRole.ENCODER,
                "encoder-epoch-99-avg-1.int8.onnx",
                154_670_139,
                "2c7bd08a8a99f9ddd0d9e458456577b1f6279214e51426f114f9eced44c54e1d",
            ),
            artifact(
                ModelFileRole.DECODER,
                "decoder-epoch-99-avg-1.onnx",
                11_767_836,
                "58b18211ae06265466bfa17172dab574df94f76c8bcb61a3640c28ba860e4124",
            ),
            artifact(
                ModelFileRole.JOINER,
                "joiner-epoch-99-avg-1.int8.onnx",
                2_696_970,
                "49cc7ea1d3d35a40a27442db5e89996da64bf0e683a903dce76e99e57a12e4de",
            ),
            artifact(
                ModelFileRole.TOKENS,
                "tokens.txt",
                45_754,
                "2c3ac659818a48a0c04010e0593bbc4d7c8a24a054340b01131499c05fd52def",
            ),
        ),
        license = ModelLicenseEvidence(
            spdxId = "Apache-2.0",
            evidenceUrl = "https://huggingface.co/reazon-research/reazonspeech-k2-v2",
            digestEvidenceUrl =
                "https://k2-fsa.github.io/sherpa/onnx/pretrained_models/" +
                    "offline-transducer/zipformer-transducer-models.html",
            decisionOwner = "lami-asr",
            reviewedAt = "2026-08-27",
            distributionReview = DistributionReview.APPROVED,
        ),
    )

    val approvalPolicy = PinnedModelApprovalPolicy(setOf(APPROVAL_FINGERPRINT))

    val requiredFileNames: Set<String> =
        manifest.filesByRole.values.mapTo(linkedSetOf()) { it.relativePath }

    fun requireExactSelection(fileNames: List<String>) {
        require(fileNames.size == requiredFileNames.size) {
            "model selection has an invalid file count"
        }
        require(fileNames.toSet().size == fileNames.size) {
            "model selection contains duplicate file names"
        }
        require(fileNames.toSet() == requiredFileNames) {
            "model selection does not match the approved manifest"
        }
    }

    init {
        check(manifest.contentFingerprint == CONTENT_FINGERPRINT)
        check(manifest.approvalFingerprint == APPROVAL_FINGERPRINT)
    }

    private fun artifact(
        role: ModelFileRole,
        path: String,
        size: Long,
        sha256: String,
    ) = ModelArtifactFile(role, path, size, sha256)

    private const val CONTENT_FINGERPRINT =
        "57ef505f85bdce7068f1a4071213dddddc182a08ecc866ec215d16e40615c75d"
    private const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01.tar.bz2"
}
