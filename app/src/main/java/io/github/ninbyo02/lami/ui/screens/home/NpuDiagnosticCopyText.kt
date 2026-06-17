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
    "npu_standard_route_dev_gate_enabled",
    "npu_standard_route_phase",
    "npu_standard_route_phase_name",
    "npu_standard_route_connected",
    "npu_standard_route_quality_gate_passed",
    "npu_standard_route_output_suppressed",
    "npu_standard_route_suppression_reason",
    "npu_standard_route_generate_diagnostic_only",
    "npu_standard_route_output_delivery_allowed",
    "npu_standard_route_candidate_text_present",
    "npu_standard_route_ui_append_allowed",
    "npu_standard_route_ui_append_source",
    "npu_standard_route_ui_appended_text_length",
    "npu_standard_route_ui_append_block_reason",
    "npu_standard_route_tts_allowed",
    "npu_standard_route_tts_source",
    "npu_standard_route_tts_text_length",
    "npu_standard_route_tts_block_reason",
    "npu_standard_route_db_save_allowed",
    "npu_standard_route_markdown_allowed",
    "npu_standard_route_streaming_allowed",
    "npu_standard_route_rollback_required",
    "npu_standard_route_rollback_reason",
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
