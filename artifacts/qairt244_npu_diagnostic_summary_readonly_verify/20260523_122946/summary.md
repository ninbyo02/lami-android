# QAIRT NPU Diagnostic Summary Read-Only Verification

Result: `success`

Device: `192.168.52.52:37859`

Activity:

```text
io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
```

Synced app-private file:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt
```

Displayed source:

```text
source=app_private_file
```

Displayed keys:

- `latest_artifact=artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243`
- `run_count=2`
- `run1_result=success`
- `run1_output=! How Hi`
- `run1_elapsed_ms=1907`
- `run1_decode_elapsed_ms=96`
- `run2_result=success`
- `run2_output=! How Hi`
- `run2_elapsed_ms=1661`
- `run2_decode_elapsed_ms=70`
- `final_guard_state=success`
- `state_started_final=false`
- `after_10s_total_pss_kb=78536`
- `after_10s_native_heap_kb=20571`
- `tombstone_classification=stale-tombstone-ignored`
- `fresh_crash=false`
- `normal_chatscreen_npu_route=disabled`
- `selected_path_npu=disabled`

Evidence files:

- `synced_summary_on_device.txt`
- `window.xml`
- `screenshot.png`
- `logcat_tail.txt`
- `package_dump_extract.txt`

Safety confirmation:

- NPU generation: `not_run`
- Engine.initialize: `not_run`
- RunDecode: `not_run`
- high-level generateResponse: `not_run`
- normal ChatScreen route: `not_connected`
- normal selectedPath=npu route: `not_used`

The verification launched the diagnostic Activity and used its read-only
startup display after syncing the host artifact summary. It did not press the
DEV checkbox or the guarded run button.
