package io.github.ninbyo02.lami.asr.foundation.model

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicModelInstallerTest {
    @Test
    fun `successful install is fingerprint addressed synced and exactly verified`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val syncs = mutableListOf<Path>()
        val installer = fixture.installer(root, DirectorySync { directory -> syncs.add(directory) })

        val verified = installer.install(fixture.manifest)
        val target = root.resolve(fixture.manifest.id)
            .resolve(fixture.manifest.version)
            .resolve(fixture.manifest.contentFingerprint)

        assertTrue(Files.isDirectory(target))
        assertEquals(target.resolve("encoder.onnx"), verified.fileFor(ModelFileRole.ENCODER))
        assertEquals(fixture.manifest.contentFingerprint, String(Files.readAllBytes(target.resolve(".install-complete"))))
        assertEquals(expectedTree(), relativeTree(target))
        assertTrue(syncs.any { it.fileName.toString().startsWith(".tmp-") })
        assertTrue(syncs.contains(target.parent))
        assertTrue(syncs.contains(root))
        assertFalse(Files.list(root).use { paths -> paths.anyMatch { it.fileName.toString().startsWith(".tmp-") } })
    }

    @Test
    fun `digest mismatch rolls back temp and exposes no source payload`() {
        val fixture = Fixture(overrides = mapOf("joiner.onnx" to "SECRET".toByteArray()))
        val root = Files.createTempDirectory("model-installer")

        val failure = assertThrows(ModelInstallationException::class.java) {
            fixture.installer(root).install(fixture.manifest)
        }

        assertEquals("model installation failed: role=joiner reason=digest", failure.message)
        assertFalse(failure.message.orEmpty().contains("SECRET"))
        assertEquals(emptySet<String>(), relativeTree(root))
    }

    @Test
    fun `existing exact target is reverified and reused without source access`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        fixture.installer(root).install(fixture.manifest)
        fixture.openCount = 0
        val noSource = ModelArtifactSource { throw AssertionError("source must not be opened") }

        val reused = AtomicModelInstaller(
            root = root,
            source = noSource,
            approvalPolicy = PinnedModelApprovalPolicy(setOf(fixture.manifest.approvalFingerprint)),
            directorySync = DirectorySync.NO_OP,
        ).install(fixture.manifest)

        assertEquals(fixture.manifest.contentFingerprint, reused.contentFingerprint)
        assertEquals(0, fixture.openCount)
    }

    @Test
    fun `unexpected final tree entry fails closed`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        fixture.installer(root).install(fixture.manifest)
        val target = target(root, fixture.manifest)
        Files.write(target.resolve("undeclared.bin"), "unexpected".toByteArray())

        val failure = assertThrows(ModelInstallationException::class.java) {
            fixture.installer(root).install(fixture.manifest)
        }

        assertEquals("model installation failed: reason=unexpected-tree", failure.message)
        assertTrue(Files.exists(target.resolve("undeclared.bin")))
    }

    @Test
    fun `failed atomic move cleans temp and never exposes final directory`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val move = AtomicDirectoryMove { _, _ -> throw IOException("PRIVATE_MOVE_CANARY") }

        val failure = assertThrows(ModelInstallationException::class.java) {
            fixture.installer(root, atomicMove = move).install(fixture.manifest)
        }

        assertEquals("model installation failed: operation=atomic-move type=IOException", failure.message)
        assertFalse(failure.message.orEmpty().contains("PRIVATE_MOVE_CANARY"))
        assertFalse(Files.exists(target(root, fixture.manifest)))
        assertFalse(Files.list(root).use { it.anyMatch { path -> path.fileName.toString().startsWith(".tmp-") } })
    }

    @Test
    fun `stale owned temp is cleaned while unrelated root entry is preserved`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val stale = Files.createDirectory(root.resolve(".tmp-stale"))
        Files.write(stale.resolve("partial"), "partial".toByteArray())
        val unrelated = Files.write(root.resolve("keep.txt"), "keep".toByteArray())

        fixture.installer(root).install(fixture.manifest)

        assertFalse(Files.exists(stale))
        assertEquals("keep", String(Files.readAllBytes(unrelated)))
    }

    @Test
    fun `corrupt target requires explicit removal before reinstall`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val installer = fixture.installer(root)
        installer.install(fixture.manifest)
        Files.write(target(root, fixture.manifest).resolve("encoder.onnx"), "corrupt".toByteArray())

        assertThrows(ModelInstallationException::class.java) {
            installer.install(fixture.manifest)
        }
        assertTrue(installer.removeCorruptInstallation(fixture.manifest))
        assertFalse(Files.exists(target(root, fixture.manifest)))

        val repaired = installer.install(fixture.manifest)
        assertEquals(fixture.manifest.contentFingerprint, repaired.contentFingerprint)
        assertFalse(installer.removeCorruptInstallation(fixture.manifest))
    }

    @Test
    fun `installation cancellation cleans temp and is sanitized`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        var checks = 0
        val failure = assertThrows(CancellationException::class.java) {
            fixture.installer(root).install(
                fixture.manifest,
                CopyCancellationCheck {
                    checks += 1
                    if (checks == 2) throw CancellationException("PRIVATE_CANCEL_CANARY")
                },
            )
        }

        assertEquals("model installation cancelled", failure.message)
        assertEquals(emptySet<String>(), relativeTree(root))
    }

    @Test
    fun `default approval policy rejects caller supplied approved manifests`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val installer = AtomicModelInstaller(
            root = root,
            source = fixture,
            directorySync = DirectorySync.NO_OP,
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            installer.install(fixture.manifest)
        }

        assertEquals("model manifest is not allowlisted", failure.message)
        assertEquals(0, fixture.openCount)
    }

    @Test
    fun `symlinked model id ancestor is rejected without touching external target`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val external = Files.createTempDirectory("external-model-target")
        Files.createSymbolicLink(root.resolve(fixture.manifest.id), external)

        val failure = assertThrows(ModelInstallationException::class.java) {
            fixture.installer(root).install(fixture.manifest)
        }

        assertTrue(failure.message.orEmpty().contains("unsafe-directory"))
        assertEquals(emptySet<String>(), relativeTree(external))
    }

    @Test
    fun `symlinked persistent lock is rejected without opening external file`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val external = Files.createTempFile("external-lock", ".txt")
        Files.createSymbolicLink(root.resolve(".installer.lock"), external)

        val failure = assertThrows(ModelInstallationException::class.java) {
            fixture.installer(root).install(fixture.manifest)
        }

        assertEquals("model installation failed: reason=unsafe-lock", failure.message)
        assertEquals(0L, Files.size(external))
    }

    @Test
    fun `oversized completion marker is rejected with bounded failure`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val installer = fixture.installer(root)
        installer.install(fixture.manifest)
        val marker = target(root, fixture.manifest).resolve(".install-complete")
        Files.write(marker, ByteArray(1_024) { 7 })

        val failure = assertThrows(ModelInstallationException::class.java) {
            installer.install(fixture.manifest)
        }

        assertEquals("model installation failed: reason=marker", failure.message)
    }

    @Test
    fun `target appearing at publish time is not overwritten`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val move = AtomicDirectoryMove { source, target ->
            Files.createDirectory(target)
            AtomicDirectoryMove.NIO.move(source, target)
        }
        val installer = fixture.installer(root, atomicMove = move)

        val failure = assertThrows(ModelInstallationException::class.java) {
            installer.install(fixture.manifest)
        }

        assertTrue(failure.message.orEmpty().contains("atomic-move"))
        assertTrue(Files.isDirectory(target(root, fixture.manifest)))
        assertEquals(emptySet<String>(), relativeTree(target(root, fixture.manifest)))
    }

    @Test
    fun `concurrent installers for the same root serialize and reuse the verified target`() {
        val fixture = Fixture()
        val root = Files.createTempDirectory("model-installer")
        val firstOpen = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstFailure = AtomicReference<Throwable?>()
        val secondFailure = AtomicReference<Throwable?>()
        val secondFinished = CountDownLatch(1)
        val blockingSource = ModelArtifactSource { relativePath ->
            if (relativePath == "encoder.onnx") {
                firstOpen.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS)) { "test release timeout" }
            }
            fixture.open(relativePath)
        }
        var secondSourceOpened = false
        val forbiddenSecondSource = ModelArtifactSource {
            secondSourceOpened = true
            throw AssertionError("second source must not be opened")
        }
        val approval = PinnedModelApprovalPolicy(setOf(fixture.manifest.approvalFingerprint))
        val first = AtomicModelInstaller(
            root,
            blockingSource,
            approvalPolicy = approval,
            directorySync = DirectorySync.NO_OP,
        )
        val second = AtomicModelInstaller(
            root,
            forbiddenSecondSource,
            approvalPolicy = approval,
            directorySync = DirectorySync.NO_OP,
        )

        val firstThread = Thread {
            try {
                first.install(fixture.manifest)
            } catch (failure: Throwable) {
                firstFailure.set(failure)
            }
        }
        val secondThread = Thread {
            try {
                second.install(fixture.manifest)
            } catch (failure: Throwable) {
                secondFailure.set(failure)
            } finally {
                secondFinished.countDown()
            }
        }

        firstThread.start()
        assertTrue(firstOpen.await(5, TimeUnit.SECONDS))
        secondThread.start()
        assertFalse(secondFinished.await(150, TimeUnit.MILLISECONDS))
        releaseFirst.countDown()
        firstThread.join(5_000)
        secondThread.join(5_000)

        assertFalse(firstThread.isAlive)
        assertFalse(secondThread.isAlive)
        assertEquals(null, firstFailure.get())
        assertEquals(null, secondFailure.get())
        assertFalse(secondSourceOpened)
        assertTrue(Files.isDirectory(target(root, fixture.manifest)))
    }

    private inner class Fixture(
        private val overrides: Map<String, ByteArray> = emptyMap(),
    ) : ModelArtifactSource {
        private val canonicalBytes = mapOf(
            "encoder.onnx" to "encoder".toByteArray(),
            "decoder.onnx" to "decoder".toByteArray(),
            "joiner.onnx" to "joiner".toByteArray(),
            "tokens.txt" to "tokens".toByteArray(),
        )
        var openCount = 0
        val manifest = manifestFor(canonicalBytes)

        override fun open(relativePath: String): InputStream {
            openCount += 1
            return ByteArrayInputStream(overrides[relativePath] ?: canonicalBytes.getValue(relativePath))
        }

        fun installer(
            root: Path,
            directorySync: DirectorySync = DirectorySync.NO_OP,
            atomicMove: AtomicDirectoryMove = AtomicDirectoryMove.NIO,
        ) = AtomicModelInstaller(
            root = root,
            source = this,
            approvalPolicy = PinnedModelApprovalPolicy(setOf(manifest.approvalFingerprint)),
            directorySync = directorySync,
            atomicMove = atomicMove,
        )
    }

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

    private fun file(role: ModelFileRole, path: String, bytes: Map<String, ByteArray>): ModelArtifactFile {
        val content = bytes.getValue(path)
        return ModelArtifactFile(
            role,
            path,
            content.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(content).toHex(),
        )
    }

    private fun target(root: Path, manifest: ModelArtifactManifest): Path =
        root.resolve(manifest.id).resolve(manifest.version).resolve(manifest.contentFingerprint)

    private fun expectedTree() = setOf(
        ".install-complete",
        "encoder.onnx",
        "decoder.onnx",
        "joiner.onnx",
        "tokens.txt",
    )

    private fun relativeTree(root: Path): Set<String> {
        if (!Files.exists(root)) return emptySet()
        return Files.walk(root).use { paths ->
            paths.filter { it != root }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { !it.endsWith(".install.lock") && !it.endsWith(".installer.lock") }
                .toList()
                .toSet()
        }
    }
}
