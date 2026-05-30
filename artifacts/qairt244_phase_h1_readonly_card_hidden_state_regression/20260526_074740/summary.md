# QAIRT244 Phase H1 Read-Only Card Hidden-State Regression

Timestamp: 20260526_074740

## Scope

This connected-device regression verifies that the Phase H1 read-only transient
card remains visible only for success metadata and is hidden for stale,
rollback, and toggle-false states.

No NPU execution, `Engine.initialize`, `RunDecode`, ChatScreen normal route
connection, assistant message insertion, DB save, TTS, Markdown, streaming,
selected-path persistence, retry, or fallback was performed.

## Cases

| Case | Input | Expected | Observed |
| --- | --- | --- | --- |
| success | fresh gate-pass metadata | card visible | `preview_visible=true`, `readonly_card_visible=true`, `Status: SUCCESS` |
| stale | `artifact_timestamp_ms=1` | card hidden | `preview_visible=false`, `readonly_card_visible=false`, `reasonCode=stale_artifact` |
| rollback | `fallback_used=true` | card hidden | `preview_visible=false`, `readonly_card_visible=false`, `reasonCode=fallback_used` |
| toggle_false | `dev_enable_npu_chatscreen_route=false` | no metadata read, card hidden | `metadata_read=false`, `preview_visible=false`, `readonly_card_visible=false`, `reasonCode=initial` |

## Success Baseline

```text
DEV ONLY - DEV NPU transient preview
Status: SUCCESS
Read-only sanitized output
Output:
こんにちは！何かお手伝いできることはありますか？
```

## Hidden-State Safety

- stale window: no card text, no sanitized output
- rollback window: no card text, no sanitized output
- toggle false window: no card text, no sanitized output, metadata not read
- raw output not displayed
- `<start_of_turn>` not displayed
- `<end_of_turn>` not displayed

## Side-Effect Safety

- selectedPathNpuSaved=false
- standard_route_connected=false
- normal_ui_route_connected=false
- db=false
- tts=false
- markdown=false
- streaming=false
- retry=false
- auto_fallback=false
- npu_generation=false
- engine_initialize=false
- run_decode=false

## Files

- `success_screenshot.png`
- `success_window.xml`
- `stale_screenshot.png`
- `stale_window.xml`
- `rollback_screenshot.png`
- `rollback_window.xml`
- `toggle_false_screenshot.png`
- `toggle_false_window.xml`
- `success_metadata_input.txt`
- `stale_metadata_input.txt`
- `rollback_metadata_input.txt`
- `rendered_success.txt`
- `rendered_stale.txt`
- `rendered_rollback.txt`
- `rendered_toggle_false.txt`
- `logcat_tail.txt`
- `runtime_marker_scan.txt`
- `package_dump_extract.txt`
- `grep_safety.txt`
