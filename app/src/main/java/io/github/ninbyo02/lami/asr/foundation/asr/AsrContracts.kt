package io.github.ninbyo02.lami.asr.foundation.asr

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactManifest
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactSource
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactVerificationException
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactVerifier
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.model.isFatalModelFailure
import io.github.ninbyo02.lami.asr.foundation.model.safeType
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptStage
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Collections

interface AsrEngine : AutoCloseable {
    /** Opens an independent session. Calling this after [close] must fail. */
    suspend fun open(model: VerifiedModel): AsrSession

    /** Idempotent. Existing sessions remain owned by their caller and must be closed separately. */
    override fun close()
}

/**
 * Single-owner session; implementations need not be thread-safe. Calls are serialized by the
 * coordinator. [cancel] and [close] are idempotent terminal operations. After cancel, close, or
 * successful [finish], both [accept] and [finish] must fail.
 */
interface AsrSession : AutoCloseable {
    suspend fun accept(frame: Pcm16Frame): List<AsrEvent>

    suspend fun finish(): AsrResult

    fun cancel()

    override fun close()
}

sealed interface AsrEvent {
    data class Partial(val text: String) : AsrEvent

    data class Endpoint(val finalText: String) : AsrEvent
}

data class AsrResult(
    val finalText: String,
    val transcript: TranscriptRevision? = null,
) {
    init {
        require(transcript == null || transcript.text == finalText) {
            "ASR result text does not match transcript"
        }
        require(
            transcript == null ||
                transcript.stage == TranscriptStage.FINAL ||
                transcript.stage == TranscriptStage.COMMITTED,
        ) {
            "ASR result cannot contain a partial transcript"
        }
    }
}

class VerifiedModel private constructor(
    val id: String,
    val version: String,
    private val root: Path,
    val contentFingerprint: String,
    filesByRole: Map<ModelFileRole, VerifiedModelFile>,
) {
    private val filesByRole: Map<ModelFileRole, VerifiedModelFile> =
        Collections.unmodifiableMap(filesByRole.toMap())

    fun fileFor(role: ModelFileRole): Path {
        val file = checkNotNull(filesByRole[role]) { "verified model role is missing" }
        val resolved = root.resolve(file.relativePath).normalize()
        check(resolved.startsWith(root)) { "verified model path escaped root" }
        return resolved
    }

    override fun toString(): String =
        "VerifiedModel(id=$id, version=$version, contentFingerprint=$contentFingerprint)"

    internal companion object {
        fun fromVerifiedInstallation(
            manifest: ModelArtifactManifest,
            root: Path,
        ): VerifiedModel {
            manifest.requireInstallable()
            val normalizedRoot = root.toAbsolutePath().normalize()
            try {
                verifyExactTopLevelTree(manifest, normalizedRoot)
                verifyCompletionMarker(manifest, normalizedRoot)
                val source = ModelArtifactSource { relativePath ->
                    val path = normalizedRoot.resolve(relativePath).normalize()
                    check(path.parent == normalizedRoot) { "verified model path escaped root" }
                    Channels.newInputStream(
                        Files.newByteChannel(
                            path,
                            setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                        ),
                    )
                }
                ModelArtifactVerifier().verify(manifest, source)
            } catch (failure: ModelArtifactVerificationException) {
                throw IllegalStateException("verified model creation failed: ${failure.safeDetail}")
            } catch (failure: Error) {
                if (failure.isFatalModelFailure()) throw failure
                throw IllegalStateException(
                    "verified model creation failed: type=${failure.safeType()}",
                )
            } catch (failure: Throwable) {
                throw IllegalStateException(
                    "verified model creation failed: type=${failure.safeType()}",
                )
            }

            val verifiedFiles = manifest.filesByRole.mapValues { (_, file) ->
                VerifiedModelFile(
                    relativePath = file.relativePath,
                    sizeBytes = file.sizeBytes,
                    sha256 = file.sha256,
                )
            }
            return VerifiedModel(
                id = manifest.id,
                version = manifest.version,
                root = normalizedRoot,
                contentFingerprint = manifest.contentFingerprint,
                filesByRole = verifiedFiles,
            )
        }

        private fun verifyExactTopLevelTree(manifest: ModelArtifactManifest, root: Path) {
            check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
                "verified model tree is invalid"
            }
            val expected = manifest.filesByRole.values.map { it.relativePath }.toMutableSet().apply {
                add(COMPLETION_MARKER)
            }
            val observed = Files.list(root).use { entries ->
                val result = mutableSetOf<String>()
                val iterator = entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    check(!Files.isSymbolicLink(entry)) { "verified model tree is invalid" }
                    check(Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        "verified model tree is invalid"
                    }
                    result += entry.fileName.toString()
                }
                result
            }
            check(observed == expected) { "verified model tree is invalid" }
        }

        private fun verifyCompletionMarker(manifest: ModelArtifactManifest, root: Path) {
            val marker = root.resolve(COMPLETION_MARKER)
            check(Files.size(marker) == FINGERPRINT_LENGTH.toLong()) {
                "verified model marker is invalid"
            }
            Files.newByteChannel(
                marker,
                setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
            ).use { channel ->
                val buffer = ByteBuffer.allocate(FINGERPRINT_LENGTH + 1)
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // Read one bounded marker and a sentinel byte.
                }
                check(buffer.position() == FINGERPRINT_LENGTH) {
                    "verified model marker is invalid"
                }
                buffer.flip()
                val bytes = ByteArray(FINGERPRINT_LENGTH)
                buffer.get(bytes)
                check(String(bytes, StandardCharsets.US_ASCII) == manifest.contentFingerprint) {
                    "verified model marker is invalid"
                }
            }
        }

        private const val COMPLETION_MARKER = ".install-complete"
        private const val FINGERPRINT_LENGTH = 64
    }
}

internal class VerifiedModelFile(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)
