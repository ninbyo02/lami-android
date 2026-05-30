# QAIRT244 StandardDebug Local Failure Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
checks, install APKs, or change native code.

## Summary

After hard-gating the legacy QAIRT244 ChatScreen route, `standardDebug` no
longer has an active ChatScreen NPU path by default.

When `standardDebug` Local send shows:

```text
ローカル推論の応答取得に失敗しました
```

the app is no longer reporting the legacy QAIRT route. It has fallen through to
the normal local/Ollama inference branch, and that normal local branch failed to
produce a usable response.

The message is shown through `snackbarHostState.showSnackbar(...)`, not a Toast.

## Failure Message Location

There are two relevant failure locations in `ChatScreen.kt`.

Normal local comparison failure path:

```kotlin
snackbarHostState.showSnackbar(
    message = when (resolvedState) {
        null -> "ローカル推論エンジンの確認がタイムアウトしました"
        LocalInferenceEngineState.READY -> "ローカル推論の応答取得に失敗しました"
        LocalInferenceEngineState.UNINITIALIZED -> "ローカル基本モデルが未設定です"
        LocalInferenceEngineState.ERROR -> "ローカル推論の応答取得に失敗しました"
        LocalInferenceEngineState.PREPARING -> "ローカル推論エンジンを準備中です"
    },
    duration = SnackbarDuration.Short,
)
```

Normal local exception path:

```kotlin
snackbarHostState.showSnackbar(
    message = "ローカル推論の応答取得に失敗しました",
    duration = SnackbarDuration.Short,
)
```

These paths are after:

```text
debugLocalUiTrace(label = "LOCAL_UI_SEND_TAPPED", ...)
-> localInferenceJob = coroutineScope.launch { ... }
-> localInferenceEngineHolder.acquireOrCreate/acquireWithDiagnostic(...)
-> runWithHeldEngine(...)
```

They are not emitted by `runDevQairt244Sm8750NpuChatScreenRouteViaReflection`.

## Current StandardDebug Route

The relevant `InferenceTarget.LOCAL` order is:

```text
image input rejection
-> requestPrompt blank check
-> S1 standard route gate
-> legacy QAIRT ChatScreen route gate
-> normal local/Ollama route
```

For `standardDebug`:

```text
BuildConfig.CUSTOM_BUILD_EXPERIMENT=false
NpuStandardRouteS1GateConfig.enabled=false
ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false
```

Therefore:

- S1 does not enter;
- RealProvider is not selected;
- legacy QAIRT route does not enter;
- developer QAIRT toggle alone is not enough;
- execution reaches the normal local/Ollama route.

Provider selector behavior:

```text
standardDebug -> FixedNpuStandardRouteS1Provider
customBuildExperimentDebug -> RealNpuStandardRouteS1Provider
```

In `standardDebug`, this selector is effectively unused unless S1 is separately
enabled. It does not call real NPU.

## Legacy Hard Gate Impact

The hard gate added:

```kotlin
private const val ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE = false
```

and routes legacy QAIRT through:

```kotlin
shouldEnterLegacyQairt244ChatScreenRoute(
    hardGateEnabled = ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE,
    debugBuild = BuildConfig.DEBUG,
    customBuildExperiment = BuildConfig.CUSTOM_BUILD_EXPERIMENT,
    developerAccessEnabled = developerAccessEnabled,
    legacyToggleEnabled = devEnableQairt244Sm8750NpuRoute,
)
```

With `hardGateEnabled=false`, the legacy branch is blocked even if:

- developer access is enabled;
- `devEnableQairt244Sm8750NpuRoute=true`;
- the build is `standardDebug`.

This is intentional and matches the hard-gate goal: developer toggle alone
must not enter the legacy route.

## Did StandardDebug Lose NPU?

Yes, for ChatScreen Local send.

Before hard gate:

```text
standardDebug + developer access + QAIRT toggle
-> legacy QAIRT ChatScreen route
-> possible `実験的NPU route success`
```

After hard gate:

```text
standardDebug + developer access + QAIRT toggle
-> legacy QAIRT blocked
-> normal local/Ollama route
-> may show `ローカル推論の応答取得に失敗しました`
```

This does not affect `customBuildExperimentDebug` S1-S5:

```text
customBuildExperimentDebug
-> NpuStandardRouteS1GateConfig.enabled=true
-> S1 gate enters before legacy route
-> RealProvider path remains the promoted experiment path
```

The hard gate only removes the old standardDebug ChatScreen NPU escape hatch.

## Relationship To RealProvider

`RealNpuStandardRouteS1Provider` exists only under:

```text
app/src/customBuildExperimentDebug/java/...
```

`standardDebug` cannot instantiate that implementation as its normal S1
provider because:

```kotlin
NpuStandardRouteS1ProviderSelector.defaultProvider()
```

returns `FixedNpuStandardRouteS1Provider` unless:

```text
BuildConfig.CUSTOM_BUILD_EXPERIMENT=true
```

So the observed standardDebug failure is not a RealProvider failure. It is the
expected result of standardDebug taking the normal local route after both NPU
ChatScreen gates are off.

## Promote Blocker Assessment

This is a blocker only if the expected promote behavior is:

```text
standardDebug Local send should still reach NPU
```

Under the current promotion plan, it is not a blocker for keeping standardDebug
non-NPU by default. It is an intentional consequence of hard-gating legacy
QAIRT and keeping S1 enabled only for `customBuildExperimentDebug`.

It becomes a blocker before default-on NPU promotion because standardDebug has
no active real NPU ChatScreen path after the hard gate. A default-on promotion
would need one of:

- S1 gate policy changed so standardDebug can intentionally enter S1;
- a standardDebug-safe RealProvider or main-source provider path;
- a build/runtime flag that enables S1 without reviving the legacy route;
- clear UX separating normal local failure from gated NPU-unavailable state.

Do not solve this by re-enabling the legacy route as the default path. That
would reintroduce the split route semantics that the hard gate was meant to
remove.

## Next Safe Actions

Recommended next steps before promote/default-on work:

1. Document that standardDebug Local now means normal local/Ollama unless S1 is
   explicitly promoted.
2. Decide whether standardDebug should remain non-NPU until a proper S1 gate
   policy exists.
3. If standardDebug needs NPU again, promote S1 intentionally rather than
   flipping `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE`.
4. Improve the UI/debug message so normal Local failure is not mistaken for NPU
   failure.
