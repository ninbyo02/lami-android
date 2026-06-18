# NPU Settings Display Consolidation Plan

## Current Problem

The Settings backend selector currently exposes entries such as:

- NPU S1 response display
- NPU S2 DB save
- NPU S3 Markdown
- NPU S4 Streaming
- NPU S5 TTS

These are not separate hardware backends. They are staged NPU standard-route
integration phases. Presenting them beside CPU/GPU makes the user-facing
backend model look more fragmented than the actual runtime design.

## Interpretation

The durable backend choices should trend toward:

- Automatic
- CPU
- GPU Experimental
- NPU Experimental / DEV

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

After Phase 6/7/8 gates and final promotion review are validated, split the UI into:

- Backend selector: Automatic / CPU / GPU Experimental / NPU Experimental
- NPU detail selector: Phase 1 through Phase 7

Migration should preserve compatibility:

- Existing S1-S8 preference keys remain readable.
- Stored S1-S8 values map to equivalent NPU detail phases.
- No destructive deletion of preferences during the transition.

## User-Facing Label Proposal

Short term:

- `NPU DEV Phase 1: diagnostics`
- `NPU DEV Phase 2: conversation`
- `NPU DEV Phase 3: generate diagnostics`
- `NPU DEV Phase 4: UI`
- `NPU DEV Phase 5: UI + TTS`
- `NPU DEV Phase 6: UI + TTS + DB`
- `NPU DEV Phase 7A: UI + TTS + DB + Markdown`
- `NPU DEV Phase 7B: pseudo streaming`

Backend-level label:

- `NPU Experimental / DEV`

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

## Implementation Order

1. Keep existing keys and route behavior.
2. Fix Phase 4/5 actual UI/TTS delivery while DB/Markdown/Streaming remain off.
3. Update docs and diagnostics to distinguish `allowed` from `executed`.
4. Adjust Settings labels non-destructively in a later UI-only change.
5. Add a separate NPU phase selector only after Phase 8 and
   `docs/npu_standard_route_final_promotion_review.md` are stable.
