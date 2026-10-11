package io.github.ninbyo02.lami.asr.foundation.model

import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

fun interface DirectorySync {
    fun sync(directory: Path)

    companion object {
        val NO_OP = DirectorySync { }
        val NIO = DirectorySync { directory ->
            FileChannel.open(directory, StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        }
    }
}

fun interface AtomicDirectoryMove {
    fun move(source: Path, target: Path)

    companion object {
        val NIO = AtomicDirectoryMove { source, target ->
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw java.nio.file.FileAlreadyExistsException("model target")
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

class ModelInstallationException internal constructor(message: String) :
    IllegalStateException(message)

class AtomicModelInstaller(
    root: Path,
    private val source: ModelArtifactSource,
    private val verifier: ModelArtifactVerifier = ModelArtifactVerifier(),
    private val approvalPolicy: ModelApprovalPolicy = ModelApprovalPolicy.DENY_ALL,
    private val directorySync: DirectorySync = DirectorySync.NIO,
    private val atomicMove: AtomicDirectoryMove = AtomicDirectoryMove.NIO,
) {
    private val root = root.toAbsolutePath().normalize()
    private val rootMonitor = ROOT_MONITORS.computeIfAbsent(this.root.toString()) { Any() }

    fun install(
        manifest: ModelArtifactManifest,
        cancellation: CopyCancellationCheck = CopyCancellationCheck.NONE,
    ): VerifiedModel {
        manifest.requireInstallable()
        check(approvalPolicy.isApproved(manifest)) { "model manifest is not allowlisted" }
        return synchronized(rootMonitor) {
            withFileSystemLock {
                installLocked(manifest, cancellation)
            }
        }
    }

    fun removeCorruptInstallation(manifest: ModelArtifactManifest): Boolean {
        manifest.requireInstallable()
        check(approvalPolicy.isApproved(manifest)) { "model manifest is not allowlisted" }
        return synchronized(rootMonitor) {
            withFileSystemLock {
                val target = targetFor(manifest)
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    false
                } else {
                    try {
                        verifyInstalled(manifest, target)
                        false
                    } catch (_: ModelInstallationException) {
                        deleteTree(target)
                        syncDirectory(target.parent)
                        cleanEmptyParents(target.parent, manifest)
                        true
                    }
                }
            }
        }
    }

    private fun installLocked(
        manifest: ModelArtifactManifest,
        cancellation: CopyCancellationCheck,
    ): VerifiedModel {
        cleanStaleTemps()
        val target = targetFor(manifest)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return verifyInstalled(manifest, target)
        }

        createDirectoriesSafely(target.parent)
        val temp = root.resolve(".tmp-${UUID.randomUUID()}")
        var moved = false
        try {
            Files.createDirectory(temp)
            copyDeclaredFiles(manifest, temp, cancellation)
            writeCompletionMarker(temp, manifest.contentFingerprint)
            verifyExactTree(manifest, temp)
            syncDirectory(temp)
            try {
                atomicMove.move(temp, target)
                moved = true
            } catch (failure: Error) {
                if (failure.isFatalModelFailure()) throw failure
                throw operationFailure("atomic-move", failure)
            } catch (failure: Throwable) {
                throw operationFailure("atomic-move", failure)
            }
            syncDirectory(target.parent)
            syncDirectory(root)
            return verifyInstalled(manifest, target)
        } catch (_: SanitizedInstallationCancellation) {
            throw SanitizedInstallationCancellation()
        } catch (_: CancellationException) {
            throw SanitizedInstallationCancellation()
        } catch (failure: ModelInstallationException) {
            throw failure
        } catch (failure: ModelArtifactVerificationException) {
            throw verificationAsInstallationFailure(failure)
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("install", failure)
        } catch (failure: Throwable) {
            throw operationFailure("install", failure)
        } finally {
            if (!moved) {
                deleteTreeBestEffort(temp)
                cleanEmptyParentsBestEffort(target.parent, manifest)
            }
        }
    }

    private fun copyDeclaredFiles(
        manifest: ModelArtifactManifest,
        temp: Path,
        cancellation: CopyCancellationCheck,
    ) {
        val buffer = ByteArray(64 * 1024)
        ModelFileRole.entries.forEach { role ->
            val file = manifest.filesByRole.getValue(role)
            val destination = temp.resolve(file.relativePath).normalize()
            check(destination.startsWith(temp)) { "model destination escaped temp" }
            Files.createDirectories(destination.parent)
            try {
                source.open(file.relativePath).use { input ->
                    FileChannel.open(
                        destination,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                    ).use { channel ->
                        val output = Channels.newOutputStream(channel)
                        verifier.copyAndVerify(file, input, output, cancellation, buffer)
                        output.flush()
                        channel.force(true)
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ModelArtifactVerificationException) {
                throw verificationAsInstallationFailure(failure)
            } catch (failure: Error) {
                if (failure.isFatalModelFailure()) throw failure
                throw ModelInstallationException(
                    "model installation failed: role=${role.safeName()} type=${failure.safeType()}",
                )
            } catch (failure: Throwable) {
                throw ModelInstallationException(
                    "model installation failed: role=${role.safeName()} type=${failure.safeType()}",
                )
            }
        }
    }

    private fun writeCompletionMarker(temp: Path, fingerprint: String) {
        val marker = temp.resolve(COMPLETION_MARKER)
        FileChannel.open(
            marker,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        ).use { channel ->
            val bytes = fingerprint.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    private fun verifyInstalled(
        manifest: ModelArtifactManifest,
        target: Path,
    ): VerifiedModel {
        verifyExactTree(manifest, target)
        val marker = readCompletionMarker(target.resolve(COMPLETION_MARKER))
        if (marker != manifest.contentFingerprint) {
            throw ModelInstallationException("model installation failed: reason=marker")
        }

        val buffer = ByteArray(64 * 1024)
        ModelFileRole.entries.forEach { role ->
            val file = manifest.filesByRole.getValue(role)
            val path = target.resolve(file.relativePath).normalize()
            try {
                Files.newByteChannel(
                    path,
                    setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                ).use { channel ->
                    Channels.newInputStream(channel).use { input ->
                        verifier.copyAndVerify(file, input, output = null, buffer = buffer)
                    }
                }
            } catch (failure: ModelArtifactVerificationException) {
                throw verificationAsInstallationFailure(failure)
            } catch (failure: Error) {
                if (failure.isFatalModelFailure()) throw failure
                throw ModelInstallationException(
                    "model installation failed: role=${role.safeName()} type=${failure.safeType()}",
                )
            } catch (failure: Throwable) {
                throw ModelInstallationException(
                    "model installation failed: role=${role.safeName()} type=${failure.safeType()}",
                )
            }
        }
        return VerifiedModel.fromVerifiedInstallation(manifest, target)
    }

    private fun readCompletionMarker(marker: Path): String {
        try {
            if (Files.size(marker) != FINGERPRINT_LENGTH.toLong()) {
                throw ModelInstallationException("model installation failed: reason=marker")
            }
            Files.newByteChannel(
                marker,
                setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
            ).use { channel ->
                val buffer = ByteBuffer.allocate(FINGERPRINT_LENGTH + 1)
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // Read one bounded marker and one sentinel byte.
                }
                if (buffer.position() != FINGERPRINT_LENGTH) {
                    throw ModelInstallationException("model installation failed: reason=marker")
                }
                buffer.flip()
                val bytes = ByteArray(FINGERPRINT_LENGTH)
                buffer.get(bytes)
                return String(bytes, StandardCharsets.US_ASCII)
            }
        } catch (failure: ModelInstallationException) {
            throw failure
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("read-marker", failure)
        } catch (failure: Throwable) {
            throw operationFailure("read-marker", failure)
        }
    }

    private fun verifyExactTree(manifest: ModelArtifactManifest, tree: Path) {
        if (!Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(tree)) {
            throw ModelInstallationException("model installation failed: reason=unexpected-tree")
        }
        val expectedFiles = manifest.filesByRole.values.map { it.relativePath }.toMutableSet().apply {
            add(COMPLETION_MARKER)
        }
        val expectedDirectories = mutableSetOf<String>()
        manifest.filesByRole.values.forEach { file ->
            var parent = tree.fileSystem.getPath(file.relativePath).parent
            while (parent != null) {
                expectedDirectories += parent.toString().replace('\\', '/')
                parent = parent.parent
            }
        }

        try {
            Files.walk(tree).use { paths ->
                val iterator = paths.iterator()
                while (iterator.hasNext()) {
                    val path = iterator.next()
                    if (path == tree) continue
                    if (Files.isSymbolicLink(path)) unexpectedTree()
                    val relative = tree.relativize(path).toString().replace('\\', '/')
                    when {
                        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> {
                            if (relative !in expectedDirectories) unexpectedTree()
                        }
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
                            if (relative !in expectedFiles) unexpectedTree()
                        }
                        else -> unexpectedTree()
                    }
                }
            }
        } catch (failure: ModelInstallationException) {
            throw failure
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("verify-tree", failure)
        } catch (failure: Throwable) {
            throw operationFailure("verify-tree", failure)
        }
    }

    private fun unexpectedTree(): Nothing =
        throw ModelInstallationException("model installation failed: reason=unexpected-tree")

    private fun targetFor(manifest: ModelArtifactManifest): Path {
        val target = root.resolve(manifest.id)
            .resolve(manifest.version)
            .resolve(manifest.contentFingerprint)
            .normalize()
        check(target.startsWith(root)) { "model target escaped root" }
        ensureNoSymlinkComponents(target)
        return target
    }

    private fun cleanStaleTemps() {
        Files.list(root).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate.fileName.toString().startsWith(TEMP_PREFIX)) {
                    deleteTree(candidate)
                    syncDirectory(root)
                }
            }
        }
    }

    private fun cleanEmptyParentsBestEffort(start: Path?, manifest: ModelArtifactManifest) {
        try {
            cleanEmptyParents(start, manifest)
        } catch (_: Throwable) {
            // Best-effort rollback cannot replace the primary failure.
        }
    }

    private fun cleanEmptyParents(start: Path?, manifest: ModelArtifactManifest) {
        val versionDirectory = root.resolve(manifest.id).resolve(manifest.version)
        val idDirectory = root.resolve(manifest.id)
        listOf(start, versionDirectory, idDirectory).filterNotNull().distinct().forEach { directory ->
            if (directory.startsWith(root) && directory != root && isEmptyDirectory(directory)) {
                Files.deleteIfExists(directory)
                directory.parent?.let(::syncDirectory)
            }
        }
    }

    private fun isEmptyDirectory(path: Path): Boolean {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return false
        return Files.list(path).use { paths -> !paths.findAny().isPresent }
    }

    private fun createDirectoriesSafely(directory: Path) {
        ensureNoSymlinkComponents(directory)
        val missing = mutableListOf<Path>()
        var candidate: Path? = directory
        while (candidate != null && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(candidate)
            candidate = candidate.parent
        }
        try {
            Files.createDirectories(directory)
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("create-directory", failure)
        } catch (failure: Throwable) {
            throw operationFailure("create-directory", failure)
        }
        ensureNoSymlinkComponents(directory)
        check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "model installation failed: reason=unsafe-directory"
        }
        missing.asReversed().forEach { created ->
            created.parent?.let(::syncDirectory)
        }
    }

    private fun ensureNoSymlinkComponents(path: Path) {
        var current = path.root ?: throw ModelInstallationException(
            "model installation failed: reason=unsafe-directory",
        )
        path.forEach { component ->
            current = current.resolve(component)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) {
                    throw ModelInstallationException(
                        "model installation failed: reason=unsafe-directory",
                    )
                }
            }
        }
    }

    private fun syncDirectory(directory: Path) {
        try {
            directorySync.sync(directory)
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("directory-sync", failure)
        } catch (failure: Throwable) {
            throw operationFailure("directory-sync", failure)
        }
    }

    private fun <T> withFileSystemLock(block: () -> T): T {
        createDirectoriesSafely(root)
        val lockPath = root.resolve(LOCK_FILE)
        val lockExisted = Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
        if (lockExisted &&
            (Files.isSymbolicLink(lockPath) ||
                !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS))
        ) {
            throw ModelInstallationException("model installation failed: reason=unsafe-lock")
        }
        try {
            return FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                if (!lockExisted) syncDirectory(root)
                channel.lock().use { block() }
            }
        } catch (failure: ModelInstallationException) {
            throw failure
        } catch (_: SanitizedInstallationCancellation) {
            throw SanitizedInstallationCancellation()
        } catch (failure: Error) {
            if (failure.isFatalModelFailure()) throw failure
            throw operationFailure("lock", failure)
        } catch (failure: Throwable) {
            throw operationFailure("lock", failure)
        }
    }

    private fun deleteTreeBestEffort(path: Path) {
        try {
            val parent = path.parent
            deleteTree(path)
            parent?.let(::syncDirectory)
        } catch (_: Throwable) {
            // Best-effort rollback cannot replace the primary failure.
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(path).use { paths ->
            val entries = mutableListOf<Path>()
            paths.forEach(entries::add)
            entries.asReversed().forEach(Files::deleteIfExists)
        }
    }

    private fun verificationAsInstallationFailure(failure: ModelArtifactVerificationException) =
        ModelInstallationException(
            "model installation failed: ${failure.safeDetail}",
        )

    private fun operationFailure(operation: String, failure: Throwable) =
        ModelInstallationException(
            "model installation failed: operation=$operation type=${failure.safeType()}",
        )

    private class SanitizedInstallationCancellation :
        CancellationException("model installation cancelled")

    private companion object {
        const val COMPLETION_MARKER = ".install-complete"
        const val FINGERPRINT_LENGTH = 64
        const val LOCK_FILE = ".installer.lock"
        const val TEMP_PREFIX = ".tmp-"
        val ROOT_MONITORS = ConcurrentHashMap<String, Any>()
    }
}
