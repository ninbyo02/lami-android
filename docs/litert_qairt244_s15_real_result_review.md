# QAIRT244 Phase S1.5 Real Result Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, or change native code.

## Current State

Phase S1 currently proves that ChatScreen can select an isolated NPU display
route without entering the normal DB-backed local path.

Current flow:

```text
ChatScreen S1 gate
-> NpuStandardRouteS1Bridge.run()
-> NpuStandardRouteS1Invoker.invoke()
-> fixed NpuStandardRouteS1RawResult
-> NpuStandardRouteS1Mapper.map(...)
-> transient ChatScreen display
```

`NpuStandardRouteS1Invoker` currently returns a fixed success-equivalent result:

```text
sanitized_output=こんにちは。
quality_classification=natural_japanese
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
requested/effective max_output_tokens=32
fallback=false
timeout=false
fresh_crash=false
```

This is useful for proving ChatScreen insertion and side-effect isolation, but
it is not a real NPU execution from the standard route.

## Target S1.5

Phase S1.5 should replace the fixed invoker result with a real NPU result while
preserving the S1 display-only boundary.

Target flow:

```text
ChatScreen S1 gate
-> NpuStandardRouteS1Bridge.run()
-> NpuStandardRouteS1Invoker.invoke()
-> real NPU one-turn result equivalent to dev-only conversation
-> NpuStandardRouteS1Mapper.map(...)
-> transient ChatScreen display
```

Required preserved boundaries:

- no DB insert;
- no assistant insert;
- no `runWithHeldEngine`;
- no streaming;
- no Markdown;
- no TTS;
- no `Backend.NPU` persistence;
- no conversation history save.

The S1.5 result should still use:

```text
prompt_tail_variant=raw_dialog_tail_variant_b
max_output_tokens=32
```

## Source Set Constraint

The proven real execution path currently lives under debug source set:

- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuOneTurnConversationEntry.kt`
- `app/src/debug/java/io/github/ninbyo02/lami/npu/Qairt244DevOnlyNpuRouteAdapter.kt`

The S1 standard-route classes live under main source set:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Contract.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Invoker.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Bridge.kt`

Main source cannot directly depend on debug-only classes. S1.5 therefore needs
an explicit boundary before implementation. Reusing the dev-only behavior is
acceptable as a design reference, but importing debug-only classes from main is
not acceptable.

## Comparison

| Area | Current S1 | Target S1.5 |
| --- | --- | --- |
| Invoker result | fixed `こんにちは。` | real NPU one-turn result |
| NPU execution | none | yes, through a standard-route-safe adapter |
| ChatScreen display | transient `NPU STANDARD ROUTE S1` block | same transient block |
| DB | disconnected | disconnected |
| TTS | disconnected | disconnected |
| Markdown | disconnected | disconnected |
| Streaming | disconnected | disconnected |
| Backend persistence | disconnected | disconnected |
| Rollback | gate off | gate off, plus invoker provider fallback to fixed result if needed |
| Main/debug dependency | main only | must not import debug-only classes into main |

## Change Target Files

Likely S1.5 implementation targets:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Invoker.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Bridge.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Mapper.kt`
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Contract.kt`
- a new main-source standard-route NPU adapter or provider interface
- tests under `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/`

Potential implementation shapes:

1. Add a main-source provider interface that returns `NpuStandardRouteS1RawResult`.
2. Keep the default provider fixed/off until a main-source-safe real provider is
   available.
3. Add a standardDebug-only provider binding only if the source-set boundary can
   be kept explicit and ChatScreen does not import debug-only symbols.
4. Later promote the proven real adapter behavior from debug-only into a
   standard-route-safe main-source adapter after API ownership is clear.

## Files Not To Change

S1.5 should not modify:

- DB entity, DAO, repository, or ViewModel persistence paths;
- `LocalStreamingRunner.kt`;
- held engine lifecycle files;
- TTS controller or speech text files;
- Markdown repair/rendering files;
- settings backend persistence files;
- native/JNI files;
- release-only source set behavior.

`ChatScreen.kt` should remain limited to the existing S1 gate and transient
display unless a provider injection point is strictly required. The preferred
S1.5 change is behind the existing `NpuStandardRouteS1Bridge` boundary.

## Gate Conditions

S1.5 can run only when all of these are true:

- S1 gate is explicitly enabled;
- selected target is `InferenceTarget.LOCAL`;
- image input is absent;
- prompt is non-blank;
- request uses `raw_dialog_tail_variant_b`;
- requested/effective `max_output_tokens=32`;
- result reports `run_decode_reached=true`;
- result reports `QNN_HTP_V79_FastRPC_native_diag` or normalized equivalent;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- `sanitized_output` is non-empty;
- `quality_classification=natural_japanese`;
- side-effect flags remain false:
  - `db=false`;
  - `tts=false`;
  - `markdown=false`;
  - `streaming=false`;
  - `backend_npu_persisted=false`;
  - `conversation_history_saved=false`.

If any result gate fails, S1.5 should display the failure contract in the same
transient block and return without falling through to the normal local route.

## Rollback Conditions

Rollback remains gate-off first:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=false
```

Additional rollback triggers:

- any DB/TTS/Markdown/streaming side-effect flag becomes true;
- fallback, timeout, or fresh crash occurs;
- NPU backend evidence is missing;
- `run_decode_reached=false`;
- `sanitized_output` is blank;
- quality is not `natural_japanese`;
- implementation requires main source to import debug-only classes;
- repeated S1.5 runs show non-deterministic failures after a previously stable
  dev-only baseline.

Because S1.5 remains display-only, rollback should not require DB cleanup,
settings cleanup, native changes, or conversation history migration.

## Blockers

- Main source cannot directly reference debug-only `DevOnlyNpuOneTurnConversationEntry`.
- The dev-only route currently owns result-file diagnostics and debug-only
  activity/receiver entry behavior; S1.5 needs a standard-route-safe result
  return path rather than relying on files.
- A real provider must preserve prompt shaping:
  `raw_dialog_tail_variant_b`, Japanese-only instruction, and
  `max_output_tokens=32`.
- The real execution path must not reuse DB-backed hidden ChatScreen QAIRT244
  branches.
- Failure reporting must be mapped into `NpuStandardRouteS1RawResult` without
  silently falling back to the fixed success result.
- Any adapter that requires APK/runtime-specific setup must stay behind the gate
  and must not affect gate-off local inference.

## Recommended Next Step

Before implementation, define the main-source provider boundary:

```text
NpuStandardRouteS1RealResultProvider
-> returns NpuStandardRouteS1RawResult
-> has no ChatScreen, DB, TTS, Markdown, streaming, or Backend persistence dependency
```

Then implement S1.5 in two commits:

1. Provider contract and tests with a fake real-result provider.
2. Real QAIRT244 adapter wiring behind the existing S1 gate after source-set
   ownership is resolved.
