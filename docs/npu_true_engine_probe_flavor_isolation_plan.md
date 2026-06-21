# NPU True Engine Probe Flavor Isolation Plan

Status: isolation shell in progress. `trueEngineNpuProbeDebug` has a Gradle
flavor/sourceSet shell, but native execution remains disabled. Do not add JNI,
native packaging, native overlay tasks, or patch changes until the next gated
implementation step.

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
- `BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED=false`.
- No isolated `jniLibs` payload is staged yet.
- No isolated native overlay task exists yet.
- The Run button still returns a blocked summary.

Purpose:

- Run `true_engine_create_close_only` in isolation.
- Exercise only `ModelAssets::Create`, `EngineSettings::CreateDefault`,
  `EngineFactory::CreateDefault`, and Engine close/release.
- Keep startup safe by deferring native load and native call until explicit
  button press.
- Avoid touching normal chat, one-shot NPU route, Stability Test, Long
  Generation, R6 streaming, fallback policy, and `standardDebug` native stack.

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
- `probe_execution_available=true`
- No native call during app start.
- No native call during DEV diagnostics rendering.
- No native call during initial Summary / Full Dump copy.
- The Run button may resolve the model path and then enter the isolated native
  create/close-only path.
- Exceptions must be captured into Summary / Full Dump, not thrown through UI
  startup.

## Create/Close-Only Native Rules

Allowed only in `trueEngineNpuProbeDebug`:

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

## TrueEngineNpuProbeDebug Pass Conditions

- Installs with a separate application id.
- Can coexist with `standardDebug`.
- App starts without native call from this probe.
- DEV diagnostics opens.
- `probe_execution_available=true`.
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

## Files To Change In The Implementation Phase

Expected implementation files:

- `app/build.gradle.kts`
- `app/src/trueEngineNpuProbeDebug/AndroidManifest.xml`
- `app/src/trueEngineNpuProbeDebug/java/...`
- `app/src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a/...`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderApi.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/NpuTrueEngineHolderCreateCloseDevProbe.kt`
- relevant unit tests for standard blocked and isolated available summaries
- docs listed in this plan

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
4. Enable `probe_execution_available=true` only in the isolated flavor.
5. Enable button-triggered `true_engine_create_close_only` in the isolated
   flavor only.
6. Run physical-device create/close-only validation.
7. Only after passing, design held Engine run once.

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

1. Install `trueEngineNpuProbeDebug`.
2. Confirm it coexists with `standardDebug`.
3. Launch the isolated app.
4. Open DEV diagnostics.
5. Confirm no native work has run before button press.
6. Press `Run True Engine Holder Create/Close Probe`.
7. Copy Full Dump.
8. Confirm create/close-only pass conditions and zero Session/decode/generate
   counts.
