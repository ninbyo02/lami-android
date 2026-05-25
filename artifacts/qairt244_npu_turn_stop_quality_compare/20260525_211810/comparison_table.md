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
