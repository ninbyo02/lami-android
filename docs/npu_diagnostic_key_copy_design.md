# NPU Diagnostic Key Copy Design

Scope: design only. This document does not change Android runtime behavior,
UI, CPU/GPU/NPU routes, native libraries, hidden configuration, or inference
flow.

## Purpose

GPU investigation showed that focused diagnostic copy text is more reliable
than reading a long details panel. NPU investigation should use the same shape:
a stable, machine-readable key list that can be copied from device runs and fed
into classifiers, reports, and promotion gate review.

The implemented UI action name is:

```text
NPU diagnostic keys
```

The action should be route-specific and diagnostic-only. It must not imply that
NPU standard route is promoted.

The user-facing button label is:

```text
NPU診断キーをコピー
```

This differs from GPU diagnostic copy: GPU copy focuses on executor/runtime
decode and raw callback quality, while NPU copy focuses on backend evidence,
fallback/crash/timeout, standard route connection, output quality, and cleanup
evidence for the promotion gate.

## Copy Text Format

Use one key per line:

```text
[NPU diagnostic keys]
selected_backend=NPU
requested_backend=NPU
effective_backend=NPU
route_family=local_npu
backend_evidence=QNN_HTP_V79_FastRPC
npu_backend_evidence=QNN_HTP_V79_FastRPC
status=success
reason=completed
fallback_used=false
fresh_crash=false
timeout=false
selected_path_npu_saved=false
normal_ui_route_connected=false
standard_route_connected=true
conversation_created=true
generate_response=true
quality_classification=natural_japanese
db=false
tts=false
markdown=false
streaming=false
cleanup_status=success
engine_close_evidence=present
fresh_tombstone_status=none
promotion_blocker=false
promotion_decision=eligible_candidate
promotion_decision_reason=hidden_route_gate_passed
```

Missing keys should render as `unavailable` so copied text is stable across
device runs.

Use this button during device verification before reading the long details
panel. The copied output should be saved with the run artifact and used to check
the NPU promotion gate in `docs/npu_promotion_gate_definition.md`.

## Priority Keys

Backend identity:

```text
selected_backend
requested_backend
effective_backend
route_family
backend_evidence
npu_backend_evidence
```

Run result:

```text
status
reason
fallback_used
fresh_crash
timeout
```

Route isolation:

```text
selected_path_npu_saved
normal_ui_route_connected
standard_route_connected
conversation_created
generate_response
```

Quality:

```text
quality_classification
```

Integration boundaries:

```text
db
tts
markdown
streaming
```

Cleanup and crash evidence:

```text
cleanup_status
engine_close_evidence
fresh_tombstone_status
```

Promotion decision:

```text
promotion_blocker
promotion_decision
promotion_decision_reason
```

## Copy Design Rules

- Generate copy text from the diagnostics map, not from rendered details text.
- Keep the key order stable.
- Preserve `unavailable` for missing values.
- Never infer NPU success from UI output alone.
- Never convert CPU fallback into NPU success.
- Do not connect DB, TTS, Markdown, or streaming before the promotion gate
  explicitly allows it.

## Test Design

Future unit tests should cover:

- complete NPU copy output includes backend evidence, route isolation, quality,
  cleanup, and promotion decision keys
- missing keys render as `unavailable`
- `fallback_used=true` remains visible
- CPU/GPU route maps do not crash the formatter
- copy output is independent of details panel truncation
