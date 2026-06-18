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
NPU Experimental
```

The NPU option maps to the existing standard route preference shape rather than
introducing a new runtime path:

```text
preferredBackendDryRunSetting=DEFAULT
npuStandardRouteMode=FULL
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
   NPU Experimental
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
