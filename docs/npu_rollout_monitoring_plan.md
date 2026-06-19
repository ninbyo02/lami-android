# NPU Rollout Monitoring Plan

Scope: scripts/docs only. This monitor does not change Android runtime,
ChatScreen delivery, Settings UI, NPU route behavior, native libraries, DB
schema, quality gates, or suppression behavior.

## Purpose

The NPU standard route has passed Phase 1-8 staging and final promotion review.
Before any later dev-gate removal review, collected device artifacts need a
repeatable rollout monitor that answers:

- how many Phase 8 success samples exist
- whether dangerous quality-gate outputs are suppressed correctly
- whether timeout, fallback, fresh crash, rollback, engine-create failure, or
  unsafe delivery appeared
- whether the current evidence is low / medium / high risk

Use:

```text
scripts/review_npu_rollout_monitor.sh --device-runs artifacts/device_runs
scripts/review_npu_rollout_monitor.sh --input artifacts/device_runs/npu_phase8_latest.txt
```

Before running the monitor after a manual collection session, generate and check
the rollout sample set:

```text
scripts/create_npu_rollout_sample_manifest.sh --date YYYYMMDD
scripts/review_npu_rollout_sample_set.sh --device-runs artifacts/device_runs --date YYYYMMDD
```

The collection guide is `docs/npu_rollout_sample_collection.md`.

## Output Keys

The script emits machine-readable keys:

```text
NPU_ROLLOUT_MONITOR_STATUS
NPU_ROLLOUT_SAMPLE_COUNT
NPU_ROLLOUT_SUCCESS_COUNT
NPU_ROLLOUT_SUPPRESSION_PASS_COUNT
NPU_ROLLOUT_FAILURE_COUNT
NPU_ROLLOUT_ROLLBACK_COUNT
NPU_ROLLOUT_TIMEOUT_COUNT
NPU_ROLLOUT_FRESH_CRASH_COUNT
NPU_ROLLOUT_FALLBACK_COUNT
NPU_ROLLOUT_ENGINE_CREATE_FAILURE_COUNT
NPU_ROLLOUT_QUALITY_FAILURE_COUNT
NPU_ROLLOUT_SUCCESS_RATE
NPU_ROLLOUT_RISK_LEVEL
NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW
NPU_ROLLOUT_BLOCKERS
SAFE_NEXT_ACTION
```

## Success Classification

A success artifact is a Phase 7B / property phase 8 run where:

- `status=success`
- selected/effective backend indicates NPU
- backend evidence includes QNN / HTP / FastRPC / NPU
- `fallback=false` or `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `run_decode_reached=true`
- `native_cleanup_reached=true`
- `npu_standard_route_phase=8`
- `npu_standard_route_streaming_executed=true`
- `npu_standard_route_native_streaming_used=false`
- `npu_standard_route_rollback_required=false`
- streaming final text matches DB / Markdown when those keys are present

R1b keys such as `npu_standard_route_selection_mode` and
`npu_standard_route_completed_route_selected` are used as explanatory evidence
when present. They are not required so older Phase 8 artifacts remain
classifiable.

The rollout monitor counts older explicit-phase Phase 8 samples as success when
the runtime gates pass. R1b completed-route diagnostics are evaluated by the dev
gate removal readiness layer. That layer requires at least one positive Phase 8
success artifact with:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_completed_route_family=npu_standard_route_completed
```

It does not require every historical success artifact to carry those keys.

## Suppression Pass Classification

A quality-candidate-fail artifact is counted as a safety pass, not as a rollout
failure, when:

- `output_quality_candidate_status=quality_candidate_fail` or
  `npu_standard_route_quality_gate_passed=false`
- `npu_standard_route_output_suppressed=true`
- UI append did not execute
- TTS did not start
- DB save did not execute
- Markdown did not execute
- pseudo streaming did not execute
- rollback is required with a quality-candidate-fail / quality-gate reason

This proves dangerous output such as `_turn>` remained suppressed. It is not a
positive promotion sample by itself.

## Failure Classification

An artifact is counted as failure when any of these appear:

- `timeout=true`
- `fresh_crash=true`
- `fallback=true` or `fallback_used=true`
- `run_decode_reached=false`
- `native_cleanup_reached=false`
- engine-create failure evidence
- rollback required for a reason other than safe quality-candidate suppression
- quality-candidate-fail output reached UI / TTS / DB / Markdown / streaming
- `npu_standard_route_native_streaming_used=true`
- streaming text mismatch with DB or Markdown

## Risk Levels

`low`:

- success count is at least 3
- failure / timeout / fresh crash / fallback counts are zero
- at least one suppression pass exists

`medium`:

- at least one success exists
- no failures are present
- sample count or suppression evidence is still insufficient

`high`:

- any failure exists
- timeout / fresh crash / fallback exists
- unsafe delivery or text mismatch exists

`unknown`:

- no NPU rollout samples are found

## Dev Gate Review Criteria

Only move to a later dev-gate removal review when:

```text
NPU_ROLLOUT_MONITOR_STATUS=healthy
NPU_ROLLOUT_RISK_LEVEL=low
NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true
NPU_ROLLOUT_BLOCKERS=none
```

The monitor is evidence aggregation only. It does not authorize removing the dev
gate by itself.

The next review layer is:

```text
scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
```

That review combines rollout monitor output, final promotion GO evidence, R1b
completed-route diagnostics, text-consistency evidence, and rollback-plan
documentation. Phase R3b uses that approval to remove the dev gate requirement
for the user-facing completed route only.

R3b does not add NPU to Automatic selection. It allows:

```text
Settings: NPU Experimental
debug.lami.npu_standard_route_phase=0 or absent
debug.lami.npu_standard_route_completed_route_disabled!=true
```

to resolve to completed route phase `8` even when
`debug.lami.npu_standard_route_dev_gate` is false. Explicit developer phase
overrides `1..8` remain dev-gated.

The rollback property for the completed route is:

```text
debug.lami.npu_standard_route_completed_route_disabled=true
```

## Rollback Criteria

Stop rollout review and keep the dev gate if any of these become non-zero:

- `NPU_ROLLOUT_FAILURE_COUNT`
- `NPU_ROLLOUT_TIMEOUT_COUNT`
- `NPU_ROLLOUT_FRESH_CRASH_COUNT`
- `NPU_ROLLOUT_FALLBACK_COUNT`
- `NPU_ROLLOUT_ENGINE_CREATE_FAILURE_COUNT`
- `NPU_ROLLOUT_QUALITY_FAILURE_COUNT`

## R1b Pending Handling

R1b adds completed-route summary keys to compact/full dumps and NPU diagnostic
copy. Device confirmation may lag behind script work. The monitor therefore
does not require these keys. A pre-R1b Phase 8 success artifact can still count
as success if phase, backend, delivery, pseudo-streaming, and rollback gates
pass.

Dev-gate removal readiness remains blocked until at least one positive Phase 8
success artifact includes the R1b completed-route diagnostics.

## R5a UX Acceptance

R2 rollout monitoring answers whether Phase 8 artifacts are healthy. R5a adds a
user-visible acceptance layer for explicit `NPU Experimental`:

```text
scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
```

The UX review requires positive completed-route samples with
`npu_standard_route_dev_gate_required=false`, UI append, DB save, Markdown,
pseudo streaming, text consistency, no native streaming, and no rollback. It
also counts quality-candidate-fail suppression as safety-pass evidence and can
count a completed-route kill switch block sample.

Use the R5a review before R5b/R5c rollout planning. Do not treat it as Automatic
backend approval.
