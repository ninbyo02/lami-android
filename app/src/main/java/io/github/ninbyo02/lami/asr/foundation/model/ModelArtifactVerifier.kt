package io.github.ninbyo02.lami.asr.foundation.model

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

fun interface ModelArtifactSource {
    fun open(relativePath: String): InputStream
}

fun interface CopyCancellationCheck {
    fun check()

    companion object {
        val NONE = CopyCancellationCheck { }
    }
}

class ModelArtifactVerificationException internal constructor(
    internal val safeDetail: String,
) : IllegalStateException("model verification failed: $safeDetail")

class ModelArtifactVerifier(
    private val bufferSize: Int = MAX_BUFFER_SIZE,
) {
    init {
        require(bufferSize in 1..MAX_BUFFER_SIZE) { "model verification buffer is invalid" }
    }

    fun verify(
        manifest: ModelArtifactManifest,
        source: ModelArtifactSource,
        cancellation: CopyCancellationCheck = CopyCancellationCheck.NONE,
    ) {
        manifest.requireInstallable()
        val buffer = ByteArray(bufferSize)
        ModelFileRole.entries.forEach { role ->
            val file = manifest.filesByRole.getValue(role)
            verifySourceFile(file, source, cancellation, buffer)
        }
    }

    internal fun copyAndVerify(
        file: ModelArtifactFile,
        input: InputStream,
        output: OutputStream?,
        cancellation: CopyCancellationCheck = CopyCancellationCheck.NONE,
        buffer: ByteArray = ByteArray(bufferSize),
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var byteCount = 0L
        try {
            while (true) {
                cancellation.check()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                byteCount += read
                if (byteCount > file.sizeBytes) {
                    throw reasonFailure(file.role, "size")
                }
                digest.update(buffer, 0, read)
                output?.write(buffer, 0, read)
            }
            cancellation.check()
        } catch (_: SanitizedVerificationCancellation) {
            throw SanitizedVerificationCancellation()
        } catch (_: CancellationException) {
            throw SanitizedVerificationCancellation()
        } catch (failure: ModelArtifactVerificationException) {
            throw ModelArtifactVerificationException(failure.safeDetail)
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw ModelArtifactVerificationException(
                "role=${file.role.safeName()} type=${failure.safeType()}",
            )
        } catch (failure: Throwable) {
            throw ModelArtifactVerificationException(
                "role=${file.role.safeName()} type=${failure.safeType()}",
            )
        }

        if (byteCount != file.sizeBytes) {
            throw reasonFailure(file.role, "size")
        }
        val expected = hexToBytes(file.sha256)
        if (!MessageDigest.isEqual(expected, digest.digest())) {
            throw reasonFailure(file.role, "digest")
        }
    }

    private fun verifySourceFile(
        file: ModelArtifactFile,
        source: ModelArtifactSource,
        cancellation: CopyCancellationCheck,
        buffer: ByteArray,
    ) {
        try {
            source.open(file.relativePath).use { input ->
                copyAndVerify(file, input, output = null, cancellation, buffer)
            }
        } catch (_: SanitizedVerificationCancellation) {
            throw SanitizedVerificationCancellation()
        } catch (_: CancellationException) {
            throw SanitizedVerificationCancellation()
        } catch (failure: ModelArtifactVerificationException) {
            throw ModelArtifactVerificationException(failure.safeDetail)
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw ModelArtifactVerificationException(
                "role=${file.role.safeName()} type=${failure.safeType()}",
            )
        } catch (failure: Throwable) {
            throw ModelArtifactVerificationException(
                "role=${file.role.safeName()} type=${failure.safeType()}",
            )
        }
    }

    private fun reasonFailure(role: ModelFileRole, reason: String) =
        ModelArtifactVerificationException(
            "role=${role.safeName()} reason=$reason",
        )

    private class SanitizedVerificationCancellation :
        CancellationException("model verification cancelled")

    private companion object {
        const val MAX_BUFFER_SIZE = 64 * 1024
    }
}

internal fun ModelFileRole.safeName(): String = name.lowercase()

internal fun Throwable.safeType(): String =
    this::class.java.simpleName.takeIf { it.matches(Regex("[A-Za-z0-9_$]{1,128}")) }
        ?: "Throwable"

internal fun Throwable.isFatalModelFailure(): Boolean =
    this is VirtualMachineError || this is ThreadDeath || this is LinkageError

private fun hexToBytes(value: String): ByteArray = ByteArray(value.length / 2) { index ->
    value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
