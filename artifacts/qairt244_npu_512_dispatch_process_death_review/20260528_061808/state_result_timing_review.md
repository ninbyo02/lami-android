# State / Result / Native-Diag Timing Review

Prompt 2 files:

- `result_2.txt` contains `state=started` for the prompt run.
- `native_diag_2.txt` contains native entry markers through
  `before RunDecode SetMaxOutputTokens(512)`.
- `cleanup_2.txt` has no cleanup evidence.
- `raw_output_2.txt` and `sanitized_output_2.txt` are empty.

Run-id:

- expected and observed run id match:
  `chat-real-1779913393042-4fc1f045-b62f-418c-a11e-f820aae5f845`.
- stale result and run-id mismatch are both false.

Timing interpretation:

- The receiver worker created run state and reached native diagnostics.
- The process disappeared before result completion and cleanup.
- Since `after_dispatch` is after `am broadcast` returns, the process death
  occurred during the broadcast/receiver worker window, not after a clean
  runner-side cleanup step.
