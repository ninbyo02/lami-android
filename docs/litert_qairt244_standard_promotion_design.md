# QAIRT244 StandardDebug Promotion Design

Date: 2026-05-30

Scope: design only. This document does not implement code, run runtime checks,
install APKs, change native code, or run `update.sh promote`.

## Current State

The working S1-S5 proof is currently tied to `customBuildExperimentDebug`:

```text
ChatScreen Local send
-> NpuStandardRouteS1GateConfig.enabled
-> NpuStandardRouteS1Bridge
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationEntry
-> real NPU
-> optional S2/S3/S4-A/S5 gates
```

The current S1 gate is:

```kotlin
NpuStandardRouteS1GateConfig.enabled = BuildConfig.CUSTOM_BUILD_EXPERIMENT
```

Current provider selection is:

```text
standardDebug -> FixedNpuStandardRouteS1Provider
customBuildExperimentDebug -> RealNpuStandardRouteS1Provider
```

`RealNpuStandardRouteS1Provider` currently lives in:

```text
app/src/customBuildExperimentDebug/java/...
```

and uses:

```text
DevOnlyNpuOneTurnConversationEntry
raw_dialog_tail_variant_b
max_output_tokens=32
```

`standardDebug` currently has:

- `BuildConfig.CUSTOM_BUILD_EXPERIMENT=false`;
- S1 gate disabled;
- legacy QAIRT ChatScreen route hard-gated off;
- no active real NPU ChatScreen path.

## 1. Minimal StandardDebug Configuration

Minimum viable standardDebug promotion should enable only the proven S1 path
first, then re-enable S2-S5 one phase at a time.

Minimum code-level pieces:

- `NpuStandardRouteS1GateConfig` must support a standardDebug-safe gate that is
  not tied only to `CUSTOM_BUILD_EXPERIMENT`;
- `NpuStandardRouteS1ProviderSelector` must be able to return a real provider
  in standardDebug when explicitly enabled;
- the real provider implementation must be visible to standardDebug;
- S2/S3/S4-A/S5 gates should remain independent and default false unless the
  promotion explicitly enables them;
- legacy QAIRT ChatScreen route must remain hard-gated off.

Minimum default-off runtime behavior:

```text
standardDebug
-> S1 gate false
-> legacy QAIRT false
-> normal local/Ollama route
```

Minimum explicit standardDebug NPU behavior:

```text
standardDebug
-> S1 gate true by explicit promotion gate
-> real provider selected
-> S1 result displayed
-> S2/S3/S4-A/S5 remain separately gated
```

For full S1-S5 parity, standardDebug must also explicitly enable:

```kotlin
ENABLE_NPU_STANDARD_ROUTE_S2_DB
ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING
ENABLE_NPU_STANDARD_ROUTE_S5_TTS
```

but these should not be coupled to the initial S1 provider promotion.

## 2. Moving RealProvider To Standard

Current blocker:

```text
RealNpuStandardRouteS1Provider exists only in customBuildExperimentDebug.
```

Possible placements:

### Option A: Move RealProvider To `debug`

Place real provider in:

```text
app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/
```

Pros:

- available to both `standardDebug` and `customBuildExperimentDebug`;
- can still reference debug-source `DevOnlyNpuOneTurnConversationEntry`;
- main source remains dependent only on `NpuStandardRouteS1Provider`;
- avoids copying provider code between variants.

Cons:

- all debug variants can resolve the provider class;
- provider selector must still gate actual use by build config or explicit
  route flag;
- native/runtime availability must be checked at provider runtime.

### Option B: Duplicate Provider In `standardDebug`

Place a standard-specific provider in:

```text
app/src/standardDebug/java/...
```

Pros:

- narrowest source-set exposure;
- `customBuildExperimentDebug` can keep its current provider untouched.

Cons:

- duplicates logic;
- increases divergence between standard and custom paths;
- later fixes must be applied twice.

### Option C: Move Provider To `main`

Move provider to main.

This is not recommended now because the provider depends on debug/dev-only NPU
entry classes. Moving it to main would either break source-set boundaries or
force reflection around debug internals.

Recommended first step: Option A, move the real provider to `debug`, but keep
selection default-off for standardDebug.

## 3. Default-Off Promotion

Default-off is the safest promote path.

Design:

```kotlin
object NpuStandardRouteS1GateConfig {
    val enabled: Boolean
        get() = BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
            BuildConfig.NPU_STANDARD_ROUTE_S1_ENABLED
}
```

or a developer/runtime setting:

```text
dev_enable_npu_standard_route_s1
```

Default values:

```text
standardDebug: NPU_STANDARD_ROUTE_S1_ENABLED=false
customBuildExperimentDebug: CUSTOM_BUILD_EXPERIMENT=true
```

Provider selector:

```text
if S1 real provider gate is enabled:
    try RealNpuStandardRouteS1Provider
else:
    FixedNpuStandardRouteS1Provider
```

Properties:

- standardDebug default behavior remains unchanged;
- legacy QAIRT route stays off;
- S1 can be enabled intentionally without reviving legacy route;
- S2-S5 remain independently gated;
- rollback is gate off only.

Recommended for promote before any default-on change.

## 4. Default-On Promotion

Default-on means standardDebug Local send uses the NPU route without an
additional developer action.

Required design:

```text
standardDebug -> S1 gate true
standardDebug -> RealProvider selected
S2/S3/S4-A/S5 policy explicitly chosen
legacy route hard gate remains false
normal local/Ollama fallback policy documented
```

Default-on should not be enabled until:

- RealProvider uses the user prompt, not only the proven default prompt
  `こんにちは`;
- standardDebug has the required native/runtime stack or explicit failure
  messaging;
- failure UI distinguishes NPU unavailable from normal local inference failure;
- S5 trace visibility is fixed;
- S4-A long-text chunk behavior is verified;
- S2/S3/S4-A/S5 default policy is chosen;
- stop/retry/failure cases pass on device;
- existing local/Ollama route remains reachable by user choice or rollback.

Default-on is not recommended as the next step.

## 5. Rollback

Default-off rollback:

```text
set S1 standard gate false
keep legacy gate false
standardDebug returns to normal local/Ollama behavior
```

Provider rollback:

```text
NpuStandardRouteS1ProviderSelector -> FixedNpuStandardRouteS1Provider
```

Phase rollback:

```text
ENABLE_NPU_STANDARD_ROUTE_S2_DB=false
ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=false
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
```

Emergency diagnostic rollback:

```text
temporarily set ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=true
```

This should be treated as diagnostic rollback only, not as the standard route.

## 6. Promote Blockers

Blockers before standardDebug default-off S1 promotion:

- decide provider source-set move: `customBuildExperimentDebug` -> `debug` or
  `standardDebug`;
- add a standardDebug-safe S1 gate that is independent of
  `CUSTOM_BUILD_EXPERIMENT`;
- keep legacy route hard gate false;
- make provider failure explicit, not silent fixed success;
- add tests for standardDebug provider selection and S1 gate defaults.

Blockers before full S1-S5 standardDebug promotion:

- S1 RealProvider currently sends the default prompt `こんにちは`; it must accept
  the actual ChatScreen user prompt before real conversation promotion;
- S2/S3/S4-A/S5 are still private constants and require an intentional gate
  policy;
- S5 trace visibility did not show `NPU_S5_TTS` in `logcat` during runtime
  success;
- S4-A has only been smoke-checked with short one-chunk output;
- failure path must not fall through to legacy route or normal local route with
  confusing UX;
- standardDebug native/runtime availability must be confirmed for the real
  provider path;
- `Backend.NPU` persistence remains intentionally disconnected and needs its
  own promotion plan if ever enabled.

## Recommendation

Proceed in two stages:

1. Default-off S1 promotion to standardDebug:
   - move or expose RealProvider to standardDebug through debug source;
   - add explicit standard S1 gate defaulting false;
   - keep S2-S5 false;
   - keep legacy route hard-gated off.

2. Full S1-S5 standardDebug promotion:
   - pass the actual ChatScreen user prompt into RealProvider;
   - define phase gate policy for S2/S3/S4-A/S5;
   - fix S5 trace visibility;
   - verify long-text pseudo streaming and failure handling;
   - only then consider default-on.

This keeps promote safe while preserving the successful
`customBuildExperimentDebug` path as the reference implementation.
