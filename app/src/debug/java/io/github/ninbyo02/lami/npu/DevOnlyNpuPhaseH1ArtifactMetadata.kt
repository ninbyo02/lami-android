package io.github.ninbyo02.lami.npu

data class DevOnlyNpuPhaseH1ArtifactMetadata(
    val values: Map<String, String>,
) {
    val artifactTimestampMs: Long? =
        values["artifact_timestamp_ms"]?.toLongOrNull()
}

data class DevOnlyNpuPhaseH1ArtifactMetadataParseResult(
    val metadata: DevOnlyNpuPhaseH1ArtifactMetadata?,
    val reasonCode: String,
) {
    val success: Boolean = metadata != null
}

data class DevOnlyNpuPhaseH1MetadataBoundaryResult(
    val shouldReadMetadata: Boolean,
    val metadata: DevOnlyNpuPhaseH1ArtifactMetadata?,
    val uiState: DevOnlyNpuPhaseH1UiState,
)
