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

    fun resolve(context: Context): Resolution =
        resolve(context.applicationContext.filesDir.resolve("local_models"))

    fun resolve(localModelsDir: File): Resolution {
        val litertlmFiles = localModelsDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val executionCandidates = litertlmFiles.filter(::isCompatibleSm8750Model)

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
                candidates = executionCandidates.map { it.absolutePath },
                checkedPath = null,
                checkedExists = null,
                checkedCanRead = null,
                checkedLength = null,
            )
        }

        val selected = executionCandidates.single()
        val selectedExists = selected.exists()
        val selectedCanRead = selected.canRead()
        val selectedLength = selected.length()

        return if (!selectedExists || !selectedCanRead || selectedLength <= 0L) {
            Resolution(
                path = null,
                reasonCode = REASON_MODEL_FILE_INVALID,
                candidates = executionCandidates.map { it.absolutePath },
                checkedPath = selected.absolutePath,
                checkedExists = selectedExists,
                checkedCanRead = selectedCanRead,
                checkedLength = selectedLength,
            )
        } else {
            Resolution(
                path = selected.absolutePath,
                reasonCode = REASON_OK,
                candidates = executionCandidates.map { it.absolutePath },
                checkedPath = selected.absolutePath,
                checkedExists = selectedExists,
                checkedCanRead = selectedCanRead,
                checkedLength = selectedLength,
            )
        }
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
