# QAIRT244 Real NPU Provider Source Set Review

Date: 2026-05-30

Scope: design only. This document does not implement code, run runtime probes,
install APKs, or change native code.

## Goal

Define where the S1.5 provider pieces should live across source sets before
implementing a real NPU provider.

The selected design remains:

```text
main interface
debug provider implementation
```

The purpose is to connect real NPU results only where the build variant has the
required debug/dev-only implementation available, while keeping main free of
debug-only dependencies.

## Existing Source Sets

Relevant source sets:

- `app/src/main`
- `app/src/debug`
- `app/src/standardDebug`
- `app/src/customBuildExperimentDebug`
- `app/src/test`
- `app/src/testCustomBuildExperimentDebug`

Relevant flavors/build variants:

- `standardDebug`
  - `BuildConfig.CURRENT_FLAVOR="standard"`
  - `QUALCOMM_DISPATCH_EXPERIMENT=false`
  - `CUSTOM_BUILD_EXPERIMENT=false`
- `customBuildExperimentDebug`
  - application id suffix `.customnpu`
  - `BuildConfig.CURRENT_FLAVOR="customBuildExperiment"`
  - `QUALCOMM_DISPATCH_EXPERIMENT=true`
  - `CUSTOM_BUILD_EXPERIMENT=true`
  - native stack staged under `app/src/customBuildExperimentDebug`

## 1. Interface Placement

Place `NpuStandardRouteS1Provider` in main:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Provider.kt
```

Reason:

- main owns the stable S1.5 boundary;
- tests can use the provider contract without debug source;
- future S2/S3/S4/S5 phases can depend on the same provider result shape;
- debug implementations can implement the main interface legally.

Main source must only know the interface and main providers. It must not import
debug provider classes.

## 2. Fixed Provider Placement

Place `FixedNpuStandardRouteS1Provider` in main:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/FixedNpuStandardRouteS1Provider.kt
```

Reason:

- it is the safe default for every build variant;
- it preserves the S1 ChatScreen display proof;
- it gives an immediate rollback target;
- it keeps unit tests independent of device/runtime/NPU availability.

Expected behavior:

```text
status=success
sanitized_output=こんにちは。
quality_classification=natural_japanese
run_decode_reached=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
timeout=false
fresh_crash=false
```

This remains a fixed result and is not evidence of real runtime execution.

## 3. Failure Provider Placement

Place `FailureNpuStandardRouteS1Provider` in main:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/FailureNpuStandardRouteS1Provider.kt
```

Reason:

- any build variant can represent unavailable or invalid real-provider state;
- real provider selection can fail closed instead of falling back to fixed
  success silently;
- tests can cover fallback, timeout, fresh crash, missing evidence, and blank
  output without runtime.

Failure provider must preserve side-effect isolation:

```text
db=false
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
conversation_history_saved=false
```

## 4. Real Provider Placement

Do not place the first real provider in main.

Preferred first placement:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/
  CustomBuildExperimentRealNpuStandardRouteS1Provider.kt
```

Reason:

- `customBuildExperimentDebug` is the variant with the custom LiteRT-LM/native
  experiment stack;
- the proven dev-only NPU conversation work has been validated against that
  experiment path;
- placing real provider code there avoids exposing unfinished NPU execution to
  `standardDebug`;
- the implementation can still implement the main
  `NpuStandardRouteS1Provider` interface.

Do not put the first real provider in `app/src/debug` unless the intended scope
is every debug variant. `debug` is shared by `standardDebug`,
`customBuildExperimentDebug`, and other debug variants. A shared debug provider
would risk making real-NPU behavior visible in variants without the required
native/runtime stack.

Do not put the first real provider in `app/src/standardDebug`. `standardDebug`
does not have `QUALCOMM_DISPATCH_EXPERIMENT=true` or
`CUSTOM_BUILD_EXPERIMENT=true`, so it should remain fixed/failure only for S1.5.

## 5. Guarantee Main Does Not Reference Debug-Only Implementations

Required guarantees:

- `NpuStandardRouteS1Invoker` default provider remains a main provider;
- `NpuStandardRouteS1Bridge` accepts only main-visible provider/invoker types;
- no main file imports `DevOnlyNpuOneTurnConversationEntry`,
  `Qairt244DevOnlyNpuRouteAdapter`, or a debug real provider class;
- provider selection is expressed through a main-visible factory contract or
  build-variant source-set implementation that has the same public symbol but
  keeps debug implementation details outside main;
- unit tests in `app/src/test` compile without debug provider classes.

Static checks before implementation:

```text
rg "DevOnlyNpuOneTurnConversationEntry|Qairt244DevOnlyNpuRouteAdapter|RealNpuStandardRouteS1Provider" app/src/main
```

Expected result: no debug-only implementation imports from main.

## 6. standardDebug Behavior

`standardDebug` should continue to use main defaults:

```text
NpuStandardRouteS1Provider = FixedNpuStandardRouteS1Provider
```

or, if provider selection is enabled but real provider is unavailable:

```text
NpuStandardRouteS1Provider = FailureNpuStandardRouteS1Provider(reason=real_provider_unavailable_for_variant)
```

Required behavior:

- S1 gate default remains off;
- gate on can still display the fixed S1 proof result if fixed provider is
  selected;
- no real NPU execution is attempted;
- no native/runtime requirement is introduced;
- DB/TTS/Markdown/streaming remain disconnected.

This keeps `standardDebug` useful for UI/gate verification without representing
it as real NPU runtime proof.

## 7. customBuildExperimentDebug Behavior

`customBuildExperimentDebug` is the intended first target for the real provider.

Expected behavior when real provider is explicitly selected:

```text
NpuStandardRouteS1Provider = CustomBuildExperimentRealNpuStandardRouteS1Provider
```

Required runtime contract:

- `prompt_tail_variant=raw_dialog_tail_variant_b`;
- requested/effective `max_output_tokens=32`;
- result maps into `NpuStandardRouteS1RawResult`;
- `run_decode_reached=true`;
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- `quality_classification=natural_japanese`;
- side-effect flags remain false.

If the real provider cannot initialize or required native/runtime pieces are
missing, it must return a failure raw result. It must not silently return the
fixed success provider because that would blur real-runtime evidence.

## 8. Rollback Method

Primary rollback:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=false
```

Provider rollback:

```text
NpuStandardRouteS1Provider = FixedNpuStandardRouteS1Provider
```

Failure rollback for unavailable real provider:

```text
NpuStandardRouteS1Provider = FailureNpuStandardRouteS1Provider(reason=...)
```

Rollback must not require:

- DB cleanup;
- TTS cleanup;
- Markdown state cleanup;
- streaming placeholder cleanup;
- `Backend.NPU` setting cleanup;
- native changes;
- conversation history migration.

## Recommended Layout

Use this layout for the next implementation step:

```text
main:
  NpuStandardRouteS1Provider
  FixedNpuStandardRouteS1Provider
  FailureNpuStandardRouteS1Provider
  NpuStandardRouteS1Invoker
  NpuStandardRouteS1Bridge

customBuildExperimentDebug:
  CustomBuildExperimentRealNpuStandardRouteS1Provider
  optional customBuildExperimentDebug provider factory/binding

standardDebug:
  no real provider
  fixed or explicit failure provider only

test:
  provider interface/fixed/failure tests
  fake real provider tests

testCustomBuildExperimentDebug:
  custom real provider contract tests without running runtime
```

This keeps main source stable, keeps `standardDebug` safe for UI display checks,
and limits the first real NPU provider to the variant that owns the experiment
runtime stack.
