# QAIRT244 Promotion Readiness Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
checks, install APKs, or change native code.

## Summary

The gated standard UI NPU roadmap has reached an end-to-end
`customBuildExperimentDebug` proof:

```text
ChatScreen Local send
-> S1 gate
-> RealProvider
-> DevOnlyNpuOneTurnConversationEntry
-> real NPU decode
-> S2 DB save
-> S3 final text path
-> S4-A pseudo streaming final display
-> S5 TTS speak
```

However, the repository is not yet ready for broad promotion as a default
standard route. The current promoted branch would still keep the new S1 gate
disabled in `standardDebug`, while the older hidden experimental QAIRT244 route
remains available behind developer-only toggles.

## Remaining StandardDebug Experimental Route

`ChatScreen.kt` still contains the older hidden QAIRT244 route inside
`InferenceTarget.LOCAL`.

The branch location is:

```text
InferenceTarget.LOCAL
-> image input rejection
-> requestPrompt blank check
-> S1 standard route gate
-> older hidden QAIRT244 route gate
-> normal local/Ollama route
```

The standardDebug gate is:

```kotlin
val standardHiddenQairt244NpuEnabled =
    BuildConfig.DEBUG &&
        !BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
        developerAccessEnabled &&
        devEnableQairt244Sm8750NpuRoute
```

When this older path succeeds, the snackbar reports:

```text
実験的NPU route success
```

Behavior of this older path:

- uses `runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)`;
- creates or reuses a chat;
- inserts the user message;
- inserts the assistant message;
- writes dev diagnostics;
- reports success/failure through snackbar text;
- is separate from the newer S1/S2/S3/S4-A/S5 contracts and bridges.

This means `standardDebug` still has a developer-toggle hidden experimental
route even though the new S1 gate is disabled there.

## CustomBuildExperimentDebug S1-S5 Path

`customBuildExperimentDebug` differs from `standardDebug` in two important
ways.

First, the S1 gate is enabled by build variant:

```kotlin
NpuStandardRouteS1GateConfig.enabled = BuildConfig.CUSTOM_BUILD_EXPERIMENT
```

Second, provider selection changes by build variant:

```text
standardDebug -> FixedNpuStandardRouteS1Provider
customBuildExperimentDebug -> RealNpuStandardRouteS1Provider
```

`RealNpuStandardRouteS1Provider` exists only in:

```text
app/src/customBuildExperimentDebug/java/...
```

and calls:

```text
DevOnlyNpuOneTurnConversationEntry
```

with the proven request shape:

- default prompt `こんにちは`;
- `raw_dialog_tail_variant_b`;
- `max_output_tokens=32`;
- unsafe dev prompt length bypass enabled;
- real NPU diagnostics mapped back to `NpuStandardRouteS1RawResult`.

The S1 gate currently runs before the older hidden QAIRT244 branch. Therefore,
in `customBuildExperimentDebug`, a normal nonblank Local send is expected to
take the newer S1 path first.

## Current Phase Gates

The current source still keeps downstream phase gates false by default:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S2_DB = false
private const val ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN = false
private const val ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING = false
private const val ENABLE_NPU_STANDARD_ROUTE_S5_TTS = false
```

Observed runtime checks temporarily enabled these gates locally, then rolled
them back to false.

Current default behavior:

- `standardDebug`: S1 gate disabled; newer S1-S5 path is not entered.
- `customBuildExperimentDebug`: S1 gate enabled; S2-S5 remain disabled unless
  temporarily edited on.
- older hidden QAIRT244 route remains gated separately by developer toggles.

## Promote Behavior

`update.sh promote` merges the current branch into `main`:

```text
git switch main
git pull --ff-only origin main
git merge --no-ff <current-branch>
./gradlew :app:compileDebugKotlin
git push origin main
```

If `--install` is passed, it then installs a selected flavor. The default
flavor is:

```text
standard
```

So a default promote/install flow builds and installs the standard flavor, not
`customBuildExperimentDebug`, unless `--flavor customBuildExperiment` is passed.

Promote will move all committed source files to `main`, including:

- S1-S5 main-source contracts, mappers, bridges, and ChatScreen gates;
- `customBuildExperimentDebug` RealProvider source files;
- tests and docs;
- existing hidden QAIRT244 standardDebug route code;
- build flavor wiring.

Promote does not by itself change the runtime gate values or the selected
install flavor.

## Routes Enabled After Promote

Default `./update.sh promote --install` behavior:

- installs the standard flavor;
- `BuildConfig.CUSTOM_BUILD_EXPERIMENT=false`;
- `NpuStandardRouteS1GateConfig.enabled=false`;
- newer S1-S5 standard route path remains off;
- S2/S3/S4-A/S5 constants remain false;
- older hidden QAIRT244 route can still be reached only when developer access
  and `devEnableQairt244Sm8750NpuRoute` are enabled.

`./update.sh promote --install --flavor customBuildExperiment` behavior:

- installs `io.github.ninbyo02.lami.customnpu`;
- `BuildConfig.CUSTOM_BUILD_EXPERIMENT=true`;
- S1 gate is on;
- RealProvider is selected;
- S2/S3/S4-A/S5 constants still remain false unless edited before build;
- standard ChatScreen can display the S1 real NPU result.

Therefore, promotion to `main` does not automatically make the complete S1-S5
runtime path broadly active. The complete S1-S5 path is still a gated/custom
build experiment unless phase constants are intentionally changed.

## Blockers Before Broad Promotion

Blockers to resolve before promoting this as a default standard route:

- decide whether the older `実験的NPU route success` route should be removed,
  renamed, or kept behind a separate developer-only gate;
- decide whether S1 should remain tied to `CUSTOM_BUILD_EXPERIMENT` or move to
  a safer runtime/developer gate;
- decide phase gate policy for S2/S3/S4-A/S5 instead of manually editing
  constants for each runtime check;
- verify that S1 should use the actual user prompt, because the current
  RealProvider request uses the proven default prompt `こんにちは`;
- improve S5 trace visibility, since `logcat` grep for `NPU_S5_TTS` was empty
  during the successful TTS speak runtime check;
- confirm S4-A long-text chunk behavior, because the successful runtime check
  used the short response `こんにちは。`;
- add a clear rollback policy for enabling S2/S3/S4-A/S5 beyond local constant
  edits;
- confirm failure behavior for RealProvider runtime failures after promotion,
  including `dev_only_entry_unavailable` and provider reflection failures;
- keep `Backend.NPU` persistence disabled until there is a separate explicit
  promotion and rollback plan;
- ensure standard local/Ollama behavior remains unchanged when all NPU gates
  are off.

## Promotion Readiness Decision

Ready to promote as code present on `main` behind gates:

- yes, if the goal is to preserve the current default standard behavior while
  carrying the gated S1-S5 implementation and custom experiment path forward.

Not ready to promote as a default-on standard NPU route:

- S2/S3/S4-A/S5 are still false by default;
- S1 is enabled only for `CUSTOM_BUILD_EXPERIMENT`;
- the old hidden experimental route still exists in `standardDebug`;
- trace visibility and long-text pseudo streaming checks remain open;
- permanent gate policy is not settled.
