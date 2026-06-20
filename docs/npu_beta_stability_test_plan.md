# NPU Beta Stability Test Plan

## Purpose

`NPU Beta Stability Test` is the primary DEV entry for repeated NPU health
checks. The implementation is intentionally small: it reuses the existing NPU
S1 repeated-run runner, keeps the same safety stop lines, and exposes the
current NPU Beta route with clearer user-facing labels plus summary keys.

This does not change the NPU route, persistent engine behavior, custom JNI
behavior, fallback policy, or completed standard-route delivery path.

## Current Implementation

- UI label: `NPU Beta安定性テスト開始`
- DEV diagnostics group: Primary
- Summary test name: `test_name=NPU Beta Stability Test`
- Modes exposed in Primary: `Recreate` and `Reuse`
- Default mode: `mode=safe_recreate`
- Initial requested runs: `requested_runs=10`
- Reuse scope: DEV-only, NPU Beta / NPU standard route only, 10 runs, wait
  500ms or 2000ms, stop on first failure
- Existing runner: `startNpuS1RepeatedRun()` via `NpuStandardRouteS1Bridge`
- Existing compatibility names remain: `NPU S1 repeated run`, `NPU_S1`, and
  `npu_s1` may still appear in internal diagnostics and tests.

`Reuse` requests the existing stability runner path without the post-run
recreate request. The lower-level engine reuse signal is not fully exposed, so
artifacts should report `engine_reuse_requested=true` and
`engine_reused=unavailable` until a native/persistent-engine signal is added.

## Why 10 Runs First

Ten runs are enough to validate the DEV entry, aggregate formatting, cancellation
path, and basic NPU stability signals without turning the button into a long
device soak test. The existing 50/100 concepts remain useful, but they should be
enabled only after the 10-run path is validated on a physical NPU device.

## Summary Keys

The summary should expose these keys for artifact review:

- `test_name`
- `mode`
- `requested_runs`
- `completed_runs`
- `success_count`
- `failed_count`
- `success_rate`
- `fallback_used_count`
- `fallback_rate`
- `timeout_count`
- `timeout_rate`
- `fresh_crash_count`
- `fresh_crash_rate`
- `run_decode_reached_count`
- `run_decode_reached_rate`
- `average_total_ms`
- `average_decode_ms`
- `average_tokens_per_second`
- `first_failure_reason`
- `backend_evidence_summary`
- `quality_classification_summary`
- `reuse_enabled`
- `reuse_gate_allowed`
- `reuse_gate_reason`
- `engine_reuse_requested`
- `engine_reused`
- `engine_request_count`
- `engine_create_count`
- `adapter_call_count`
- `decode_attempt_count`
- `decode_success_count`

Unavailable metrics must be written as `unavailable`; they should not be
inferred from unrelated fields.

## Copy Actions

Primary DEV diagnostics now expose two one-tap copy actions next to the Stability
Test runner:

- `Copy Stability Summary`
- `Copy Stability Full Dump`

`Copy Stability Summary` copies only the
`[DEV診断: NPU S1 repeated run summary]` block. It includes
`test_name=NPU Beta Stability Test`, safe recreate mode, requested/completed
runs, success/failure counts, fallback/timeout/fresh-crash/decode rates,
average total/decode time, average tokens/sec, first failure reason, backend
evidence summary, quality classification summary, selected/requested/effective
backend, route family, stop state, safety policy, and guard counters.

`Copy Stability Full Dump` copies the current Stability Test diagnostics text.
When failure detail blocks exist, they are included in the copied text. If no
per-run detail block exists yet, the copy still captures the current displayed
summary/idle/unavailable state.

Both copy actions are UI/export affordances only. They do not change the
repeated-run runner, NPU route, fallback policy, persistent engine behavior,
custom JNI behavior, telemetry meaning, or output quality classifier.

## Physical Device Checks

Run on a physical NPU device:

1. Select `NPU Beta`.
2. Open DEV diagnostics.
3. Start `NPU Beta安定性テスト開始`.
4. Save `Copy Stability Summary` output for quick review.
5. Save `Copy Stability Full Dump` if any failure or unexpected timing/quality
   signal appears.

Expected observations:

- `total_runs` or `requested_runs`
- `completed_runs`
- `success_count`
- `failed_count`
- `fallback_used_count`
- `timeout_count`
- `fresh_crash_count`
- `run_decode_reached_count`
- `average_total_ms`
- `average_decode_ms`
- `average_tokens_per_second`
- backend evidence containing `QNN_HTP` or `FastRPC`
- quality classification summary
- `repeated_run_mode=reuse` when Reuse is selected
- `reuse_gate_allowed=true` for NPU Beta / NPU standard route Reuse runs
- `effective_max_output_tokens=512` when the Settings max output value is
  above the current native limit

`not run: requires physical NPU device`

## Pass Conditions

Initial 10-run pass candidate:

- `completed_runs=10`
- `success_count=10`
- `failed_count=0`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `run_decode_reached_count=10`
- backend evidence contains `QNN_HTP` or `FastRPC`
- Reuse investigation pass: `repeated_run_mode=reuse`, `completed_runs=10`,
  `success_count=10`, `fallback_used_count=0`, `timeout_count=0`,
  `fresh_crash_count=0`, `effective_max_output_tokens=512`

## Fail Or Hold Conditions

Hold promotion or investigate if any of these appear:

- `failed_count > 0`
- `fallback_used_count > 0`
- `timeout_count > 0`
- `fresh_crash_count > 0`
- `run_decode_reached_count < completed_runs`
- backend evidence missing or not NPU/QNN/HTP/FastRPC
- repeated `quality_classification` failure patterns
- Reuse still reports `engine_create_failed`, `LiteRtLmJniException`,
  timeout, fresh crash, fallback, or `run_decode_reached=false`

## Next Steps

Step 3 adds a Long Generation Test runner that compares
`max_output_tokens=32/128/512`, records EOS/stop reason when exposed, and writes
`unavailable` when native finish telemetry is not exposed. The 1024-token case
remains an Advanced follow-up until physical-device evidence is stable.

Later stability work can add guarded 50/100-run modes after the 10-run path has
physical-device evidence and cancellation/safety behavior remains stable.

Step 4 places this test in the Primary DEV diagnostics group. Low-level
persistent Engine, custom JNI, full dump, GPU investigation, and route debug
controls remain available under Advanced.
