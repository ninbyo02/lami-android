# Gate definition

Accepted 512 mode: `hidden_per_run_isolated_512`.

Required:

- `max_output_tokens=512`
- force-stop before each prompt
- force-stop after each prompt
- one run per approved prompt
- `RunDecode` reached
- `SetMaxOutputTokens(512)` evidence
- `timeout=false`
- `fresh_crash=false`
- `fallback_used=false`
- `QNN_HTP_V79_FastRPC_native_diag`
- `Engine.close=unique_ptr_cleanup`
- cleanup evidence
- no retained process after 10 seconds
- no high retained memory
- code-aware sanitizer
- indentation preserved
- code fence completed or closed
- `selectedPathSaved=false`
- `assistant_message_list_inserted=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`

The Kotlin formal gate is `DevOnlyNpuHiddenExperimentalModeGate`. The force-stop
runner now records `mode=hidden_per_run_isolated_512` and
`execution_isolation=per_run_force_stop`.
