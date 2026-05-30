# QAIRT244 Real NPU Provider Design Review

Date: 2026-05-30

Scope: design only. This document does not implement code, run runtime probes,
install APKs, or change native code.

## Goal

Phase S1.5 now has a main-source provider interface:

```kotlin
interface NpuStandardRouteS1Provider {
    fun invoke(): NpuStandardRouteS1RawResult
}
```

The current default provider remains fixed:

```text
FixedNpuStandardRouteS1Provider
-> "こんにちは。"
```

The next design decision is how a real QAIRT244 NPU provider should connect to
this interface without breaking source-set boundaries or S1 display-only
rollback.

## Baseline Constraints

The real execution behavior proven so far lives in the debug source set:

- `DevOnlyNpuOneTurnConversationEntry`
- `Qairt244DevOnlyNpuRouteAdapter`
- debug-only Activity/receiver entry points

The standard S1 provider abstraction lives in main:

- `NpuStandardRouteS1Provider`
- `FixedNpuStandardRouteS1Provider`
- `FailureNpuStandardRouteS1Provider`
- `NpuStandardRouteS1Invoker`
- `NpuStandardRouteS1Bridge`

Main source must not import debug-only classes directly. Real provider wiring
must preserve:

- `db=false`;
- `tts=false`;
- `markdown=false`;
- `streaming=false`;
- `backend_npu_persisted=false`;
- `conversation_history_saved=false`;
- rollback by disabling the S1 gate or restoring the fixed provider.

## Candidate A: Main Interface, Debug Provider Implementation

Design:

```text
main:
  NpuStandardRouteS1Provider
  FixedNpuStandardRouteS1Provider
  FailureNpuStandardRouteS1Provider
  NpuStandardRouteS1Invoker(provider)

debug:
  DebugRealNpuStandardRouteS1Provider : NpuStandardRouteS1Provider
  uses dev-only NPU conversation internals
```

The real provider is implemented in a source set that can legally depend on the
dev-only NPU conversation code. Main continues to depend only on the provider
interface.

Important detail: ChatScreen must not import the debug provider class directly
from main. If the debug provider is used for S1.5, selection should be via an
explicit debug-only factory, build-variant binding, or a small main factory with
debug-only override that still keeps main free of debug class references.

## Candidate B: Main Interface, Reflection Bridge

Design:

```text
main:
  NpuStandardRouteS1Provider
  ReflectionRealNpuStandardRouteS1Provider
  provider locates dev-only implementation by class name
```

The provider stays in main and attempts to load a debug-only implementation by
reflection when present. If not present, it can return a failure or fixed result.

This avoids compile-time dependency on debug classes but introduces runtime
string coupling and weaker test guarantees.

## Candidate C: Main Interface, Service Locator

Design:

```text
main:
  NpuStandardRouteS1Provider
  NpuStandardRouteS1ProviderRegistry.current
  default = FixedNpuStandardRouteS1Provider

debug:
  registers DebugRealNpuStandardRouteS1Provider at startup or debug entry point
```

Main owns a registry or locator. Debug source registers the real provider when
the debug runtime path is active.

This keeps compile-time dependencies legal, but global mutable provider state
creates ordering and rollback risks unless tightly constrained.

## Comparison

| Criterion | A. Debug provider implementation | B. Reflection bridge | C. Service locator |
| --- | --- | --- | --- |
| Source set consistency | Strong if main only references the interface and debug owns implementation | Medium: no compile dependency, but main knows debug class names | Medium: compile-safe, but runtime registration crosses source-set boundaries |
| Rollback ease | High: restore fixed provider or disable S1 gate | Medium: reflection failure paths must be audited | Medium: registry reset must be reliable |
| Implementation difficulty | Medium: requires a clear binding/factory point | Medium/high: reflection mapping and exception handling | Medium: registry lifecycle and test isolation needed |
| Test ease | High: provider can be faked through the interface | Medium: reflection tests are brittle and variant-sensitive | Medium: tests must reset global provider state |
| S2 DB impact | Low: provider returns raw result only; DB remains outside | Low/medium: same contract, but hidden reflection failures complicate diagnosis | Medium: global state can leak into DB tests |
| S3 Markdown impact | Low: provider returns raw/sanitized text; Markdown stays later | Low/medium: result ownership is less explicit | Medium: provider state must not alter Markdown tests |
| S4 Streaming impact | Low: single-shot provider can stay separate from future streaming provider | Medium: reflection is a poor fit for callback/streaming APIs | Medium/high: locator may grow into multiple provider types |
| S5 TTS impact | Low: TTS remains a consumer after final result | Low/medium: failures must not trigger TTS | Medium: global provider state can affect TTS gate tests |

## Candidate A Risks And Controls

Risks:

- main ChatScreen may be tempted to directly instantiate the debug provider;
- debug provider may accidentally reuse DB-backed hidden ChatScreen route code;
- adapter result may not include all fields required by `NpuStandardRouteS1RawResult`.

Controls:

- keep `NpuStandardRouteS1Provider` as the only main-facing type;
- keep `FixedNpuStandardRouteS1Provider` as default;
- use `FailureNpuStandardRouteS1Provider` for unavailable or invalid real
  provider states;
- require tests that exercise success, fallback, timeout, fresh crash, blank
  output, and missing evidence through the provider interface;
- require the real debug provider to map all diagnostics into
  `NpuStandardRouteS1RawResult`;
- do not call DB, TTS, Markdown, streaming, backend persistence, or conversation
  history APIs from the real provider.

## Candidate B Risks And Controls

Risks:

- class-name strings can drift without compile-time failures;
- reflection exceptions can collapse into generic failure and hide root cause;
- ProGuard/R8 or source-set packaging can affect availability later;
- test behavior can differ between unit tests and installed debug APKs.

Controls:

- reflection path must return `FailureNpuStandardRouteS1Provider`-equivalent
  raw results on any error;
- reflection must expose reason codes such as `class_not_found`,
  `method_not_found`, or `invalid_result_type`;
- reflection must not fall back to fixed success silently.

Even with controls, reflection is better as a last-resort diagnostic bridge than
as the standard S1.5 promotion design.

## Candidate C Risks And Controls

Risks:

- global provider state can leak across tests or app sessions;
- registration order becomes part of correctness;
- rollback requires both gate-off and registry reset;
- S2/S3/S4/S5 tests may accidentally run against a previously registered real
  provider.

Controls:

- registry default must always be `FixedNpuStandardRouteS1Provider`;
- registry must expose an explicit reset path for tests;
- debug registration must be gated and visible in diagnostics;
- provider selection must be included in S1 display text.

This option is viable only if the app already has a strong pattern for explicit
debug-only registration. It is not preferable as the first real provider path.

## Required Real Provider Contract

Any real provider must return `NpuStandardRouteS1RawResult` with these fields
mapped from the dev-only NPU conversation result:

- `status`;
- `result`;
- `success`;
- `reason`;
- `rawOutput`;
- `sanitizedOutput`;
- `qualityClassification`;
- `runDecodeReached`;
- `npuBackendEvidence`;
- `fallbackUsed`;
- `timeout`;
- `freshCrash`;
- `requestedMaxOutputTokens=32`;
- `effectiveMaxOutputTokens=32`.

It must preserve S1.5 prompt shaping:

```text
prompt_tail_variant=raw_dialog_tail_variant_b
max_output_tokens=32
```

It must not persist or invoke:

- DB;
- assistant insert;
- `runWithHeldEngine`;
- streaming callbacks;
- Markdown;
- TTS;
- `Backend.NPU` selection;
- conversation history save.

## Recommendation

Choose **Candidate A: main interface with debug provider implementation**.

Reasons:

- It keeps compile-time source-set boundaries honest: main depends on the
  provider interface only, while debug can own the dev-only NPU implementation.
- Rollback is simple: keep or restore `FixedNpuStandardRouteS1Provider`, or turn
  `ENABLE_NPU_STANDARD_ROUTE_S1=false`.
- It is the easiest to test without runtime: fake providers and failure
  providers already use the same interface.
- It keeps S2 DB, S3 Markdown, S4 Streaming, and S5 TTS out of the provider.
- It is less brittle than reflection and less stateful than a service locator.

Implementation should still be split:

1. Add a provider selection/factory point with fixed provider as default.
2. Add a debug-source real provider implementation that maps dev-only
   conversation output to `NpuStandardRouteS1RawResult`.
3. Enable it only behind the existing S1 gate and explicit debug/developer
   control.

Do not implement reflection or service locator first unless the debug provider
binding cannot be expressed cleanly through source-set structure.
