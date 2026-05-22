# NPU Diagnostic Chat Read-Only Launch

Artifact: `artifacts/qairt244_npu_diagnostic_chat_readonly/20260523_065214/`

## Result

```text
classification=read-only-launch-verified
activity=io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
applicationId=io.github.ninbyo02.lami.customnpu
flavor=customBuildExperimentDebug
activity_started=true
run_button_enabled=false
prompt=Hi
maxOutputTokens=1
last_result=success
last_output=!
last_elapsed_ms=1053
npu_backend=NPU
native_diag_qnn=true
native_diag_htp=true
native_diag_v79_stub=true
native_diag_fastrpc=true
screenshot=screenshot.png
window_dump=window.xml
generation_executed=false
engine_initialize_executed=false
rundecode_executed=false
normal_ui_connected=false
```

## Evidence

- `activity_start.txt` contains a successful explicit `am start` for
  `NpuDiagnosticChatActivity`.
- `window.xml` shows package `io.github.ninbyo02.lami.customnpu`.
- `window.xml` shows title `NPU Diagnostic Chat`.
- `window.xml` shows `prompt=Hi` and a disabled `EditText` with text `Hi`.
- `window.xml` shows `RUN 1-TOKEN SMOKE DISABLED` with `enabled=false`.
- `window.xml` shows `maxOutputTokens=1`.
- `window.xml` shows the previous isolated verifier result:
  `result=success`, `output=!`, `elapsed_ms=1053`, `npu_backend=NPU`.
- `window.xml` shows timing values:
  `engine_create_elapsed_ms=905`, `session_create_elapsed_ms=0`,
  `prefill_elapsed_ms=13`, `decode_elapsed_ms=22`, `cleanup_elapsed_ms=111`.
- `window.xml` shows native diagnostic summary:
  `QNN=true`, `HTP=true`, `V79Stub=true`, `FastRPC=true`, `RunDecode=true`.

The `RunDecode=true` line is from the previously collected native diagnostic
file displayed by the read-only screen. This launch did not start a new
generation or a new NPU initialize/decode run.

## Safety

- No launch extra was provided.
- The disabled run button was not clicked.
- The normal `ChatScreen` route was not opened.
- No `selectedPath=npu` normal route was introduced.
- `logcat_tail.txt` was captured after clearing logcat before launch and did
  not show new `Engine.initialize`, `RunDecode`, `runLowerLevelSingleTokenSmoke`,
  high-level `generateResponse`, `FATAL`, or `AndroidRuntime` lines.
