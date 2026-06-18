# NPU Settings Display Consolidation Plan

## Current Problem

The Settings backend selector used to expose entries such as:

- NPU S1 response display
- NPU S2 DB save
- NPU S3 Markdown
- NPU S4 Streaming
- NPU S5 TTS

These are not separate hardware backends. They are staged NPU standard-route
integration phases. Presenting them beside CPU/GPU made the user-facing backend
model look more fragmented than the actual runtime design.

The UI now shows one user-facing NPU backend entry:

- NPU Experimental

S1-S5 remain available only as developer legacy phase choices.

Phase R1 adds a non-destructive source marker so the app can distinguish the
user-facing `NPU Experimental` selection from legacy developer S1-S5 choices.
The marker does not remove or rename the existing preference keys.

## Interpretation

The durable backend choices are:

- Automatic
- CPU
- GPU Experimental
- NPU Experimental

S1-S8 should be treated as NPU route phases:

- Phase 1: route entry diagnostics
- Phase 2: conversation-created diagnostic
- Phase 3: generate-response diagnostic with output suppression
- Phase 4: UI append gate
- Phase 5: TTS gate
- Phase 6: DB save gate
- Phase 7A: Markdown gate
- Phase 7B: pseudo streaming gate (`debug.lami.npu_standard_route_phase=8`)

## Short-Term UI Policy

Keep existing preference keys and persisted values. Do not remove S1-S8 choices
or migrate stored preferences in the short term.

Non-destructive display improvements are acceptable:

- Rename user-visible labels to make clear they are `NPU DEV phase` choices.
- Keep the underlying enum / preference value unchanged.
- Keep all NPU phase choices behind debug/developer affordances where possible.
- In normal user-facing copy, describe the backend as `NPU Experimental / DEV`.

## Medium-Term Migration Policy

After Phase 6/7/8 gates and final promotion review were validated, the UI was
split into:

- Backend selector: Automatic / CPU / GPU Experimental / NPU Experimental
- NPU developer detail selector: legacy S1-S5 phase choices, with Phase 6-8
  still controlled by `debug.lami.npu_standard_route_phase`

Migration should preserve compatibility:

- Existing S1-S8 preference keys remain readable.
- Stored S1-S8 values map to equivalent NPU detail phases.
- No destructive deletion of preferences during the transition.

The rollout-specific review and migration sequence are tracked in
`docs/npu_settings_consolidation_rollout_plan.md`. Use
`scripts/review_npu_rollout_readiness.sh` before starting Settings UI changes.

## User-Facing Label Proposal

Developer-only legacy labels:

- `DEV: NPU S1 response only`
- `DEV: NPU S2 DB save`
- `DEV: NPU S3 Markdown`
- `DEV: NPU S4 Streaming`
- `DEV: NPU S5 TTS`

Backend-level label:

- `NPU Experimental`

## Developer Phase Selector Proposal

Expose the phase explicitly as a developer control:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=1..8
```

Settings can mirror this later, but the system property remains the canonical
safe gate during staged validation.

## Migration Risk

- Removing S1-S8 choices would break existing developer workflows.
- Renaming values instead of labels would require preference migration.
- Moving too early to a single `NPU` user-facing entry could imply production
  readiness before Phase 6/7 validation is complete.

## Implementation Status

1. Existing keys and route behavior are kept.
2. The normal backend list now shows NPU as one `NPU Experimental` entry.
3. Legacy S1-S5 entries are developer-only phase choices.
4. CPU/GPU labels and route behavior are unchanged.
5. Phase 6-8 remain property-driven and are not exposed as destructive
   preference migrations.
6. R1 maps user-facing `NPU Experimental` to completed phase 8 only while
   `debug.lami.npu_standard_route_dev_gate=true`; explicit phase properties
   still override the completed-route default.
