package io.github.ninbyo02.lami.npu

object DevOnlyNpuPhaseH1ArtifactMetadataParser {
    val requiredFields: Set<String> = linkedSetOf(
        "artifact_timestamp_ms",
        "result",
        "sanitized_output",
        "quality_classification",
        "npu_backend",
        "npu_backend_evidence",
        "fallback_used",
        "timeout",
        "fresh_crash",
        "selected_path_npu_saved",
        "standard_route_connected",
        "normal_ui_route_connected",
        "db",
        "tts",
        "markdown",
        "streaming",
        "decode_ms",
        "max_output_tokens",
        "artifact_path",
    )

    private val allowedFields: Set<String> = requiredFields + setOf(
        "reasonCode",
        "reason_code",
        "decode_elapsed_ms",
    )

    private val boolFields: Set<String> = setOf(
        "fallback_used",
        "timeout",
        "fresh_crash",
        "selected_path_npu_saved",
        "standard_route_connected",
        "normal_ui_route_connected",
        "db",
        "tts",
        "markdown",
        "streaming",
    )

    private val longFields: Set<String> = setOf(
        "artifact_timestamp_ms",
        "decode_ms",
        "decode_elapsed_ms",
    )

    private val intFields: Set<String> = setOf("max_output_tokens")

    fun parseKeyValueText(text: String): DevOnlyNpuPhaseH1ArtifactMetadataParseResult =
        parseMap(
            text.lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return@mapNotNull null
                    val index = trimmed.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    trimmed.substring(0, index).trim() to trimmed.substring(index + 1).trim()
                }
                .toMap(),
        )

    fun parseMap(values: Map<String, String>): DevOnlyNpuPhaseH1ArtifactMetadataParseResult {
        val filtered = values
            .filterKeys { it in allowedFields }
            .mapValues { (_, value) -> value.trim() }

        requiredFields.firstOrNull { filtered[it].isNullOrBlank() }?.let { missing ->
            return rollback("missing_required_field:$missing")
        }

        boolFields.firstOrNull { key -> filtered[key]?.isBoolString() != true }?.let { invalid ->
            return rollback("invalid_bool:$invalid")
        }

        longFields.firstOrNull { key ->
            filtered.containsKey(key) && filtered[key]?.toLongOrNull() == null
        }?.let { invalid ->
            return rollback("invalid_number:$invalid")
        }

        intFields.firstOrNull { key ->
            filtered.containsKey(key) && filtered[key]?.toIntOrNull() == null
        }?.let { invalid ->
            return rollback("invalid_number:$invalid")
        }

        return DevOnlyNpuPhaseH1ArtifactMetadataParseResult(
            metadata = DevOnlyNpuPhaseH1ArtifactMetadata(filtered),
            reasonCode = "success",
        )
    }

    fun uiInputFrom(
        parseResult: DevOnlyNpuPhaseH1ArtifactMetadataParseResult,
        nowMs: Long,
    ): DevOnlyNpuPhaseH1UiInput {
        val metadata = parseResult.metadata ?: return rollbackInput(parseResult.reasonCode)
        val freshness = DevOnlyNpuPhaseH1ArtifactFreshness.evaluate(
            artifactTimestampMs = metadata.artifactTimestampMs,
            nowMs = nowMs,
        )
        val mapped = DevOnlyNpuPhaseH1ArtifactMapper.from(metadata.values)
        return if (freshness.artifactFresh) {
            mapped
        } else {
            mapped.copy(
                success = false,
                sanitizedOutput = null,
                reasonCode = freshness.reasonCode,
                rollback = true,
                artifactFresh = false,
            )
        }
    }

    fun stateFromKeyValueText(
        text: String,
        nowMs: Long,
    ): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1Presenter.present(uiInputFrom(parseKeyValueText(text), nowMs))

    fun boundaryForToggle(
        devEnableNpuChatScreenRoute: Boolean,
        nowMs: Long,
        metadataTextProvider: () -> String,
    ): DevOnlyNpuPhaseH1MetadataBoundaryResult {
        if (!devEnableNpuChatScreenRoute) {
            return DevOnlyNpuPhaseH1MetadataBoundaryResult(
                shouldReadMetadata = false,
                metadata = null,
                uiState = DevOnlyNpuPhaseH1StateReducer.initial(),
            )
        }

        val parseResult = parseKeyValueText(metadataTextProvider())
        return DevOnlyNpuPhaseH1MetadataBoundaryResult(
            shouldReadMetadata = true,
            metadata = parseResult.metadata,
            uiState = DevOnlyNpuPhaseH1Presenter.present(uiInputFrom(parseResult, nowMs)),
        )
    }

    private fun rollback(reasonCode: String): DevOnlyNpuPhaseH1ArtifactMetadataParseResult =
        DevOnlyNpuPhaseH1ArtifactMetadataParseResult(
            metadata = null,
            reasonCode = reasonCode,
        )

    private fun rollbackInput(reasonCode: String): DevOnlyNpuPhaseH1UiInput =
        DevOnlyNpuPhaseH1UiInput(
            success = false,
            sanitizedOutput = null,
            reasonCode = reasonCode,
            decodeMs = null,
            backendEvidence = null,
            maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
            artifactPath = null,
            qualityClassification = "",
            rollback = true,
        )

    private fun String.isBoolString(): Boolean =
        equals("true", ignoreCase = true) || equals("false", ignoreCase = true)
}
