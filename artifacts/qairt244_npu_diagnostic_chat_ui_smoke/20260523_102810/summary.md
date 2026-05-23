# QAIRT 2.44 NPU Diagnostic Chat UI Smoke

Artifact: `artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810`

## Outcome

```text
result=success
output=! How Hi
prompt=Hi
max_output_tokens=3
elapsed_ms=1268
decode_elapsed_ms=97
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
timeout=false
tombstone_classification=stale-tombstone-ignored
run_id=diag-chat-1779499713497-a1f0822e-d06b-48b0-b067-3bbe089eb43b
ui_dev_checkbox_taps=1
ui_run_button_taps=1
ui_operation_ok=true
normal_chat_screen_connected=false
selectedPath_npu_normal_route=false
high_level_generateResponse=false
streaming=false
```

## Artifacts

- `screenshot_before.png`
- `screenshot_after.png`
- `window_before.xml`
- `window_after.xml`
- `result.txt`
- `native_diag.txt`
- `logcat_tail.txt`
- `stale_tombstone_note.md`
- `package_dump_extract.txt`

The script launches only `io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity`, checks the DEV confirmation once, taps the guarded run button once, and never connects the normal ChatScreen NPU route.
