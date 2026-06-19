# NPU Dev Gate Removal Readiness

Scope: review / docs / scripts only. This phase does not remove the dev gate and
does not change runtime, ChatScreen, Settings UI, NPU route behavior, native
libraries, DB schema, quality gate, or suppression behavior.

## Purpose

The NPU standard route has passed staged implementation through Phase 8, final
promotion review, Settings consolidation, R1 completed-route mapping, R1b
diagnostics polish, and R2 rollout monitoring. Phase R3 answers one question:

```text
Is there enough evidence to start a future dev-gate removal implementation?
```

Use:

```text
scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
scripts/review_npu_dev_gate_removal_readiness.sh --input artifacts/device_runs/npu_phase8_latest.txt
scripts/review_npu_dev_gate_removal_readiness.sh --input artifacts/device_runs/npu_rollout_monitor_latest.txt
```

## Output Keys

```text
NPU_DEV_GATE_REMOVAL_REVIEW
READY_TO_REMOVE_DEV_GATE
DEV_GATE_REMOVAL_DECISION
DEV_GATE_REMOVAL_DECISION_REASON
REQUIRED_SAMPLE_COUNT
CURRENT_SUCCESS_COUNT
CURRENT_SUPPRESSION_PASS_COUNT
CURRENT_FAILURE_COUNT
CURRENT_ROLLBACK_COUNT
CURRENT_TIMEOUT_COUNT
CURRENT_FRESH_CRASH_COUNT
CURRENT_FALLBACK_COUNT
REQUIRED_GATES
PASSED_GATES
FAILED_GATES
REMAINING_BLOCKERS
ROLLBACK_PLAN_REQUIRED
SAFE_NEXT_ACTION
```

## Required Gates

Ready requires all of the following:

- rollout monitor is low risk
- Phase 8 success sample count is at least 3
- suppression pass sample count is at least 1
- failure count is 0
- non-suppression rollback count is 0
- timeout count is 0
- fresh crash count is 0
- fallback count is 0
- final promotion review is GO
- R1b completed route diagnostics are present
- `npu_standard_route_native_streaming_used=false`
- pseudo streaming final text matches DB / Markdown when those keys are present
- rollback plan is documented

Expected ready output:

```text
NPU_DEV_GATE_REMOVAL_REVIEW=ready
READY_TO_REMOVE_DEV_GATE=true
DEV_GATE_REMOVAL_DECISION=go
DEV_GATE_REMOVAL_DECISION_REASON=rollout_monitor_low_risk_and_final_promotion_go
SAFE_NEXT_ACTION=implement_dev_gate_removal_with_runtime_kill_switch
```

This still does not remove the gate. It only authorizes planning the next
implementation phase.

## Not Ready Conditions

Readiness is blocked by:

- sample count below the threshold
- no suppression-pass sample
- any failure sample
- timeout / fresh crash / fallback
- unsafe delivery
- final promotion not GO
- rollout monitor missing
- R1b diagnostics missing
- rollback plan missing

Example insufficient-samples output:

```text
NPU_DEV_GATE_REMOVAL_REVIEW=not_ready
READY_TO_REMOVE_DEV_GATE=false
DEV_GATE_REMOVAL_DECISION=blocked
DEV_GATE_REMOVAL_DECISION_REASON=insufficient_rollout_samples
SAFE_NEXT_ACTION=collect_phase8_success_and_suppression_samples
```

Use the rollout sample collection helper before rerunning readiness:

```text
scripts/create_npu_rollout_sample_manifest.sh --date YYYYMMDD
scripts/review_npu_rollout_sample_set.sh --device-runs artifacts/device_runs --date YYYYMMDD
```

The required minimum collection is three Phase 8 success samples plus one
quality-candidate-fail suppression-pass sample. See
`docs/npu_rollout_sample_collection.md`.

## R1 / R1b / R2 Dependencies

R1 is required because user-facing `NPU Experimental` must map to the completed
standard route.

R1b is required because diagnostics must distinguish the user-facing completed
route from the internal legacy `NPU_S5` / `npu_s5` compatibility path:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_effective_phase=8
npu_standard_route_completed_route_family=npu_standard_route_completed
```

R1b diagnostics are not required on every historical artifact. Older Phase 8
samples collected with explicit `debug.lami.npu_standard_route_phase=8` can
remain valid for success-count evidence even when they report:

```text
npu_standard_route_selection_mode=developer_phase_override
npu_standard_route_completed_route_selected=false
npu_standard_route_effective_phase_source=debug_property
```

The R1b gate passes when at least one positive Phase 8 success artifact has the
completed-route diagnostics above. A positive artifact must be NPU-backed,
Phase 8, streaming-executed, rollback-free, and have no fallback / timeout /
fresh crash. The readiness script reports:

```text
R1B_DIAGNOSTICS_FOUND=true
R1B_DIAGNOSTICS_ARTIFACT=<basename>
R1B_DIAGNOSTICS_MODE=user_facing_npu_experimental/completed_route_default
```

R1c additionally requires `debug.lami.npu_standard_route_phase=0` to mean
"clear explicit developer phase override". With Settings showing
`NPU Experimental`, phase `0` or an absent phase property must resolve to the
completed route default:

```text
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
```

Explicit phase values `1` through `8` remain developer overrides and should not
be used for default rollout samples unless intentionally testing a phase.

R2 is required because dev-gate removal should be based on multiple artifacts,
not a single final-promotion proof.

## Rollback Plan

Dev-gate removal must have a rollback plan before implementation.

Rollback triggers:

- `fallback=true` or `fallback_used=true`
- `timeout=true`
- `fresh_crash=true`
- `npu_standard_route_rollback_required=true` except safe
  quality-candidate-fail suppression samples
- unsafe delivery of quality-candidate-fail output
- DB save text mismatch
- Markdown final text mismatch
- pseudo streaming final text mismatch
- `npu_standard_route_native_streaming_used=true`
- user-visible quality regression
- crash / tombstone evidence
- engine-create failure recurrence

Rollback action:

- restore the dev gate requirement
- force `NPU Experimental` completed route back to diagnostic / blocked mode
- keep CPU and GPU behavior unaffected
- keep legacy S1-S5 developer override available
- preserve quality-candidate-fail suppression
- document an adb property kill switch before implementation

Phase R3b implements dev-gate removal for the user-facing completed route with
this runtime kill switch:

```text
debug.lami.npu_standard_route_completed_route_disabled=true
```

When this property is `true`, `NPU Experimental` does not select the completed
route, delivery gates stay closed, and diagnostics report
`npu_standard_route_completed_route_block_reason=kill_switch_disabled`.

## Next Phase

Phase R3b may be enabled only after:

```text
NPU_DEV_GATE_REMOVAL_REVIEW=ready
READY_TO_REMOVE_DEV_GATE=true
DEV_GATE_REMOVAL_DECISION=go
```

R3b remains reversible, preserves CPU/GPU behavior, preserves legacy developer
phase overrides, and keeps quality-candidate-fail suppression as the stop line.
It does not make NPU part of Automatic backend selection.

R3b behavior:

```text
NPU Experimental + phase=0 or absent + kill switch off
  -> completed route default phase=8
  -> dev gate not required

explicit phase=1..8 + dev gate=true
  -> developer phase override

explicit phase=1..8 + dev gate=false
  -> developer override blocked
  -> user-facing NPU Experimental falls back to completed route phase=8
```

## Legacy S1-S5 Cleanup Dependency

Before removing or hiding additional developer controls, run:

```text
scripts/review_npu_legacy_s1_s5_inventory.sh
```

Legacy S1-S5 values are still part of the rollback and developer override
surface. Dev-gate removal readiness does not authorize deleting those values.
Cleanup must follow the staged deprecation plan in
`docs/npu_legacy_s1_s5_deprecation_inventory.md`.

## R5a UX Acceptance Dependency

After R3b, use R5a to review the user-visible `NPU Experimental` experience
before any formal promotion wording or Automatic-backend discussion:

```text
scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
```

R5a is still explicit-user-selection only. It checks UI visibility, TTS ON/OFF
handling, DB save, Markdown, pseudo streaming, safe suppression, and the
completed-route kill switch. A positive R5a review does not authorize removing
legacy S1-S5 compatibility or adding NPU to Automatic.
