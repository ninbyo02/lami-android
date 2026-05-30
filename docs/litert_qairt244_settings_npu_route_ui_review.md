# QAIRT244 Settings NPU Route UI Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, or run `update.sh promote`.

## Current Settings Meaning

The current Settings item:

```text
実験的NPU（SM8750）
```

is the old legacy QAIRT ChatScreen route toggle, not the new S1-S5 standard NPU
route toggle.

Evidence:

- UI is shown only for `!BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
  settingsData.developerAccessEnabled`;
- it writes `settingsPreferences.saveDevEnableQairt244Sm8750NpuRoute(...)`;
- preference key is `dev_enable_qairt244_sm8750_npu_route`;
- the prompt template section says
  `standardDebug hidden qairt244 NPU route限定`;
- `ChatScreen.kt` reads `devEnableQairt244Sm8750NpuRoute` only for the legacy
  route gate;
- the legacy route is now additionally blocked by
  `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`.

Therefore, turning on the existing `実験的NPU（SM8750）` setting no longer enters
the ChatScreen NPU path by default. It is not a control for:

```text
NpuStandardRouteS1GateConfig
NpuStandardRouteS1ProviderSelector
S2/S3/S4-A/S5 gates
```

## Legacy UI Handling

The legacy UI should not remain labeled as a generic NPU route after S1-S5
promotion work.

Recommended short-term options:

### Option A: Hide Legacy UI

Hide:

- `実験的NPU（SM8750）`;
- `実験的NPU prompt template`;
- standardDebug hidden template radio buttons.

This is the safest default because the legacy ChatScreen branch is hard-gated
off anyway.

### Option B: Move To Legacy Diagnostics

Show only in developer mode with explicit naming:

```text
Legacy QAIRT244 ChatScreen diagnostic
```

Supporting text should state:

```text
旧 hidden qairt244 route 用。S1-S5 NPU標準ルートではありません。
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false の間は実行されません。
```

This keeps diagnostic affordance without confusing it with the new route.

Recommended first step: Option B if the team still needs legacy diagnostics;
otherwise Option A.

## New NPU Standard Route UI

Add a separate control for the new path:

```text
NPU標準ルート
```

Suggested values:

```text
OFF
S1のみ
S1+DB
S1+DB+Markdown
S1+DB+Markdown+PseudoStreaming
Full(S1〜S5)
```

Default:

```text
OFF
```

Suggested enum:

```kotlin
enum class NpuStandardRouteMode {
    OFF,
    S1_ONLY,
    S1_DB,
    S1_DB_MARKDOWN,
    S1_DB_MARKDOWN_PSEUDO_STREAMING,
    FULL_S1_TO_S5,
}
```

Suggested preference key:

```text
npu_standard_route_mode
```

Gate mapping:

| UI value | S1 | S2 DB | S3 Markdown | S4-A PseudoStreaming | S5 TTS |
| --- | --- | --- | --- | --- | --- |
| OFF | false | false | false | false | false |
| S1のみ | true | false | false | false | false |
| S1+DB | true | true | false | false | false |
| S1+DB+Markdown | true | true | true | false | false |
| S1+DB+Markdown+PseudoStreaming | true | true | true | true | false |
| Full(S1〜S5) | true | true | true | true | true |

Important: this must not reuse `dev_enable_qairt244_sm8750_npu_route`.

## Minimum Conditions For StandardDebug NPU

For standardDebug to run the new NPU route:

- `NpuStandardRouteS1GateConfig` must read the new standard route mode;
- mode must be at least `S1_ONLY`;
- `NpuStandardRouteS1ProviderSelector` must select `RealNpuStandardRouteS1Provider`;
- RealProvider must remain available from the `debug` source set;
- legacy route hard gate must remain false;
- S2-S5 must be enabled only according to the selected mode;
- failure must be visible as NPU standard route failure, not as normal local
  inference failure;
- `Backend.NPU` persistence must remain disconnected.

Minimum standardDebug route:

```text
NPU標準ルート = S1のみ
-> S1 gate true
-> RealProvider
-> S1 UI block
-> no DB
-> no Markdown
-> no PseudoStreaming
-> no TTS
```

## Visibility Policy

Recommended visibility:

- show `NPU標準ルート` only in developer-facing Settings for now;
- do not show it in normal user Settings until default-on policy is settled;
- show a concise status line in developer mode:
  `standardDebug: default OFF / RealProvider available / legacy route disabled`;
- keep `Full(S1〜S5)` visually marked as experimental.

Rationale:

- the route still depends on QAIRT244/SM8750-specific native/runtime conditions;
- S1 RealProvider currently uses the proven default prompt path and still needs
  user-prompt promotion review;
- S5 trace visibility and S4-A long-text chunk checks remain open.

Normal Settings exposure should wait until these blockers are closed.

## MediaPipe preferredBackend Difference

Current Settings has:

```text
MediaPipe preferredBackend（実験）
```

This is separate from the QAIRT244 NPU standard route.

Current preferredBackend text states:

```text
LiteRT-LM EngineConfig に DEFAULT / CPU / GPU を指定します。
NPUはvendor FastRPC namespace制約のため本線では無効化し、GPUを推奨します。
```

Meaning:

- affects the normal local/Ollama LiteRT-LM engine path;
- does not select `RealNpuStandardRouteS1Provider`;
- does not enter S1-S5;
- changing it requires local engine recreation;
- NPU options in that picker are intentionally mapped away from NPU.

New `NPU標準ルート` meaning:

- bypasses the normal local/Ollama route when enabled;
- uses QAIRT244 RealProvider and dev-only NPU entry;
- has its own S1-S5 phase gates;
- does not use MediaPipe preferredBackend.

Settings copy should explicitly say these are separate controls.

## TTS Relationship

Existing TTS setting:

```text
ttsEnabled
```

still controls whether S5 can speak.

S5 should require both:

```text
NPU標準ルート = Full(S1〜S5)
ttsEnabled = true
```

and runtime conditions:

- final assistant text is non-empty after `sanitizeTextForTts`;
- not punctuation-only;
- not streaming active;
- assistant row exists;
- cooldown and stop suppression allow speech.

If `ttsEnabled=false`, `Full(S1〜S5)` should still run through S1-S4-A and skip
speech with a clear `tts_disabled` reason.

The existing `文区切りストリーミングTTS` option should remain scoped to normal
local/Ollama token streaming. It should not apply to S4-A pseudo streaming
chunks.

## Rollback

Primary rollback:

```text
NPU標準ルート = OFF
```

Expected rollback behavior:

- S1 gate false;
- S2/S3/S4-A/S5 false;
- standardDebug falls back to normal local/Ollama;
- legacy route remains hard-gated off;
- no native changes required.

Secondary rollback:

```text
RealProvider selector -> FixedNpuStandardRouteS1Provider
```

Diagnostic emergency only:

```text
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=true
```

This should not be exposed as the normal rollback path.

## Implementation File Candidates

Likely files:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsData.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/SettingsPreferences.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/Settings.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1GateConfig.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1ProviderSelector.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
- tests under `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/`
- tests under `app/src/test/java/io/github/ninbyo02/lami/ui/screens/settings/` if
  Settings preference mapping tests exist or are added.

Do not change for first Settings UI pass:

- native code;
- `Backend.NPU` persistence;
- legacy route implementation;
- MediaPipe preferredBackend semantics;
- TTS engine implementation.

## Recommended Sequence

1. Rename or hide legacy QAIRT Settings UI.
2. Add `NpuStandardRouteMode` preference with default `OFF`.
3. Wire `NpuStandardRouteS1GateConfig` to mode >= `S1_ONLY`.
4. Keep S2-S5 mapped from mode but default false.
5. Show the new selector only in developer mode.
6. Verify standardDebug compile/tests with mode default `OFF`.
7. Later, run explicit standardDebug runtime checks mode by mode.
