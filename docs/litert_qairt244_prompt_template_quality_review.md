# LiteRT QAIRT244 Prompt Template Quality Review

This is a design review only. It does not implement code changes, run runtime
checks, install APKs, change native code, change decode behavior, or promote the
route.

## Scope

Target route:

- NPU standard route S1 via `RealNpuStandardRouteS1Provider`;
- dev-only one-turn execution through `DevOnlyNpuOneTurnConversationEntry`;
- prompt tail variant `raw_dialog_tail_variant_b`;
- app-facing template mode remains `raw`;
- `max_output_tokens=32`;
- no fallback policy change.

Observed real-prompt quality:

| input | observed output / classification |
| --- | --- |
| `こんにちは` | `こんにちは。` |
| `明日の天気は` | `明日の天気は` |
| `こんばんは` | `mixed_language` |

The route is no longer a fixed-response path. The output changes by input, but
the current prompt shape is not robust enough for all short Japanese prompts.

## 1. Current `raw_dialog_tail_variant_b` Structure

`raw_dialog_tail_variant_b` builds a raw text prompt, not a model-native chat
template:

```text
必ず日本語だけで短く返答してください。
ユーザー: <user prompt>
アシスタント: はい、
```

Important properties:

- `contextText` is blank for the standard S1 provider.
- `userPrompt` comes from `ChatScreen` and is passed through
  `S1Bridge -> S1Invoker -> S1Provider -> DevOnlyNpuOneTurnConversationRequest`.
- `promptTailVariant=raw_dialog_tail_variant_b` is fixed in the real provider.
- The native/app-facing template mode is still `raw`.
- `アシスタント: はい、` is only a textual continuation seed. It is not a
  structured assistant-role boundary.

This tail was introduced because weaker tails avoided some empty-output cases
but could drift into Hindi or Korean. The `はい、` seed improved the greeting
case and produced stable `こんにちは。` results, but the latest real-prompt
tests show it can also produce echo or mixed-language failures.

## 2. Why Question Repetition Can Happen

`明日の天気は -> 明日の天気は` is best treated as prompt continuation or echo,
not a valid answer. Likely causes:

- The model sees a raw transcript and may continue the visible text rather than
  obeying a hidden chat-role structure.
- `ユーザー:` and `アシスタント:` are natural text markers, not enforced roles.
- The phrase `明日の天気は` is incomplete and easy to continue by repeating the
  same phrase.
- The current tail asks for a short Japanese answer but does not explicitly say
  "do not repeat the user's text."
- `max_output_tokens=32` limits length but does not prevent the first generated
  tokens from being an echo.

This should remain a quality failure or warning until a safer answer segment can
be extracted. It should not be fixed by relaxing the sanitizer to accept prompt
echo as an answer.

## 3. Assistant Role Recognition

The model appears to recognize the assistant role only partially.

Evidence for partial recognition:

- `こんにちは` produces the natural assistant-like response `こんにちは。`.
- The `はい、` seed helped stabilize earlier dev-only runs.
- The route reaches NPU decode and returns prompt-dependent output.

Evidence against robust role recognition:

- `明日の天気は` repeats the user text instead of answering.
- `こんばんは` can produce `mixed_language` rather than a greeting.
- Previous raw-output reviews recorded possible `ユーザー:` / `アシスタント:`
  remnants and continuation artifacts.

Conclusion: `raw_dialog_tail_variant_b` is a useful baseline, but it is a
heuristic prompt shape. It should not be treated as equivalent to a validated
Gemma instruction template.

## 4. Why `simple_ja_chat` Previously Failed

Earlier hidden-route comparisons recorded two distinct phases.

Initial same-prompt comparison for `こんにちは`:

- `simple_ja_chat` expanded the final input to 38 code points.
- The route hit the Java-side editable prompt guard before native execution.
- Result was `adapter_failure:IllegalStateException` with
  `reasonCode=too_long`.

Later, under the 128-input hidden-template experiment:

- `simple_ja_chat` reached native decode.
- It improved assistant-like behavior compared with raw.
- Output quality was still not clean: prior docs recorded mixed non-Japanese
  content even when the classifier reported `natural_japanese`.

So `simple_ja_chat` is not rejected forever, but it is not a safe drop-in for
the standard route. It changes both prompt length and output artifact behavior.

## 5. Why `gemma_it_like` Previously Failed

`gemma_it_like` also had two phases.

Initial same-prompt comparison:

- The template expanded `こんにちは` to 60 code points.
- It failed before native decode under the old 32-code-point prompt guard.
- Result was `adapter_failure:IllegalStateException` / `too_long`.

Under the later 128-input hidden-template experiment:

- It reached native decode.
- It produced a shorter useful Japanese answer.
- The run was classified as `template_artifact` because model turn markers
  leaked into output.

This suggests Gemma-style formatting may be directionally relevant for the
model, but the current integration does not yet have a clean stop/boundary
strategy for those markers.

## 6. Room For Improvement While Keeping `raw`

The safest next work can stay in raw mode and compare prompt tails only.

Candidate raw-only improvements:

1. Add an explicit no-echo instruction:

```text
必ず日本語だけで短く返答してください。ユーザーの文を繰り返さないでください。
ユーザー: <prompt>
アシスタント: はい、
```

2. Use an answer-label shape instead of a transcript role:

```text
必ず日本語だけで短く返答してください。質問には答えだけを書いてください。
入力: <prompt>
答え:
```

3. Keep the assistant role but weaken the seed:

```text
必ず日本語だけで短く返答してください。ユーザーの文を繰り返さないでください。
ユーザー: <prompt>
アシスタント:
```

4. Keep the current seed but add a short-answer constraint:

```text
必ず日本語だけで1文だけ返答してください。ユーザーの文を繰り返さないでください。
ユーザー: <prompt>
アシスタント: はい、
```

Do not compare these while also changing sanitizer, decode, max output tokens,
or native behavior. The next useful comparison is a small dev-only matrix with
the same inputs:

- `こんにちは`
- `こんばんは`
- `明日の天気は`
- `ありがとう`
- `q`
- `え`

Each row should record prompt hash/length, raw output hash/length, sanitized
output hash/length, quality classification, reason, NPU evidence, timeout, and
fallback.

## 7. Prompt Template Improvement Plan

Recommended sequence:

1. Keep `raw_dialog_tail_variant_b` as the baseline.
2. Add a dev-only raw tail variant with explicit no-echo wording.
3. Compare it against `raw_dialog_tail_variant_b` on the short prompt matrix.
4. If no-echo raw tail improves `明日の天気は` without regressing greetings, keep
   it as a candidate standard-route tail.
5. If raw variants still fail, re-evaluate `simple_ja_chat` and `gemma_it_like`
   only as developer-only template experiments.
6. Treat Gemma-style templates as unsafe for standard route until turn-marker
   leakage and stop-boundary behavior are controlled.

Decision rules:

- Natural Japanese answer, no prompt echo -> pass candidate.
- Prompt echo only -> quality fail, not S1 success if the sanitized text is just
  the user's phrase.
- Mixed language or malformed Unicode -> keep failure or fallback display, not
  DB/TTS success.
- Template markers in output -> template artifact; do not promote.

## 8. Rollback

Runtime rollback:

- set NPU standard route Settings mode to `OFF`;
- or set it back to `S1_ONLY` with the current baseline tail if later variants
  are added behind developer-only selection.

Code rollback:

- keep `raw_dialog_tail_variant_b` as the default prompt tail;
- remove or disable any new prompt-tail variant;
- keep sanitizer strict for malformed `mixed_language`;
- keep `simple_ja_chat` and `gemma_it_like` out of standard route selection.

Rollback does not require:

- DB migration;
- TTS cleanup;
- native artifact change;
- decode setting change;
- `Backend.NPU` persistence change.

## Current Recommendation

Prompt template improvement is likely needed, but the next change should be a
dev-only raw-tail comparison, not a switch to `simple_ja_chat` or
`gemma_it_like`.

The highest-signal candidate is a raw variant that keeps the current Japanese
short-answer instruction but adds an explicit no-echo constraint. This directly
targets `明日の天気は -> 明日の天気は` while preserving the raw transport and the
known NPU decode path.
