package io.github.ninbyo02.lami.npu

import android.content.Context
import java.io.File

object Qairt244ModelPathResolver {
    private val preferredTokens = listOf("gemma", "qualcomm", "sm8750")

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
        val candidates = localModelsDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (candidates.isEmpty()) {
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

        val selected = if (candidates.size == 1) {
            candidates.single()
        } else {
            selectPreferred(candidates)
                ?: return Resolution(
                    path = null,
                    reasonCode = REASON_MODEL_FILE_AMBIGUOUS,
                    candidates = candidates.map { it.absolutePath },
                    checkedPath = null,
                    checkedExists = null,
                    checkedCanRead = null,
                    checkedLength = null,
                )
        }

        val selectedExists = selected.exists()
        val selectedCanRead = selected.canRead()
        val selectedLength = selected.length()

        return if (!selectedExists || !selectedCanRead || selectedLength <= 0L) {
            Resolution(
                path = null,
                reasonCode = REASON_MODEL_FILE_INVALID,
                candidates = candidates.map { it.absolutePath },
                checkedPath = selected.absolutePath,
                checkedExists = selectedExists,
                checkedCanRead = selectedCanRead,
                checkedLength = selectedLength,
            )
        } else {
            Resolution(
                path = selected.absolutePath,
                reasonCode = REASON_OK,
                candidates = candidates.map { it.absolutePath },
                checkedPath = selected.absolutePath,
                checkedExists = selectedExists,
                checkedCanRead = selectedCanRead,
                checkedLength = selectedLength,
            )
        }
    }

    private fun selectPreferred(candidates: List<File>): File? {
        val scored = candidates.map { file ->
            file to preferredTokens.count { token -> file.name.contains(token, ignoreCase = true) }
        }
        val maxScore = scored.maxOf { it.second }
        if (maxScore <= 0) return null
        val best = scored.filter { it.second == maxScore }
        return best.singleOrNull()?.first
    }

    const val REASON_OK = "ok"
    const val REASON_MODEL_FILE_NOT_FOUND = "model_file_not_found"
    const val REASON_MODEL_FILE_AMBIGUOUS = "model_file_ambiguous"
    const val REASON_MODEL_FILE_INVALID = "model_file_invalid"
}
