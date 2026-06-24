# NPU True Engine Persistent Reuse Design

## Purpose

This is a design review and implementation plan for true Engine persistent
reuse in the DEV-only NPU standard-route path. It does not implement JNI,
native Engine ownership, decode, normal chat routing, Long Generation changes,
R6 streaming, fallback changes, or official session API re-enablement.

The current evidence shows that holder lifecycle alone does not improve the
failure shape:

- `NPU Beta Stability Test`: `success_count=6`, `failure_count=1`,
  `first_failure_run_index=7`, `first_failure_reason=adapter_failure:LiteRtLmJniException`,
  backend evidence `QNN_HTP_V79_FastRPC_native_diag`
- `NPU Persistent Holder Ten Turn Probe`: `run_count_completed=7`,
  `success_count=6`, `failure_count=1`, `success_rate=0.86`,
  backend evidence `QNN_HTP_V79_FastRPC_native_diag:7`,
  `fallback_used_count=0`, `timeout_count=0`, `fresh_crash_count=0`,
  `engine_reuse_observed=unavailable`, `true_engine_persistent_reuse=false`

Interpretation: the current holder proves Kotlin -> JNI -> native holder
lifecycle and holder-gated repeated one-shot decode. It does not prove or
perform:

```text
Engine create 1x
decode Nx
Engine close 1x
```

It still performs repeated one-shot native adapter calls after the holder gate.

## Current Holder方式

Current flow:

```text
NpuPersistentHolderTenTurnDevProbe
-> NativeStubNpuPersistentHolderApi.createHolder()
-> nativeCreateStandardRouteAdapterHolder()
   app JNI holder record only
   EngineFactory::CreateDefault not called
   ModelAssets::Create not called

per turn:
-> NativeStubNpuPersistentHolderApi.runOnce()
-> nativeRunStandardRouteAdapterHolderOnce()
   validates holder is open
   does not decode
-> DevOnlyNpuOneTurnConversationEntry.run()
-> Qairt244DevOnlyNpuRouteAdapter.runRoute()
-> Qairt244ShortMultitokenSmoke.runEditablePrompt()
-> nativeRunEditablePrompt()
-> litertlm_jni one-shot native adapter

finally:
-> NativeStubNpuPersistentHolderApi.closeHolder()
-> nativeCloseStandardRouteAdapterHolder()
   closes app JNI holder record only
```

Current holder diagnostics are useful but limited:

- `holder_create_succeeded=true` means an app JNI holder record exists.
- `run_once_supported=true` means the holder gate accepted a run request.
- `run_decode_reached=true` comes from the later one-shot adapter call.
- `engine_factory_create_called=false` in holder diagnostics is expected.
- `true_engine_persistent_reuse=false` is expected.

## Why failure around run 7 can still happen

The Stability Test and Ten-Turn Holder Probe both fail around the same point.
That is consistent with repeated one-shot native adapter pressure rather than
with a Kotlin holder lifecycle problem.

The current Ten-Turn path still repeatedly enters the known one-shot native
sequence. Patch evidence for `nativeRunEditablePrompt` shows this shape inside
a single call:

```text
ModelAssets::Create(model_path)
EngineSettings::CreateDefault(model_assets, NPU)
EngineSettings.SetCacheDir(cache_dir)
EngineSettings.SetLitertDispatchLibDir(native_library_dir)
EngineFactory::CreateDefault(settings)
Engine::CreateSession(SessionConfig::CreateDefault())
Session::RunPrefill(inputs)
DecodeConfig::CreateDefault()
DecodeConfig.SetMaxOutputTokens(max_output_tokens)
Session::RunDecode(decode_config)
session_ptr.reset()
engine_ptr.reset()
```

Therefore a 7th-run failure can still be caused by repeated Engine create /
session create / QNN delegate attach / cleanup pressure even though the app JNI
holder remained open.

This design treats that as the working hypothesis, not as a proven root cause.
The next PoC must add native counters that can distinguish repeated Engine
creation from true held Engine reuse.

## Lifecycle ownership review

### ModelAssets lifecycle

Current one-shot path:

- Created inside `nativeRunEditablePrompt`.
- Not visible to Kotlin.
- Lifetime ends before the JNI call returns.

True reuse candidate:

- Store `ModelAssets` in a native holder during create.
- Create it once per holder.
- Close it when the holder is closed.
- Diagnostics: `model_assets_create_count=1`,
  `model_assets_destroy_count=1`.

Risk:

- The LiteRT-LM ownership contract may require `ModelAssets` to outlive
  `EngineSettings` and `Engine`. Keep it in the holder until Engine close.

### EngineSettings lifecycle

Current one-shot path:

- Created inside `nativeRunEditablePrompt`.
- Configures NPU backend, cache dir, and dispatch lib dir.
- Not visible to Kotlin.

True reuse candidate:

- Create settings once during holder create.
- Set cache and dispatch library directory before Engine creation.
- Keep source strings in diagnostics even if settings is moved into the Engine
  creation path and cannot be retained.

Diagnostics:

- `engine_settings_create_count=1`
- `engine_settings_backend=NPU`
- `engine_settings_cache_dir_set=true`
- `engine_settings_dispatch_lib_dir_set=true`

### Engine lifecycle

Current one-shot path:

- `EngineFactory::CreateDefault` runs inside every `nativeRunEditablePrompt`.
- `engine_ptr.reset()` runs before each JNI call returns.
- Kotlin receives no Engine handle.

True reuse candidate:

- `createEngineHolder(...)` creates one Engine and stores it in a process-local
  native holder table.
- `runWithHeldEngine(...)` must never call `EngineFactory::CreateDefault`.
- `closeEngineHolder(...)` releases the Engine exactly once.

Minimum true reuse condition:

- `engine_create_count=1`
- `engine_close_count=1`
- `engine_reuse_observed=true` only if native diagnostics prove all successful
  decodes used the same native Engine holder generation and no additional
  Engine create happened.

### Session lifecycle

There are two candidate phases:

1. Engine reuse with per-turn Session:
   - Holder owns ModelAssets, EngineSettings evidence, and Engine.
   - Each run creates a fresh `Engine::Session`.
   - Each run performs `RunPrefill` and `RunDecode`.
   - Session is closed after each run.
   - This tests whether repeated Engine create/destroy is the instability
     source without yet preserving conversation state.

2. Engine + Session reuse:
   - Holder owns Engine and a Session.
   - Multiple run calls reuse the same Session.
   - This may preserve conversational state or accumulate context depending on
     LiteRT-LM semantics.
   - This must be a later design because reset/clear-context semantics are not
     currently exposed in Kotlin diagnostics.

Recommended first PoC: Engine reuse with per-turn Session.

Reason: it is the smallest native change that can disprove the repeated Engine
create hypothesis while keeping conversation behavior close to the existing
one-shot route.

### Conversation lifecycle

Current DEV probes are not normal chat conversations. Prompts are fixed and
outputs are copied as diagnostics only.

True reuse design should keep that rule:

- No UI/TTS/DB/Markdown/streaming delivery.
- No normal chat route connection.
- No claim of conversation memory unless the Session reuse phase explicitly
  proves it.

## What Kotlin can and cannot do

Kotlin can:

- Resolve model path.
- Provide native library dir, cache dir, prompt, max output tokens, run id,
  and result/diag output paths.
- Gate DEV-only execution and block concurrent diagnostics.
- Copy summary/full dump.
- Parse native result/diagnostic files.
- Enforce fixed run counts and stop after first failure.

Kotlin cannot currently:

- Hold `ModelAssets`.
- Hold `EngineSettings`.
- Hold `Engine`.
- Hold `Engine::Session`.
- Prove that a decode used the same native Engine.
- Prevent `nativeRunEditablePrompt` from creating a new Engine.
- Observe native pointer identity safely.

Conclusion: Kotlin-only work cannot implement true Engine persistent reuse.
JNI/native changes are required.

## Candidate Kotlin API

Keep this DEV-only and separate from the current app JNI holder API until the
PoC is proven.

```kotlin
internal interface NpuTrueEnginePersistentReuseApi {
    fun createEngineHolder(
        request: NpuTrueEngineCreateRequest,
    ): NpuTrueEngineHolderResult

    fun runWithHeldEngine(
        request: NpuTrueEngineRunRequest,
    ): NpuTrueEngineRunResult

    fun closeEngineHolder(
        request: NpuTrueEngineCloseRequest,
    ): NpuTrueEngineHolderResult

    fun invalidateEngineHolder(
        request: NpuTrueEngineInvalidateRequest,
    ): NpuTrueEngineHolderResult

    fun getEngineHolderDiagnostics(
        holderId: String,
    ): NpuTrueEngineHolderDiagnostics
}
```

Request fields:

- create:
  - `modelPath`
  - `nativeLibraryDir`
  - `cacheDir`
  - `backend=NPU`
  - `maxOutputTokensLimit`
  - `probeMode=engine_reuse_session_per_run`
- run:
  - `holderId`
  - `runId`
  - `prompt`
  - `promptInputLimitMode`
  - `maxOutputTokens`
  - `resultPath`
  - `diagPath`
- close:
  - `holderId`
  - `reason`
- invalidate:
  - `holderId`
  - `reason`
  - `fatal=true|false`

Result fields:

- `status`
- `reason`
- `holderId`
- `nativeSummary`
- `diagnostics`
- run result text fields copied from the current one-shot result format:
  `raw_output`, `sanitized_output`, `quality_classification`,
  `backend_evidence`, timing, tokens, finish/stop/eos where available.

## Candidate JNI/native API

JNI declarations should live beside `Qairt244ShortMultitokenSmoke` only for the
DEV PoC, or in a new debug-only class if the symbol surface gets large.

Candidate names:

```text
nativeCreateTrueEnginePersistentHolder(
    modelPath,
    nativeLibraryDir,
    cacheDir,
    maxTokens
): String

nativeRunTrueEnginePersistentHolderOnce(
    holderId,
    runId,
    prompt,
    promptInputLimitMode,
    maxOutputTokens,
    resultPath,
    diagPath
): String

nativeCloseTrueEnginePersistentHolder(
    holderId,
    reason
): String

nativeInvalidateTrueEnginePersistentHolder(
    holderId,
    reason
): String

nativeGetTrueEnginePersistentHolderDiagnostics(
    holderId
): String
```

Native holder contents for the first PoC:

```cpp
struct TrueEngineHolder {
  std::string holder_id;
  uint64_t generation;
  std::unique_ptr<ModelAssets> model_assets;
  // EngineSettings may be consumed by Engine creation depending on API shape.
  std::unique_ptr<Engine> engine;
  bool open;
  bool fatal_latch;
  std::string fatal_reason;
  Counters counters;
};
```

If exact C++ types differ in the LiteRT-LM source, keep the ownership concept
and adapt the storage to the returned `absl::StatusOr<std::unique_ptr<T>>`
types used by the existing native path.

## Lifecycle state machine

```text
NOT_CREATED
  createEngineHolder
  v
MODEL_ASSETS_CREATED
  EngineSettings::CreateDefault
  v
ENGINE_SETTINGS_CREATED
  EngineFactory::CreateDefault
  v
ENGINE_CREATED
  holder registered
  v
READY
  runWithHeldEngine
  v
SESSION_CREATED
  RunPrefill
  v
PREFILL_DONE
  RunDecode
  v
DECODE
  decode success + close per-run Session
  v
READY
  runWithHeldEngine again
  v
...
READY
  closeEngineHolder
  v
CLOSED
```

Error states:

```text
MODEL_ASSETS_CREATE_FAILED
ENGINE_SETTINGS_CREATE_FAILED
ENGINE_CREATE_FAILED
SESSION_CREATE_FAILED
PREFILL_FAILED
DECODE_FAILED
CLOSE_FAILED
FATAL_LATCH
```

Fatal latch policy:

- ModelAssets/EngineSettings/Engine create failure: set fatal latch.
- Native crash cannot be caught in-process; next app start should report the
  previous crash through existing crash/fresh-crash diagnostics where possible.
- Decode failure: close the per-run Session, mark run failed, and set fatal
  latch until physical evidence shows decode failures are recoverable.
- Close failure: mark fatal latch and recommend app restart.
- After fatal latch, reject create/run in the same process.

## True reuse判定条件

Do not set `engine_reuse_observed=true` unless all of these are true in native
diagnostics:

- `true_engine_persistent_reuse_possible=true`
- `holder_api_available=true`
- `engine_handle_visibility=native_held`
- `engine_create_count=1`
- `engine_close_count=1`
- `model_assets_create_count=1`
- `model_assets_close_count=1` or `model_assets_destroy_count=1`
- `run_count_requested=10`
- `run_count_completed=10`
- `decode_count=10`
- `decode_success_count=10`
- `engine_generation` stable across all turns
- `session_create_count=10` for the first PoC, or `session_create_count=1`
  only for a later Session reuse phase
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `holder_fatal_latch=false`
- no call to the old one-shot `nativeRunEditablePrompt` inside the run phase

Difference from current holder方式:

| Field | Current holder-gated one-shot | True Engine persistent reuse PoC |
| --- | --- | --- |
| Holder create | app JNI record only | creates native ModelAssets + Engine |
| Engine create count | unavailable in holder; one-shot likely per run | exactly 1 |
| Run implementation | calls existing one-shot after holder gate | uses held native Engine |
| Session | inside one-shot call | per run from held Engine in phase 1 |
| Decode count | observed from one-shot result files | counted in holder native diagnostics |
| Reuse proof | unavailable | native Engine generation stable |
| `true_engine_persistent_reuse` | false | true only after native counters prove it |

## Implemented First PoC: True Engine Holder Create/Close Probe

The first executable unit is now exposed as
`NPU True Engine Holder Create/Close Probe` in DEV diagnostics.

Startup safety update after commit `63bfacfa`:

- A physical install after the `true_engine_create_close_only` native stack
  update caused the app to stop before DEV diagnostics could be used.
- No logcat, dropbox, or tombstone evidence was used for this recovery path.
  The rollback is based on static diff review and staged native library
  comparison.
- The DEV UI entry is temporarily blocked to restore startup safety. Showing
  DEV diagnostics, copying the initial summary/full dump, and pressing the
  Run button must not load `litertlm_jni` through this probe path.
- Copy output now reports
  `true_engine_create_close_probe_startup_safe=true`,
  `native_call_deferred_until_button_click=true`,
  `startup_native_call_blocked=true`, and
  `probe_execution_block_reason=temporarily_blocked_to_restore_startup`.
- Re-enable native execution only after the create/close-only mode is rebuilt
  and staged in isolation without destabilizing `standardDebug` startup.

Isolation decision:

- `standardDebug` must keep this probe blocked. The correct expectation is
  `probe_execution_available=false` and `startup_native_call_blocked=true`.
- Native `true_engine_create_close_only` execution should resume only in the
  planned `trueEngineNpuProbeDebug` isolated flavor.
- The isolation plan is documented in
  `docs/npu_true_engine_probe_flavor_isolation_plan.md`.
- `customBuildExperimentDebug` is not sufficient unless its `jniLibs` source is
  first proven not to feed any `standardDebug` staging or overlay task.
- Held Engine run once remains blocked until create/close-only passes in the
  isolated flavor.
- The `trueEngineNpuProbeDebug` flavor/sourceSet now stages the isolated
  qairt244 native stack through `stageTrueEngineNpuProbeDebugNativeLibs`, but
  native execution is disabled after the cold-start crash. It must still block
  startup native calls and must not create a Session, decode, or generate.
- Packaging validation must prove that
  `liblami_true_engine_npu_probe_payload.so` and the true Engine probe staging
  appear only in the `trueEngineNpuProbeDebug` APK and never in `standardDebug`.

Future Phase C create/close-only scope:

- DEV-only.
- Calls the existing `litertlm_jni` persistent custom JNI path with
  `nativeProbeMode=true_engine_create_close_only` and `runCount=0`.
- Exercises `ModelAssets::Create`, `EngineSettings::CreateDefault`,
  `EngineFactory::CreateDefault`, and native Engine close/release in that
  path.
- Does not create a Session.
- Does not run prefill, decode, or generate.
- Does not connect to the normal NPU chat route.

This is intentionally a create/close-only safety probe. It is not yet the
split native API that keeps an Engine holder open across multiple JNI calls.
The selected implementation avoids adding LiteRT-LM C++ symbol dependencies to
`liblami_npu_persistent_holder_stub.so`; that debug app JNI stub remains an app
holder/run-gate library. The actual Engine create/close check runs in the
already-linked `liblitertlm_jni` persistent custom JNI implementation.

The first physical-device attempt used `nativeProbeMode=full_20` with
`runCount=0` and failed before Engine create at the native argument gate:
`first_failure_stage=argument`, `first_failure_reason=invalid_run_count`,
`first_failure_diag_tail=run_count must be 1..100`. That was not an NPU or
Engine create failure. The fix is to split create/close-only behavior into the
dedicated `true_engine_create_close_only` mode. In that mode `runCount=0` is
valid, `run_count_validation_skipped_for_create_close_only=true` is reported,
and the 1..100 run-count validation remains limited to decode/run modes.

The next physical-device attempt reached the native entrypoint with
`nativeProbeMode=true_engine_create_close_only`, but the staged
`liblitertlm_jni.so` still had the older allowlist and failed with
`first_failure_reason=invalid_native_probe_mode`. The isolated
`trueEngineNpuProbeDebug` staging was then pointed at the
`20260621_181952_true_engine_create_close_only` patched core stack, whose
native allowlist includes `true_engine_create_close_only`, but that APK crashed
on cold start before the Run button. The startup-recovery build disables native
execution again and reports
`probe_execution_block_reason=temporarily_disabled_after_startup_crash`.

UI controls:

- `Run True Engine Holder Create/Close Probe`
- `Copy True Engine Holder Summary`
- `Copy True Engine Holder Full Dump`

While startup-crash recovery is active, `trueEngineNpuProbeDebug` native
execution remains disabled and the true Engine create/close button reports a
blocked result. The next stabilization step is the separate
`NPU Non-Streaming Repeated Stability Test`, which repeats the existing
one-shot NPU decode path for 10 fixed prompts without pseudo streaming, TTS, DB,
markdown, fallback, holder/session creation, or true Engine reuse. Its artifact
should be compared with recreate stability, persistent holder, and true Engine
blocked summaries before true Engine create/close is re-enabled.

The first Non-Streaming Repeated Stability physical-device run strengthens the
reuse hypothesis:

- 6 one-shot NPU decodes succeeded.
- run 7 failed at `native_call` with
  `adapter_failure:LiteRtLmJniException`.
- the native tail reached `before ModelAssets::Create`,
  `before EngineSettings::CreateDefault`, and
  `before EngineFactory::CreateDefault`.
- the native tail reported `engine-create-failed: INTERNAL` at
  `runtime/executor/llm_litert_npu_compiled_model_executor.cc:2725` and
  `external/litert/litert/cc/litert_compiled_model.h:1140`.
- fallback, timeout, and fresh-crash counts were all zero.

That means pseudo streaming, UI coroutine updates, TTS, DB writes, markdown
rendering, fallback, and app-side timeout are unlikely to be the primary cause.
The likely pressure point is repeated short-interval one-shot Engine recreate.
This is a reason to investigate true Engine reuse, not a reason to bypass the
startup safety gate.

## Staged True Engine Reopen Order

Phase A keeps `trueEngineNpuProbeDebug` startup-stable:

- `probe_execution_available=false`
- `isolated_native_execution_enabled=false`
- `probe_reason=temporarily_disabled_after_startup_crash`
- no model path resolution or native class load from UI rendering or copy
  actions.

Phase B reopens only existing native modes, button-triggered and one at a time:

- `entrypoint_only`
- `model_assets_only`
- `engine_settings_only`
- `before_engine_create`
- `engine_create_only`

Phase C designs create/close-only v2. Do not revive
`true_engine_create_close_only` first; use Phase B artifacts to decide the
native mode and staging shape. Native load must stay lazy until Run button
press.

Phase D is held Engine run once:

- `engine_create_count=1`
- one Session
- one decode
- close

Phase E is held Engine repeated:

- 2 / 5 / 10 turns
- `engine_create_count=1`
- `decode_success_count=N`
- `true_engine_persistent_reuse=true` only after native counters prove it.

Future Phase C summary keys include:

- `test_name=NPU True Engine Holder Create Close Probe`
- `selected_native_probe_mode=true_engine_create_close_only`
- `argument_validation_passed=true`
- `run_count_validation_skipped_for_create_close_only=true`
- `model_assets_create_called`
- `model_assets_create_succeeded`
- `engine_settings_create_called`
- `engine_settings_create_succeeded`
- `engine_factory_create_called`
- `engine_create_succeeded`
- `engine_close_succeeded`
- `session_create_count=0`
- `decode_count=0`
- `generate_count=0`
- `npu_decode_called=false`
- `qnn_decode_called=false`
- `true_engine_persistent_reuse=false`
- `engine_reuse_observed=unavailable`

Pass conditions:

- `argument_validation_passed=true`
- `model_assets_create_succeeded=true`
- `engine_settings_create_succeeded=true`
- `engine_create_succeeded=true`
- `engine_close_succeeded=true`
- `session_create_count=0`
- `decode_count=0`
- `generate_count=0`
- `engine_fatal_latch=false`

Hold conditions:

- argument failure.
- ModelAssets create failure.
- EngineSettings create failure.
- EngineFactory create failure.
- close failure.
- fatal latch.
- `session_create_count>0`
- `decode_count>0`
- `generate_count>0`

If this passes on device, the next implementation unit is a real split
held-Engine API for `createEngineHolder` and `closeEngineHolder` that keeps the
Engine open across JNI returns, followed by held-Engine run once with a
per-run Session. Do not implement or run decode in the create/close-only probe.

## Diagnostics proposal

Review summary:

```text
test_name=NPU True Engine Persistent Reuse Review
review_status=design_only
true_engine_persistent_reuse_possible=requires_native_jni
engine_handle_visibility=not_visible_to_kotlin
session_handle_visibility=not_visible_to_kotlin
engine_create_site=nativeRunEditablePrompt_one_shot
engine_destroy_site=nativeRunEditablePrompt_before_return
current_holder_mode=app_jni_holder_lifecycle_plus_one_shot_decode
candidate_native_api=createEngineHolder,runWithHeldEngine,closeEngineHolder,invalidateEngineHolder,getEngineHolderDiagnostics
candidate_kotlin_api=NpuTrueEnginePersistentReuseApi
risk_summary=repeated_engine_create_destroy_or_qnn_delegate_attach_pressure_suspected
recommended_next_step=implement_create_close_true_engine_holder_without_decode
```

PoC runtime summary should include:

```text
test_name=NPU True Engine Persistent Reuse Probe
probe_mode=engine_reuse_session_per_run
true_engine_persistent_reuse=false|true
engine_reuse_observed=unavailable|true|false
holder_id
holder_generation
engine_generation
model_assets_create_count
engine_settings_create_count
engine_create_count
engine_close_count
session_create_count
session_close_count
prefill_count
decode_count
decode_success_count
run_count_requested
run_count_completed
backend_evidence_summary
fallback_used_count
timeout_count
fresh_crash_count
holder_fatal_latch
holder_fatal_reason
restart_app_recommended
recommended_next_step
```

Per-turn details should match the current Ten-Turn full dump fields and add:

```text
turn_index
engine_generation
session_generation
session_create_called
prefill_called
decode_called
session_close_called
used_held_engine=true|false
called_one_shot_native_run=false
```

## Minimal PoC plan

### Phase 0: design-only review

This document. No implementation.

### Phase 1: create/close true Engine holder without decode

Goal: prove that a DEV-only native holder can create and close ModelAssets and
Engine exactly once.

Allowed:

- `ModelAssets::Create`
- `EngineSettings::CreateDefault`
- `EngineFactory::CreateDefault`
- Engine close

Forbidden:

- `CreateSession`
- `RunPrefill`
- `RunDecode`
- normal chat route connection

Pass:

- `model_assets_create_count=1`
- `engine_settings_create_count=1`
- `engine_create_count=1`
- `engine_close_count=1`
- `decode_count=0`
- `holder_fatal_latch=false`

### Phase 2: run once with held Engine, per-run Session

Goal: prove one decode can use the held Engine.

Allowed:

- `Engine::CreateSession`
- `Session::RunPrefill`
- `Session::RunDecode`
- per-run Session close

Forbidden:

- second Engine create
- normal chat route connection
- output delivery outside diagnostics

Pass:

- `engine_create_count=1`
- `session_create_count=1`
- `decode_success_count=1`
- `called_one_shot_native_run=false`
- backend evidence includes QNN HTP / FastRPC

### Phase 3: two/five/ten with held Engine, per-run Session

Goal: test whether removing repeated Engine create improves the run 7 failure.

Pass for Ten-Turn:

- `engine_create_count=1`
- `engine_close_count=1`
- `session_create_count=10`
- `decode_success_count=10`
- `run_count_completed=10`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `holder_fatal_latch=false`

Only after this phase may diagnostics set:

```text
engine_reuse_observed=true
true_engine_persistent_reuse=true
```

### Phase 4: optional Engine + Session reuse design

This is not part of the minimum PoC. It needs a separate review of context
reset, conversation memory, and prompt accumulation semantics.

## Risks

- Native symbol access: the app debug holder library currently cannot safely
  link to the needed LiteRT-LM C++ symbols inside `litertlm_jni`. The true
  holder likely needs to be implemented in the same LiteRT-LM JNI build or in a
  native library linked against the same C++ objects.
- Lifetime ordering: ModelAssets, EngineSettings, Engine, and Session ownership
  must follow the LiteRT-LM contracts exactly.
- QNN/HTP resource retention: holding Engine may retain DSP/FastRPC resources
  longer than current one-shot runs. Single-flight and close/restart guidance
  are mandatory.
- Failure recovery unknown: after decode failure, reusing the same Engine may
  be unsafe. The first PoC should set fatal latch on any decode failure.
- Official high-level session API remains blocked for NPU because of
  `logits_output_not_supported_on_npu_backend`; do not re-enable it as part of
  this design.

## Recommendation

Kotlin-only work is insufficient. Add a DEV-only native/JNI true Engine holder
surface and start with create/close only. The smallest implementation unit is:

1. Add native holder state in the LiteRT-LM JNI build or another safely linked
   native target.
2. Implement `nativeCreateTrueEnginePersistentHolder(...)` and
   `nativeCloseTrueEnginePersistentHolder(...)`.
3. Return diagnostics proving ModelAssets/EngineSettings/Engine create and
   Engine close counts.
4. Do not implement decode until create/close is stable on device.

Normal NPU chat route persistentization remains blocked until a Ten-Turn held
Engine probe succeeds with native counters proving `engine_create_count=1` and
`decode_success_count=10`.
