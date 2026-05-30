# QAIRT244 Real Provider Runtime Review

Date: 2026-05-30

Scope: runtime confirmation plan only. This document does not implement code,
run runtime probes, install APKs, or change native code.

## Goal

Confirm the `customBuildExperimentDebug` ChatScreen S1 path can select
`RealNpuStandardRouteS1Provider` and route through the dev-only one-turn NPU
entry.

Current code path:

```text
ChatScreen S1 gate
-> NpuStandardRouteS1Bridge
-> NpuStandardRouteS1Invoker
-> NpuStandardRouteS1ProviderSelector.defaultProvider()
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationEntry
-> NpuStandardRouteS1RawResult
-> NpuStandardRouteS1Mapper
-> transient ChatScreen display
```

This remains S1 display-only: DB, TTS, Markdown, streaming, `Backend.NPU`
persistence, and conversation history save are not connected.

## 1. RealProvider Selection Conditions

`RealNpuStandardRouteS1Provider` is selected only when:

- build variant is `customBuildExperimentDebug`;
- `BuildConfig.CUSTOM_BUILD_EXPERIMENT=true`;
- `NpuStandardRouteS1ProviderSelector.defaultProvider()` can load
  `io.github.ninbyo02.lami.ui.screens.home.RealNpuStandardRouteS1Provider`;
- ChatScreen S1 gate is temporarily enabled:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=true
```

`standardDebug` must continue to select `FixedNpuStandardRouteS1Provider`.

## 2. Build And Install Plan

Temporary local flag change:

```bash
perl -pi -e 's/private const val ENABLE_NPU_STANDARD_ROUTE_S1 = false/private const val ENABLE_NPU_STANDARD_ROUTE_S1 = true/' app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
```

Build/install the custom variant:

```bash
./gradlew :app:installCustomBuildExperimentDebug
```

Launch `MainActivity`:

```bash
adb -s <device> shell am start -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity
```

UI action:

- choose the Local path;
- send a text prompt such as `こんにちは`;
- inspect the transient `NPU STANDARD ROUTE S1` block.

## 3. Expected Results

Successful RealProvider run should display these fields:

```text
NPU STANDARD ROUTE S1
status=success
reason=success
run_decode_reached=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
sanitized_output=<non-empty text>
db=false
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
conversation_history_saved=false
```

Minimal success gate:

- `run_decode_reached=true`;
- `QNN_HTP_V79_FastRPC_native_diag`;
- `fallback=false`;
- `timeout=false`;
- `fresh_crash=false`.

Known failure values:

```text
reason=real_provider_not_implemented
```

This should no longer appear after the provider is connected to
`DevOnlyNpuOneTurnConversationEntry`. If it appears, the installed APK is likely
older than the connection commit or provider rollback is still active.

```text
reason=dev_only_entry_unavailable
```

This indicates `RealNpuStandardRouteS1Provider` could not resolve an Android
application context before invoking the dev-only entry. It is expected in JVM
unit tests, but not expected from an installed `customBuildExperimentDebug` APK.

Other failure reasons should be treated as explicit provider/runtime failures,
not as standard-route success.

## 4. Rollback

Primary rollback:

```bash
perl -pi -e 's/private const val ENABLE_NPU_STANDARD_ROUTE_S1 = true/private const val ENABLE_NPU_STANDARD_ROUTE_S1 = false/' app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt
```

Reinstall the custom variant after rollback:

```bash
./gradlew :app:installCustomBuildExperimentDebug
```

Verify source cleanup:

```bash
git status -sb
```

Expected after rollback:

```text
## future...origin/future
```

Rollback should require no DB cleanup, TTS cleanup, Markdown cleanup, streaming
placeholder cleanup, `Backend.NPU` setting cleanup, native changes, or
conversation history migration.

## 5. Confirmation Commands

Preflight compile/test:

```bash
git diff --check
./gradlew :app:compileCustomBuildExperimentDebugKotlin
./gradlew :app:testCustomBuildExperimentDebugUnitTest --tests io.github.ninbyo02.lami.ui.screens.home.RealNpuStandardRouteS1ProviderTest
```

Install and launch:

```bash
./gradlew :app:installCustomBuildExperimentDebug
adb -s <device> shell am start -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity
```

Optional app file inspection after a UI send:

```bash
adb -s <device> shell run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_short_multitoken_smoke_result.txt
```

Do not run this confirmation until the temporary S1 gate change is intentional
and rollback is ready.

## 6. Runtime Success Result

Date: 2026-05-30

The RealProvider runtime confirmation succeeded on the custom build experiment
package:

```text
app_package=io.github.ninbyo02.lami.customnpu
```

Observed UI:

```text
NPU STANDARD ROUTE S1
こんにちは。
```

The app file `qairt244_short_multitoken_smoke_result.txt` was updated by the
run. Recorded result:

```text
result=success
prompt_source=dev_only_conversation
requested_max_output_tokens=32
effective_max_output_tokens=32
max_output_tokens=32
run_decode_reached=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
route_type=dev_only_one_turn_conversation
sanitized_output=こんにちは。
quality_classification=natural_japanese
db=false
tts=false
markdown=false
stream=false
```

Interpretation:

- the `customBuildExperimentDebug` standard UI path reached
  `S1 Gate -> RealProvider -> DevOnlyEntry -> real NPU -> UI display`;
- `standardDebug` remains on S1 Gate disabled / FixedProvider behavior;
- DB, TTS, Markdown, streaming, and `Backend.NPU` persistence remain
  unconnected.
