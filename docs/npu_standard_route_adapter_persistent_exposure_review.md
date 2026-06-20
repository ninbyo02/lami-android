# NPU Standard Route Adapter Persistent Exposure Review

## Purpose

This review checks whether the NPU standard-route adapter/native decode path
that already succeeds with `QNN_HTP_V79_FastRPC_native_diag` can be reused by
`NPU Persistent Engine Multi-turn Probe`.

This is a docs-first investigation. It does not change normal NPU chat,
fallback behavior, official LiteRT-LM session usage, JNI/native code, Long
Generation Test, or Stability Test execution.

## Current Result

Summary:

- `standard_route_adapter_decode_success_known=true`
- `standard_route_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `persistent_adapter_available=false`
- `persistent_adapter_reason=needs_native_adapter_work`
- `session_api_supported_for_npu=false`
- `session_api_block_reason=logits_output_not_supported_on_npu_backend`
- `engine_lifecycle_visibility=partial`
- `engine_reuse_observed=unavailable`
- `recommended_next_step=design_dev_only_standard_route_persistent_adapter_jni`

The successful route exists, but it is exposed as a one-shot adapter call. A
persistent standard-route adapter API is not currently exposed to Kotlin.

## Successful Standard Route Entry

The normal NPU standard route enters the NPU adapter through these Kotlin
layers:

1. `ChatScreen.kt`
   - Creates and runs `NpuStandardRouteS1Bridge`.
   - Applies standard-route phase gates and quality/delivery gates.
   - Does not own native Engine lifetime directly.

2. `NpuStandardRouteS1Bridge.kt`
   - Calls `NpuStandardRouteS1Invoker.invoke(...)`.
   - Maps raw provider output via `NpuStandardRouteS1Mapper.map(...)`.

3. `NpuStandardRouteS1Invoker.kt`
   - Uses `NpuStandardRouteS1ProviderSelector`.
   - In debug/standard NPU-enabled builds, resolves the reflected
     `RealNpuStandardRouteS1Provider`.

4. `RealNpuStandardRouteS1Provider.kt`
   - Clamps requested output tokens through
     `NpuStandardRoutePreferences.resolveNativeMaxOutputTokens(...)`.
   - Builds `DevOnlyNpuOneTurnConversationRequest`.
   - Runs `DevOnlyNpuOneTurnConversationEntry(appContext).run(request)`.
   - Maps the display result with `RealNpuStandardRouteS1ResultMapper`.

5. `DevOnlyNpuOneTurnConversationEntry.kt`
   - Creates the display/safety wrapper for a single one-turn run.
   - Uses `Qairt244DevOnlyNpuRouteAdapter.runDevOnlyConversationOnce(...)`.

6. `Qairt244DevOnlyNpuRouteAdapter.kt`
   - Resolves the model path with `Qairt244ModelPathResolver`.
   - Applies prompt/template validation and final model input formatting.
   - Calls `Qairt244ShortMultitokenSmoke.runEditablePrompt(...)`.
   - Parses native result files and native diagnostics.
   - Emits `QNN_HTP_V79_FastRPC_native_diag` when QNN/HTP/V79/FastRPC evidence
     is present in native diagnostics.

7. `Qairt244ShortMultitokenSmoke.kt`
   - Loads `litertlm_jni`.
   - Calls external `nativeRunEditablePrompt(...)`.
   - Supplies `modelPath`, `nativeLibraryDir`, `cacheDir`, result path,
     diag path, normalized prompt, prompt input limit mode, and
     `maxOutputTokens`.

The key observation is that the standard route calls a single external native
function for each decode. Kotlin sees native call start/finish and result files,
but not a reusable Engine/session handle.

## Native Function Shape

The public Kotlin-visible one-shot entrypoint is:

```kotlin
private external fun nativeRunEditablePrompt(
    modelPath: String,
    nativeLibraryDir: String,
    cacheDir: String,
    resultPath: String,
    diagPath: String,
    prompt: String,
    promptInputLimitMode: String,
    maxOutputTokens: Int,
): String
```

That function is enough for one run, but it does not expose:

- Engine handle
- ModelAssets handle
- session/conversation handle
- adapter holder identity
- create/reuse/close controls
- single-flight lock
- fatal-error invalidation state
- per-run holder generation/counter state

The repository source under `app/src/customBuildExperimentDebug/cpp` only
contains the app JNI smoke stub. The actual `nativeRunEditablePrompt` /
`nativeRunPersistentProbe` implementation is provided by the `litertlm_jni`
stack and is not editable from Kotlin alone.

The repository now also has a DEV-only app JNI holder create/close probe:

- Kotlin declarations are on `Qairt244ShortMultitokenSmoke`.
- The native stub library is `liblami_npu_persistent_holder_stub.so`.
- The functions are create/run-once/close/diagnostics for the standard-route
  adapter holder contract.
- The create/close probe can create one app JNI holder record, report a
  `holderId`, and close it explicitly.
- `run once` still reports `status=not_implemented`.
- It does not call `EngineFactory::CreateDefault`, `ModelAssets::Create`,
  `EngineSettings::CreateDefault`, QNN/LiteRT/NPU decode, generate, or the
  normal NPU chat route.
- Current create depth is
  `holder_native_create_level=app_jni_holder_lifecycle_only_pre_engine_create`
  because this separate app JNI library does not safely link to the LiteRT-LM
  C++ symbols inside `litertlm_jni`.

This proves that Kotlin can reach native create/close holder lifecycle symbols.
It is not evidence of persistent Engine reuse.

## Lifecycle Visibility

Current standard-route lifecycle visibility is partial.

Visible from Kotlin/result files:

- native call reached/returned
- native stage history
- native decode started/finished
- native cleanup reached
- native result/diag tails
- QNN/HTP/V79/FastRPC backend evidence
- timeout/fresh crash/fallback status

Not exposed as Kotlin handles:

- `ModelAssets::Create`
- `EngineSettings::CreateDefault`
- `EngineFactory::CreateDefault`
- session creation/destruction
- Engine close/dispose lifecycle
- reusable holder identity

## Create / Dispose Timing

| Layer | Timing | Current visibility | Persistent reuse implication |
| --- | --- | --- | --- |
| `RealNpuStandardRouteS1Provider.invoke(...)` | One provider call per standard-route generation. | Exposed in Kotlin. Logs request start, requested/effective max tokens, and provider success/failure. | Can wrap or gate a call, but cannot keep a native Engine alive. |
| `DevOnlyNpuOneTurnConversationEntry.run(...)` | One display/safety wrapper per one-turn request. | Exposed in Kotlin. Builds one-turn display and safety diagnostics. | One-turn contract only; no reusable conversation/session object is returned. |
| `Qairt244DevOnlyNpuRouteAdapter.runRoute(...)` | One adapter run per decode. | Exposed in Kotlin. Starts timeout scope, calls the native adapter, parses result/diag files. | Good integration point for a future holder-backed adapter, but current method still calls a one-shot native function. |
| `Qairt244ShortMultitokenSmoke.runEditablePrompt(...)` | One native call per decode. | Exposed only as JNI wrapper call. | This is the successful native decode entrypoint, but it returns text/result metadata, not a holder handle. |
| `nativeRunEditablePrompt(...)` / `litertlm_jni` | Native model assets / settings / engine / session / prefill / decode / cleanup appear to happen inside this call. | Only native result and diagnostic files are visible to Kotlin. | True persistent reuse requires a new native API that splits create, run, and close. |

Native diagnostic text has historically shown the lower-level create path:

- `ModelAssets::Create`
- `EngineSettings::CreateDefault`
- `SetLitertDispatchLibDir`
- `EngineFactory::CreateDefault`
- `DispatchDelegate::Initialize`
- `RunPrefill`
- `RunDecode`

However, this is logging evidence, not an API surface for persistent reuse.

The current evidence therefore supports `engine_lifecycle_visibility=partial`:
Kotlin can see that native create/decode/cleanup stages happened, but it cannot
hold, reuse, or invalidate the underlying Engine.

## Existing Persistent Probes

There are two distinct persistent-related paths:

### Official Session Probe

`NpuS1PersistentEngineDevProbe` uses official LiteRT-LM `Engine` /
`Session.generateContent`. Physical-device evidence shows this fails on NPU
because session generate content requests logits output:

- `session_api_supported_for_npu=false`
- `session_api_block_reason=logits_output_not_supported_on_npu_backend`
- `logits_output_required=true`
- `logits_output_backend_supported=false`

This API must remain blocked for NPU persistent diagnostics unless upstream
changes the NPU backend behavior.

### Persistent Custom JNI Probe

`NpuS1PersistentCustomJniDevProbe` calls
`Qairt244ShortMultitokenSmoke.runPersistentProbe(...)`, which is a separate
custom holder PoC with holder keys, native probe modes, and quality prompt
profiles.

This path is useful evidence, but it is not currently the same public contract
as the standard route:

- It is not wired through `NpuStandardRouteS1Bridge`.
- It has separate prompt profiles and native probe modes.
- It is not exposed as `NpuStandardRouteS1Provider`.
- It does not currently provide a small standard-route adapter object that can
  accept repeated `userPrompt/maxOutputTokens` calls and return
  `NpuStandardRouteS1RawResult`.

It may be the closest implementation reference for a future persistent
standard-route adapter, but it still needs native/API alignment work.

## Why Kotlin Alone Is Not Enough

Kotlin can repeatedly call `NpuStandardRouteS1Bridge` or
`RealNpuStandardRouteS1Provider`, but each call still reaches the one-shot
native adapter. Kotlin cannot keep the underlying native Engine alive because
no reusable native handle is returned.

Kotlin-only work can add:

- diagnostics
- single-flight guards around calls
- fatal-error cooldown policy
- result parsing
- UI copy/export
- a wrapper interface for a future persistent adapter

Kotlin-only work cannot prove true persistent Engine reuse unless native/JNI
exposes a reusable standard-route adapter handle.

## Candidate Integration Points

Recommended shape for a DEV-only persistent standard-route adapter:

1. Add a native holder API beside the existing one-shot editable prompt path:
   - `nativeCreateStandardRouteAdapterHolder(...)`
   - `nativeRunStandardRouteAdapterHolderOnce(holderId, prompt, maxOutputTokens, ...)`
   - `nativeCloseStandardRouteAdapterHolder(holderId, reason)`
   - `nativeGetStandardRouteAdapterHolderDiagnostics(holderId)`

   Current status: the four JNI declarations exist. Create/close manage one
   app JNI holder lifecycle record and safety diagnostics. Run-once remains
   `not_implemented`, and no decode/generate path is implemented.

2. Keep the same standard-route prompt/quality contract:
   - Use `NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT`.
   - Use the same `Qairt244DevOnlyNpuRouteAdapter` final model input rules.
   - Return fields that can map into `NpuStandardRouteS1RawResult`.
   - Preserve `raw_output`, `sanitized_output`, `quality_classification`,
     output quality candidate status, and backend evidence.

3. Keep it DEV-only until safety gates pass:
   - Do not change normal chat route.
   - Do not enable fallback.
   - Do not route through official session/logits API.

4. Wire `NPU Persistent Engine Multi-turn Probe` to this adapter only after the
   native holder API exists.

An intermediate Kotlin summary builder can report
`persistent_adapter_available=false` / `needs_native_adapter_work`, but it
would not prove persistent reuse. It should be treated as an exposure review,
not as a multi-turn execution path.

The native create/close summary uses
`test_name=NPU Persistent Holder Create Close Probe` and should continue to
report `persistent_multi_turn_possible=false` until a real Engine-backed holder
exists and physical-device evidence confirms the lifecycle.

The DEV diagnostics UI now has a dedicated
`NPU Persistent Holder Create/Close Probe` entry for physical-device collection.
It runs create/diagnostics/close/diagnostics plus a second close safety check,
and offers `Copy Holder Create/Close Summary` and
`Copy Holder Create/Close Full Dump`. This UI entry is not a generation test:
`runHolderOnce`, native decode/generate, QNN/HTP/FastRPC decode, and normal NPU
chat routing remain forbidden. Pass requires create and close to be called,
decode/generate flags to remain false, and `holder_fatal_latch=false`; any
fatal latch, create/close failure, or decode/generate flag is a hold condition.

After the physical-device Create/Close pass, the next DEV-only exposure step is
`NPU Persistent Holder Run Once Probe`. It runs one create -> run once -> close
sequence with prompt `こんにちは` and `max_output_tokens=32`. This does not
claim persistent reuse: the holder native call verifies the open-holder/run-once
gate, while decode still uses the existing one-shot standard-route adapter
success path. Normal NPU chat routing, 10-turn probing, Long Generation, and R6
streaming remain out of scope.

Run Once pass requires create success, `run_once_called=true`,
`run_once_succeeded=true`, `run_decode_reached=true`, no fallback, no timeout,
no fresh crash, close success, no fatal latch, and QNN HTP / FastRPC evidence.
Hold on create failure, unsupported/failed run once, fallback, timeout, fresh
crash, close failure, fatal latch, or missing backend evidence. Multi-turn is
still blocked because `engine_reuse_observed=unavailable` and the test only
proves one holder-gated one-shot decode.

## Required Safety Conditions

Before persistent standard-route adapter execution is enabled:

- single-flight guarantee for all NPU adapter calls
- no concurrent generate
- no concurrent Stability/Long/Persistent/custom JNI runs
- `fallback_used=false` remains mandatory
- timeout and fresh-crash monitoring remain mandatory
- fatal native error disables NPU until app restart or explicit cooldown
- `engine_create_failed` prevents further create attempts in the same process
- raw/sanitized output and quality classification are recorded per run
- QNN/HTP/V79/FastRPC backend evidence remains visible
- session/logits API is not re-enabled
- output quality gate remains unchanged
- no UI/TTS/DB/Markdown/streaming delivery from the probe

## Relationship To Recreate Stress Failure

`NPU Beta Stability Test` recreate mode can decode successfully several times
and then fail near run 7 with `engine_create_failed`. That suggests repeated
create/destroy pressure may be unsafe.

Persistent adapter exposure is the right next investigation if the goal is
normal-chat stability over multiple turns. Repeatedly calling the current
one-shot adapter from Kotlin does not answer that question, because it still
exercises repeated native create/destroy.

## Decision

Current feasibility:

- Kotlin-only persistent adapter exposure: not sufficient
- JNI/native holder API: required for true persistent standard-route adapter
  reuse
- Existing custom JNI holder PoC: useful reference, not yet the standard-route
  adapter contract
- Normal NPU chat route persistentization: not ready
- Recommended near-term work: DEV-only native holder API design and a summary
  mapper that returns `NpuStandardRouteS1RawResult`-compatible diagnostics

Recommended next implementation unit for Codex:

1. Add a DEV-only native holder interface proposal and Kotlin wrapper stubs that
   can return `not_exposed` until the native side exists.
2. Align the holder output with `NpuStandardRouteS1RawResult` before connecting
   it to the Persistent Probe.
3. Add unit tests for the wrapper's `not_exposed` and fatal-error latch
   behavior.
4. Only after native holder support exists, run the physical-device
   `NPU Persistent Engine Multi-turn Probe` for 10 turns and compare it against
   the recreate Stability Test.

The concrete DEV-only holder contract is now captured in
`docs/npu_dev_only_persistent_holder_api_design.md`. That document defines the
Kotlin wrapper stub, lifecycle, failure transitions, diagnostics summary, and
minimum native/JNI function list without implementing native behavior.

Do not connect this to the normal NPU chat route until the DEV probe has
physical-device evidence and the safety conditions below are satisfied.

Do not proceed to normal NPU chat persistentization until the DEV-only
persistent standard-route adapter can run the 10-turn probe with:

- `run_count_completed=10`
- `success_count=10`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `engine_create_failed_count=0`
- `run_decode_reached_count=10`
- stable `QNN_HTP_V79_FastRPC_native_diag`
- no unsafe output delivery
