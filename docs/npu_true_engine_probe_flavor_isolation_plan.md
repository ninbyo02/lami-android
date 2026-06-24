# NPU True Engine Probe Flavor Isolation Plan

Status: Phase 2 button-only `entrypoint_only` passed on a physical device.
Phase 3 button-only `model_assets_only` is now enabled only in
`trueEngineNpuProbeDebug`; isolated create/close-only execution remains
disabled after the startup crash.
`trueEngineNpuProbeDebug` has a Gradle flavor/sourceSet shell, variant-only
native staging, a dedicated `entrypoint_only` button path, and a dedicated
`model_assets_only` button path.
`true_engine_create_close_only` execution is still blocked before native class
load. `standardDebug` remains blocked. Do not add
decode, Session creation, generate, held Engine run once, route changes,
fallback changes, or patch changes in this step.

Current native staging note: a physical-device startup crash was observed after
staging the `20260621_181952_true_engine_create_close_only` LiteRT-LM core
artifacts into `trueEngineNpuProbeDebug`. The startup-recovery build stages the
previous qairt244 stack again and reports
`probe_execution_block_reason=temporarily_disabled_after_startup_crash`.

## Why Isolation Is Required

`true_engine_create_close_only` was added to move true Engine persistent reuse
from holder-gated one-shot decode toward native `ModelAssets` / `EngineSettings`
/ `Engine` lifecycle ownership. After that native stack was staged into
`standardDebug`, the Lami app became unable to start. The recovery commit then
blocked `NPU True Engine Holder Create/Close Probe` and restored the startup
expectation:

- `probe_status=blocked`
- `probe_reason=temporarily_blocked_to_restore_startup`
- `startup_native_call_blocked=true`
- `native_call_deferred_until_button_click=true`
- `probe_execution_available=false`
- `session_create_count=0`
- `decode_count=0`
- `generate_count=0`

The key lesson is that `standardDebug` must not be used as the first execution
surface for experimental true Engine native staging. It is too close to the
normal app startup path, normal chat UI, existing one-shot NPU route, Stability
Test, and holder lifecycle probes.

## Static Risk Summary

The risky shape is not the Kotlin summary formatter by itself. The observed
risk comes from two static facts:

1. `Qairt244ShortMultitokenSmoke` loads native libraries in its companion
   object initializer. Any class load can run:
   - `System.loadLibrary("litertlm_jni")`
   - `System.loadLibrary("lami_npu_persistent_holder_stub")`
2. The current Gradle/native staging shape has used
   `app/src/customBuildExperimentDebug/jniLibs/arm64-v8a` as the source for
   `standardDebug` qairt244 native overlay tasks. Therefore a native stack that
   appears to be custom-build-only can still be copied into `standardDebug` if
   those tasks remain wired to the same directory.

Logcat, dropbox, and tombstone evidence were intentionally not used for this
recovery analysis. The plan is based on static code, sourceSet, task, and native
library load-path review.

## StandardDebug Policy

`standardDebug` must keep the true Engine create/close probe blocked until an
isolated flavor has passed create/close-only execution on device.

This is a specification, not a temporary test weakness. Until
`trueEngineNpuProbeDebug` exists and passes device create/close-only validation,
`standardDebug` must continue to report the blocked state and must not enter the
native true Engine probe from startup, DEV diagnostics rendering, copy actions,
or the Run button.

Expected `standardDebug` summary values:

- `true_engine_create_close_probe_startup_safe=true`
- `native_call_deferred_until_button_click=true`
- `startup_native_call_blocked=true`
- `probe_execution_available=false`
- `probe_execution_block_reason=temporarily_blocked_to_restore_startup`
- `session_create_count=0`
- `decode_count=0`
- `generate_count=0`
- `npu_decode_called=false`
- `qnn_decode_called=false`
- `true_engine_persistent_reuse=false`
- `engine_reuse_observed=unavailable`

`standardDebug` pass conditions:

- Lami starts.
- DEV diagnostics opens.
- `NPU True Engine Holder Create/Close Probe` is visible but blocked.
- `probe_execution_available=false`.
- `startup_native_call_blocked=true`.
- Existing one-shot NPU route behavior is unchanged.
- Existing NPU Stability Test behavior is unchanged.
- Existing persistent holder lifecycle probes are unchanged.

## Why Not Reuse CustomBuildExperimentDebug Directly

`customBuildExperimentDebug` is conditionally usable only if its native
packaging is first separated from `standardDebug`. Today it is risky because
`src/customBuildExperimentDebug/jniLibs` has been used as a source for
`standardDebug` staged qairt244 libraries.

Using it without first untangling that source path can reintroduce the same
failure class: a native stack intended for an experiment can appear in
`standardDebug` and destabilize app startup.

If `customBuildExperimentDebug` is used instead of a new flavor, the first
implementation task must be to prove that its `jniLibs` are no longer consumed
by any `standardDebug` task.

## Recommended Flavor

Create a dedicated debug-only flavor:

- flavor name: `trueEngineNpuProbe`
- variant: `trueEngineNpuProbeDebug`
- application id suffix: `.trueengineprobe`
- sourceSet: `app/src/trueEngineNpuProbeDebug`
- jniLibs root: `app/src/trueEngineNpuProbeDebug/jniLibs`
- native stack scope: true Engine create/close-only probe only

Current shell status:

- The flavor/sourceSet name is reserved.
- `BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR=true` only for this flavor.
- `BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED=true` only for this
  flavor.
- `BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false` for
  this flavor.
- `stageTrueEngineNpuProbeDebugNativeLibs` stages the previous qairt244 stack
  and a marker payload into
  `app/src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a`.
- The staged payload is packaged only by
  `mergeTrueEngineNpuProbeDebugJniLibFolders`.
- The create/close Run button still returns a blocked startup-safe result in this flavor.
- The separate `Run True Engine Entrypoint Probe` button is available only in
  this flavor and only calls the existing `entrypoint_only` mode after button
  press. Physical-device evidence completed with `native_entrypoint_reached=true`
  and no ModelAssets/EngineSettings/EngineFactory/Session/decode/generate
  reach.
- The separate `Run True Engine ModelAssets Probe` button is available only in
  this flavor and only calls the existing `model_assets_only` mode after button
  press.

Purpose:

- Keep startup safe after the cold-start crash.
- Keep `probe_execution_available=false` and
  `probe_execution_block_reason=temporarily_disabled_after_startup_crash`.
- Do not exercise `ModelAssets::Create`, `EngineSettings::CreateDefault`,
  `EngineFactory::CreateDefault`, or Engine close/release while the recovery
  flag keeps native execution disabled.
- Keep startup safe by deferring native load and native call until explicit
  button press.
- Avoid touching normal chat, one-shot NPU route, Stability Test, Long
  Generation, R6 streaming, fallback policy, and `standardDebug` native stack.

## Device Finding Driving The Next Probe Shape

Two `NPU Non-Streaming Repeated Stability Test` physical-device runs
reproduced the run-7 failure even
with pseudo streaming, TTS, DB writes, markdown rendering, and normal chat UI
side effects excluded:

- 6 successful one-shot NPU decodes in both checks.
- failure on run 7 in both checks; `first_failure_run_index=7` is reproducible.
- `first_failure_stage=native_call`.
- `first_failure_reason=adapter_failure:LiteRtLmJniException`.
- native tail reached `before ModelAssets::Create`,
  `before EngineSettings::CreateDefault`, and
  `before EngineFactory::CreateDefault`.
- native tail reported `engine-create-failed: INTERNAL`.
- fallback, timeout, and fresh crash counts were all zero in both checks.
- summaries report `engine_create_failure_detected=true`,
  `suspected_failure_area=engine_create`, `repeated_recreate_suspected=true`,
  `true_engine_reuse_investigation_recommended=true`, and
  `guard_recommendation=investigate_true_engine_reuse_with_staged_probe`.

This strengthens the suspicion that repeated short-interval one-shot recreate
and `EngineFactory::CreateDefault` pressure are involved. It does not justify
re-enabling true Engine create/close immediately because the previous
`true_engine_create_close_only` stack caused a cold-start crash. The next work
must be staged and button-only.

## Staged Probe Reopen Plan

Phase 1: `trueEngineNpuProbeDebug` startup stability check.

- Execution remains disabled.
- Blocked summaries remain the expected result.
- `TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false`.
- `probe_execution_available=false`.
- `startup_native_call_blocked=true`.
- `native_call_deferred_until_button_click=true`.
- `session_create_count=0`.
- `decode_count=0`.
- `generate_count=0`.
- `restart_app_recommended=false`.

Phase 2: button-only `entrypoint_only`.

- Enable only in `trueEngineNpuProbeDebug`, only after explicit button press.
- Confirm native entrypoint reach only.
- Do not call `ModelAssets::Create`.
- Do not call `EngineSettings::CreateDefault`.
- Do not call `EngineFactory::CreateDefault`.
- Startup native calls remain forbidden.

Phase 3: `model_assets_only`.

- Stop after `ModelAssets::Create`.
- Do not call `EngineSettings::CreateDefault`.
- Do not call `EngineFactory::CreateDefault`.

Phase 4: `engine_settings_only`.

- Stop after `EngineSettings::CreateDefault`.
- Do not call `EngineFactory::CreateDefault`.

Phase 5: `before_engine_create`.

- Reach the point immediately before `EngineFactory::CreateDefault`.
- Do not call `EngineFactory::CreateDefault`.

Phase 6: `engine_create_only`.

- Call `EngineFactory::CreateDefault` exactly once.
- Do not create a Session.
- Do not run prefill, decode, generate, or normal route delivery.
- Verify close and zero Session/decode/generate counters.

Phase 7: held Engine run once.

- `engine_create_count=1`.
- Create one Session and decode once.
- Close once.
- This is future work and must not be implemented in the current cleanup.

Do not revive `true_engine_create_close_only` before Phase 2-5 artifacts are
reviewed. Do not add new native allowlist values in the current cleanup.

## SourceSet And Native Packaging Design

The isolated implementation should use separate paths:

- `app/src/trueEngineNpuProbeDebug/AndroidManifest.xml`
- `app/src/trueEngineNpuProbeDebug/java/...`
- `app/src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a/...`
- optional: `app/src/trueEngineNpuProbeDebug/cpp/...`

Gradle requirements:

- Add a new product flavor with `BuildConfig.CURRENT_FLAVOR =
  "trueEngineNpuProbe"`.
- Add an explicit build flag such as
  `BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR = true`.
- Add a sourceSet for `trueEngineNpuProbeDebug`.
- Add isolated native staging/overlay tasks whose source and outputs are not
  used by `standardDebug`.
- The current staging task is `stageTrueEngineNpuProbeDebugNativeLibs`; it
  stages the patched `liblitertlm_jni.so` and related qairt244 native stack plus
  `liblami_true_engine_npu_probe_payload.so`. The marker library has no JNI
  entrypoint and is not loaded by Kotlin. Generated `.so` files are ignored by
  git.
- Do not point `standardDebug` tasks at
  `app/src/trueEngineNpuProbeDebug/jniLibs`.
- Do not point `trueEngineNpuProbeDebug` tasks at
  `app/src/customBuildExperimentDebug/jniLibs` unless that directory is first
  proven not to feed `standardDebug`.

## Runtime Gate Design

Both variants must defer native work until user action:

`standardDebug`:

- `startup_native_call_blocked=true`
- `native_call_deferred_until_button_click=true`
- `probe_execution_available=false`
- Run button returns blocked summary.
- No model path resolution for this probe.
- No `Qairt244ShortMultitokenSmoke` class load from this probe.
- No `System.loadLibrary` from this probe.

`trueEngineNpuProbeDebug`:

- `startup_native_call_blocked=true`
- `native_call_deferred_until_button_click=true`
- `probe_execution_available=false`.
- `isolated_native_payload_staged=true`.
- `isolated_native_execution_enabled=false`.
- `probe_execution_block_reason=temporarily_disabled_after_startup_crash`.
- No native call during app start.
- No native call during DEV diagnostics rendering.
- No native call during initial Summary / Full Dump copy.
- The create/close Run button currently returns a blocked summary and must not
  resolve the model path or enter the isolated native create/close path while
  recovery is active.
- The separate entrypoint Run button may resolve the model and enter only the
  existing `entrypoint_only` native mode after explicit button press.
- Exceptions must be captured into Summary / Full Dump, not thrown through UI
  startup.

## Create/Close-Only Native Rules

Future allowed shape only after Phase 2-5 evidence is reviewed and native
execution is explicitly re-enabled in `trueEngineNpuProbeDebug`:

- `nativeProbeMode=true_engine_create_close_only`
- `runCount=0`
- `ModelAssets::Create`
- `EngineSettings::CreateDefault`
- `EngineFactory::CreateDefault`
- Engine close/release
- ModelAssets / EngineSettings release

Forbidden:

- Session creation
- Conversation creation
- prefill
- decode
- generate
- normal NPU chat route connection
- fallback policy change
- Long Generation Test changes
- R6 streaming investigation
- `true_engine_persistent_reuse=true`
- `engine_reuse_observed=true`

## TrueEngineNpuProbeDebug Current Pass Conditions

Current startup-recovery pass conditions:

- Installs with a separate application id.
- Can coexist with `standardDebug`.
- App starts without native call from this probe.
- DEV diagnostics opens.
- `isolated_native_payload_staged=true`.
- `isolated_native_execution_enabled=false`.
- `probe_execution_available=false`.
- `probe_execution_block_reason=temporarily_disabled_after_startup_crash`.
- APK contains the staged patched qairt244 native stack and
  `lib/arm64-v8a/liblami_true_engine_npu_probe_payload.so`.
- `standardDebug` APK does not contain
  `liblami_true_engine_npu_probe_payload.so`.
- Run button returns a blocked summary and does not enter the native path.
- Summary / Full Dump report zero Session/decode/generate counts.

Future Phase 6 create-only/close pass conditions, after Phase 2-5 evidence and an
explicit execution re-enable:

- Button press is the first point where the create/close native path is allowed.
- `selected_native_probe_mode=true_engine_create_close_only`.
- `argument_validation_passed=true`.
- `run_count_validation_skipped_for_create_close_only=true`.
- `model_assets_create_succeeded=true`.
- `engine_settings_create_succeeded=true`.
- `engine_create_succeeded=true`.
- `engine_close_succeeded=true`.
- `session_create_count=0`.
- `decode_count=0`.
- `generate_count=0`.
- `npu_decode_called=false`.
- `qnn_decode_called=false`.
- `true_engine_persistent_reuse=false`.
- `engine_reuse_observed=unavailable`.

## Rollback Conditions

Rollback immediately if any of these occurs:

- App startup fails.
- Native symbol/link error appears at build or runtime.
- `standardDebug` receives true Engine probe `jniLibs`.
- `standardDebug` no longer reports `probe_execution_available=false`.
- `standardDebug` no longer reports `startup_native_call_blocked=true`.
- Create/close-only mode creates a Session.
- Create/close-only mode runs prefill, decode, or generate.
- Existing one-shot NPU route changes behavior.
- Stability Test changes behavior.
- Persistent holder lifecycle probes change behavior.

Rollback action:

1. Keep `standardDebug` blocked.
2. Remove the isolated flavor's staged native libraries from packaging.
3. Restore the isolated Run button to a blocked summary.
4. Do not change normal NPU chat routing while recovering.

## Files Changed For Packaging-Only Isolation

Current packaging-only implementation files:

- `app/build.gradle.kts`
- `app/src/trueEngineNpuProbeDebug/cpp/lami_true_engine_npu_probe_payload.cpp`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderApi.kt`
- relevant unit tests for standard blocked and isolated staged-but-disabled
  summaries
- docs listed in this plan

Future execution-enabling work may add:

- `app/src/trueEngineNpuProbeDebug/AndroidManifest.xml`
- `app/src/trueEngineNpuProbeDebug/java/...`
- isolated `app/src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a/...` payloads
  beyond the marker library
- `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderCreateCloseDevProbe.kt`

## Files Not To Change For Isolation

Do not change these while adding the isolated create/close-only flavor:

- normal NPU chat route
- `Qairt244DevOnlyNpuRouteAdapter`
- Long Generation Test
- R6 streaming code or docs
- fallback policy
- official session API enablement
- `standardDebug` native stack re-enablement
- held Engine run once
- decode/session/generate implementation

## Suggested Implementation Order

1. Keep `standardDebug` blocked and add regression tests for that expectation.
2. Add `trueEngineNpuProbeDebug` flavor/sourceSet with no native execution.
3. Add isolated jniLibs path and staging task, still with no Run execution.
4. Reopen Phase 2 button-only `entrypoint_only` first. Keep it native
   entrypoint-only: no `ModelAssets`, no `EngineSettings`, no `EngineFactory`.
5. Advance to Phase 3-5 only after reviewing artifacts from the previous
   phase.
6. Design create/close-only v2 from Phase 2-5 artifacts; do not directly
   revive `true_engine_create_close_only`.
7. Run physical-device create/close-only validation only after explicit
   execution re-enable.
8. Only after passing, design held Engine run once.

## Current Phase 3 Implementation Scope

`entrypoint_only` passed on a physical device with `probe_status=completed`,
`native_entrypoint_reached=true`, and all ModelAssets/EngineSettings/
EngineFactory/Session/decode/generate reach counters false or zero.

`trueEngineNpuProbeDebug` now also enables only the Phase 3 button-only
`model_assets_only` probe. This is still narrower than
`TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=true` and does not reopen
`true_engine_create_close_only`.

Implementation scope:

- `app/build.gradle.kts`: `TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED=true`
  only for `trueEngineNpuProbeDebug`; `standardDebug` and the default config keep
  it `false`.
- `TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false` remains unchanged, so
  broad create/close-only execution still reports blocked.
- `NpuTrueEngineHolderApi.kt` exposes `selected_native_probe_mode=model_assets_only`,
  `model_assets_only_probe_execution_available`, startup-blocked keys,
  `model_assets_create_reached`, `model_assets_create_returned`, and
  `model_assets_create_succeeded`, while keeping EngineSettings/EngineFactory/
  Session/decode/generate counters false or zero.
- `NpuTrueEngineHolderCreateCloseDevProbe.kt` adds a separate ModelAssets runner
  that resolves the model and calls native only after the Run button is pressed.
- `Qairt244ShortMultitokenSmoke.kt` allows the isolated flavor only for the
  existing `entrypoint_only` or `model_assets_only` modes and only when the
  matching dedicated flag is true. No native allowlist value was added.
- `ChatScreen.kt` shows `Run True Engine ModelAssets Probe`,
  `Copy True Engine ModelAssets Summary`, and
  `Copy True Engine ModelAssets Full Dump` only for the true Engine probe flavor.

Still forbidden in this phase:

- `EngineSettings::CreateDefault`
- `EngineFactory::CreateDefault`
- Session creation
- prefill, decode, or generate
- held Engine run once
- normal NPU chat-route or fallback changes

The next minimum implementation step after a successful device artifact is
Phase 4, button-only `engine_settings_only`.

## Device Verification Procedure

`standardDebug`:

1. Install `standardDebug`.
2. Launch Lami.
3. Open DEV diagnostics.
4. Copy True Engine Holder Summary.
5. Confirm `probe_execution_available=false` and
   `startup_native_call_blocked=true`.
6. Confirm normal chat UI and existing DEV probes remain usable.

`trueEngineNpuProbeDebug`:

1. `./gradlew installTrueEngineNpuProbeDebug`
2. `adb shell am start -W -n io.github.ninbyo02.lami.trueengineprobe/io.github.ninbyo02.lami.MainActivity`
3. Open DEV diagnostics.
4. Confirm no native work has run before button press.
5. Confirm `TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false`,
   `isolated_native_execution_enabled=false`, `probe_execution_available=false`,
   `startup_native_call_blocked=true`, and
   `native_call_deferred_until_button_click=true`.
6. Press `Run True Engine Entrypoint Probe` and confirm the known-good Phase 2
   keys remain: `selected_native_probe_mode=entrypoint_only`,
   `native_entrypoint_reached=true`, `model_assets_create_reached=false`,
   `engine_settings_create_reached=false`, `engine_create_reached=false`,
   `session_create_count=0`, `decode_count=0`, `generate_count=0`, and
   `restart_app_recommended=false`.
7. Press `Run True Engine ModelAssets Probe`.
8. Copy `Copy True Engine ModelAssets Full Dump`.
9. Expected ModelAssets keys:
   - `probe_status=completed` or a precise ModelAssets failure
   - `selected_native_probe_mode=model_assets_only`
   - `native_entrypoint_reached=true`
   - `model_assets_create_reached=true`
   - `model_assets_create_returned=true`
   - `model_assets_create_succeeded=true`
   - `engine_settings_create_reached=false`
   - `engine_create_reached=false`
   - `session_create_count=0`
   - `decode_count=0`
   - `generate_count=0`
   - `restart_app_recommended=false` unless native fatal
10. Separately inspect the APK and confirm only the isolated APK contains
   `lib/arm64-v8a/liblami_true_engine_npu_probe_payload.so`.
