package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS3MarkdownMapper {
    fun map(
        s1Result: NpuStandardRouteS1Result,
        finalizeMarkdown: (String) -> String = { it.trim() },
    ): NpuStandardRouteS3MarkdownMapping {
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS,
            )
        }

        val sanitizedInput = s1Result.sanitizedOutput.trim()
        val sourceDisplayText = s1Result.displayText.trim()
        if (sanitizedInput.isBlank() || sourceDisplayText.isBlank()) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT,
            )
        }

        val finalizedText = finalizeMarkdown(sanitizedInput).trim()
        if (finalizedText.isBlank()) {
            return NpuStandardRouteS3MarkdownMapping(
                markdownCandidate = null,
                failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT,
            )
        }

        return NpuStandardRouteS3MarkdownMapping(
            markdownCandidate = NpuStandardRouteS3MarkdownCandidate(
                sanitizedInputText = sanitizedInput,
                sourceDisplayText = sourceDisplayText,
                finalizedText = finalizedText,
            ),
        )
    }
}
