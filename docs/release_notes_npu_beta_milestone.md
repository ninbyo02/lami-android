# NPU Beta Milestone Release Notes

Scope: documentation milestone note. This does not change runtime behavior,
Settings UI behavior, NPU route behavior, diagnostics keys, native code, or
release packaging.

## Summary

LAMI has reached the NPU Beta milestone for explicit user selection on supported
Qualcomm NPU devices.

The user-facing backend list is:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

NPU Beta is a production-candidate completed route. It is available by explicit
selection, but it is not part of Automatic backend selection yet.

## Completed

The NPU standard route has completed the staged Phase 1-8 rollout:

- Phase 1: route-entry diagnostics
- Phase 2: conversation-created diagnostics
- Phase 3: generate diagnostics
- Phase 4: UI append gate
- Phase 5: TTS gate
- Phase 6: DB save gate
- Phase 7A: Markdown gate
- Phase 7B: pseudo streaming gate

Milestone capabilities:

- Qualcomm NPU-backed local inference route for explicit NPU Beta selection
- UI append through the completed route
- Android TTS integration
- DB persistence for safe assistant output
- Markdown rendering
- pseudo streaming from finalized safe text
- quality-gate suppression for unsafe template artifacts
- rollout monitor and final promotion review scripts
- dev-gate removal for the completed route
- completed-route kill switch

## Current Status

```text
NPU_STANDARD_ROUTE_FINAL_REVIEW=ready
PROMOTION_DECISION=go
NPU_ROLLOUT_RISK_LEVEL=low
READY_TO_REMOVE_DEV_GATE=true
```

NPU Beta should be described as:

- explicit user selection
- Qualcomm NPU acceleration path
- completed standard route / phase 8 behavior
- production candidate
- local-first / on-device inference route

NPU Beta should not yet be described as:

- Automatic default
- fully stable on every Android device
- native token streaming
- replacement for all CPU fallback behavior

## Not Yet

The milestone intentionally does not include:

- Automatic backend enrollment
- native token streaming from the LiteRT-LM lower-level route
- removal of legacy S1-S5 enum / preference / parser compatibility
- broad device compatibility claims beyond validated hardware

## Migration Notes

The user-facing label changed:

```text
NPU Experimental -> NPU Beta
```

This is a display and documentation milestone. It does not rename diagnostics or
stored compatibility values.

## Compatibility Notes

The completed route intentionally keeps legacy internal compatibility evidence:

```text
selected_backend=NPU_S5
route_family=npu_s5
npu_standard_route_selection_mode=user_facing_npu_experimental
```

These values are retained for parser, artifact, rollout monitor, and final
promotion compatibility. User-facing docs and Settings wording should use
`NPU Beta`.

## Kill Switch

The completed route can be blocked with:

```text
debug.lami.npu_standard_route_completed_route_disabled=true
```

When enabled, the route should produce a NPU completed-route safe block:

- no native call
- no generation
- no UI append
- no TTS
- no DB save
- no Markdown processing
- no pseudo streaming
- no fallback to `Automatic` / `local_default`

## Badge Candidates

Potential README badges:

- Android
- Apache-2.0
- Local AI
- On-device AI
- Qualcomm NPU
- NPU Beta

Badge wording should avoid implying that NPU is the Automatic default or that
native token streaming is implemented.

## Follow-Up

Recommended next docs / release tasks:

- keep README concise and point detailed NPU history here
- continue device compatibility collection for NPU Beta
- keep GPU marked Experimental until its promotion blocker is resolved
- keep legacy S1-S5 cleanup staged and non-destructive
- consider Automatic enrollment only after a separate explicit review
