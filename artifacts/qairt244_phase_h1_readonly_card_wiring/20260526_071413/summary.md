# QAIRT244 Phase H1 Read-Only Card Wiring

Timestamp: 20260526_071413

## Scope

- Wired the Phase H1 DEV-only read-only transient card into `NpuDiagnosticChatActivity`.
- The card reads committed fresh baseline artifact metadata and renders through the existing metadata parser, mapper, presenter, card view model, and preview renderer path.
- No ChatScreen normal conversation promotion was performed.
- No assistant message list insertion was performed.
- No NPU generation was performed during capture.

## Artifact Source

- Baseline artifact: `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- Metadata source: committed baseline metadata
- Metadata read: true
- Preview visible: true
- Read-only card visible: true

## Rendered Card Content

```text
DEV ONLY
DEV NPU transient preview
Status: SUCCESS
Read-only sanitized output
Output:
こんにちは！何かお手伝いできることはありますか？
Reason: reasonCode=ok
Details:
- maxOutputTokens=128
- decode_ms=829
- backendEvidence=QNN_HTP_V79_FastRPC
- artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810
- selectedPathSaved=false
- db=false
- tts=false
- markdown=false
- streaming=false
```

## Safety Confirmation

- sanitized_output visible: true
- raw_output visible in UI/card/renderer: false
- `<start_of_turn>` visible in UI/card/renderer: false
- `<end_of_turn>` visible in UI/card/renderer: false
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

## Capture Files

- `screenshot.png`: representative UI screenshot with read-only card visible
- `window.xml`: UI hierarchy with read-only card text
- `metadata_input.txt`: artifact metadata input, including raw_output only as artifact evidence
- `renderer_output.txt`: sanitized renderer output
- `rendered_preview.txt`: diagnostic preview output
- `logcat_tail.txt`: capture-time logcat tail
- `foreground_package.txt`: foreground activity evidence
- `runtime_marker_scan.txt`: runtime marker summary
- `package_dump_extract.txt`: focused package extract
- `grep_safety.txt`: positive and negative safety checks
