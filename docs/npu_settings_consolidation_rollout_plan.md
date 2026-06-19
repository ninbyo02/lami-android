# NPU Settings Consolidation Rollout Plan

Scope: Settings display consolidation only. This plan does not change Android
runtime behavior, ChatScreen delivery, NPU route behavior, native libraries,
hidden configuration, DB schema, or stored preference keys.

## Current Settings Structure

Settings previously exposed NPU staged entries as if they were separate backend
choices. The visible history included entries such as:

- NPU S1 response display
- NPU S2 DB save
- NPU S3 Markdown
- NPU S4 Streaming
- NPU S5 TTS

The staged standard-route work now extends through the later DEV phases:

- Phase 1: route entry diagnostic
- Phase 2: conversation-created diagnostic
- Phase 3: generate-response diagnostic
- Phase 4: UI append gate
- Phase 5: TTS gate
- Phase 6: DB save gate
- Phase 7A: Markdown gate
- Phase 7B: pseudo streaming gate (`debug.lami.npu_standard_route_phase=8`)

These are integration phases, not separate hardware backends. The Settings UI
now presents NPU as one user-facing backend option and keeps S1-S5 as
developer-only legacy phase choices.

## Problem With NPU S1-S5 As Backends

Showing S1-S5 beside CPU and GPU creates three problems:

- Users can interpret each phase as a different accelerator backend.
- Developer phase labels leak implementation details into the backend selector.
- Promotion state becomes ambiguous because Phase 7B is the first end-to-end
  standard-route candidate, while earlier phases intentionally keep parts of
  the route closed.

The stable mental model should be one NPU backend with a separate developer
phase selector while rollout remains gated.

## NPU Backend Consolidation Proposal

Current user-facing backend selector:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

R5b UX wording review recommended this display-only label direction, and R5c
implements it:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

This label change does not change route behavior, diagnostics, or preference
values. It reflects that NPU has completed the explicit-selection standard route
and is a production candidate, while still not being Automatic default or a
fully unqualified stable backend. See
`docs/npu_settings_ux_label_review.md`.

The NPU option maps to the existing standard route preference shape rather than
introducing a new runtime path:

```text
preferredBackendDryRunSetting=DEFAULT
npuStandardRouteMode=FULL
npuStandardRouteSelectionSource=USER_FACING_NPU_EXPERIMENTAL
```

The later Phase 6-8 verification remains controlled by the existing DEV
property:

```text
debug.lami.npu_standard_route_phase=6..8
```

NPU remains marked experimental even after rollout readiness confirms:

```text
NPU_ROLLOUT_READY=true
ROLLOUT_RISK_LEVEL=medium or lower
```

The expected ready state can still carry `medium` rollout risk because rollout
monitoring and any future production label change are separate tasks.

## R1 Runtime Mapping

Phase R1 connected the user-facing NPU selection to the completed
standard route behavior behind the developer gate. Phase R3b removes that dev
gate requirement for the completed route only. The current mapping is:

```text
NPU Beta + no explicit phase property + completed-route kill switch off
  -> effective phase=8
  -> npu_standard_route_phase_name=7b_pseudo_streaming_gate
```

If `debug.lami.npu_standard_route_phase` is explicitly set to `1..8`, it is a
developer phase override and still requires
`debug.lami.npu_standard_route_dev_gate=true`:

```text
NPU Beta + debug.lami.npu_standard_route_dev_gate=true
  + debug.lami.npu_standard_route_phase=5
  -> effective phase=5
  -> npu_standard_route_selection_mode=developer_phase_override
```

If the dev gate is false and an explicit developer phase is set, the developer
override is blocked. User-facing NPU falls back to the completed
route default phase `8` instead of running a partial phase:

```text
npu_standard_route_developer_phase_override_block_reason=dev_gate_disabled
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
```

This is still not an Automatic-backend rollout. CPU / GPU / Automatic behavior
remains unchanged.

R3b also adds a completed-route kill switch:

```text
debug.lami.npu_standard_route_completed_route_disabled=true
```

When set, user-facing `NPU Beta` does not select the completed route:

```text
npu_standard_route_completed_route_selected=false
npu_standard_route_completed_route_block_reason=kill_switch_disabled
npu_standard_route_completed_route_disabled_by_property=true
```

R5a requires this state to produce a NPU completed-route safe block artifact,
not an `Automatic` / `local_default` failure. The expected diagnostic shape is:

```text
status=blocked
reason=kill_switch_disabled
effective_backend=NPU
backend_evidence=NPU_completed_route_kill_switch_blocked
npu_standard_route_completed_route_rollout_state=disabled_by_kill_switch
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_phase=8
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_executed=false
npu_standard_route_streaming_executed=false
fallback=false
```

The kill switch remains a rollback control: no native call, generation, UI
append, TTS, DB save, Markdown, pseudo streaming, or Automatic fallback should
occur while it is enabled.

R4/R4b inventory after R5c treats `NPU Beta` as the current user-facing label.
The old Experimental wording is a cleanup candidate only when used as current
Settings wording. It remains KEEP when used in diagnostics compatibility
contexts such as `npu_standard_route_selection_mode=user_facing_npu_experimental`.

## R1b Diagnostics Polish

Phase R1b keeps the R1 runtime behavior unchanged and makes the completed-route
mapping visible in every copied diagnostic surface. The Settings label is now
`NPU Beta`, but a successful user-facing NPU selection should still show these
compatibility diagnostics:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_user_facing_backend=NPU Experimental
npu_standard_route_completed_phase_default=8
npu_standard_route_completed_route_selected=true
npu_standard_route_developer_phase_override=false
npu_standard_route_completed_route_block_reason=none
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_user_facing_selected_backend=NPU Experimental
npu_standard_route_completed_route_family=npu_standard_route_completed
npu_standard_route_internal_legacy_backend=NPU_S5
npu_standard_route_internal_legacy_route_family=npu_s5
```

`selected_backend=NPU_S5` and `route_family=npu_s5` may still appear in compact
logs because the completed route reuses the legacy S5-compatible internal path.
Treat those fields as internal compatibility evidence. Use the
`npu_standard_route_user_facing_*` and `npu_standard_route_completed_*` keys for
rollout and final-promotion interpretation.

Android `setprop` cannot clear a property with an empty value. Do not use
`setprop debug.lami.npu_standard_route_phase ""`. To remove an explicit phase
override, reboot the device or set:

```text
adb shell setprop debug.lami.npu_standard_route_phase 0
```

Phase `0` is treated as no explicit developer override, allowing user-facing
`NPU Beta` to resolve to the completed route default phase `8` while the
dev gate remains enabled.

## R1c Phase 0 Resolution Fix

R1c fixes a device-observed gap where `debug.lami.npu_standard_route_phase=0`
could still resolve to Phase 1 when the stored
`npu_standard_route_selection_source` was missing or legacy-unspecified. In the
consolidated Settings UI, `preferredBackend=DEFAULT` plus
`npuStandardRouteMode=FULL` is displayed as user-facing `NPU Beta`.
Therefore:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=0
preferredBackend=DEFAULT
npuStandardRouteMode=FULL
npuStandardRouteSelectionSource=LEGACY_UNSPECIFIED
```

now resolves as:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_completed_route_family=npu_standard_route_completed
```

Explicit phase values `1` through `8` still remain developer overrides and win
over the completed-route default.

## R2 Rollout Monitor

Phase R2 adds a separate artifact monitor:

```text
scripts/review_npu_rollout_monitor.sh --device-runs artifacts/device_runs
```

This is not a runtime change. It aggregates copied NPU diagnostics and reports
success count, suppression-pass count, failure count, rollback count, timeout,
fresh crash, fallback, engine-create failure, quality failure, success rate, and
risk level.

R1b completed-route keys are useful but optional for this monitor. Older Phase 8
artifacts still count when the standard route gates, pseudo streaming gates, and
rollback gates pass. Move toward a later dev-gate removal review only when:

```text
NPU_ROLLOUT_MONITOR_STATUS=healthy
NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true
NPU_ROLLOUT_RISK_LEVEL=low
```

## R3 Dev Gate Removal Readiness Review

Phase R3 adds the review script but does not remove the dev gate:

```text
scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
```

The review requires low-risk rollout monitoring, at least three Phase 8 success
samples, at least one suppression-pass sample, final promotion GO, R1b
completed-route diagnostics, phase 8 text consistency, and a documented rollback
plan. A positive result authorizes R3b completed-route dev-gate removal with a
runtime kill switch.

## R3b Completed Route Dev Gate Removal

Phase R3b implements that authorized change. User-facing NPU
now uses the completed standard route default phase `8` without requiring
`debug.lami.npu_standard_route_dev_gate=true`, as long as the completed-route
kill switch is not enabled. Explicit developer phase overrides remain gated by
`debug.lami.npu_standard_route_dev_gate=true`.

Expected completed-route diagnostics when the dev gate is off:

```text
npu_standard_route_dev_gate_enabled=false
npu_standard_route_dev_gate_required=false
npu_standard_route_rollout_gate_enabled=true
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_completed_route_block_reason=none
npu_standard_route_completed_route_kill_switch_enabled=false
npu_standard_route_completed_route_disabled_by_property=false
npu_standard_route_completed_route_rollout_state=enabled
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
```

This keeps legacy S1-S5 preference values available for developer compatibility
and rollback, but they are not normal user-facing backends.

## R4 Legacy S1-S5 Inventory

Phase R4 adds a static inventory for legacy S1-S5 references:

```text
scripts/review_npu_legacy_s1_s5_inventory.sh
```

S1-S5 remain compatibility and developer-phase controls, not user-facing
backends. Cleanup should start with user-facing label remnants and stale docs;
do not remove enum values, preference parsing, route execution compatibility, or
artifact parser compatibility yet.

## R5a UX Acceptance Review

Phase R5a adds a final UX acceptance checklist and artifact review for explicit
`NPU Beta` usage:

```text
docs/npu_experimental_ux_acceptance_checklist.md
scripts/create_npu_experimental_ux_manifest.sh --date YYYYMMDD
scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
```

This is not formal backend promotion and does not add NPU to Automatic. It
checks that the completed route is usable from the user's perspective:

- UI append is visible
- TTS starts when enabled, or reports `tts_disabled` when disabled
- DB save succeeds
- Markdown executes
- pseudo streaming executes with `native_streaming_used=false`
- quality-candidate-fail output is suppressed
- the completed-route kill switch can block delivery

R5a low risk requires three positive UX success artifacts, one suppression-pass
artifact, no failures, at least one TTS ON success, and preferably one kill
switch block artifact. Medium risk can still pass the minimum UX gates while
requesting optional TTS or kill-switch evidence.

## Debug Phase Selector Proposal

Keep the system properties as the canonical DEV gate during rollout:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=1..8
```

Settings now keeps S1-S5 behind developer access as legacy phase choices:

```text
DEV: NPU S1 response only
DEV: NPU S2 DB save
DEV: NPU S3 Markdown
DEV: NPU S4 Streaming
DEV: NPU S5 TTS
```

The property-driven Phase 6-8 selector remains external for now:

```text
NPU DEV Phase 6: UI + TTS + DB
NPU DEV Phase 7A: UI + TTS + DB + Markdown
NPU DEV Phase 7B: pseudo streaming
```

Do not expose these as independent production backend choices.

## Migration Strategy

Use a non-destructive migration:

1. Keep existing preference keys readable.
2. Keep existing S1-S5/S8 values mapped to their equivalent phase.
3. Add display labels that clarify these values are NPU DEV phases.
4. Show a single consolidated NPU backend label in the normal backend list.
5. Keep the DEV phase selector hidden or developer-scoped until standard route
   rollout monitoring is clean.

No stored preference should be deleted during this transition.

## Backward Compatibility

Backward compatibility requirements:

- Existing preference keys remain valid.
- Existing S1-S5 selections continue to map to the same diagnostic behavior.
- Existing S1-S5 enum values remain parseable and selectable from the developer
  section.
- `npu_standard_route_selection_source` records whether the current NPU value
  came from the user-facing NPU item or from a developer legacy
  phase selector. Existing installations without this key remain readable as
  legacy unspecified.
- `debug.lami.npu_standard_route_phase` remains the authoritative phase gate.
- CPU and GPU backend selection semantics do not change.
- GPU remains experimental and blocked from promotion independently of NPU.
- Quality-gate suppression remains active for NPU even after Settings labels
  are consolidated.

## Rollout Steps

1. Run final promotion review:

   ```bash
   scripts/review_npu_standard_route_final_promotion.sh \
     --input artifacts/device_runs/npu_phase8_latest.txt
   ```

2. Run rollout readiness review:

   ```bash
   scripts/review_npu_rollout_readiness.sh \
     --input artifacts/device_runs/npu_phase8_latest.txt
   ```

   The rollout script accepts either raw diagnostics or final promotion review
   output. It uses the final promotion review as the source of truth.

3. Require a positive Phase 7B artifact:

   ```text
   NPU_STANDARD_ROUTE_FINAL_REVIEW=ready
   READY_FOR_NPU_STANDARD_ROUTE=true
   PROMOTION_DECISION=go
   PROMOTION_SCORE=100
   ```

4. Keep at least one suppression artifact proving unsafe output is blocked:

   ```text
   NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass
   PROMOTION_DECISION=blocked_for_this_artifact
   ```

5. Settings UI consolidation is implemented as a display-only change:

   ```text
   Automatic
   CPU
   GPU Experimental
   NPU Beta
   ```

   S1-S5 are no longer shown as normal backend options.

6. Monitor rollout artifacts and keep rollback available.

## Rollback Plan

Rollback immediately if any of these appear after Settings consolidation:

- fallback is used while reporting NPU success
- timeout or fresh crash appears
- Phase 7B final review becomes blocked
- quality-candidate-fail output reaches UI, TTS, DB, Markdown, or streaming
- DB / Markdown / pseudo streaming text diverges
- Settings migration breaks existing S1-S5 developer values
- CPU route behavior changes
- GPU route promotion blocker is bypassed
- `NPU Beta` opens Phase 8 while
  `debug.lami.npu_standard_route_completed_route_disabled=true`

Rollback action:

```text
SAFE_NEXT_ACTION=restore_developer_phase_selection_and_disable_consolidated_npu_backend
```

## Readiness Script

`scripts/review_npu_rollout_readiness.sh` emits:

```text
NPU_ROLLOUT_READY=...
ROLLOUT_RISK_LEVEL=...
PASSED_COMPONENTS=...
FAILED_COMPONENTS=...
REMAINING_WORK=...
SAFE_NEXT_ACTION=...
```

Expected positive output after a Phase 7B success artifact:

```text
NPU_ROLLOUT_READY=true
ROLLOUT_RISK_LEVEL=medium
PASSED_COMPONENTS=final_promotion_review,phase7b_pseudo_streaming,quality_gate_suppression,settings_consolidation_plan,backward_compatibility_plan
FAILED_COMPONENTS=none
REMAINING_WORK=settings_ui_consolidation_implementation,developer_phase_selector_implementation,rollout_monitoring
SAFE_NEXT_ACTION=implement_settings_consolidation_ui_behind_backward_compatible_preferences
```

`medium` risk means the NPU route can proceed to Settings consolidation work,
not that the UI migration has already been implemented.
