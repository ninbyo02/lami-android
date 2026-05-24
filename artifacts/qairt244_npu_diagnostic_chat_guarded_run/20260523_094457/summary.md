# NPU Diagnostic Chat Guarded Run Control

Artifact: `artifacts/qairt244_npu_diagnostic_chat_guarded_run/20260523_094457/`

## Result

```text
activity_start=success
package=io.github.ninbyo02.lami.customnpu
activity=io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
run_button_visible=true
run_button_enabled=false
dev_confirmation_visible=true
dev_confirmation_checked=false
prompt=Hi
maxOutputTokens=3
last_result=success
last_output=! How Hi
normal_ui_connected=false
selectedPath_npu_normal_route=false
generation_executed=false
```

## Guard Verification

- The Activity launched from the `customBuildExperimentDebug` package.
- The `DEV confirm isolated 3-token NPU smoke` checkbox is visible and unchecked.
- The `RUN 3-TOKEN SMOKE` button is visible but disabled until explicit DEV confirmation.
- The separate `NORMAL CHATSCREEN NPU ROUTE DISABLED` button remains disabled.
- No click or smoke execution was performed in this verification.

## Evidence Files

- `window.xml`
- `screenshot.png`
- `package_dump_extract.txt`
- `activity_start.txt`
- `logcat_tail.txt`

## Safety

This verification did not call `Engine.initialize`, `RunDecode`, high-level
`generateResponse`, `Conversation`, `Session`, `selectedPath=npu`, or the normal
`ChatScreen` inference route.
