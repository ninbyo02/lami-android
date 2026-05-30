# QAIRT244 NPU Standard Route Mode DataStore Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, change routes, or run `update.sh
promote`.

## Purpose

Settings needs a separate persisted selector for the new NPU standard route.
The existing `実験的NPU（SM8750）` setting is a legacy QAIRT ChatScreen route
toggle and must not be reused for S1-S5.

The new setting should express which NPU standard route phase is allowed:

- S1 display-only;
- S2 DB save;
- S3 Markdown finalization;
- S4-A pseudo streaming;
- S5 TTS.

Default must remain OFF.

## Proposed Enum

Use a string-backed enum in the Settings layer:

```kotlin
enum class NpuStandardRouteMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    OFF(...),
    S1_ONLY(...),
    S2_DB(...),
    S3_MARKDOWN(...),
    S4A_PSEUDO_STREAMING(...),
    FULL(...);

    companion object {
        fun fromStorage(raw: String?): NpuStandardRouteMode =
            entries.firstOrNull { it.storageValue == raw } ?: OFF
    }
}
```

Recommended storage values:

| Enum | storageValue | UI label |
| --- | --- | --- |
| `OFF` | `off` | `OFF` |
| `S1_ONLY` | `s1_only` | `S1のみ` |
| `S2_DB` | `s2_db` | `S1+DB` |
| `S3_MARKDOWN` | `s3_markdown` | `S1+DB+Markdown` |
| `S4A_PSEUDO_STREAMING` | `s4a_pseudo_streaming` | `S1+DB+Markdown+PseudoStreaming` |
| `FULL` | `full` | `Full(S1〜S5)` |

Reasoning:

- storage strings are stable even if enum names are later renamed;
- unknown or missing values safely resolve to `OFF`;
- the enum lives with Settings data rather than ChatScreen UI code.

## Gate Mapping

The mode should map monotonically. Later phases imply earlier phases.

| Mode | S1 | S2 DB | S3 Markdown | S4-A PseudoStreaming | S5 TTS |
| --- | --- | --- | --- | --- | --- |
| `OFF` | false | false | false | false | false |
| `S1_ONLY` | true | false | false | false | false |
| `S2_DB` | true | true | false | false | false |
| `S3_MARKDOWN` | true | true | true | false | false |
| `S4A_PSEUDO_STREAMING` | true | true | true | true | false |
| `FULL` | true | true | true | true | true |

Recommended helper shape:

```kotlin
val npuStandardRouteS1Enabled: Boolean
val npuStandardRouteS2DbEnabled: Boolean
val npuStandardRouteS3MarkdownEnabled: Boolean
val npuStandardRouteS4aPseudoStreamingEnabled: Boolean
val npuStandardRouteS5TtsEnabled: Boolean
```

These can either be computed on the enum or exposed through a small resolver.
The important constraint is that ChatScreen should not compare raw strings.

## DataStore Key

Use a new DataStore key:

```text
npu_standard_route_mode
```

Recommended type:

```kotlin
private val npuStandardRouteModeKey = stringPreferencesKey("npu_standard_route_mode")
```

Default:

```text
OFF
```

Read path:

```kotlin
npuStandardRouteMode = NpuStandardRouteMode.fromStorage(
    preferences[npuStandardRouteModeKey],
)
```

Write path:

```kotlin
suspend fun saveNpuStandardRouteMode(mode: NpuStandardRouteMode)
```

The write should persist `mode.storageValue`, not `mode.name`.

## SettingsData Integration

Add one field to `SettingsData`:

```kotlin
val npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF
```

Do not add separate persisted booleans for S1/S2/S3/S4-A/S5. The enum is the
single source of truth. Derived booleans should be computed at the gate layer.

This avoids invalid combinations such as:

```text
S3=true while S2=false
S5=true while S4-A=false
```

## standardDebug And customBuildExperimentDebug

### standardDebug

For standardDebug:

- default stored mode is `OFF`;
- missing DataStore value resolves to `OFF`;
- `S1_ONLY` or higher enables the S1 gate;
- `NpuStandardRouteS1ProviderSelector` may choose `RealNpuStandardRouteS1Provider`
  only when S1 is enabled;
- S2-S5 remain off unless the selected mode includes them;
- legacy QAIRT route remains hard-gated off.

Minimum standardDebug NPU enablement:

```text
npu_standard_route_mode = s1_only
-> S1 true
-> RealProvider selected
-> no DB
-> no Markdown
-> no PseudoStreaming
-> no TTS
```

### customBuildExperimentDebug

For customBuildExperimentDebug:

- current behavior can remain auto-enabled for S1 during the transition;
- the Settings value should still be readable and visible;
- if a stored explicit mode is introduced, `OFF` should be honored only after
  the migration decision is made deliberately.

Recommended transition:

1. Keep current customBuildExperimentDebug S1 behavior unchanged while adding
   the DataStore enum.
2. In the next pass, decide whether customBuildExperimentDebug should also obey
   the mode strictly.
3. Do not let customBuildExperimentDebug force S2-S5 on unless mode requires it.

This avoids accidentally disabling the already-proven custom experiment path
while the Settings UI is being added.

## Legacy Setting Separation

Do not reuse:

```text
dev_enable_qairt244_sm8750_npu_route
```

That key controls the legacy QAIRT ChatScreen route only:

- Settings label: `実験的NPU（SM8750）`;
- prompt template setting: `hidden_qairt244_prompt_template_mode`;
- ChatScreen path: `runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)`;
- current hard gate: `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`.

The new NPU standard route must use:

```text
npu_standard_route_mode
```

Legacy UI should be renamed to a diagnostics area or hidden. It should not sit
next to the new selector with generic NPU wording.

## ChatScreen Gate Usage

Current S2-S5 gates are compile-time constants in `ChatScreen.kt`.

The DataStore-backed design should replace those constants with a resolved mode
or derived booleans flowing from Settings:

- S1: `mode >= S1_ONLY`;
- S2: `mode >= S2_DB`;
- S3: `mode >= S3_MARKDOWN`;
- S4-A: `mode >= S4A_PSEUDO_STREAMING`;
- S5: `mode == FULL`.

`NpuStandardRouteS1GateConfig` should no longer be the only source of S1 truth
once Settings mode is wired. It can become:

- a resolver for build-variant baseline plus user mode; or
- a small object that exposes whether standard route can be selected for this
  variant, while Settings mode decides the phase.

The gate decision should be explicit enough for unit tests to cover:

```text
Build variant availability
Settings mode
RealProvider selection
S2-S5 phase flags
```

## Rollback

Primary rollback:

```text
npu_standard_route_mode = off
```

Expected behavior:

- S1 false;
- RealProvider not selected for normal send flow;
- S2 false;
- S3 false;
- S4-A false;
- S5 false;
- standardDebug returns to normal local/Ollama path;
- legacy QAIRT route remains hard-gated off.

Secondary rollback:

- remove or hide the Settings UI row while keeping the DataStore parser;
- force `NpuStandardRouteS1GateConfig` to return false for standardDebug;
- keep `NpuStandardRouteS1ProviderSelector` fallback to fixed/failure provider.

Do not rollback by reusing the legacy `実験的NPU（SM8750）` toggle.

## Test Items

### Enum Tests

- missing storage value resolves to `OFF`;
- unknown storage value resolves to `OFF`;
- each known storage value resolves to the expected enum;
- each enum has the expected gate mapping;
- `FULL` enables S1-S5;
- `OFF` disables S1-S5.

### DataStore Tests

- default `SettingsData.npuStandardRouteMode == OFF`;
- `saveNpuStandardRouteMode(S1_ONLY)` persists and restores `S1_ONLY`;
- `saveNpuStandardRouteMode(FULL)` persists and restores `FULL`;
- invalid stored string restores as `OFF`;
- legacy key `dev_enable_qairt244_sm8750_npu_route` does not affect
  `npuStandardRouteMode`;
- `npu_standard_route_mode` does not affect legacy route toggle.

### Gate Tests

- standardDebug + `OFF` does not enter S1;
- standardDebug + `S1_ONLY` enters S1 and keeps S2-S5 false;
- standardDebug + `S2_DB` enables S1/S2 only;
- standardDebug + `S3_MARKDOWN` enables S1/S2/S3 only;
- standardDebug + `S4A_PSEUDO_STREAMING` enables S1/S2/S3/S4-A only;
- standardDebug + `FULL` enables S1-S5;
- legacy hard gate remains independent.

### Provider Selector Tests

- `OFF` uses fixed or non-real provider behavior;
- `S1_ONLY` or higher can select `RealNpuStandardRouteS1Provider` when the
  variant has it;
- RealProvider unavailable still maps to explicit failure;
- customBuildExperimentDebug transition behavior is documented and covered.

### Settings UI Tests

- selector default is `OFF`;
- selecting each option calls `saveNpuStandardRouteMode(...)`;
- legacy NPU UI is either hidden or labeled as legacy diagnostics;
- MediaPipe preferredBackend copy remains separate from NPU standard route;
- TTS setting remains separate and only affects S5.

## Recommended Implementation Order

1. Add `NpuStandardRouteMode` enum and DataStore read/write with default `OFF`.
2. Add unit tests for enum parsing and gate mapping.
3. Add `SettingsData.npuStandardRouteMode`.
4. Add Settings UI selector in developer-facing Settings only.
5. Add gate resolver tests before wiring ChatScreen.
6. Wire S1 first, keeping S2-S5 false unless mode requires them.
7. Wire S2-S5 mode checks incrementally.
8. Keep legacy QAIRT route hard-gated and separately labeled.
