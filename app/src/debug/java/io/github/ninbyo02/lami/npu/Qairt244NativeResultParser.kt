package io.github.ninbyo02.lami.npu

internal object Qairt244NativeResultParser {
    private const val OUTPUT_KEY = "output"
    private const val ROUTE_MARKER_PREFIX = "qairt244_chat_screen_real_npu_adapter_v1 "

    private val outputTerminatorKeys = setOf(
        "marker",
        "base_marker",
        "result",
        "reasonCode",
        "detail",
        "actual_prompt",
        "raw_user_prompt",
        "normalized_prompt",
        "final_model_input",
        "final_model_input_length",
        "conversation_history_count",
        "system_prompt_used",
        "chat_template_used",
        "selected_route",
        "resolved_model_basename",
        "canonical_model_basename",
        "timestamp_prefix_stripped",
        "required_sm8750_model_path",
        "requested_prompt",
        "prompt_source",
        "prompt_validation_mode",
        "prompt_input_code_points",
        "prompt_input_code_point_limit",
        "prompt_input_limit_mode",
        "prompt_formatting_mode",
        "template_mode",
        "template_prefix_length",
        "template_suffix_length",
        "native_prompt_validation_mode",
        "native_prompt_input_code_point_limit",
        "native_prompt_input_limit_mode",
        "utf8_allowed",
        "prompt_bytes",
        "prompt_token_count",
        "prompt_token_count_source",
        "max_output_tokens",
        "native_max_output_tokens_limit",
        "output_bytes",
        "output_token_count",
        "output_token_count_source",
        "eos_detected",
        "output_contains_replacement_chars",
        "replacement_char_count",
        "output_contains_control_chars",
        "output_unicode_summary",
        "quality_classification",
        "output_first_200_chars",
        "output_last_200_chars",
        "elapsed_ms",
        "model_assets_elapsed_ms",
        "engine_settings_elapsed_ms",
        "engine_create_elapsed_ms",
        "session_create_elapsed_ms",
        "prefill_elapsed_ms",
        "decode_elapsed_ms",
        "cleanup_elapsed_ms",
        "npu_backend",
        "npu_backend_evidence",
        "run_decode_reached",
        "fallback_used",
        "timeout",
        "fresh_crash",
        "db",
        "tts",
        "markdown",
        "streaming",
        "selected_path_npu_saved",
        "route_type",
        "raw_native_output",
        "raw_native_output_length",
        "raw_output",
        "raw_output_length",
        "sanitized_output",
        "sanitized_output_length",
        "sanitizer_applied",
        "removed_template_token_count",
        "removed_prompt_echo",
        "code_block_detected",
        "code_fence_completed",
        "adapter_output",
        "adapter_output_length",
        "displayed_assistant_text",
        "displayed_assistant_text_length",
        "stop_reason",
        "finish_reason",
        "markdown_mode",
        "repair_applied",
        "intent_dispatch_status",
        "receiver_result_success",
        "receiver_reasonCode",
        "ui_cleanup_wait_status",
    )

    fun parse(text: String): ParsedResult {
        val values = linkedMapOf<String, String>()
        val lines = text.lines()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val separator = line.indexOf('=')
            if (separator <= 0) {
                index += 1
                continue
            }

            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key == OUTPUT_KEY) {
                val outputLines = mutableListOf(value)
                index += 1
                while (index < lines.size && !isOutputTerminator(lines[index])) {
                    outputLines += lines[index]
                    index += 1
                }
                values[key] = outputLines.joinToString("\n").trimEnd()
                continue
            }

            values[key] = value
            index += 1
        }

        return ParsedResult(values = values, output = values[OUTPUT_KEY].orEmpty())
    }

    private fun isOutputTerminator(line: String): Boolean {
        if (line.startsWith(ROUTE_MARKER_PREFIX)) return true
        val separator = line.indexOf('=')
        if (separator <= 0) return false
        return line.substring(0, separator) in outputTerminatorKeys
    }

    data class ParsedResult(
        val values: Map<String, String>,
        val output: String,
    )
}
