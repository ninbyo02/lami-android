# QAIRT244 Phase H1 Transient Preview UI Capture

Date: 2026-05-26

Artifact: `artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`

## Scope

This pass supplements
`artifacts/qairt244_phase_h1_transient_preview_wiring/20260526_062814` with a
representative connected-device screenshot and window dump for the existing
Diagnostic-only Phase H1 transient preview wiring.

No code implementation, NPU execution, native change, model change, or normal
ChatScreen promotion was performed.

## Capture Result

- screenshot: `screenshot.png`
- window dump: `window.xml`
- representative H1 preview visible: `true`
- foreground package during representative capture:
  `io.github.ninbyo02.lami.customnpu`
- activity:
  `io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity`
- foreground stabilization: `cmd activity task lock` during capture, released
  after capture
- post-capture foreground returning to Termux: expected for the interactive test
  session

## Rendered Preview

```text
DEV ONLY - DEV NPU transient preview
Status: SUCCESS
Read-only sanitized output
Output:
こんにちは！何かお手伝いできることはありますか？
```

## Safety

- sanitized output displayed: `true`
- raw output displayed: `false`
- `<end_of_turn>` displayed: `false`
- `<start_of_turn>` displayed: `false`
- selectedPath NPU saved: `false`
- standard route connected: `false`
- normal UI route connected: `false`
- DB: `false`
- TTS: `false`
- Markdown: `false`
- streaming: `false`
- retry: `false`
- auto fallback: `false`
- additional NPU execution: `false`
- `Engine.initialize`: `false`
- `RunDecode`: `false`

## Notes

The prior wiring artifact proved the metadata-to-renderer path. This artifact
adds the missing representative UI evidence for the same Diagnostic-only wiring.
