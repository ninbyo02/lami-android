# QAIRT244 Real Provider Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, or change native code.

## Current State

`customBuildExperimentDebug` now has a `RealNpuStandardRouteS1Provider`
scaffold:

```text
RealNpuStandardRouteS1Provider.invoke()
-> FailureNpuStandardRouteS1Provider(reason=real_provider_not_implemented)
```

Provider selection is already variant-aware:

```text
standardDebug
-> FixedNpuStandardRouteS1Provider

customBuildExperimentDebug
-> RealNpuStandardRouteS1Provider scaffold
-> reason=real_provider_not_implemented
```

`ChatScreen` and `NpuStandardRouteS1Bridge` remain unchanged. `main` uses only
the main-source `NpuStandardRouteS1Provider` interface and provider selector.

## 1. RealProvider Call Target

The first real connection target should be the dev-only one-turn conversation
entry point:

```text
DevOnlyNpuOneTurnConversationEntry
```

The real provider should call the same proven conceptual path used by the
dev-only NPU conversation tests:

```text
DevOnlyNpuOneTurnConversationRequest(
  userPrompt = "こんにちは",
  contextText = "",
  unsafeDevBypassPromptLengthGate = true,
  maxOutputTokens = 32,
  promptTailVariant = raw_dialog_tail_variant_b,
)
```

It must not call the DB-backed hidden ChatScreen route. It must not call
`runWithHeldEngine`, streaming helpers, Markdown helpers, TTS, backend
preference persistence, or conversation history save.

## 2. Main Must Not Reference Debug-Only Code

The guarantee remains:

- `app/src/main` may reference only `NpuStandardRouteS1Provider`,
  `NpuStandardRouteS1ProviderSelector`, `FixedNpuStandardRouteS1Provider`, and
  `FailureNpuStandardRouteS1Provider`;
- `app/src/main` must not import `DevOnlyNpuOneTurnConversationEntry`;
- `app/src/main` must not import `Qairt244DevOnlyNpuRouteAdapter`;
- `app/src/main` must not import `RealNpuStandardRouteS1Provider`;
- debug/custom source sets may implement the main provider interface.

Static check before commit:

```text
rg "DevOnlyNpuOneTurnConversationEntry|Qairt244DevOnlyNpuRouteAdapter|RealNpuStandardRouteS1Provider" app/src/main
```

Expected result: only string-based selector references are allowed if already
documented; no direct imports or typed references to debug-only implementations.

## 3. customBuildExperimentDebug Scope

The real connection should happen only in:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt
```

This source set has the intended custom LiteRT-LM/native experiment context. It
also keeps `standardDebug` on the fixed provider path.

Expected variant behavior after connection:

```text
standardDebug:
  NpuStandardRouteS1ProviderSelector.defaultProvider()
  -> FixedNpuStandardRouteS1Provider

customBuildExperimentDebug:
  NpuStandardRouteS1ProviderSelector.defaultProvider()
  -> RealNpuStandardRouteS1Provider
  -> DevOnlyNpuOneTurnConversationEntry
```

## 4. Mapping To NpuStandardRouteS1RawResult

The real provider should return `NpuStandardRouteS1RawResult`, not a display
string. Mapping should be explicit.

Suggested mapping:

| S1 raw result field | Dev-only source |
| --- | --- |
| `status` | `display.status` or result success classification |
| `result` | same as `status` |
| `success` | `display.status == "success"` |
| `reason` | `display.reason` |
| `rawOutput` | `raw_output_first_200_chars` only if full raw is unavailable, otherwise raw output |
| `sanitizedOutput` | `display.output` |
| `qualityClassification` | `display.quality` |
| `runDecodeReached` | `display.decodeReached` |
| `npuBackendEvidence` | `display.npuEvidence` |
| `fallbackUsed` | `display.fallback` |
| `timeout` | `display.timeout` |
| `freshCrash` | `display.freshCrash` |
| `requestedMaxOutputTokens` | `display.requestedMaxOutputTokens` |
| `effectiveMaxOutputTokens` | `display.effectiveMaxOutputTokens` |

Required normalized S1.5 success values:

```text
status=success
result=success
success=true
runDecodeReached=true
npuBackendEvidence=QNN_HTP_V79_FastRPC_native_diag
fallbackUsed=false
timeout=false
freshCrash=false
sanitizedOutput is non-empty
qualityClassification=natural_japanese
requested/effective max_output_tokens=32
```

If any required value is missing, the provider should return failure rather than
fixed success.

## 5. Rollback Method

Primary rollback:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=false
```

Provider rollback:

```text
RealNpuStandardRouteS1Provider.invoke()
-> FailureNpuStandardRouteS1Provider(reason=real_provider_not_implemented)
```

Variant rollback:

```text
NpuStandardRouteS1ProviderSelector.defaultProvider()
customBuildExperimentDebug -> FailureNpuStandardRouteS1Provider(reason=...)
```

Rollback must not require:

- DB cleanup;
- TTS cleanup;
- Markdown cleanup;
- streaming placeholder cleanup;
- backend setting cleanup;
- native changes;
- conversation history migration.

## 6. Failure Handling

Failure must be explicit and visible through `NpuStandardRouteS1RawResult`.

Recommended failure reasons:

- `real_provider_not_implemented`
- `dev_only_entry_unavailable`
- `dev_only_request_failed`
- `npu_decode_not_reached`
- `npu_evidence_missing`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `empty_sanitized_output`
- `quality_not_natural_japanese`
- `max_output_tokens_mismatch`

Failure behavior:

- return `status=failure`;
- return `success=false`;
- preserve diagnostics when available;
- keep side-effect flags false through the mapper;
- do not fall through to the normal local route;
- do not silently return fixed success.

## 7. Removing `real_provider_not_implemented`

Do not remove `real_provider_not_implemented` until all of these are true:

- `RealNpuStandardRouteS1Provider` calls `DevOnlyNpuOneTurnConversationEntry`;
- result mapping to `NpuStandardRouteS1RawResult` is covered by
  `testCustomBuildExperimentDebug` unit tests;
- failure paths are explicit;
- `compileStandardDebugKotlin` still proves `standardDebug` does not need the
  real provider;
- `compileCustomBuildExperimentDebugKotlin` passes;
- no runtime probe or APK install is required for the implementation commit;
- a later explicit runtime confirmation plan exists.

After removal, a different failure reason should still exist for unavailable
dev-only entry or invalid result states.

## Connection-Time Change File List

The next implementation should be limited to these files:

- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1Provider.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1ProviderTest.kt`

Only if an explicit mapper helper is needed, add one custom source-set file:

- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/RealNpuStandardRouteS1ResultMapper.kt`

Files that should not change for the first connection:

- `ChatScreen.kt`
- `NpuStandardRouteS1Bridge.kt`
- `NpuStandardRouteS1Invoker.kt`
- `NpuStandardRouteS1ProviderSelector.kt`
- DB/ViewModel/repository files
- TTS files
- Markdown files
- streaming files
- native/JNI files

This keeps the first real-provider connection scoped to the custom debug
provider and its tests.
