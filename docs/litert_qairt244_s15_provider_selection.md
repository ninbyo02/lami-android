# QAIRT244 Phase S1.5 Provider Selection

Date: 2026-05-30

Scope: design only. This document does not implement code, run runtime probes,
install APKs, or change native code.

## Goal

Phase S1.5 replaces the current fixed S1 result with a real NPU one-turn result
while preserving the S1 display-only boundary.

Current:

```text
NpuStandardRouteS1Invoker
-> fixed "こんにちは。"
-> NpuStandardRouteS1Mapper
-> ChatScreen transient display
```

Target:

```text
NpuStandardRouteS1Invoker
-> real NPU one-turn result equivalent to dev-only conversation
-> NpuStandardRouteS1Mapper
-> ChatScreen transient display
```

The selected provider shape must preserve:

- no DB connection;
- no TTS;
- no Markdown;
- no streaming;
- no `Backend.NPU` persistence;
- no conversation history save;
- no main-source dependency on debug-only classes.

## Options

### A. Put An Interface In Main

Design:

```text
main:
  NpuStandardRouteS1ResultProvider interface
  FixedNpuStandardRouteS1ResultProvider
  NpuStandardRouteS1Invoker(provider)

debug or later standard adapter:
  Real provider implements the main interface
```

The main source owns the contract. A fixed provider remains the default. A real
provider can be supplied only from source sets that can legally see both the
main interface and their own implementation details.

### B. Put A Lightweight Provider In Main

Design:

```text
main:
  NpuStandardRouteS1LightweightProvider
  provider contains the real result plumbing directly
```

The real NPU bridge would move toward main immediately. This reduces indirection
but forces source-set and runtime ownership decisions earlier.

### C. Main Calls A Fixed Provider, Debug Source Replaces It

Design:

```text
main:
  fixed provider symbol

debug:
  alternative implementation or source-set replacement
```

The main API would continue to look fixed, while debug source replaces behavior
at build time or through an alternate binding.

## Comparison

| Criterion | A. Main interface | B. Main lightweight provider | C. Debug replacement |
| --- | --- | --- | --- |
| Implementation difficulty | Medium: define a small provider contract and inject it | High: real route ownership moves into main immediately | Medium/high: depends on source-set replacement or binding mechanics |
| Rollback ease | High: default provider remains fixed; gate off still works | Medium: rollback may require removing real plumbing from main | Low/medium: replacement behavior can be harder to audit |
| Source set consistency | High: main depends only on main interface | Medium: must move or duplicate dev-only logic into main safely | Risky: easy to hide debug-only behavior behind same main symbol |
| Future standard route promotion | High: provider contract becomes the standard seam for S1.5/S2 | Medium/high if real provider is clean, but early coupling risk is higher | Low: debug replacement does not model production ownership well |
| S2 DB impact | Low: provider returns raw result; DB can remain outside provider | Medium: provider may accidentally grow persistence concerns | Medium/high: debug-only replacement may not exercise S2-compatible ownership |
| S3 Markdown impact | Low: provider returns sanitized/raw text only; Markdown stays later | Medium: main provider may tempt output formatting in the wrong layer | Medium: hidden replacement makes final text ownership less explicit |
| S4 Streaming impact | Low: interface can stay single-shot; streaming gets a later provider type | Medium/high: lightweight provider could mix single-shot and streaming concerns | High: source replacement does not scale cleanly to streaming callbacks |
| S5 TTS impact | Low: TTS remains a consumer after successful final result | Medium: provider must be kept free of speech concerns | Medium/high: debug route may bypass normal TTS gating assumptions |

## Option A Details

Recommended main contract:

```text
interface NpuStandardRouteS1ResultProvider {
    fun invoke(): NpuStandardRouteS1RawResult
}
```

Main default:

```text
FixedNpuStandardRouteS1ResultProvider
-> returns the current "こんにちは。" S1 result
```

Invoker:

```text
NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1ResultProvider())
-> provider.invoke()
```

Real provider later:

```text
RealNpuStandardRouteS1ResultProvider
-> performs QAIRT244 one-turn execution
-> maps diagnostics into NpuStandardRouteS1RawResult
```

Required behavior:

- returns `NpuStandardRouteS1RawResult`;
- does not know about ChatScreen;
- does not write DB;
- does not call TTS;
- does not call Markdown;
- does not stream;
- does not persist backend selection;
- does not save conversation history.

## Option B Details

This option makes the main provider responsible for more real execution logic
immediately.

Pros:

- fewer abstractions;
- fewer moving parts once real execution is ready;
- closer to the eventual standard-route implementation if the API ownership is
  already clear.

Risks:

- main source must own NPU execution details before source-set boundaries are
  settled;
- higher chance of pulling debug-only assumptions into main;
- harder rollback if real provider changes broad dependencies;
- increases pressure to solve S2/S3/S4/S5 concerns before S1.5 is stable.

This option is better after the real QAIRT244 adapter is already standard-route
safe.

## Option C Details

This option keeps main looking fixed and uses debug source to swap behavior.

Pros:

- can be quick for a debug-only experiment;
- keeps release source untouched if replacement is strictly debug-only.

Risks:

- build/source-set behavior becomes less explicit;
- production promotion path is unclear;
- tests can pass against a fixed main provider while debug behavior differs;
- rollback depends on build variant behavior rather than a visible provider
  boundary;
- S2 DB, S3 Markdown, S4 Streaming, and S5 TTS planning get weaker because the
  real execution owner is not represented in main.

This option is acceptable for short-lived experiments, but it is not a strong
standard-route promotion path.

## Gate Conditions For Any Option

The selected design must keep these S1.5 gates:

- S1 gate explicitly enabled;
- `InferenceTarget.LOCAL`;
- no image input;
- non-blank prompt;
- `prompt_tail_variant=raw_dialog_tail_variant_b`;
- requested/effective `max_output_tokens=32`;
- `run_decode_reached=true`;
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag` or normalized
  equivalent;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- non-empty `sanitized_output`;
- `quality_classification=natural_japanese`;
- side effects remain false:
  - `db=false`;
  - `tts=false`;
  - `markdown=false`;
  - `streaming=false`;
  - `backend_npu_persisted=false`;
  - `conversation_history_saved=false`.

## Rollback Requirements

Rollback must remain simple:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=false
```

Additionally, provider-level rollback should be possible by restoring the fixed
provider as the default. This matters because S1.5 introduces real execution
risk while S1 already proved ChatScreen display.

Rollback must not require:

- DB cleanup;
- TTS cleanup;
- Markdown state cleanup;
- streaming placeholder cleanup;
- backend setting cleanup;
- native changes;
- conversation history migration.

## Recommendation

Choose **Option A: put an interface in main**.

Reasons:

- It keeps source-set dependencies correct: main owns only a small provider
  contract and does not import debug-only NPU conversation classes.
- It preserves the current fixed result as a safe default and makes rollback
  local to provider selection plus the existing S1 gate.
- It gives S2/S3/S4/S5 a stable boundary: DB, Markdown, streaming, and TTS can
  consume a final `NpuStandardRouteS1Result` later without being pulled into the
  provider.
- It makes tests explicit: fixed provider, fake real provider, failure provider,
  and later real provider can all be tested through the same invoker/bridge
  path.
- It supports future standard-route promotion better than debug source
  replacement, because the real result owner becomes visible in main before
  persistence or downstream features are connected.

Option B should wait until the real QAIRT244 adapter is ready to live in main.
Option C should be avoided for S1.5 promotion because it hides the most
important behavior behind variant replacement and weakens the standard-route
rollback story.
