package io.github.ninbyo02.lami.npu

object DevOnlyNpuPhaseH1ArtifactMapper {
    fun fromKeyValueText(text: String): DevOnlyNpuPhaseH1UiInput =
        from(parseKeyValueText(text))

    fun from(values: Map<String, String>): DevOnlyNpuPhaseH1UiInput {
        val resultSuccess = values.value("result") == "success" ||
            values.value("success") == "true"
        val sanitizedOutput = values.value("sanitized_output").unescapeArtifactValue().trim()
        val qualityClassification = values.value("quality_classification")
        val backend = values.value("npu_backend")
        val backendEvidence = values.value("npu_backend_evidence")
        val maxOutputTokens = values.value("max_output_tokens")
            .toIntOrNull() ?: DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS
        val reasonCode = values.value("reasonCode")
            .ifBlank { values.value("reason_code") }
            .ifBlank { if (resultSuccess) "success" else "unknown_failure" }
        val gateFailure = if (resultSuccess) {
            gateFailureReason(
                sanitizedOutput = sanitizedOutput,
                qualityClassification = qualityClassification,
                backend = backend,
                backendEvidence = backendEvidence,
                maxOutputTokens = maxOutputTokens,
                values = values,
            )
        } else {
            null
        }
        val rollback = gateFailure != null

        return DevOnlyNpuPhaseH1UiInput(
            success = resultSuccess && !rollback,
            sanitizedOutput = if (resultSuccess && !rollback) sanitizedOutput else null,
            reasonCode = gateFailure ?: reasonCode,
            decodeMs = values.value("decode_ms").toLongOrNull()
                ?: values.value("decode_elapsed_ms").toLongOrNull(),
            backendEvidence = backendEvidence.ifBlank { null },
            maxOutputTokens = maxOutputTokens,
            artifactPath = values.value("artifact_path").ifBlank { null },
            qualityClassification = qualityClassification,
            rollback = rollback,
            artifactFresh = true,
            fallbackUsed = values.isTrue("fallback_used"),
            timeout = values.isTrue("timeout"),
            freshCrash = values.isTrue("fresh_crash"),
            standardRouteConnected = values.isTrue("standard_route_connected"),
            normalUiRouteConnected = values.isTrue("normal_ui_route_connected"),
            shouldPersistToDb = values.isTrue("db"),
            shouldSpeakTts = values.isTrue("tts"),
            shouldRenderMarkdown = values.isTrue("markdown"),
            shouldStream = values.isTrue("streaming"),
        )
    }

    private fun gateFailureReason(
        sanitizedOutput: String,
        qualityClassification: String,
        backend: String,
        backendEvidence: String,
        maxOutputTokens: Int,
        values: Map<String, String>,
    ): String? =
        when {
            sanitizedOutput.isBlank() -> "empty_sanitized_output"
            containsTemplateArtifact(sanitizedOutput) -> "template_artifact_after_sanitize"
            qualityClassification != "natural_japanese" -> "quality_not_natural_japanese"
            values.isTrue("fallback_used") -> "fallback_used"
            values.isTrue("timeout") -> "timeout"
            values.isTrue("fresh_crash") -> "fresh_crash"
            values.isTrue("selected_path_npu_saved") -> "selected_path_npu_saved"
            values.isTrue("standard_route_connected") -> "standard_route_connected"
            values.isTrue("normal_ui_route_connected") -> "normal_ui_route_connected"
            values.isTrue("db") -> "db_connected"
            values.isTrue("tts") -> "tts_connected"
            values.isTrue("markdown") -> "markdown_connected"
            values.isTrue("streaming") -> "streaming_connected"
            backend != "NPU" -> "npu_backend_not_npu"
            !backendEvidence.contains("QNN_HTP_V79_FastRPC") -> "npu_backend_evidence_missing"
            maxOutputTokens != DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS -> "max_output_tokens_not_128"
            else -> null
        }

    private fun parseKeyValueText(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()

    private fun containsTemplateArtifact(value: String): Boolean =
        value.contains("<end_of_turn>") || value.contains("<start_of_turn>")

    private fun Map<String, String>.value(key: String): String =
        this[key].orEmpty()

    private fun Map<String, String>.isTrue(key: String): Boolean =
        value(key).equals("true", ignoreCase = true)

    private fun String.unescapeArtifactValue(): String =
        replace("\\n", "\n").replace("\\\\", "\\")
}
