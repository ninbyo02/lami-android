package io.github.ninbyo02.lami.asr.foundation.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Collections

enum class ModelFileRole {
    ENCODER,
    DECODER,
    JOINER,
    TOKENS,
}

enum class DistributionReview {
    APPROVED,
    HOLD,
}

fun interface ModelApprovalPolicy {
    fun isApproved(manifest: ModelArtifactManifest): Boolean

    companion object {
        val DENY_ALL = ModelApprovalPolicy { false }
    }
}

class PinnedModelApprovalPolicy(approvedFingerprints: Set<String>) : ModelApprovalPolicy {
    private val approvedFingerprints = Collections.unmodifiableSet(approvedFingerprints.toSet())

    init {
        require(this.approvedFingerprints.all(SHA256::matches)) {
            "model approval fingerprint is invalid"
        }
    }

    override fun isApproved(manifest: ModelArtifactManifest): Boolean =
        manifest.approvalFingerprint in approvedFingerprints
}

class ModelArtifactFile(
    val role: ModelFileRole,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(isSafeRelativePath(relativePath)) { "model file path is invalid" }
        require(sizeBytes > 0) { "model file size must be positive" }
        require(sizeBytes <= role.maximumSizeBytes()) { "model file size exceeds policy" }
        require(SHA256.matches(sha256)) { "model file sha256 is invalid" }
    }
}

class ModelLicenseEvidence(
    val spdxId: String,
    val evidenceUrl: String,
    val digestEvidenceUrl: String,
    val decisionOwner: String,
    val reviewedAt: String,
    val distributionReview: DistributionReview,
) {
    init {
        require(spdxId.isNotBlank() && spdxId.length <= 64) { "model SPDX identifier is invalid" }
        require(isSafeHttpsUrl(evidenceUrl)) { "model license evidence URL is invalid" }
        require(isSafeHttpsUrl(digestEvidenceUrl)) { "model digest evidence URL is invalid" }
        require(SAFE_COMPONENT.matches(decisionOwner)) { "model decision owner is invalid" }
        require(runCatching { LocalDate.parse(reviewedAt) }.isSuccess) {
            "model review date is invalid"
        }
    }
}

class ModelArtifactManifest(
    val id: String,
    val version: String,
    val engine: String,
    val runtimeVersion: String,
    val sampleRateHz: Int,
    languages: List<String>,
    val sourceUrl: String,
    files: List<ModelArtifactFile>,
    val license: ModelLicenseEvidence,
) {
    val languages: List<String>
    val filesByRole: Map<ModelFileRole, ModelArtifactFile>
    val contentFingerprint: String
    val approvalFingerprint: String

    init {
        require(SAFE_COMPONENT.matches(id)) { "model id is invalid" }
        require(SAFE_COMPONENT.matches(version)) { "model version is invalid" }
        require(SAFE_COMPONENT.matches(engine)) { "model engine is invalid" }
        require(SAFE_COMPONENT.matches(runtimeVersion)) { "model runtime version is invalid" }
        require(sampleRateHz == 16_000) { "model sample rate is unsupported" }
        require(isSafeHttpsUrl(sourceUrl)) { "model source URL is invalid" }

        val languageSnapshot = languages.toList()
        require(languageSnapshot.isNotEmpty()) { "model languages are missing" }
        require(languageSnapshot.all(LANGUAGE::matches)) { "model language is invalid" }
        require(languageSnapshot.distinct().size == languageSnapshot.size) {
            "model languages contain duplicates"
        }
        this.languages = Collections.unmodifiableList(languageSnapshot.sorted())

        val fileSnapshot = files.toList()
        require(fileSnapshot.size == ModelFileRole.entries.size) { "model role set is incomplete" }
        require(fileSnapshot.map { it.role }.distinct().size == fileSnapshot.size) {
            "model roles contain duplicates"
        }
        require(fileSnapshot.map { it.relativePath }.distinct().size == fileSnapshot.size) {
            "model paths contain duplicates"
        }
        require(fileSnapshot.sumOf { it.sizeBytes } <= MAX_MODEL_TOTAL_BYTES) {
            "model total size exceeds policy"
        }
        val byRole = fileSnapshot.associateBy { it.role }
        require(byRole.keys == ModelFileRole.entries.toSet()) { "model role set is incomplete" }
        filesByRole = Collections.unmodifiableMap(byRole)
        contentFingerprint = calculateContentFingerprint()
        approvalFingerprint = calculateApprovalFingerprint()
    }

    fun requireInstallable() {
        check(license.distributionReview == DistributionReview.APPROVED) {
            "model distribution is not approved"
        }
    }

    private fun calculateContentFingerprint(): String {
        val canonical = ByteArrayOutputStream()
        DataOutputStream(canonical).use { output ->
            output.writeInt(FINGERPRINT_FORMAT_VERSION)
            output.writeLengthPrefixed(id)
            output.writeLengthPrefixed(version)
            output.writeLengthPrefixed(engine)
            output.writeLengthPrefixed(runtimeVersion)
            output.writeInt(sampleRateHz)
            output.writeInt(languages.size)
            languages.forEach { language -> output.writeLengthPrefixed(language) }
            output.writeInt(filesByRole.size)
            ModelFileRole.entries.forEach { role ->
                val file = checkNotNull(filesByRole[role])
                output.writeLengthPrefixed(role.name)
                output.writeLengthPrefixed(file.relativePath)
                output.writeLong(file.sizeBytes)
                output.writeLengthPrefixed(file.sha256)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .toHex()
    }

    private fun calculateApprovalFingerprint(): String {
        val canonical = ByteArrayOutputStream()
        DataOutputStream(canonical).use { output ->
            output.writeInt(APPROVAL_FORMAT_VERSION)
            output.writeLengthPrefixed(contentFingerprint)
            output.writeLengthPrefixed(sourceUrl)
            output.writeLengthPrefixed(license.spdxId)
            output.writeLengthPrefixed(license.evidenceUrl)
            output.writeLengthPrefixed(license.digestEvidenceUrl)
            output.writeLengthPrefixed(license.decisionOwner)
            output.writeLengthPrefixed(license.reviewedAt)
            output.writeLengthPrefixed(license.distributionReview.name)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .toHex()
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private companion object {
        const val FINGERPRINT_FORMAT_VERSION = 1
        const val APPROVAL_FORMAT_VERSION = 1
        val LANGUAGE = Regex("[a-z]{2,3}")
    }
}

internal val SAFE_COMPONENT = Regex("[a-z0-9][a-z0-9._-]{0,63}")
internal val SHA256 = Regex("[0-9a-f]{64}")
private val PATH_COMPONENT = Regex("[a-z0-9][a-z0-9._-]{0,127}")
private const val MAX_MODEL_FILE_BYTES = 2L * 1024L * 1024L * 1024L
private const val MAX_TOKEN_FILE_BYTES = 64L * 1024L * 1024L
internal const val MAX_MODEL_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L

internal fun ModelFileRole.maximumSizeBytes(): Long = when (this) {
    ModelFileRole.TOKENS -> MAX_TOKEN_FILE_BYTES
    else -> MAX_MODEL_FILE_BYTES
}

internal fun isSafeRelativePath(value: String): Boolean =
    value.isNotBlank() &&
        !value.contains('/') &&
        !value.contains('\\') &&
        value != "." &&
        value != ".." &&
        PATH_COMPONENT.matches(value)

private fun isSafeHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null
}.getOrDefault(false)

internal fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
