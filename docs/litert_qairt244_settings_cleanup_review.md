# QAIRT244 Settings Cleanup Review

Date: 2026-05-31

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, or change routes.

## Current Position

The current legacy NPU UI appears under the DEBUG-only experimental settings
area in `Settings.kt`.

There are two visible variants:

- customBuildExperimentDebug:
  - label: `DEV: SM8750 NPU実験`;
  - key: `dev_enable_qairt244_sm8750_npu_route`;
  - supporting text says it is customBuildExperimentDebug-only;
  - followed by the `DEV NPU ChatScreen route boundary` diagnostic card.
- standardDebug with developer access enabled:
  - label: `実験的NPU（SM8750）`;
  - key: `dev_enable_qairt244_sm8750_npu_route`;
  - followed by `実験的NPU prompt template`.

The storage key is:

```text
dev_enable_qairt244_sm8750_npu_route
```

This key is not the new NPU standard route setting. It is tied to the legacy
QAIRT ChatScreen route guarded by:

```text
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false
```

The new standard route setting is separate:

```text
npu_standard_route_mode
```

implemented by `NpuStandardRoutePreferences` and `NpuStandardRouteMode`, but not
yet connected to Settings UI, ChatScreen, or gates.

## Rename To Legacy QAIRT Diagnostics

Recommended rename:

```text
Legacy QAIRT244診断
```

For the toggle:

```text
Legacy QAIRT244 ChatScreen route
```

Supporting text:

```text
旧 hidden QAIRT244 route 用です。NPU標準ルート(S1〜S5)ではありません。
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false の間は実行されません。
```

For the prompt template card:

```text
Legacy QAIRT244 prompt template
```

Supporting text:

```text
legacy ChatScreen route 専用。NPU標準ルートの prompt shaping には使いません。
```

This keeps the old diagnostic controls available while preventing users from
mistaking them for the new `NpuStandardRouteMode` path.

## Developer Access Visibility

Recommended first implementation:

- show legacy diagnostics only when `settingsData.developerAccessEnabled=true`;
- keep it DEBUG-build only;
- do not show it to normal users;
- keep customBuildExperimentDebug visibility developer-scoped as well, unless a
  test workflow explicitly still needs always-visible diagnostics.

Reasoning:

- the route is hard-gated off by default;
- the toggle does not enable the S1-S5 standard route;
- showing it beside the new route selector would be misleading.

If a customBuildExperimentDebug workflow still needs a visible diagnostic toggle,
place it under the same `Legacy QAIRT244診断` section with explicit legacy copy.

## Ordering With NPU Standard Route

Recommended order inside developer-facing Settings:

1. `NPU標準ルート`
   - backed by `npu_standard_route_mode`;
   - default `OFF`;
   - values: `OFF`, `S1_ONLY`, `S2_DB`, `S3_MARKDOWN`,
     `S4A_PSEUDO_STREAMING`, `FULL`;
   - controls S1-S5 standard route phases.
2. `Legacy QAIRT244診断`
   - backed by `dev_enable_qairt244_sm8750_npu_route`;
   - clearly marked as old route;
   - hidden or collapsed by default if the UI supports it.
3. `MediaPipe preferredBackend（実験）`
   - kept separate from both;
   - copy should continue explaining that it affects the normal local engine
     path, not the QAIRT244 standard route.

The standard route should appear before legacy diagnostics because it is the
forward path.

## Hide From General Users

For the first Settings cleanup pass:

- keep both the new NPU standard route selector and legacy diagnostics hidden
  from general users;
- show them only in developer-facing mode;
- leave TTS, avatar, model, and normal backend settings unaffected.

General Settings exposure should wait until:

- standardDebug S1 user-prompt behavior is reviewed;
- S2-S5 default-on policy is decided;
- S5 trace visibility is improved;
- long-text S4-A pseudo chunking has a clearer manual check.

## Complete Removal Comparison

### Keep As Legacy Diagnostics

Pros:

- preserves a known debugging route;
- allows regression comparison against historical QAIRT behavior;
- rollback is only a label/visibility change;
- lower risk while S1-S5 Settings path is still being wired.

Cons:

- still carries confusing old concepts;
- requires clear wording and developer-only visibility;
- can be mistaken for the new route if labels are weak.

### Complete Removal

Pros:

- removes ambiguity entirely;
- reduces Settings complexity;
- avoids future accidental use of a hard-gated route.

Cons:

- removes a diagnostic comparison point;
- harder to inspect legacy prompt-template behavior;
- larger cleanup blast radius because references/tests may need removal.

Recommendation:

1. Rename/move to `Legacy QAIRT244診断` first.
2. Add the new `NPU標準ルート` selector separately.
3. Remove legacy UI only after the standard route mode path is validated in
   standardDebug.

## Rollback

Primary rollback:

- keep `npu_standard_route_mode=OFF`;
- keep `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`;
- hide or leave legacy diagnostics developer-only.

If the Settings cleanup itself causes confusion:

- restore old labels temporarily;
- keep the new route selector hidden;
- do not map `dev_enable_qairt244_sm8750_npu_route` to the standard route.

Do not rollback by merging legacy and standard NPU controls.

## Implementation Candidates

Likely files for a later implementation:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt`
  - rename/move the legacy toggle and prompt-template card;
  - add a separate standard route selector later.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt`
  - later adds `npuStandardRouteMode`.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt`
  - later exposes `NpuStandardRoutePreferences` or mirrors its key in the
    existing settings flow.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRoutePreferences.kt`
  - already owns `npu_standard_route_mode`.
- Settings-related tests once a UI/persistence connection is added.

Do not change in the cleanup-only pass:

- `ChatScreen.kt` route behavior;
- `NpuStandardRouteS1GateConfig`;
- S2-S5 gates;
- native code;
- runtime scripts.

## Recommended Cleanup Sequence

1. Rename existing `実験的NPU（SM8750）` to legacy diagnostics.
2. Move the prompt-template card under the same legacy diagnostics section.
3. Ensure the whole legacy section is developer-only.
4. Add the new `NPU標準ルート` selector separately in a later pass.
5. Keep default standard route mode `OFF`.
6. Keep legacy route hard-gated off.
