package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

internal const val GPU_DIAGNOSTIC_COPY_BUTTON_LABEL = "GPU診断キーをコピー"
internal const val GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL = "GPU内部surfaceキーをコピー"

internal val GPU_DIAGNOSTIC_COPY_KEYS = listOf(
    "selected_backend",
    "requested_backend",
    "effective_backend",
    "route_family",
    "backend_evidence",
    "selected_model_name",
    "selected_model_file",
    "gpu_output_quality_matrix_mode",
    "edge_gallery_executor_probe_result",
    "edge_gallery_executor_difference_summary",
    "edge_gallery_generate_api_candidate",
    "executor_selection_fingerprint",
    "runtime_backend_fingerprint",
    "runtime_executor_fingerprint",
    "runtime_dispatch_fingerprint",
    "runtime_compiled_model_fingerprint",
    "engine_config_fingerprint",
    "conversation_config_fingerprint",
    "sampler_config_fingerprint",
    "gpu_internal_surface_probe_enabled",
    "gpu_internal_surface_probe_result",
    "gpu_internal_surface_probe_disabled_reason",
    "gpu_output_quality_candidate_result",
    "gpu_output_quality_gate_status",
    "gpu_output_quality_promotion_blocker",
    "gpu_output_quality_summary",
    "gpu_sampler_root_cause_candidate",
    "gpu_output_source_corruption_stage",
    "callback_corruption_earliest_stage",
    "callback_quality_classification",
    "gpu_fragmentation_score",
    "gpu_output_suspicious_fragment_detected",
    "gpu_output_suspicious_fragment_reason",
    "gpu_callback_invoked_count",
    "gpu_callback_empty_text_count",
    "gpu_callback_non_empty_text_count",
    "gpu_output_callback_chunk_count",
    "gpu_output_raw_callback_text_head",
    "gpu_output_raw_callback_text_tail",
    "gpu_output_final_assistant_text_head",
    "gpu_output_final_assistant_text_tail",
    "gpu_perf_engine_acquire_elapsed_ms",
    "gpu_perf_engine_create_or_reuse",
    "gpu_perf_generate_to_first_token_ms",
    "gpu_perf_callback_total_elapsed_ms",
    "gpu_perf_slow_path_detected",
    "gpu_perf_slow_path_reason",
)

internal val GPU_INTERNAL_SURFACE_COPY_KEYS = listOf(
    "gpu_internal_surface_probe_enabled",
    "gpu_internal_surface_probe_result",
    "gpu_internal_surface_probe_disabled_reason",
    "gpu_internal_runtime_config_class_present",
    "gpu_internal_backend_constraint_class_present",
    "gpu_internal_preferred_engine_type_class_present",
    "gpu_internal_gpu_options_class_present",
    "gpu_internal_artisan_class_present",
    "gpu_internal_llm_gpu_artisan_executor_symbol_present",
    "gpu_internal_kv_cache_symbol_present",
    "gpu_internal_runtime_config_methods",
    "gpu_internal_backend_constraint_methods",
    "gpu_internal_gpu_options_methods",
    "gpu_internal_probe_exception_class",
    "gpu_internal_probe_exception_message",
)

internal fun buildGpuDiagnosticKeysCopyText(
    stats: InferenceStats,
    trace: LocalInferenceTrace? = null,
): String = buildDiagnosticKeyCopyText(
    title = "GPU diagnostic keys",
    keys = GPU_DIAGNOSTIC_COPY_KEYS,
    diagnostics = collectInferenceDiagnosticKeyValues(stats = stats, trace = trace),
)

internal fun buildGpuInternalSurfaceKeysCopyText(
    stats: InferenceStats,
    trace: LocalInferenceTrace? = null,
): String = buildDiagnosticKeyCopyText(
    title = "GPU internal surface keys",
    keys = GPU_INTERNAL_SURFACE_COPY_KEYS,
    diagnostics = collectInferenceDiagnosticKeyValues(stats = stats, trace = trace),
)

internal fun buildDiagnosticKeyCopyText(
    title: String,
    keys: List<String>,
    diagnostics: Map<String, String>,
): String = buildString {
    appendLine("[$title]")
    keys.forEach { key ->
        append(key)
        append('=')
        appendLine(diagnostics[key]?.sanitizeDiagnosticCopyValue() ?: "unavailable")
    }
}.trimEnd()

internal fun collectInferenceDiagnosticKeyValues(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): Map<String, String> {
    val sources = listOfNotNull(
        trace?.localFailureDiagnosticsText,
        stats.localSourceSummary,
    )
    return buildMap {
        sources.forEach { source ->
            putAll(parseDiagnosticCopySource(source))
        }
    }.withGpuDiagnosticCopyFallbacks()
}

private fun parseDiagnosticCopySource(text: String): Map<String, String> =
    buildMap {
        putAll(parseDiagnosticKeyValueText(text))
        text.lineSequence()
            .map(String::trim)
            .forEach { line ->
                val sourceSummary = line
                    .removePrefix("source_summary=")
                    .takeIf { it != line }
                    ?.trim()
                if (!sourceSummary.isNullOrBlank()) {
                    putAll(parseDiagnosticKeyValueText(sourceSummary))
                }
            }
    }

private fun String.sanitizeDiagnosticCopyValue(): String =
    replace("\r", "\\r")
        .replace("\n", "\\n")
        .ifBlank { "unavailable" }

private fun Map<String, String>.withGpuDiagnosticCopyFallbacks(): Map<String, String> {
    val backend = this["selected_backend"]
        ?: this["preferred_backend"]
        ?: this["gpu_backend_setting"]
        ?: this["effective_backend"]
    val normalizedBackend = backend?.uppercase()
    val routeFamily = this["route_family"] ?: when (normalizedBackend) {
        "GPU" -> "local_gpu"
        "CPU" -> "local_cpu"
        "NPU" -> "local_npu"
        else -> null
    }
    val backendEvidence = this["backend_evidence"] ?: when (normalizedBackend) {
        "GPU" -> "gpu_route"
        "CPU" -> "cpu_route"
        "NPU" -> "npu_route"
        else -> null
    }
    return this +
        listOfNotNull(
            backend?.let { "selected_backend" to (this["selected_backend"] ?: it) },
            backend?.let { "requested_backend" to (this["requested_backend"] ?: it) },
            backend?.let { "effective_backend" to (this["effective_backend"] ?: it) },
            routeFamily?.let { "route_family" to it },
            backendEvidence?.let { "backend_evidence" to it },
        ).toMap()
}
