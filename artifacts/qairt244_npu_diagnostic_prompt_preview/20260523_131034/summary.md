# QAIRT NPU Diagnostic Prompt Preview Verification

Result: `success`

Device: `192.168.52.52:37859`

Activity:

```text
io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
```

Preview display:

- section: `Short prompt input preview`
- value: `Hi`
- `input_enabled=false`
- `preview_only=true`
- `isValid=true`
- `reasonCode=ok`
- `normalizedPrompt=Hi`
- `message=OK`
- `run_button_connected=false`
- `npu_generation=false`

Run control:

- default Activity launch did not pass `allowGuardedNpuRun=true`
- guarded run checkbox remained disabled
- Run button text: `RUN 3-TOKEN SMOKE DISABLED`
- status: `idle`
- result tail before and after Activity launch matched

Safety confirmation for this final verification:

- NPU generation: `not_run`
- Engine.initialize: `not_run`
- RunDecode: `not_run`
- high-level generateResponse: `not_run`
- normal ChatScreen route: `not_connected`
- normal selectedPath=npu route: `not_used`

Evidence files:

- `window.xml`
- `screenshot.png`
- `logcat_tail.txt`
- `result_tail_before.txt`
- `result_tail_after.txt`
- `package_dump_extract.txt`

This verification did not press the DEV checkbox and did not press the guarded
Run button.
