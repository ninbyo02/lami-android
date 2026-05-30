package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1UiInput(
    val success: Boolean,
    val sanitizedOutput: String?,
    val reasonCode: String,
    val decodeMs: Long?,
    val backendEvidence: String?,
    val maxOutputTokens: Int,
    val artifactPath: String?,
    val qualityClassification: String,
    val rollback: Boolean = false,
    val artifactFresh: Boolean = true,
    val fallbackUsed: Boolean = false,
    val timeout: Boolean = false,
    val freshCrash: Boolean = false,
    val standardRouteConnected: Boolean = false,
    val normalUiRouteConnected: Boolean = false,
    val shouldPersistToDb: Boolean = false,
    val shouldSpeakTts: Boolean = false,
    val shouldRenderMarkdown: Boolean = false,
    val shouldStream: Boolean = false,
)

data class DevOnlyNpuPhaseH1UiState(
    val visible: Boolean,
    val devLabel: String,
    val status: Status,
    val outputPreview: String?,
    val reasonCode: String,
    val decodeMsText: String,
    val backendEvidenceText: String,
    val maxOutputTokensText: String,
    val artifactText: String,
    val rollback: Boolean,
    val shouldPersistToDb: Boolean = false,
    val shouldSpeakTts: Boolean = false,
    val shouldRenderMarkdown: Boolean = false,
    val shouldStream: Boolean = false,
) {
    enum class Status {
        SUCCESS,
        FAILURE,
        ROLLBACK,
        HIDDEN,
    }
}

object DevOnlyNpuPhaseH1Presenter {
    const val DEV_LABEL = "DEV NPU transient preview"

    fun present(input: DevOnlyNpuPhaseH1UiInput): DevOnlyNpuPhaseH1UiState {
        val gateFailure = gateFailureReason(input)
        val status = when {
            gateFailure != null -> DevOnlyNpuPhaseH1UiState.Status.ROLLBACK
            input.rollback -> DevOnlyNpuPhaseH1UiState.Status.ROLLBACK
            input.success -> DevOnlyNpuPhaseH1UiState.Status.SUCCESS
            else -> DevOnlyNpuPhaseH1UiState.Status.FAILURE
        }
        val visible = when (status) {
            DevOnlyNpuPhaseH1UiState.Status.SUCCESS -> true
            DevOnlyNpuPhaseH1UiState.Status.FAILURE -> true
            DevOnlyNpuPhaseH1UiState.Status.ROLLBACK -> false
            DevOnlyNpuPhaseH1UiState.Status.HIDDEN -> false
        }
        val reasonCode = gateFailure ?: input.reasonCode

        return DevOnlyNpuPhaseH1UiState(
            visible = visible,
            devLabel = DEV_LABEL,
            status = status,
            outputPreview = if (status == DevOnlyNpuPhaseH1UiState.Status.SUCCESS) {
                input.sanitizedOutput?.trim()?.ifBlank { null }
            } else {
                null
            },
            reasonCode = reasonCode,
            decodeMsText = "decode_ms=${input.decodeMs?.toString() ?: "unknown"}",
            backendEvidenceText = shortBackendEvidence(input.backendEvidence),
            maxOutputTokensText = "maxOutputTokens=${input.maxOutputTokens}",
            artifactText = shortArtifactPath(input.artifactPath),
            rollback = status == DevOnlyNpuPhaseH1UiState.Status.ROLLBACK,
            shouldPersistToDb = false,
            shouldSpeakTts = false,
            shouldRenderMarkdown = false,
            shouldStream = false,
        )
    }

    private fun gateFailureReason(input: DevOnlyNpuPhaseH1UiInput): String? {
        val sanitizedOutput = input.sanitizedOutput?.trim().orEmpty()
        return when {
            input.rollback -> input.reasonCode.ifBlank { "rollback" }
            !input.artifactFresh -> "stale_artifact"
            input.fallbackUsed -> "fallback_used"
            input.timeout -> "timeout"
            input.freshCrash -> "fresh_crash"
            input.standardRouteConnected -> "standard_route_connected"
            input.normalUiRouteConnected -> "normal_ui_route_connected"
            input.shouldPersistToDb -> "db_connected"
            input.shouldSpeakTts -> "tts_connected"
            input.shouldRenderMarkdown -> "markdown_connected"
            input.shouldStream -> "streaming_connected"
            input.maxOutputTokens != DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS -> "max_output_tokens_not_128"
            input.success && sanitizedOutput.isBlank() -> "empty_sanitized_output"
            input.success && input.qualityClassification != "natural_japanese" -> "quality_not_natural_japanese"
            input.success && containsTemplateArtifact(sanitizedOutput) -> "template_artifact_after_sanitize"
            else -> null
        }
    }

    private fun containsTemplateArtifact(value: String): Boolean =
        value.contains("<end_of_turn>") || value.contains("<start_of_turn>")

    private fun shortBackendEvidence(value: String?): String {
        val evidence = value.orEmpty()
        return when {
            evidence.contains("QNN_HTP_V79_FastRPC") -> "backendEvidence=QNN_HTP_V79_FastRPC"
            evidence.isBlank() -> "backendEvidence=none"
            else -> "backendEvidence=${evidence.substringBefore('\n').take(64)}"
        }
    }

    private fun shortArtifactPath(value: String?): String {
        val path = value.orEmpty()
        if (path.isBlank()) return "artifact=none"
        val segments = path.split('/').filter { it.isNotBlank() }
        return "artifact=${segments.takeLast(2).joinToString("/")}"
    }
}
