# QAIRT244 NPU Standard Route Mode Gate Connection Review

Date: 2026-05-31

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, or run `update.sh promote`.

## Current State

Settings now persists the new NPU standard route selector through:

```text
npu_standard_route_mode
```

The value is represented by:

```kotlin
NpuStandardRouteMode
```

and exposed through:

```kotlin
SettingsData.npuStandardRouteMode
SettingsPreferences.npuStandardRouteModeFlow
SettingsPreferences.saveNpuStandardRouteMode(...)
```

ChatScreen still uses the old gate sources:

- S1: `NpuStandardRouteS1GateConfig.enabled`;
- S2: `ENABLE_NPU_STANDARD_ROUTE_S2_DB=false`;
- S3: `ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=false`;
- S4-A: `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false`;
- S5: `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false`.

Therefore the Settings selector is persisted and visible, but it does not yet
control ChatScreen behavior.

## Reading Settings Mode In ChatScreen

Recommended minimal read path:

```kotlin
val npuStandardRouteMode by settingsPreferences.npuStandardRouteModeFlow
    .collectAsState(initial = NpuStandardRouteMode.OFF)
```

Place this near the existing Settings-backed values in `ChatScreen.kt`, close to:

```kotlin
preferredBackendDryRunSetting
markdownStreamingMode
devEnableQairt244Sm8750NpuRoute
developerAccessEnabled
ttsEnabled
```

This keeps all Settings-derived ChatScreen state in one area and avoids direct
DataStore access from the send button path.

Do not use `NpuStandardRoutePreferences.getMode()` directly in the click handler;
that would introduce a suspend read on the hot path and complicate UI state.

## Gate Mapping

The `NpuStandardRouteMode` helpers should become the source of phase gates:

| Mode | S1 | S2 DB | S3 Markdown | S4-A PseudoStreaming | S5 TTS |
| --- | --- | --- | --- | --- | --- |
| `OFF` | false | false | false | false | false |
| `S1_ONLY` | true | false | false | false | false |
| `S2_DB` | true | true | false | false | false |
| `S3_MARKDOWN` | true | true | true | false | false |
| `S4A_PSEUDO_STREAMING` | true | true | true | true | false |
| `FULL` | true | true | true | true | true |

Recommended local variables in ChatScreen:

```kotlin
val npuStandardRouteS1Enabled =
    NpuStandardRouteS1GateConfig.isEnabledForMode(npuStandardRouteMode)
val npuStandardRouteS2DbEnabled = npuStandardRouteMode.isS2Enabled()
val npuStandardRouteS3MarkdownEnabled = npuStandardRouteMode.isS3Enabled()
val npuStandardRouteS4aPseudoStreamingEnabled = npuStandardRouteMode.isS4AEnabled()
val npuStandardRouteS5TtsEnabled = npuStandardRouteMode.isS5Enabled()
```

Then replace only the S1-S5 gate inputs:

- `NpuStandardRouteS1GateConfig.enabled` -> `npuStandardRouteS1Enabled`;
- `ENABLE_NPU_STANDARD_ROUTE_S2_DB` -> `npuStandardRouteS2DbEnabled`;
- `ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN` -> `npuStandardRouteS3MarkdownEnabled`;
- `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING` ->
  `npuStandardRouteS4aPseudoStreamingEnabled`;
- `ENABLE_NPU_STANDARD_ROUTE_S5_TTS` -> `npuStandardRouteS5TtsEnabled`.

Do not alter the S1/S2/S3/S4-A/S5 business logic in the same pass.

## S1 Gate Config Role

`NpuStandardRouteS1GateConfig` currently combines build variant behavior:

```kotlin
BuildConfig.CUSTOM_BUILD_EXPERIMENT || ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1
```

Once Settings mode controls standardDebug, the object should become a build
availability resolver plus mode check.

Recommended shape:

```kotlin
internal object NpuStandardRouteS1GateConfig {
    fun isEnabledForMode(mode: NpuStandardRouteMode): Boolean =
        isAvailableForBuildVariant() && mode.isS1Enabled()

    fun isAvailableForBuildVariant(): Boolean =
        BuildConfig.DEBUG
}
```

For the first implementation, avoid changing provider selection semantics more
than needed:

- `OFF` must not select RealProvider from normal send;
- `S1_ONLY` or higher may select RealProvider;
- standardDebug can run S1 only when Settings mode is not `OFF`;
- customBuildExperimentDebug should remain compatible with explicit Settings
  mode.

If customBuildExperimentDebug must preserve historical always-on behavior for a
short transition, document that exception and keep it limited to S1 only. The
cleaner end state is strict mode-driven behavior for both debug variants.

## standardDebug NPU Conditions

standardDebug should run the new NPU route only when all conditions are met:

- build is DEBUG;
- selected inference target is `InferenceTarget.LOCAL`;
- no image input;
- request prompt is non-blank;
- `npu_standard_route_mode != OFF`;
- RealProvider is available from the debug source set;
- legacy QAIRT route remains hard-gated off.

Mode-specific behavior:

- `S1_ONLY`: NPU result appears in the S1 debug block only;
- `S2_DB`: S1 plus user/assistant DB save;
- `S3_MARKDOWN`: S2 plus Markdown finalized text;
- `S4A_PSEUDO_STREAMING`: S3 plus pseudo streaming UI;
- `FULL`: S4-A plus TTS candidate/speak path subject to `ttsEnabled` and
  existing TTS guards.

`Backend.NPU` persistence must remain disconnected.

## customBuildExperimentDebug Compatibility

customBuildExperimentDebug has been the proving ground for RealProvider and
S1-S5. The Settings selector should not break it unexpectedly.

Recommended compatibility behavior:

- default remains `OFF` in DataStore;
- if the user selects a mode, the selected mode controls phases;
- RealProvider remains available through the debug source set;
- no custom-only legacy fallback is introduced;
- S2-S5 still require their corresponding mode.

If the team wants customBuildExperimentDebug to stay default-on during manual
testing, implement that as an explicit temporary build-variant policy and
document it. Do not hide that behavior inside provider selection.

## Legacy QAIRT Route Isolation

The legacy route uses:

```text
dev_enable_qairt244_sm8750_npu_route
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE
runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)
```

The new mode must not affect those values.

Required guarantees:

- selecting `NPU標準ルート` does not change
  `dev_enable_qairt244_sm8750_npu_route`;
- legacy toggle does not change `npu_standard_route_mode`;
- legacy route hard gate remains false;
- legacy prompt template remains legacy-only;
- S1 gate remains before the legacy branch in ChatScreen.

This avoids double-running or accidentally reviving the old hidden QAIRT path.

## Rollback

Primary rollback:

```text
Settings -> NPU標準ルート -> OFF
```

Expected rollback behavior:

- S1 false;
- S2 false;
- S3 false;
- S4-A false;
- S5 false;
- standardDebug falls back to normal local/Ollama route;
- legacy QAIRT route remains hard-gated off.

Code rollback if needed:

- revert ChatScreen to compile-time S1/S2/S3/S4-A/S5 constants;
- keep `NpuStandardRouteMode` and DataStore definitions because unknown/missing
  values already fall back to `OFF`;
- keep Settings UI visible only in developer mode, or hide it temporarily.

Do not rollback by mapping the legacy toggle to the standard route.

## Test Plan

### Pure Gate Tests

- `OFF` disables S1-S5;
- `S1_ONLY` enables S1 only;
- `S2_DB` enables S1/S2 only;
- `S3_MARKDOWN` enables S1/S2/S3 only;
- `S4A_PSEUDO_STREAMING` enables S1/S2/S3/S4-A only;
- `FULL` enables S1-S5;
- legacy route helper remains independent of mode.

### ChatScreen Gate Tests

- mode `OFF` keeps the existing local route path;
- mode `S1_ONLY` enters S1 and does not evaluate S2-S5;
- mode `S2_DB` enables DB candidate path;
- mode `S3_MARKDOWN` uses Markdown candidate only after S2 candidate exists;
- mode `S4A_PSEUDO_STREAMING` uses pseudo chunks but not real token streaming;
- mode `FULL` allows S5 candidate/speak path but still respects `ttsEnabled`,
  cooldown, stop suppression, and assistant id checks.

### Provider Tests

- mode `OFF` does not select RealProvider for ChatScreen send;
- mode `S1_ONLY` or higher selects RealProvider when available;
- missing RealProvider still returns explicit failure;
- standardDebug and customBuildExperimentDebug both compile.

## Manual Runtime Check Procedure

Do this only after the implementation commit.

1. Build/install `standardDebug`.
2. Open Settings with developer access enabled.
3. Confirm `NPU標準ルート=OFF`.
4. Send Local prompt and confirm normal local/Ollama behavior.
5. Set `NPU標準ルート=S1_ONLY`.
6. Send `こんにちは`.
7. Confirm UI shows `NPU STANDARD ROUTE S1`.
8. Confirm no DB/TTS/Markdown/S4-A output for S1-only.
9. Set mode back to `OFF`.
10. Reinstall or restart if needed and confirm normal local/Ollama behavior.

Later checks should proceed one mode at a time:

```text
S2_DB -> S3_MARKDOWN -> S4A_PSEUDO_STREAMING -> FULL
```

Stop conditions:

- crash or ANR;
- fallback unexpectedly used;
- legacy QAIRT route snackbar appears;
- standard route mode changes legacy key;
- `OFF` still enters S1.

## Recommended Implementation Order

1. Add `npuStandardRouteModeFlow.collectAsState(...)` in ChatScreen.
2. Add/adjust `NpuStandardRouteS1GateConfig.isEnabledForMode(mode)`.
3. Replace S1 gate input only and test `OFF`/`S1_ONLY`.
4. Replace S2-S5 constants with mode-derived booleans.
5. Keep legacy hard gate unchanged.
6. Run compile/tests before any runtime check.
