# QAIRT244 Real Prompt Integration Review

Date: 2026-05-31

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, or change Settings/gate behavior.

## Current State

The NPU standard route now reaches the real NPU through the S1 path:

```text
ChatScreen Local send
-> NpuStandardRouteS1Bridge(mode)
-> NpuStandardRouteS1Invoker(mode)
-> NpuStandardRouteS1ProviderSelector.defaultProviderForMode(mode)
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationEntry
-> Qairt244DevOnlyNpuRouteAdapter.runDevOnlyConversationOnce(...)
```

The Settings selector controls which S1-S5 phases are enabled, but the prompt
used by the real NPU provider is still fixed.

## 1. Fixed Response And Fixed Prompt Locations

There are two separate fixed paths:

| Location | Current behavior | Notes |
| --- | --- | --- |
| `FixedNpuStandardRouteS1Provider` | returns fixed `rawOutput` and `sanitizedOutput` = `こんにちは。` | Used as a safe non-real provider fallback. This is intentionally fixed and should remain separate from real prompt work. |
| `RealNpuStandardRouteS1Provider` | creates `DevOnlyNpuOneTurnConversationRequest(userPrompt = DEFAULT_USER_PROMPT)` | `DEFAULT_USER_PROMPT` is `こんにちは`, so the real NPU path effectively uses a fixed prompt. The output has been `こんにちは。` in the successful runtime checks. |

The fixed output observed in the UI is therefore not caused by
`RealNpuStandardRouteS1ResultMapper`; that mapper only converts
`DevOnlyNpuOneTurnConversationDisplay` into `NpuStandardRouteS1RawResult`.

## 2. Where The User Prompt Is Dropped

`ChatScreen` already captures the typed prompt:

```text
val requestPrompt = userPrompt
```

and uses it for S2 DB save candidates:

```text
NpuStandardRouteS2DbBridge().prepareSaveCandidate(
  userPrompt = requestPrompt,
  s1Result = s1Result,
)
```

However, the S1 real NPU invocation is currently:

```text
NpuStandardRouteS1Bridge(npuStandardRouteMode).run()
```

The prompt is dropped because:

- `NpuStandardRouteS1Bridge.run()` accepts no prompt;
- `NpuStandardRouteS1Invoker.invoke()` accepts no prompt;
- `NpuStandardRouteS1Provider.invoke()` accepts no prompt;
- `RealNpuStandardRouteS1Provider` constructs the dev-only request with
  `DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT`.

S2 can save the actual user text, but S1 does not send that text to NPU yet.
This can produce a misleading conversation: the stored user row may contain the
real user prompt while the assistant response was generated from fixed
`こんにちは`.

## 3. Minimal Change Proposal

Keep the current proven prompt shaping and pass only the user prompt through
the existing S1 abstraction.

Recommended API changes:

```kotlin
internal fun interface NpuStandardRouteS1Provider {
    fun invoke(userPrompt: String): NpuStandardRouteS1RawResult
}
```

```kotlin
internal class NpuStandardRouteS1Invoker(...) {
    fun invoke(userPrompt: String): NpuStandardRouteS1RawResult =
        provider.invoke(userPrompt)
}
```

```kotlin
internal class NpuStandardRouteS1Bridge(...) {
    fun run(userPrompt: String): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(invoker.invoke(userPrompt))
}
```

`ChatScreen` then changes only the S1 call:

```kotlin
val s1Result = NpuStandardRouteS1Bridge(npuStandardRouteMode)
    .run(userPrompt = requestPrompt)
```

`RealNpuStandardRouteS1Provider` should pass that prompt into:

```kotlin
DevOnlyNpuOneTurnConversationRequest(
  userPrompt = userPrompt,
  contextText = "",
  unsafeDevBypassPromptLengthGate = true,
  maxOutputTokens = 32,
  promptTailVariant = raw_dialog_tail_variant_b,
)
```

`FixedNpuStandardRouteS1Provider` may ignore the prompt and keep returning the
fixed safe result. `FailureNpuStandardRouteS1Provider` may also ignore the
prompt and preserve its current failure behavior.

Do not connect:

- DB behavior beyond the already-gated S2 path;
- Markdown behavior beyond S3;
- pseudo streaming beyond S4-A;
- TTS beyond S5;
- `Backend.NPU` persistence;
- legacy QAIRT route.

## 4. Template Handling

The first real-prompt implementation should keep the current proven dev-only
conversation shape:

```text
prompt_tail_variant=raw_dialog_tail_variant_b
max_output_tokens=32
app-facing template mode=raw
```

The final prompt remains:

```text
必ず日本語だけで短く返答してください。
ユーザー: <actual user prompt>
アシスタント: はい、
```

Do not add Settings-level template selection in the first pass.

`raw`, `simple_ja_chat`, and `gemma_it_like` should be treated as separate
prompt-template experiments:

- `raw_dialog_tail_variant_b` is the proven standard-route prompt shape;
- `simple_ja_chat` and `gemma_it_like` previously introduced prompt expansion,
  echo/sanitizer risk, and validation failures;
- adding them to the standard route before real-prompt parity would make the
  failure surface ambiguous.

If template comparison is needed later, add it behind a separate developer-only
diagnostic, not as part of the first user-prompt connection.

## 5. Editable Prompt Rejection Recheck

Earlier editable-prompt runs hit multiple length gates:

```text
editable prompt rejected before native execution: reasonCode=too_long
native_diag prompt_validation reason=too_long
```

The current dev-only conversation path is different from the older hidden
editable prompt route:

- prompt source is `dev_only_conversation`;
- app-facing template mode remains `raw`;
- `unsafeDevBypassPromptLengthGate=true` is already used by the provider;
- `Qairt244DevOnlyNpuRouteAdapter.runDevOnlyConversationOnce(...)` validates
  with `validateUtf8HiddenTemplateExperiment(...)` and applies the explicit
  dev-only length-gate bypass;
- max output remains `32`.

Real prompt integration must still preserve these checks:

- empty prompt is rejected by ChatScreen before S1;
- image input is rejected before S1;
- non-length validation failures must remain failures;
- fallback, timeout, fresh crash, missing NPU evidence, and empty sanitized
  output remain S1 failures;
- the result contract should expose the prompt length or a prompt preview in a
  later diagnostic pass if runtime failures are hard to classify.

The first real-prompt runtime should use a short Japanese prompt to avoid
confusing prompt handoff with long-prefill behavior.

## 6. Rollback

Primary rollback:

```text
NPU標準ルート Settings mode -> OFF
```

Code rollback:

- restore `NpuStandardRouteS1Bridge.run()` without a prompt parameter;
- restore `NpuStandardRouteS1Invoker.invoke()` without a prompt parameter;
- restore `NpuStandardRouteS1Provider.invoke()` without a prompt parameter;
- restore `RealNpuStandardRouteS1Provider` to `DEFAULT_USER_PROMPT`.

Runtime rollback does not require:

- DB migration;
- TTS cleanup;
- Markdown cleanup;
- streaming cleanup;
- backend preference cleanup;
- native changes.

## 7. Test Plan

Pure/unit tests before runtime:

- `ChatScreen` S1 mapping passes `requestPrompt` into the Bridge;
- `NpuStandardRouteS1Bridge` forwards `userPrompt` to Invoker;
- `NpuStandardRouteS1Invoker` forwards `userPrompt` to Provider;
- `RealNpuStandardRouteS1Provider` constructs
  `DevOnlyNpuOneTurnConversationRequest(userPrompt=<actual prompt>)`;
- `FixedNpuStandardRouteS1Provider` remains stable and can ignore prompt;
- failure provider remains stable and can ignore prompt;
- blank prompt still never enters S1;
- S2 save candidate user text matches the prompt sent to S1;
- mode mapping and legacy hard gate tests remain unchanged.

Recommended runtime confirmation after implementation:

1. Build and install `standardDebug`.
2. Enable developer access.
3. Set `NPU標準ルート` to `S1_ONLY`.
4. Send a short non-default prompt, for example:

   ```text
   好きな色を一つだけ答えてください
   ```

5. Confirm that the NPU result is not the fixed `こんにちは。` response.
6. Check `qairt244_short_multitoken_smoke_result.txt` for:

   ```text
   prompt_source=dev_only_conversation
   requested/effective/max_output_tokens=32
   run_decode_reached=true
   npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
   fallback_used=false
   timeout=false
   fresh_crash=false
   ```

7. Repeat with `S2_DB` only after S1 confirms real prompt handoff.

Stop conditions:

- `invalid_prompt:too_long`;
- `native_result:invalid_prompt`;
- fallback used;
- timeout or fresh crash;
- output still exactly follows the old fixed greeting across unrelated prompts;
- DB save shows a different prompt than the one sent to S1.

## Recommended Next Implementation

Implement only S1 prompt plumbing first:

```text
ChatScreen requestPrompt
-> NpuStandardRouteS1Bridge.run(userPrompt)
-> NpuStandardRouteS1Invoker.invoke(userPrompt)
-> NpuStandardRouteS1Provider.invoke(userPrompt)
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationRequest(userPrompt)
```

Keep `raw_dialog_tail_variant_b`, `max_output_tokens=32`, Settings mode mapping,
S2-S5 gates, and legacy QAIRT hard gate unchanged.
