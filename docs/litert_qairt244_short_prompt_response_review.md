# LiteRT QAIRT244 Short Prompt Response Review

This is a static review only. It does not implement code, run runtime checks,
install APKs, change native/decode behavior, change prompt templates, or promote
the route.

## Scope

Reviewed path:

```text
ChatScreen requestPrompt
-> NpuStandardRouteS1Bridge
-> NpuStandardRouteS1Invoker
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationRequest
-> DevOnlyNpuOneTurnConversationEntry
-> Qairt244DevOnlyNpuRouteAdapter
-> Qairt244ShortMultitokenSmoke.nativeRunEditablePrompt(...)
```

Current standard route S1 prompt tail:

```text
必ず日本語だけで短く返答してください。
ユーザー: <user prompt>
アシスタント: はい、
```

The standard route provider keeps `raw_dialog_tail_variant_b`, passes the real
ChatScreen prompt, and passes Settings-backed `max_output_tokens` to the
dev-only one-turn entry. The current Settings default is `128`; selectable
values are `32, 64, 128, 256, 512, 1024, 2048, 4096`.

## 1. Stop Sequence List

No explicit app-configured stop sequence list is currently applied to the NPU
standard route.

Static findings:

- `Qairt244ShortMultitokenSmoke.nativeRunEditablePrompt(...)` accepts prompt,
  prompt input limit mode, and `maxOutputTokens`.
- Existing native/decode notes for this lower-level route state that the JNI
  path creates default decode/session config and exposes
  `DecodeConfig.SetMaxOutputTokens(...)`.
- No per-request stop sequence, stop token, EOS, or `<end_of_turn>` setter is
  exposed through the editable-prompt path currently used by S1.
- `stop_reason` in result artifacts is diagnostic metadata if present. It is
  not evidence that S1 configured an app-level stop sequence.

Conclusion: short output from `こんにちは` should not be attributed to a known
configured stop sequence in the Kotlin S1 path.

## 2. Finish / Stop Reason Availability

The dev-only display and receiver result contracts already preserve these
fields when the native result file exposes them:

- `stop_reason`
- `finish_reason`
- `eos_detected`
- `output_token_count`
- `prompt_token_count`

The same display also records:

- `run_decode_reached`
- `timeout`
- `fresh_crash`
- `raw_len`
- `sanitized_len`
- `quality_classification`

Limitations:

- `finish_reason` and `stop_reason` may be `unknown` or blank for this
  lower-level entrypoint.
- Prior docs note that output token counts can be unavailable because the
  `RunDecode` response is not fully exposed by this route.
- Therefore these fields are useful diagnostics, but the route cannot currently
  rely on them as complete native stop telemetry.

## 3. Why `こんにちは` Ends Short

The most likely static explanation is prompt and decode semantics, not a hard
Kotlin stop list.

Contributing factors:

- The instruction explicitly says `短く返答してください`.
- `raw_dialog_tail_variant_b` seeds the assistant with `はい、`, which biases the
  model toward a short continuation.
- A greeting prompt has a naturally short completion. `こんにちは。` is already a
  complete one-sentence answer.
- `max_output_tokens` is an upper bound, not a minimum generation length.
- There is no app-level stop sequence to force longer output or to differentiate
  "complete but short" from "stopped early".
- Sanitizer does not appear to shorten the known clean `こんにちは` case: previous
  matrix results record `raw/sanitized = こんにちは。` with
  `quality_classification=natural_japanese`.

For `こんにちは`, short output is therefore consistent with the current template:
brief Japanese answer + assistant continuation seed + upper-bound token cap.

## 4. Static Comparison Plan

No runtime comparison was executed in this review. The safe next runtime matrix
should compare these prompts:

```text
こんにちは
こんにちは。あなたは誰ですか？
こんにちは。Pythonについて教えて下さい。
```

For each prompt, record:

- input hash / length / code points / short preview
- request prompt hash / length / code points / short preview
- `max_output_tokens`
- `final_input_code_points`
- `run_decode_reached`
- `timeout`
- `fresh_crash`
- `fallback_used`
- raw output hash / length / code points / short preview
- sanitized output hash / length / code points / short preview
- `quality_classification`
- `status`
- `reason`
- `stop_reason`
- `finish_reason`
- `eos_detected`
- `output_token_count`
- elapsed / prefill / decode timing if available

Expected interpretation:

| prompt | expected diagnostic value |
| --- | --- |
| `こんにちは` | Tests whether a pure greeting remains a complete short answer. |
| `こんにちは。あなたは誰ですか？` | Tests whether adding a concrete question lengthens output and avoids greeting-only completion. |
| `こんにちは。Pythonについて教えて下さい。` | Tests whether task content produces a longer answer under the same template. |

If only `こんにちは` is short, the current behavior is likely prompt/task
semantics. If all three are short at every token cap, the next suspect is the
raw prompt shape, assistant seed, model-native EOS behavior, or decode defaults.

## 5. `max_output_tokens` 32 / 128 / 512

Settings now allows the route to request larger caps, and the provider passes
the sanitized value to `DevOnlyNpuOneTurnConversationRequest`.

Recommended comparison:

| max_output_tokens | role in comparison |
| ---: | --- |
| `32` | Known short baseline and lower risk. |
| `128` | Current Settings default; should be the main standard-route baseline. |
| `512` | Diagnostic only; previous docs show 512 can reach `SetMaxOutputTokens(512)` but also has timeout/process-death risk in some flows. |

Interpretation:

- If `こんにちは` stays `こんにちは。` for 32/128/512, the cap is not the limiting
  factor for greeting length.
- If the longer prompts expand at 128/512 but not 32, then max output is a
  meaningful quality knob for non-greeting prompts.
- If 512 causes timeout, process disappearance, or native non-return, keep 512
  as a diagnostic value rather than a default.
- If all caps produce echo or assistant-marker artifacts, prioritize prompt
  template or sanitizer classification rather than raising max tokens.

## 6. Temperature / Top-K / Top-P

`LocalStreamingRunner` has a separate reflected sampler path with values:

```text
topK=10
topP=0.95
temperature=0.8
```

That path is not the current qairt244 lower-level S1 NPU path.

For `RealNpuStandardRouteS1Provider` -> `DevOnlyNpuOneTurnConversationEntry` ->
`Qairt244ShortMultitokenSmoke.nativeRunEditablePrompt(...)`, the Kotlin/native
boundary passes:

- model path and native dirs
- prompt
- prompt input limit mode
- `maxOutputTokens`

It does not pass temperature, top-k, top-p, seed, repetition penalty, or stop
sequence. Existing static notes also state that public sampler controls exist in
LiteRT-LM, but the qairt244 lower-level editable-prompt entrypoint currently
does not accept a sampler config.

Conclusion: short greeting output is not currently adjustable from the S1 Kotlin
route through temperature/top-k/top-p.

## Current Hypothesis

The short response for `こんにちは` is most likely expected behavior under
`raw_dialog_tail_variant_b`:

1. The prompt asks for a short Japanese reply.
2. The assistant line starts with a continuation seed.
3. A greeting has a naturally short complete answer.
4. `max_output_tokens` only caps the upper bound.
5. Stop/sampler controls are not exposed by this S1 entrypoint.

This does not explain malformed cases such as `こんばんは`; those remain quality
or model-output failures and should continue to be handled by sanitizer,
quality classification, and safe fallback rather than by accepting mixed output.

## Recommended Next Step

Add a dev-only short-prompt response matrix, but do not change production
template/native/decode yet.

Matrix axes:

- prompts:
  - `こんにちは`
  - `こんにちは。あなたは誰ですか？`
  - `こんにちは。Pythonについて教えて下さい。`
- `max_output_tokens`:
  - `32`
  - `128`
  - `512`

Pass condition for this investigation:

- each case records prompt/request/output hashes, lengths, code points, previews;
- each case records status, reason, quality, stop/finish/eos fields when
  available;
- failures remain diagnostic and do not change DB/TTS/S4-A behavior.

Rollback is trivial because this review introduces no code changes. If a future
matrix runner is added, keep it dev-only and leave `raw_dialog_tail_variant_b`
as the standard route template until the comparison shows a safer replacement.
