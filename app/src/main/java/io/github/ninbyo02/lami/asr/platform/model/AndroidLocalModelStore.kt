package io.github.ninbyo02.lami.asr.platform.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.model.AtomicModelInstaller
import io.github.ninbyo02.lami.asr.foundation.model.DirectorySync
import io.github.ninbyo02.lami.asr.foundation.model.ModelArtifactSource
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class AndroidLocalModelStore(private val context: Context) {
    private val appDataRoot: Path = context.dataDir.toPath().toRealPath()
    private val root: Path = context.noBackupFilesDir.toPath()
        .toRealPath()
        .resolve("asr-models")
        .toAbsolutePath()
        .normalize()

    private val stagingRoot: Path = context.filesDir.toPath()
        .toRealPath()
        .resolve("model-import")
        .resolve(ReazonSpeechModel.ID)
        .toAbsolutePath()
        .normalize()

    val stagingPath: String
        get() = stagingRoot.toString()

    fun missingStagedFiles(): List<String> = missingModelFiles(
        stagingRoot = stagingRoot,
        relativePaths = ReazonSpeechModel.manifest.filesByRole.values.map { it.relativePath },
    )

    fun loadOrInstall(): VerifiedModel =
        install(
            ModelArtifactSource { relativePath ->
                val path = stagingRoot.resolve(relativePath).normalize()
                check(path.parent == stagingRoot) { "model source escaped staging root" }
                Channels.newInputStream(
                    Files.newByteChannel(
                        path,
                        setOf<OpenOption>(
                            StandardOpenOption.READ,
                            LinkOption.NOFOLLOW_LINKS,
                        ),
                    ),
                )
            },
        ).also { clearStagingFiles() }

    fun importSelected(uris: List<Uri>): VerifiedModel {
        val fileNames = uris.map(::displayName)
        ReazonSpeechModel.requireExactSelection(fileNames)
        val filesByName = fileNames.zip(uris).toMap()
        val source = ModelArtifactSource { relativePath ->
            val uri = checkNotNull(filesByName[relativePath]) {
                "model selection is incomplete"
            }
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("model source is unavailable")
        }
        return install(source)
    }

    fun isInstalledAndVerified(): Boolean {
        if (!Files.isDirectory(installedModelPath(), LinkOption.NOFOLLOW_LINKS)) return false
        return runCatching { loadOrInstall() }.isSuccess
    }

    fun removeInstalledModel() {
        deleteTree(installedModelPath())
        clearStagingFiles()
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        } ?: throw IllegalArgumentException("model document has no display name")
    }

    private fun install(source: ModelArtifactSource): VerifiedModel =
        AtomicModelInstaller(
            root = root,
            source = source,
            approvalPolicy = ReazonSpeechModel.approvalPolicy,
            directorySync = AndroidDirectorySync,
        ).install(ReazonSpeechModel.manifest)

    private fun installedModelPath(): Path = root
        .resolve(ReazonSpeechModel.ID)
        .resolve(ReazonSpeechModel.VERSION)
        .resolve(ReazonSpeechModel.manifest.contentFingerprint)
        .normalize()

    private fun clearStagingFiles() {
        deleteTree(stagingRoot)
    }

    private fun deleteTree(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        check(normalized.startsWith(appDataRoot)) { "model path escaped app storage" }
        val directory = normalized.toFile()
        if (!directory.exists()) return
        check(directory.deleteRecursively()) { "model files could not be removed" }
    }
}

internal fun missingModelFiles(
    stagingRoot: Path,
    relativePaths: List<String>,
): List<String> {
    try {
        Files.createDirectories(stagingRoot)
    } catch (_: IOException) {
        return relativePaths
    } catch (_: SecurityException) {
        return relativePaths
    }

    return relativePaths.filter { name ->
        val file = stagingRoot.resolve(name).normalize()
        file.parent != stagingRoot ||
            !file.toFile().isFile ||
            Files.isSymbolicLink(file)
    }
}

private object AndroidDirectorySync : DirectorySync {
    override fun sync(directory: Path) {
        val descriptor = Os.open(directory.toString(), OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}
