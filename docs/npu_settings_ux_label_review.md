# NPU Settings UX Label Review

Scope: R5b UX / wording review and R5c display-label implementation. This does
not change runtime, ChatScreen, NPU route behavior, phase gates, diagnostics,
kill switch behavior, Automatic backend selection, or preference keys.

R5c implements the recommended display-only rename:

```text
NPU Experimental -> NPU Beta
```

Diagnostics and parser compatibility names remain unchanged.

## Current State

The user-facing backend selector now exposes:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

In code, the current labels are slightly more explicit:

```text
Automatic（推奨）
CPU
GPU（Experimental / 非推奨）
NPU Beta
```

Internally, user-facing `NPU Beta` maps to the completed standard route while
retaining the existing diagnostic source marker:

```text
NPU Beta
  -> completed route default
  -> phase=8
  -> UI / TTS / DB / Markdown / pseudo streaming
  -> dev gate not required
npu_standard_route_selection_mode=user_facing_npu_experimental
```

This makes NPU a production candidate for explicit user selection, but not yet
an Automatic default and not a fully unqualified stable backend.

## Audience Review

### General Users

The previous `NPU Experimental` label communicated caution, but it understated
the current completed-route maturity. `NPU Beta` is a better user-facing label
for an explicit-selection production candidate. A plain `NPU` label, however,
may overpromise stability and make support questions harder when
device-specific NPU behavior appears.

### Local AI Users

Local AI users usually understand accelerator choices. They benefit from seeing
NPU as a first-class option, but still need a maturity signal because pseudo
streaming is not native token streaming and rollout remains explicitly selected.

### Developers

Developers need S1-S5, phase 1-8, diagnostics, and kill switch language, but
those concepts should remain outside the normal backend selector. Developer
labels should stay `DEV` / `Legacy` and avoid implying separate hardware
backends.

### First-Time Users

First-time users should not have to understand S1-S5 or Phase 8. They need a
short list with conservative defaults:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

This preserves caution while making NPU feel more mature than GPU.

## Label Options

| Option | Layout | Assessment |
| --- | --- | --- |
| A | Automatic / CPU / GPU Experimental / old Experimental NPU label | Safest wording, matches current implementation, but now too conservative for NPU maturity. |
| B | Automatic / CPU / GPU / NPU | Too aggressive. It hides GPU blocker status and implies NPU is already fully stable and Automatic-ready. |
| C | Automatic / CPU / GPU (Experimental) / NPU | Reasonable direction, but `NPU` alone may overstate support maturity before Automatic enrollment and broader device coverage. |
| D | Automatic / CPU / GPU / NPU (Recommended) | Not appropriate yet. Automatic remains CPU-oriented and NPU is explicit-selection only. |
| E | Automatic / CPU / GPU / NPU Local | Ambiguous. CPU and GPU are also local, so `Local` does not communicate maturity or hardware path clearly. |
| F | Automatic / CPU / GPU Experimental / NPU Beta | Recommended. Distinguishes GPU blocked/experimental from NPU production-candidate maturity without claiming final stable/default status. |

## Evaluation

| Axis | Best Fit | Notes |
| --- | --- | --- |
| User understanding | F | `Beta` is easier to understand than `Experimental` and less absolute than plain `NPU`. |
| Implementation maturity | F | NPU has completed phase 8, rollout monitor, R5a acceptance, and kill switch safe block, but remains explicit-selection only. |
| Future compatibility | F | Can later become `NPU` or `NPU Recommended` without changing preference keys. |
| Support load | F | Keeps a caution marker for device-specific NPU reports. |
| README consistency | F | Allows docs to say GPU remains experimental while NPU is a beta production candidate. |
| Diagnostics consistency | A/F | Diagnostics can continue using `NPU Experimental`; a display-only label can map to the same internal completed-route keys. |
| Legacy S1-S5 cleanup | F | Keeps the user-facing list clean while S1-S5 remain developer-only. |

## Recommendation

```text
RECOMMENDED_BACKEND_LABEL=NPU Beta
RECOMMENDED_SETTINGS_LAYOUT=Automatic / CPU / GPU Experimental / NPU Beta
RATIONALE=NPU is a completed explicit-selection standard route and production candidate, but not yet Automatic default or a fully unqualified stable backend.
```

R5c implementation policy:

- Keep the stored enum / preference value unchanged.
- Rename only the user-facing display label from `NPU Experimental` to
  `NPU Beta`.
- Keep diagnostics and artifact parsers compatible with
  the old Experimental wording, `NPU_S5`, and `npu_s5`.
- Do not rename GPU to plain `GPU` until the GPU promotion blocker is cleared.
- Do not mark NPU as `Recommended` until Automatic enrollment is explicitly
  reviewed.

## Legacy S1-S5 Cleanup Inventory

The current static inventory reports:

```text
NPU_LEGACY_S1_S5_INVENTORY_STATUS=legacy_references_present_with_cleanup_candidates
NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=0
NPU_BETA_USER_FACING_REFERENCE_COUNT=82
USER_FACING_NPU_EXPERIMENTAL_REMAINING=0
USER_FACING_NPU_BETA_COUNT=82
LEGACY_USER_FACING_BACKEND_WORDING_COUNT=121
LEGACY_SAFE_TO_REMOVE_NOW=false
LEGACY_DEPRECATION_STAGE=stage0_current_hidden_developer_compatibility
REMOVAL_BLOCKERS=preference_key_compatibility,developer_override_compatibility,artifact_parser_compatibility,route_execution_compatibility
SAFE_NEXT_ACTION=rename_or_hide_user_facing_legacy_labels_before_cleanup
```

Old Experimental-label references are now split into two groups:

- KEEP when they are diagnostic compatibility values such as
  `user_facing_npu_experimental`.
- CLEANUP_CANDIDATE when they describe the current Settings label instead of
  `NPU Beta`.

### KEEP

- enum values and persisted preference parsing for `NPU_S1` through `NPU_S5`
- developer phase override compatibility
- artifact parser compatibility for `selected_backend=NPU_S5` and
  `route_family=npu_s5`
- final promotion, rollout monitor, and UX acceptance fixtures
- historical docs that explain how S1-S5 led to the completed route

### DEPRECATE

- any normal-user-facing interpretation of S1-S5 as independent backends
- old docs that describe S1-S5 as current backend choices rather than legacy
  phases
- labels that lack `DEV` / `Legacy` context when they appear outside developer
  surfaces

### CLEANUP_CANDIDATE

- Settings text that still uses the old Experimental wording as a current user-facing
  label after R5c
- docs or comments that mention only `NPU_S5` without the completed-route
  summary keys
- stale stage-specific docs that predate Phase 8 and conflict with current
  completed-route behavior
- tests that assert the old display wording instead of preference compatibility,
  once the label-only rename is implemented

## Rollout Guidance

R5c applies the safe UI-only label rename:

```text
NPU Experimental -> NPU Beta
```

That rename should be tested as display-only:

- backend mapping remains completed route phase 8
- `Automatic`, CPU, and GPU behavior stay unchanged
- legacy S1-S5 values remain parseable
- developer phase labels stay `DEV`
- diagnostics still expose completed-route keys

## README / Release Notes

After R4b cleanup, README and release notes should use `NPU Beta` for the
current user-facing label and reserve the old Experimental wording for
compatibility or migration history. The NPU Beta milestone is documented in:

```text
README.md
README_ja.md
docs/release_notes_npu_beta_milestone.md
```

Those docs should describe NPU Beta as an explicit-selection production
candidate with Qualcomm NPU acceleration, pseudo streaming, Markdown, TTS, DB
persistence, rollout validation, dev-gate removal, and kill switch support. They
should also keep `Automatic` enrollment and native token streaming in the
not-yet bucket.
