# NPU Diagnostic Chat Guarded UI Run

Artifact: `artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/`

## Result

```text
result=success
output=! How Hi
prompt=Hi
max_output_tokens=3
elapsed_ms=1090
engine_create_elapsed_ms=883
session_create_elapsed_ms=0
prefill_elapsed_ms=13
decode_elapsed_ms=64
cleanup_elapsed_ms=129
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
result_observed_after_seconds=4
```

## UI Guard

```text
activity=io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
dev_checkbox_required=true
run_button=RUN 3-TOKEN SMOKE
prompt=Hi
maxOutputTokens=3
normal_chat_screen_connected=false
selectedPath_npu_normal_route=false
high_level_generateResponse=false
streaming=false
```

The final captured run used the guarded Diagnostic Chat UI path and the
isolated lower-level `Qairt244ShortMultitokenSmoke.run(...)` wrapper.

## Screenshots

- `screenshot_before.png`: Diagnostic Chat before the final captured run.
- `screenshot_armed.png`: shows an earlier in-memory completed guarded run
  state from the troubleshooting sequence; no additional action was taken from
  that screenshot.
- `screenshot_after.png`: Diagnostic Chat result view after the final captured
  run, showing `result=success`, `output=! How Hi`, and `npu_backend=NPU`.

## Tombstone

```text
classification=stale-tombstone-ignored
fresh_crash=false
```

The diagnostics collector selected an older `No usable Dispatch runtime found`
tombstone. The tombstone/dropbox body does not contain the current guarded UI
run id.

## Safety

This run did not connect NPU to the normal `ChatScreen`, did not set
`selectedPath=npu` in the normal route, did not call high-level
`generateResponse`, and did not use streaming generation.
