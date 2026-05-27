# Terminal Trace Summary

Source runtime artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_073227/`

## Prompt 1: こんにちは

Classification: `WORKER_COMPLETED_CLEAN`

Reached markers:
- `receiver_enter`
- `go_async_started`
- `worker_thread_started`
- `run_for_chatscreen_enter`
- `before_native_adapter_run`
- `after_native_adapter_run`
- `before_run_decode_marker_seen`
- `before_terminal_result_write`
- `after_terminal_result_write`
- `before_cleanup`
- `after_cleanup`
- `finally_enter`
- `finally_exit`
- `worker_finished`

Result: native returned, terminal result was written, cleanup was written,
`finally` completed, and worker finished.

## Prompt 2: Pythonで簡単な電卓コードを書いて

Classification: `NATIVE_NON_RETURN_OR_PROCESS_DEATH`

Reached markers:
- `receiver_enter`
- `go_async_started`
- `worker_thread_started`
- `run_for_chatscreen_enter`
- `before_native_adapter_run`

Missing markers:
- `after_native_adapter_run`
- `before_run_decode_marker_seen`
- `before_terminal_result_write`
- `after_terminal_result_write`
- `before_cleanup`
- `after_cleanup`
- `throwable_caught`
- `finally_enter`
- `finally_exit`
- `worker_finished`

Interpretation:
- The worker entered the native adapter call.
- No Java/Kotlin throwable was caught.
- The native adapter did not return to Kotlin.
- Receiver `finally` was not reached.
- Worker did not finish.
- Native diag independently reached
  `before RunDecode SetMaxOutputTokens(512)`.

This points to native non-return or process death during the native decode
window, not a terminal result writer or cleanup writer issue.

## Prompt 3: ラミィのNPU推論について短く説明して

Not dispatched. The sequence stopped after prompt 2 because lifecycle and
process-boundary policy classified the session as suspect and set
`next_prompt_allowed=false`.
