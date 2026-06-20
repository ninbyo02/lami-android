# NPU DEV-only Persistent Holder API Design

## Purpose

This document defines the DEV-only contract needed to test persistent
multi-turn NPU generation through the same standard-route/native decode path
that already succeeds with `QNN_HTP_V79_FastRPC_native_diag`.

This is a design and Kotlin contract pass only. It does not implement JNI or
native holder behavior.

## Goals

- Create the NPU Engine / standard-route adapter holder once.
- Generate through that holder multiple times.
- Close the holder once.
- Preserve the existing standard-route prompt, sanitizer, quality gate,
  backend evidence, max-output-token clamp, and diagnostics semantics.
- Make holder availability explicit through diagnostics instead of inferring
  reuse from repeated one-shot calls.
- Keep all holder work DEV-only until physical-device safety evidence exists.

## Non Goals

- No normal NPU chat route behavior change.
- No release-build holder API.
- No official LiteRT-LM session API use on NPU.
- No fallback enablement.
- No Long Generation Test behavior change.
- No R6 native token streaming implementation.
- No claim that persistent multi-turn works before native holder evidence.

## Current Successful Path

The successful one-shot NPU standard route is:

`NpuStandardRouteS1Bridge`
-> `RealNpuStandardRouteS1Provider`
-> `DevOnlyNpuOneTurnConversationEntry`
-> `Qairt244DevOnlyNpuRouteAdapter.runRoute`
-> `Qairt244ShortMultitokenSmoke.runEditablePrompt`
-> `nativeRunEditablePrompt`
-> `litertlm_jni`

Kotlin can observe request metadata, native stage history, result files,
quality classification, and backend evidence. Kotlin cannot currently hold or
reuse the underlying Engine because `nativeRunEditablePrompt` is one-shot.

## Proposed Kotlin Contract

The Kotlin-side contract is defined as a wrapper/stub in
`NpuPersistentHolderApi.kt`.

Current implementation:

- `NotExposedNpuPersistentHolderApi`
- `holder_api_available=false`
- `holder_api_reason=needs_native_jni_support`
- `persistent_multi_turn_possible=false`
- `engine_reuse_observed=unavailable`
- `recommended_next_step=implement_dev_only_native_holder_api`

The interface shape is:

```kotlin
interface NpuPersistentHolderApi {
    fun createHolder(request: NpuPersistentHolderCreateRequest): NpuPersistentHolderApiResult
    fun runOnce(request: NpuPersistentHolderRunRequest): NpuPersistentHolderApiResult
    fun closeHolder(request: NpuPersistentHolderCloseRequest): NpuPersistentHolderApiResult
    fun getDiagnostics(holderId: String): NpuPersistentHolderApiDiagnostics
}
```

The stub deliberately returns `not_exposed`. It is a contract and diagnostic
surface, not a working persistent adapter.

## Proposed Native/JNI API

The minimum JNI surface should be DEV-only and should sit beside the existing
`nativeRunEditablePrompt` path.

Priority order:

1. `nativeCreateStandardRouteAdapterHolder(...)`
   - Inputs:
     - `modelPath`
     - `nativeLibraryDir`
     - `cacheDir`
     - prompt/profile or standard-route adapter mode
     - max token limit / native clamp limit
   - Outputs:
     - `holderId`
     - create status
     - backend evidence
     - native stage history
     - engine/create diagnostics
   - Must fail closed on Engine create failure.

2. `nativeRunStandardRouteAdapterHolderOnce(...)`
   - Inputs:
     - `holderId`
     - final standard-route prompt or user prompt plus explicit prompt mode
     - `maxOutputTokens`
     - result path
     - diag path
   - Outputs:
     - raw output
     - sanitized/prepared output if native owns cleanup, otherwise raw output
     - decode status/reason
     - backend evidence
     - native stage history
     - timing
   - Must not use official session/logits API.

3. `nativeCloseStandardRouteAdapterHolder(...)`
   - Inputs:
     - `holderId`
     - close reason
   - Outputs:
     - close reached/success
     - cleanup diagnostics
   - Must be idempotent or explicitly report already closed.

4. `nativeGetStandardRouteAdapterHolderDiagnostics(...)`
   - Inputs:
     - `holderId`
   - Outputs:
     - holder state
     - create count
     - run request count
     - decode attempt count
     - decode success count
     - fatal error latched
     - holder generation
     - backend evidence summary

Optional later API:

- `nativeInvalidateStandardRouteAdapterHolder(...)` for fatal-error latching
  without attempting close/recreate after a crash-risk condition.

## Lifecycle

Normal lifecycle:

```text
NOT_CREATED
  -> CREATING
  -> CREATED
  -> READY
  -> GENERATING
  -> READY
  -> GENERATING
  -> READY
  -> CLOSING
  -> CLOSED
```

Terminal lifecycle:

```text
NOT_CREATED
  -> CREATING
  -> TERMINATED

READY
  -> GENERATING
  -> TERMINATED

READY
  -> CLOSING
  -> TERMINATED
```

State rules:

- `createHolder` is valid only from `NOT_CREATED`.
- `runOnce` is valid only from `READY`.
- `closeHolder` is valid from `CREATED`, `READY`, or failed `GENERATING` if the
  native side says close is safe.
- After `TERMINATED`, no create/run retry is allowed in the same process unless
  a future explicit cooldown design says otherwise.

## Failure Transitions

| Failure | Transition | Required diagnostics | Recovery |
| --- | --- | --- | --- |
| `engine_create_failed` | `CREATING -> TERMINATED` | `engine_create_failed_count=1`, native diag tail, `restart_app_recommended=true` | Stop probe; do not recreate in same process. |
| `native_failure` | `GENERATING -> TERMINATED` unless classified recoverable | native stage, native error class/message, diag tail | Stop probe; require review before retry. |
| `adapter_failure` | `GENERATING -> TERMINATED` unless clearly non-fatal | adapter reason, run index, diag tail | Stop probe. |
| `timeout` | `GENERATING -> TERMINATED` | timeout duration, run index | Stop probe; do not close/recreate aggressively. |
| `fresh_crash` | any state -> `TERMINATED` | tombstone/crash evidence | Stop probe; restart app recommended. |
| quality candidate fail | `GENERATING -> READY` only if native succeeded safely | output suppressed, quality reason | Continue only if test policy allows; never deliver output. |

## Diagnostics Summary

The Kotlin stub currently emits:

```text
[DEV診断: NPU persistent holder API summary]
test_name=NPU Persistent Holder API Probe
holder_api_available=false
holder_api_reason=needs_native_jni_support
holder_create_supported=false
holder_run_supported=false
holder_close_supported=false
holder_diagnostics_supported=false
persistent_multi_turn_possible=false
engine_reuse_observed=unavailable
session_api_supported_for_npu=false
session_api_block_reason=session_api_logits_output_not_supported_on_npu_backend
standard_route_adapter_decode_success_known=true
standard_route_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
engine_lifecycle_visibility=partial
required_native_api=create_holder,run_holder_once,close_holder,get_holder_diagnostics
recommended_next_step=implement_dev_only_native_holder_api
```

Do not change these to success values until the native holder API exists and
physical-device evidence proves reuse.

## Safety Requirements

Before connecting the holder to an executable Persistent Probe:

- Single-flight all holder calls.
- Forbid concurrent standard-route, Stability, Long Generation, custom JNI, or
  Persistent holder runs.
- Keep `fallback_used=false`.
- Keep `native_streaming_used=false` unless a separate R6 design changes that.
- Keep official session/logits API blocked on NPU.
- Preserve `requested_max_output_tokens` and effective native clamp diagnostics.
- Record raw output, sanitized output, quality classification, backend
  evidence, timing, and native diag tail for every run.
- Stop on first engine-create failure, timeout, fresh crash, or fatal native
  adapter failure.
- Do not deliver Persistent Probe output to UI/TTS/DB/Markdown/Streaming.

## Normal Route Connection Gate

Do not change normal NPU chat route until DEV-only holder evidence shows:

- `run_count_completed=10`
- `success_count=10`
- `fallback_used_count=0`
- `timeout_count=0`
- `fresh_crash_count=0`
- `engine_create_failed_count=0`
- `run_decode_reached_count=10`
- stable `QNN_HTP_V79_FastRPC_native_diag`
- no unsafe output delivery
- holder diagnostics show one create, multiple run calls, and one close

## Next Implementation Units

1. Keep the Kotlin stub and summary as `not_exposed`.
2. Add DEV-only JNI declarations matching the four required native functions.
3. Implement native holder create/run/close/diagnostics behind a debug-only
   entrypoint.
4. Map holder run results into `NpuStandardRouteS1RawResult`-compatible
   diagnostics.
5. Connect `NPU Persistent Engine Multi-turn Probe` to the holder only after
   native diagnostics prove the holder is real.
