package io.github.ninbyo02.lami.npu

import android.content.Context
import java.io.File

object Qairt244ModelPathResolver {
    private const val REQUIRED_MODEL_NAME = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
    private const val REQUIRED_TOKEN = "qualcomm_sm8750"
    private const val REJECTED_PLATFORM_TOKEN = "qcs8275"

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
        val name = file.name
        return name.contains(REQUIRED_TOKEN, ignoreCase = true) &&
            !name.contains(REJECTED_PLATFORM_TOKEN, ignoreCase = true)
    }

    fun isRequiredSm8750ModelPath(path: String): Boolean =
        File(path).name == REQUIRED_MODEL_NAME

    const val REASON_OK = "ok"
    const val REASON_MODEL_FILE_NOT_FOUND = "model_file_not_found"
    const val REASON_MODEL_FILE_AMBIGUOUS = "model_file_ambiguous"
    const val REASON_MODEL_FILE_INVALID = "model_file_invalid"
}
