package io.github.ninbyo02.lami.ui.screens.home

internal fun Map<String, String>.diagnosticString(key: String): String? =
    this[key]?.takeUnless { value ->
        value.isBlank() || value == "unavailable" || value == "unknown"
    }

internal fun Map<String, String>.diagnosticBoolean(key: String): Boolean? =
    diagnosticString(key)?.toBooleanStrictOrNull()

internal fun Map<String, String>.diagnosticInt(key: String): Int? =
    diagnosticString(key)?.toIntOrNull()

internal fun Map<String, String>.diagnosticLong(key: String): Long? =
    diagnosticString(key)?.toLongOrNull()

internal fun isGpuCallbackStreamingDiagnosticsText(text: String): Boolean =
    text.contains("debug_lami_gpu_generate_probe_mode=$GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI") ||
        text.contains("debug_lami_gpu_generate_probe_mode=$GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING") ||
        text.contains("gpu_callback_streaming_path_selected=true")
