# QAIRT244 NPU Gemma turn-stop quality compare

- artifact: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- device: `192.168.52.52:43045`
- package: `io.github.ninbyo02.lami`
- receiver: `io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver`
- action: `io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT`
- timeout_seconds_per_run: `30`
- template_mode: `gemma_it_like`
- maxOutputTokens_policy: `128 or lower only`
- repetition_suppression_marker: `not_requested_api_pending`
- overall_status: `success`
- stop_sequence_end_of_turn: `not_run/native_stop_not_exposed`

## Prompts

- `こんにちは`
- `はじめまして`
- `こんばんは`

## Cases

- `sanitizer_only`: standard hidden run, fixed max_output_tokens=128
- `lower_max_tokens_64_sanitizer`: not run; rollback_not_adopted after empty_after_sanitize evidence
- `lower_max_tokens_32_sanitizer`: not run; rollback_not_adopted after adapter failure / timeout evidence
- `stop_sequence_end_of_turn`: not run until native stop-sequence control is exposed

## Comparison

# QAIRT244 NPU turn-stop quality comparison

| case | prompt | requested_max_output_tokens | actual_max_output_tokens | status | npu_evidence | fallback_used | fresh_crash | quality_classification | sanitized_output_length | reasonCode | stop_reason |
| --- | --- | ---: | ---: | --- | --- | --- | --- | --- | ---: | --- | --- |
| `sanitizer_only` | `こんにちは` | 128 | 128 | `success` | `true` | `false` | `false` | `natural_japanese` | 24 | `success` | `unavailable` |
| `sanitizer_only` | `はじめまして` | 128 | 128 | `success` | `true` | `false` | `false` | `natural_japanese` | 10 | `success` | `unavailable` |
| `sanitizer_only` | `こんばんは` | 128 | 128 | `success` | `true` | `false` | `false` | `natural_japanese` | 69 | `success` | `unavailable` |
| `lower_max_tokens_64_sanitizer` | `こんにちは` | 64 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_empty_after_sanitize` | `rollback_empty_after_sanitize` |
| `lower_max_tokens_64_sanitizer` | `はじめまして` | 64 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_empty_after_sanitize` | `rollback_empty_after_sanitize` |
| `lower_max_tokens_64_sanitizer` | `こんばんは` | 64 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_empty_after_sanitize` | `rollback_empty_after_sanitize` |
| `lower_max_tokens_32_sanitizer` | `こんにちは` | 32 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_adapter_failure_or_timeout` | `rollback_adapter_failure_or_timeout` |
| `lower_max_tokens_32_sanitizer` | `はじめまして` | 32 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_adapter_failure_or_timeout` | `rollback_adapter_failure_or_timeout` |
| `lower_max_tokens_32_sanitizer` | `こんばんは` | 32 | not_run | `not_run/rollback_not_adopted` | `not_run` | `not_run` | `not_run` | `rollback_not_adopted` | not_run | `rollback_adapter_failure_or_timeout` | `rollback_adapter_failure_or_timeout` |
| `stop_sequence_end_of_turn` | `こんにちは` | 128 | not_run | `not_run/native_stop_not_exposed` | `not_run` | `not_run` | `not_run` | `not_run` | not_run | `native_stop_not_exposed` | `native_stop_not_exposed` |
| `stop_sequence_end_of_turn` | `はじめまして` | 128 | not_run | `not_run/native_stop_not_exposed` | `not_run` | `not_run` | `not_run` | `not_run` | not_run | `native_stop_not_exposed` | `native_stop_not_exposed` |
| `stop_sequence_end_of_turn` | `こんばんは` | 128 | not_run | `not_run/native_stop_not_exposed` | `not_run` | `not_run` | `not_run` | `not_run` | not_run | `native_stop_not_exposed` | `native_stop_not_exposed` |

## Notes

- The standard hidden receiver flow is reused and package `io.github.ninbyo02.lami` is targeted.
- `max_output_tokens` is fixed at 128 for the hidden safety baseline; lower token caps are recorded only as rollback rows.
- Stop sequence and repetition suppression are artifact markers only here; no unconfirmed native/API controls are invoked.
- NPU evidence, `fallback_used`, and `fresh_crash` are recorded per run.
- The runner does not edit route code, native code, DB, TTS, Markdown, or streaming paths.
