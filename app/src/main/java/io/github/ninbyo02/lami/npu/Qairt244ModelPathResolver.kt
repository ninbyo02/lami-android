package io.github.ninbyo02.lami.npu

import android.content.Context
import java.io.File

object Qairt244ModelPathResolver {
    const val CANONICAL_MODEL_BASENAME = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
    private val TIMESTAMPED_MODEL_BASENAME = Regex("^\\d+_${Regex.escape(CANONICAL_MODEL_BASENAME)}$")

    data class RequiredSm8750ModelInfo(
        val resolvedModelBasename: String,
        val canonicalModelBasename: String,
        val timestampPrefixStripped: Boolean,
        val required: Boolean,
    )

    data class Resolution(
        val path: String?,
        val reasonCode: String,
        val candidates: List<String>,
        val checkedPath: String?,
        val checkedExists: Boolean?,
        val checkedCanRead: Boolean?,
        val checkedLength: Long?,
    ) {
        val resolved: Boolean = path != null && reasonCode == REASON_OK
        val modelInfo: RequiredSm8750ModelInfo? =
            path?.let(Qairt244ModelPathResolver::requiredSm8750ModelInfo)
    }

    data class CleanupResult(
        val selectedPathValid: Boolean,
        val deletedPaths: List<String>,
        val failedPaths: List<String>,
    )

    fun resolve(
        context: Context,
        preferredModelPath: String? = null,
    ): Resolution = resolve(
        localModelsDir = context.applicationContext.filesDir.resolve("local_models"),
        preferredModelPath = preferredModelPath,
    )

    fun resolve(
        localModelsDir: File,
        preferredModelPath: String? = null,
    ): Resolution {
        val litertlmFiles = localModelsDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val executionCandidates = litertlmFiles.filter(::isCompatibleSm8750Model)
        val candidatePaths = executionCandidates.map { it.absolutePath }

        preferredModelPath?.trim()?.takeIf { it.isNotBlank() }?.let { preferredPath ->
            val modelsDirectory = runCatching { localModelsDir.canonicalFile }.getOrNull()
            val preferred = runCatching { File(preferredPath).canonicalFile }.getOrNull()
            val preferredExists = preferred?.exists() ?: false
            val preferredCanRead = preferred?.canRead() ?: false
            val preferredLength = preferred?.length() ?: 0L
            val preferredIsManaged = preferred?.parentFile == modelsDirectory
            val preferredIsCompatible = preferred?.let(::isCompatibleSm8750Model) == true
            val preferredValid =
                preferredIsManaged &&
                    preferredIsCompatible &&
                    preferred.isFile &&
                    preferredExists &&
                    preferredCanRead &&
                    preferredLength > 0L
            return Resolution(
                path = preferred?.absolutePath.takeIf { preferredValid },
                reasonCode = if (preferredValid) REASON_OK else REASON_MODEL_FILE_INVALID,
                candidates = candidatePaths,
                checkedPath = preferred?.absolutePath ?: preferredPath,
                checkedExists = preferredExists,
                checkedCanRead = preferredCanRead,
                checkedLength = preferredLength,
            )
        }

        if (executionCandidates.isEmpty()) {
            return Resolution(
                path = null,
                reasonCode = REASON_MODEL_FILE_NOT_FOUND,
                candidates = emptyList(),
                checkedPath = null,
                checkedExists = null,
                checkedCanRead = null,
                checkedLength = null,
            )
        }

        if (executionCandidates.size > 1) {
            return Resolution(
                path = null,
                reasonCode = REASON_MODEL_FILE_AMBIGUOUS,
                candidates = candidatePaths,
                checkedPath = null,
                checkedExists = null,
                checkedCanRead = null,
                checkedLength = null,
            )
        }

        return resolve(localModelsDir, executionCandidates.single().absolutePath)
    }

    fun cleanupOrphanedCompatibleCopies(
        localModelsDir: File,
        selectedModelPath: String,
    ): CleanupResult {
        val selectedResolution = resolve(localModelsDir, selectedModelPath)
        if (!selectedResolution.resolved) {
            return CleanupResult(
                selectedPathValid = false,
                deletedPaths = emptyList(),
                failedPaths = emptyList(),
            )
        }

        val selected = File(requireNotNull(selectedResolution.path)).canonicalFile
        val orphaned = selectedResolution.candidates
            .map(::File)
            .filter { candidate ->
                runCatching { candidate.canonicalFile != selected }.getOrDefault(false)
            }
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        orphaned.forEach { candidate ->
            if (!candidate.exists() || candidate.delete()) {
                deleted += candidate.absolutePath
            } else {
                failed += candidate.absolutePath
            }
        }
        return CleanupResult(
            selectedPathValid = true,
            deletedPaths = deleted,
            failedPaths = failed,
        )
    }

    private fun isCompatibleSm8750Model(file: File): Boolean {
        return isRequiredSm8750ModelPath(file.absolutePath)
    }

    fun isRequiredSm8750ModelPath(path: String): Boolean =
        requiredSm8750ModelInfo(path).required

    fun requiredSm8750ModelInfo(path: String): RequiredSm8750ModelInfo {
        val basename = File(path).name
        val timestamped = TIMESTAMPED_MODEL_BASENAME.matches(basename)
        return RequiredSm8750ModelInfo(
            resolvedModelBasename = basename,
            canonicalModelBasename = CANONICAL_MODEL_BASENAME,
            timestampPrefixStripped = timestamped,
            required = basename == CANONICAL_MODEL_BASENAME || timestamped,
        )
    }

    const val REASON_OK = "ok"
    const val REASON_MODEL_FILE_NOT_FOUND = "model_file_not_found"
    const val REASON_MODEL_FILE_AMBIGUOUS = "model_file_ambiguous"
    const val REASON_MODEL_FILE_INVALID = "model_file_invalid"
}
