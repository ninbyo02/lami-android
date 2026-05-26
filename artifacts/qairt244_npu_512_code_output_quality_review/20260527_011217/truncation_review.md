# Truncation Review

Observed tail: the output ends at `elif choice == '`.

Native/runtime evidence:

- `result=success`
- `timeout=false`
- `fresh_crash=false`
- `fallback_used=false`
- `RunDecode reached=true`
- `decode_elapsed_ms=11600`
- `cleanup_elapsed_ms=142`
- `Engine.close=unique_ptr_cleanup`
- `finish_reason=not_exposed_by_lower_level_entrypoint`
- `stop_reason=` empty
- `eos_detected=false`
- `raw_output_length=1503`
- `sanitized_output_length=1089`
- `max_output_tokens=512`

Classification: `output_truncated_by_token_limit`.

The evidence does not indicate a fresh crash, timeout, native callback failure,
or cleanup failure. Because EOS/finish reason is not exposed by this lower-level
entrypoint and the tail is mid-statement, the safest classification is natural
token-limit truncation under the 512 output cap.
