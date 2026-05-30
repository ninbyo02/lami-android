# QAIRT244 Legacy Route Hard Gate Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, or change native code.

## Current Enable Conditions

The legacy QAIRT244 ChatScreen route is still inside `ChatScreen.kt` after the
new S1 gate and before the normal local/Ollama route.

Current route position:

```text
InferenceTarget.LOCAL
-> image input rejection
-> requestPrompt blank check
-> shouldEnterNpuStandardRouteS1(...)
-> legacy QAIRT244 gate
-> normal local/Ollama route
```

The legacy gate currently has two branches:

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

The route enters when either value is true:

```kotlin
if (customQairt244NpuEnabled || standardHiddenQairt244NpuEnabled) { ... }
```

The route then calls:

```text
runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...)
-> DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(...)
```

The preference key is shared with other QAIRT244 dev controls:

```text
dev_enable_qairt244_sm8750_npu_route
```

Persistence is allowed when:

```kotlin
BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
    (BuildConfig.DEBUG && developerAccessEnabled)
```

Current risk: the existing developer access + toggle is enough to expose a
separate ChatScreen NPU route that bypasses the newer S1-S5 contract path.

## Hard Gate Option

Goal: keep the legacy route available only as an explicitly named diagnostic
fallback while making it impossible to confuse with the S1-S5 promotion route.

Recommended hard gate shape:

```text
legacy gate =
    BuildConfig.DEBUG
    && developerAccessEnabled
    && devEnableQairt244Sm8750NpuRoute
    && ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE
    && !NpuStandardRouteS1GateConfig.enabled
```

For `customBuildExperimentDebug`, prefer blocking the legacy branch by default:

```text
CUSTOM_BUILD_EXPERIMENT=true
-> S1/S5 promotion route owns ChatScreen NPU
-> legacy route disabled unless a new explicit legacy override is true
```

Implementation candidates for the explicit override:

- private constant in `ChatScreen.kt`:
  `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`;
- or a `BuildConfig` field:
  `LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`;
- or a separate hidden preference key:
  `dev_enable_legacy_qairt244_chatscreen_route`.

The safest first implementation is a private constant defaulting to false,
because rollback is a single-line change and it does not add another persisted
preference.

Recommended helper:

```kotlin
internal fun shouldEnterLegacyQairt244ChatScreenRoute(
    hardGateEnabled: Boolean,
    customBuildExperiment: Boolean,
    developerAccessEnabled: Boolean,
    legacyToggleEnabled: Boolean,
    s1Enabled: Boolean,
): Boolean =
    hardGateEnabled &&
        !customBuildExperiment &&
        developerAccessEnabled &&
        legacyToggleEnabled &&
        !s1Enabled
```

This would make standardDebug legacy access possible only with an explicit
source-level hard gate, while preventing customBuildExperimentDebug from
falling back to the legacy ChatScreen route behind S1.

Expected result:

- default standardDebug: legacy route unavailable;
- standardDebug with developer toggle only: legacy route unavailable;
- customBuildExperimentDebug: S1-S5 route remains primary;
- legacy route can still be restored intentionally for diagnostics by flipping
  the hard gate.

## Complete Removal Option

Complete removal would delete the ChatScreen-facing legacy branch:

```text
standardHiddenQairt244NpuEnabled
customQairt244NpuEnabled
runDevQairt244Sm8750NpuChatScreenRouteViaReflection(...) call site
Snackbar `実験的NPU route success`
legacy direct DB insert path
```

Likely follow-up cleanup:

- remove or relabel the standardDebug Settings toggle `実験的NPU（SM8750）`;
- remove standardDebug-only hidden prompt template UI if no other caller needs
  it;
- keep lower-level dev-only classes if RealProvider or diagnostic receivers
  still use them.

Do not remove immediately if:

- RealProvider still depends on lower-level dev-only QAIRT244 classes;
- failure-path coverage for S1-S5 is incomplete;
- S1 still uses the proven default prompt rather than the user prompt;
- there is no alternative rollback route for device-side QAIRT244 diagnosis.

Complete removal is cleaner for promotion, but it has a larger rollback cost
than hard gating.

## Rollback

Hard gate rollback:

- set `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=true`;
- rebuild the relevant debug variant;
- no DB migration required;
- no native change required.

If a hidden preference is introduced, rollback requires both:

- source gate true;
- preference enabled on device.

Complete removal rollback:

- revert the removal commit;
- rebuild/reinstall;
- larger merge-conflict risk if S1-S5 code evolves meanwhile.

Therefore, hard gate is the safer first step.

## Promote Impact

With hard gate defaulting false:

- promote can carry the legacy code to `main` without exposing it by default;
- standardDebug no longer has a developer-toggle-only route to
  `実験的NPU route success`;
- customBuildExperimentDebug remains owned by S1-S5;
- `update.sh promote --install` default standard flavor remains non-NPU unless
  S1 is separately promoted.

With complete removal:

- promote carries only the S1-S5 route story for ChatScreen NPU;
- route ambiguity is removed;
- rollback depends on reverting code.

Without either hard gate or removal:

- promote leaves a second ChatScreen NPU route in standardDebug;
- support/debug output can confuse legacy success with S1-S5 success;
- DB and diagnostics semantics remain split across two route families.

## Recommendation

Use hard gate first, then delete later.

Recommended sequence:

1. Add `ENABLE_LEGACY_QAIRT244_CHATSCREEN_ROUTE=false`.
2. Add a small pure helper/test for legacy gate selection.
3. Keep lower-level diagnostic classes untouched.
4. Confirm standardDebug developer toggle no longer enters the legacy route by
   code review or unit test.
5. After S1 uses the real user prompt and S5 trace visibility is fixed, remove
   the legacy ChatScreen branch entirely.

This reduces promote risk while preserving a deliberate rollback path during
the final S1-S5 stabilization window.
