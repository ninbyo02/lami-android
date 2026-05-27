# Worker Marker Spec

Markers:
- `receiver_enter`
- `go_async_started`
- `worker_thread_started`
- `run_for_chatscreen_enter`
- `before_native_adapter_run`
- `before_run_decode_marker_seen`
- `after_native_adapter_run`
- `before_terminal_result_write`
- `after_terminal_result_write`
- `before_cleanup`
- `after_cleanup`
- `throwable_caught`
- `finally_enter`
- `finally_exit`
- `worker_finished`

Clean completion order:
`receiver_enter` -> `go_async_started` -> `worker_thread_started` ->
`run_for_chatscreen_enter` -> `before_native_adapter_run` ->
`after_native_adapter_run` -> `before_terminal_result_write` ->
`after_terminal_result_write` -> `before_cleanup` -> `after_cleanup` ->
`finally_enter` -> `finally_exit` -> `worker_finished`.

`before_run_decode_marker_seen` is supplemental evidence. It is written only
when app code can observe native diagnostics containing pre-RunDecode or
RunDecode evidence.
