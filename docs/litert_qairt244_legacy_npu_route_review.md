# QAIRT244 Legacy NPU Route Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
checks, install APKs, or change native code.

## Summary

The `実験的NPU route success` message is emitted by the older hidden QAIRT244
ChatScreen route, not by the newer S1-S5 standard-route contracts.

The route still exists in `ChatScreen.kt` after the S1 gate and before the
normal local/Ollama route. It is developer-toggle gated and remains reachable in
`standardDebug` when developer access and the QAIRT244 toggle are enabled.

## Message Location

The success text is emitted from `ChatScreen.kt` in the hidden QAIRT244 branch:

```kotlin
snackbarHostState.showSnackbar(
    message = if (devResult.success) {
        "実験的NPU route success"
    } else {
        "実験的NPU route failed: ${devResult.reasonCode}"
    },
    duration = SnackbarDuration.Short,
)
```

This is a Snackbar, not a Toast.

Nearby failure text also exists for:

- `実験的NPU route failed: ${devResult.reasonCode}`;
- `実験的NPU route failed: ${exception.javaClass.simpleName}`;
- reflection failure mapping in
  `runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)`.

## Call Chain

The hidden route call chain is:

```text
ChatScreen Local send
-> InferenceTarget.LOCAL branch
-> image input rejection
-> requestPrompt blank check
-> S1 gate check
-> legacy hidden QAIRT244 gate
-> runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)
-> reflection into io.github.ninbyo02.lami.npu.DevOnlyNpuChatScreenBlockedBranch
-> DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(...)
-> Qairt244DevOnlyNpuRouteAdapter
-> Qairt244ShortMultitokenSmoke.runEditablePrompt(...)
```

`LocalStreamingRunner.kt` is not the owner of this legacy route. The branch
returns before the normal local/Ollama path and before `LocalStreamingRunner`
would be used.

The reflection target is debug-source:

```text
app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuChatScreenBlockedBranch.kt
```

The parsed result is converted to `DevQairt244Sm8750NpuChatScreenResult`, then:

- user message is inserted;
- assistant message is inserted;
- dev diagnostics are written;
- Snackbar success/failure is shown.

## Enable Conditions

The legacy branch has two variant-specific gates:

```kotlin
val standardHiddenQairt244NpuEnabled =
    BuildConfig.DEBUG &&
        !BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
        developerAccessEnabled &&
        devEnableQairt244Sm8750NpuRoute

val customQairt244NpuEnabled =
    BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
        devEnableQairt244Sm8750NpuRoute
```

The branch runs if either value is true.

Settings wiring:

- `standardDebug`: Settings shows `実験的NPU（SM8750）` only when developer
  access is enabled.
- `customBuildExperimentDebug`: Settings shows `DEV: SM8750 NPU実験`.
- preference key: `dev_enable_qairt244_sm8750_npu_route`.
- hidden prompt template selection is standardDebug-only and developer-access
  gated.

Persistence guard:

```kotlin
canPersistQairt244Sm8750NpuRoute =
    BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
        (BuildConfig.DEBUG && developerAccessEnabled)
```

Therefore, after promote, standardDebug can still reach this path if the debug
build is used with developer access and the route toggle enabled.

## Relationship To S1-S5

The newer S1-S5 path is checked before the legacy hidden route:

```text
requestPrompt blank check
-> shouldEnterNpuStandardRouteS1(...)
-> if entered, return@IconButton
-> legacy hidden QAIRT244 gate
```

S1 is controlled by:

```kotlin
NpuStandardRouteS1GateConfig.enabled = BuildConfig.CUSTOM_BUILD_EXPERIMENT
```

Provider selection is:

```text
standardDebug -> FixedNpuStandardRouteS1Provider
customBuildExperimentDebug -> RealNpuStandardRouteS1Provider
```

In `customBuildExperimentDebug`, S1 normally wins first because
`BuildConfig.CUSTOM_BUILD_EXPERIMENT=true`. The legacy custom gate is still
present, but it is only reached if the S1 gate does not enter.

In `standardDebug`, S1 is false, so the legacy route can still be the only NPU
ChatScreen branch when the developer toggle is enabled.

Key behavioral differences:

| Area | Legacy hidden route | S1-S5 route |
| --- | --- | --- |
| Owner | `ChatScreen.kt` hidden branch + debug reflection | main S1-S5 contracts/bridges |
| Provider | `DevOnlyNpuChatScreenBlockedBranch` | `NpuStandardRouteS1Provider` |
| standardDebug | reachable with developer access + toggle | S1 disabled |
| customBuildExperimentDebug | fallback branch after S1 gate | primary S1 path |
| Prompt | actual `requestPrompt` with hidden template mode | RealProvider currently uses proven default prompt `こんにちは` |
| DB | inserts user and assistant rows directly | S2-gated save candidate path |
| Markdown | legacy direct/non-streaming diagnostics | S3-gated final text path |
| Streaming | no standard streaming | S4-A pseudo streaming gate |
| TTS | not S5 contract controlled | S5-gated candidate/speak path |
| UI status | Snackbar `実験的NPU route success` | transient `NPU STANDARD ROUTE S1` block |

## Promote Conflict Assessment

Promote does not automatically turn the newer S1-S5 path on for standardDebug:

- `BuildConfig.CUSTOM_BUILD_EXPERIMENT=false`;
- `NpuStandardRouteS1GateConfig.enabled=false`;
- S2/S3/S4-A/S5 constants remain false.

So the immediate promote conflict is not that both new and legacy routes run in
standardDebug by default. The conflict is conceptual and operational:

- standardDebug still contains a separate NPU route with a success snackbar;
- it bypasses the S1-S5 contracts and phase gates;
- it can insert DB rows directly while S2 is supposed to own DB promotion;
- it uses debug reflection and debug-only classes;
- its Settings label overlaps with the new standard-route promotion story;
- diagnostics and failure semantics differ from S1-S5.

In `customBuildExperimentDebug`, S1 is checked first, so the legacy custom gate
is usually shadowed. It can still become visible if S1 is disabled or blocked
while the old QAIRT244 toggle remains enabled.

## Delete Or Keep

Recommendation: do not keep the legacy route as a peer standard route after the
S1-S5 path becomes the promotion target.

Short-term safe option:

- keep it temporarily as a developer-only rollback/diagnostic path;
- rename docs/UI references to make it explicitly legacy;
- keep it behind developer access or custom experiment gates;
- ensure it cannot be confused with the S1-S5 standard route.

Before default-on promotion:

- either remove the legacy `ChatScreen.kt` branch;
- or move it behind a stronger `legacy_qairt244_debug_only` gate;
- or keep only non-ChatScreen diagnostic entry points.

Deletion candidate:

```text
ChatScreen legacy hidden QAIRT244 branch
-> runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)
-> standardDebug Settings toggle for `実験的NPU（SM8750）`
```

Keep candidate:

```text
DevOnlyNpuChatScreenBlockedBranch
Qairt244DevOnlyNpuRouteAdapter
diagnostic receivers/activities
artifact/result parsers
```

Those lower-level dev-only pieces are still useful as diagnostics and as the
current RealProvider backing path. The risky part is the legacy ChatScreen
branch that can present itself as a separate route success.

## Blockers Before Removal

Do not remove the legacy branch until:

- RealProvider no longer depends on behavior that exists only through the
  legacy ChatScreen path;
- S1 uses the intended user prompt, or the default-prompt limitation is
  explicitly accepted;
- S2/S3/S4-A/S5 gate policy is settled;
- S5 trace visibility is fixed;
- at least one failure-path check confirms S1-S5 reports failures clearly
  without falling through to the legacy route;
- a rollback path exists that does not require the legacy ChatScreen branch.
