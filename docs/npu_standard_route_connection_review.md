# NPU Standard Route Connection Review

Scope: docs and scripts only. This review does not change Android runtime,
UI, CPU/GPU/NPU route behavior, hidden configuration, native libraries, or
model loading.

## Current Position

Current NPU DEV route evidence is close to a standard-route candidate:

```text
NPU_PROMOTION_READINESS=near_candidate
NPU_PROMOTION_READINESS_SCORE=80
```

Known passing evidence:

- `status=success`
- `selected_backend=NPU_S1`
- `effective_backend=NPU`
- `backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `run_decode_reached=true`
- `fallback=false`
- `timeout=false`
- `fresh_crash=false`
- `native_call_returned=true`
- `native_decode_finished=true`
- `native_cleanup_reached=true`

Known remaining blocker:

```text
REMAINING_BLOCKERS=quality_classification_alignment
```

The three-repeatability set is:

- `こんにちは`: quality candidate passes after template cleanup.
- `あなたは誰ですか？`: quality candidate passes with mixed-language proper nouns.
- `カレーの材料をお願いします。`: `natural_japanese`.

This is not enough to connect the standard route yet. It is enough to prepare a
DEV-only standard route connection review.

## Passed Gates

The DEV route has passed the pre-connection hard gates:

- backend evidence is present and NPU-like.
- decode was reached.
- native call returned on successful runs.
- native cleanup was reached.
- fallback is not used.
- timeout is false.
- fresh crash is false.
- repeatability succeeds across the current short / identity / medium prompt
  set, with quality caveats.

## Unpassed Gates

These gates remain unpassed before standard route connection:

- `quality_classification_alignment`
- `selected_path_npu_saved` must remain controlled and not persist before
  approval.
- `normal_ui_route_connected` must be explicitly reviewed.
- `standard_route_connected` must be introduced only behind a later DEV gate.
- `conversation_created` and `generate_response` must be proven in the standard
  route path, not inferred from the DEV route.
- `db`, `tts`, `markdown`, and `streaming` must stay isolated until route
  connection is stable.
- `cleanup_status` and `engine_close_evidence` must be present in the standard
  route path.

## Connection-Before Gate

Before any standard-route connection implementation, require:

```text
status=success
selected_backend/effective_backend=NPU*
backend_evidence contains QNN/HTP/FastRPC/NPU
run_decode_reached=true
fallback=false
timeout=false
fresh_crash=false
native_call_returned=true
native_cleanup_reached=true
NPU_PROMOTION_READINESS=ready_candidate
REMAINING_BLOCKERS=none
```

Current `near_candidate` output fails this gate because quality alignment is
still pending.

## Connection-After Checklist

After a future DEV-only standard route probe is implemented, verify:

```text
selected_path_npu_saved=false unless explicitly testing persistence
normal_ui_route_connected=true
standard_route_connected=true
conversation_created=true
generate_response=true
db=false initially
tts=false initially
markdown=false initially
streaming=false initially
cleanup_status=success
engine_close_evidence=present
fresh_tombstone_status=none
```

DB / TTS / Markdown / streaming should be connected one boundary at a time
after route stability is proven.

## Promotion Stop Line

Stop standard-route connection or promotion if any of these appear:

- `fallback=true` or `fallback_used=true`
- `fresh_crash=true`
- `timeout=true`
- decode is not reached
- cleanup fails or is missing on terminal failure
- a fresh tombstone appears
- quality regresses from candidate pass / natural Japanese
- standard route side effects appear before the gate
- `selected_path_npu_saved=true` before explicit approval
- DB / TTS / Markdown / streaming attach before isolated route stability

## Script

Use:

```bash
scripts/review_npu_standard_route_connection.sh --device-runs artifacts/device_runs
```

The script emits:

```text
NPU_STANDARD_ROUTE_REVIEW=needs_quality_alignment
READY_FOR_CONNECTION=false
PASSED_GATES=backend_evidence,decode_reached,cleanup_reached,no_timeout,no_fresh_crash,no_fallback,repeatability_success,cleanup_evidence
FAILED_GATES=quality_gate_review,standard_route_connected,conversation_created,generate_response,engine_close_evidence
ROLLBACK_RISKS=none
NEXT_ACTION=finish_quality_alignment_before_standard_route_connection
```

For the current known `near_candidate` repeatability set, this is the expected
result. It intentionally does not authorize Android route changes.

## Rollback Criteria

When a later DEV-only standard route probe exists, rollback to diagnostics-only
if:

- `NPU_STANDARD_ROUTE_REVIEW=rollback_required`
- `READY_FOR_CONNECTION=false` with hard gate failures
- `ROLLBACK_RISKS` includes `fallback`, `fresh_crash`, `timeout`,
  `decode_not_reached`, `cleanup_failure`, `fresh_tombstone`,
  `quality_regression`, `selected_path_saved_before_approval`, or
  `integration_side_effect_before_gate`

The safe next action while readiness remains `near_candidate` is:

```text
NEXT_ACTION=finish_quality_alignment_before_standard_route_connection
```
