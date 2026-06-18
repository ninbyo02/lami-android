# NPU Settings Consolidation Rollout Plan

Scope: review, documentation, and script planning only. This plan does not
change Android runtime behavior, ChatScreen delivery, NPU route behavior,
Settings UI implementation, native libraries, hidden configuration, or stored
preference values.

## Current Settings Structure

Settings currently exposes NPU staged entries as if they were separate backend
choices. The visible history includes entries such as:

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

These are integration phases, not separate hardware backends.

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

Short-term backend selector:

```text
Automatic
CPU
GPU Experimental
NPU Experimental / DEV
```

NPU should remain marked experimental until the rollout review confirms:

```text
NPU_ROLLOUT_READY=true
ROLLOUT_RISK_LEVEL=medium or lower
```

The current expected ready state still carries `medium` rollout risk because
Settings UI consolidation, developer phase selector UI, and rollout monitoring
are not implemented by this review step.

## Debug Phase Selector Proposal

Keep the system properties as the canonical DEV gate during rollout:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=1..8
```

Later Settings work can mirror this as a developer-only phase selector:

```text
NPU DEV Phase 1: diagnostics
NPU DEV Phase 2: conversation
NPU DEV Phase 3: generate diagnostics
NPU DEV Phase 4: UI
NPU DEV Phase 5: UI + TTS
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
4. Add a single consolidated NPU backend label only after final promotion and
   rollout readiness reviews pass.
5. Keep the DEV phase selector hidden or developer-scoped until standard route
   rollout monitoring is clean.

No stored preference should be deleted during this transition.

## Backward Compatibility

Backward compatibility requirements:

- Existing preference keys remain valid.
- Existing S1-S5 selections continue to map to the same diagnostic behavior.
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

5. Implement Settings UI consolidation in a later UI-only task.

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
