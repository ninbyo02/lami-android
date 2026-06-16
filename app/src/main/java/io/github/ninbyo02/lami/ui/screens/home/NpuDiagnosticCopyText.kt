package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

internal const val NPU_DIAGNOSTIC_COPY_BUTTON_LABEL = "NPU診断キーをコピー"

internal val NPU_DIAGNOSTIC_COPY_KEYS = listOf(
    "status",
    "reason",
    "selected_backend",
    "requested_backend",
    "effective_backend",
    "route_family",
    "backend_evidence",
    "npu_backend_evidence",
    "fallback_used",
    "fresh_crash",
    "timeout",
    "selected_path_npu_saved",
    "normal_ui_route_connected",
    "standard_route_connected",
    "conversation_created",
    "generate_response",
    "quality_classification",
    "db",
    "tts",
    "markdown",
    "streaming",
    "cleanup_status",
    "engine_close_evidence",
    "fresh_tombstone_status",
)

internal fun buildNpuDiagnosticKeysCopyText(
    stats: InferenceStats,
    trace: LocalInferenceTrace? = null,
): String = buildDiagnosticKeyCopyText(
    title = "NPU diagnostic keys",
    keys = NPU_DIAGNOSTIC_COPY_KEYS,
    diagnostics = collectInferenceDiagnosticKeyValues(stats = stats, trace = trace),
)
