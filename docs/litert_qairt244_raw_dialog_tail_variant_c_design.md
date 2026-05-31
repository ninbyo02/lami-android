# LiteRT QAIRT244 Raw Dialog Tail Variant C Design

This is a design review only. It does not implement code changes, run runtime
checks, install APKs, change native code, change decode behavior, change the
production prompt template, or promote the route.

## Purpose

The NPU standard route currently uses `raw_dialog_tail_variant_b` through the
real-prompt path:

```text
ChatScreen requestPrompt
-> NpuStandardRouteS1Bridge
-> NpuStandardRouteS1Invoker
-> RealNpuStandardRouteS1Provider
-> DevOnlyNpuOneTurnConversationEntry
```

The route reaches native decode and returns prompt-dependent output, but recent
observations show quality problems:

| input | observed behavior |
| --- | --- |
| `こんにちは` | `こんにちは。` |
| `明日の天気は` | `明日の天気は` prompt echo |
| `こんばんは` | malformed or `mixed_language`, then `empty_after_sanitize` |

`raw_dialog_tail_variant_c` should be designed as a developer-only comparison
candidate. It must not replace the production S1 template until the matrix
proves that it improves quality without regressing native decode reachability.

## 1. Current `raw_dialog_tail_variant_b` Input

`raw_dialog_tail_variant_b` keeps app-facing template mode `raw` and builds this
plain text prompt:

```text
必ず日本語だけで短く返答してください。
ユーザー: <user prompt>
アシスタント: はい、
```

Current fixed axes:

- app-facing template mode: `raw`;
- `promptTailVariant=raw_dialog_tail_variant_b`;
- `contextText=""` for the standard route provider;
- real `ChatScreen` user prompt is passed through to the dev-only entry;
- `max_output_tokens=32`;
- prompt transport remains base64;
- no DB/TTS/Markdown/streaming requirement for S1;
- no native/decode changes.

The `はい、` assistant prefix was introduced to prevent non-Japanese drift seen
with weaker tails. It stabilized simple greeting output, but it can also bias
the model toward short continuation or echo instead of a real answer.

## 2. Why `明日の天気は -> 明日の天気は` Can Happen

The observed `明日の天気は` output is best classified as question echo or raw
prompt continuation.

Likely causes:

- The model receives a raw text transcript, not structured chat roles.
- `ユーザー:` and `アシスタント:` are visible text markers, so the model may
  continue or copy the transcript rather than obey a role boundary.
- `明日の天気は` is an incomplete phrase. Continuing it by repeating or echoing
  the same span is an easy local continuation.
- The current instruction says to answer briefly in Japanese, but it does not
  explicitly say to avoid repeating the user input.
- The assistant prefix `はい、` may be too weak to force an answer segment for
  question-like prompts.

This should remain a quality failure. It should not be fixed by accepting prompt
echo as a successful answer.

## 3. Why `ユーザー:` / `アシスタント:` Remnants Can Appear

Role-marker remnants can appear because the prompt is raw text:

- The markers are part of the byte sequence passed to the model.
- There is no model-native stop boundary after `アシスタント:`.
- If generation crosses into a new turn shape, it can emit `ユーザー:` or
  `アシスタント:` as ordinary text.
- The sanitizer can remove known remnants, but excessive cleanup risks hiding a
  real template-artifact failure.

The comparison should therefore record both raw output and sanitized output
summaries:

- raw output hash/length/code points/preview;
- sanitized output hash/length/code points/preview;
- removed prompt echo;
- removed template token count;
- quality classification.

## 4. `raw_dialog_tail_variant_c` Candidate

The first candidate should remain in raw mode and avoid changing decode or
native behavior.

Recommended candidate:

```text
あなたは日本語だけで短く答えるアシスタントです。
ユーザーの文を繰り返さず、答えだけを1文で書いてください。
ユーザー: <user prompt>
アシスタント:
```

Design intent:

- make the assistant role explicit before the transcript;
- add a no-echo instruction for incomplete questions;
- remove the `はい、` prefix to avoid over-biasing the first generated tokens;
- keep the assistant start marker at the end so generation begins in the answer
  slot;
- keep the output short without depending on a hard stop sequence.

Alternative candidate if the no-prefix form drifts:

```text
あなたは日本語だけで短く答えるアシスタントです。
ユーザーの文を繰り返さず、答えだけを1文で書いてください。
ユーザー: <user prompt>
アシスタント: 承知しました。
```

This alternative adds a Japanese seed, but it risks producing generic
acknowledgement rather than answering. It should be a later variant, not the
first `variant_c`.

Rejected for first comparison:

- switching to `simple_ja_chat` or `gemma_it_like`, because those compare
  template families rather than a raw-tail variant;
- adding stop sequence changes, because that mixes prompt-shaping and decode
  behavior;
- making sanitizer more permissive, because malformed or mixed-language output
  should remain visible as a generation-quality failure.

## 5. Stop Sequence And Boundary Considerations

`variant_c` should be designed with stop behavior in mind but should not require
new stop settings for the first comparison.

Expected safe behavior:

- If output is a short answer and does not contain a new `ユーザー:` line, it can
  pass.
- If output emits `ユーザー:` / `アシスタント:` remnants, classify it as
  `template_artifact`.
- If output repeats the prompt exactly or mostly, classify it as `question_echo`
  or answer-quality failure.
- If output is malformed or mixed-language, keep `mixed_language` or
  `empty_after_sanitize`.

Do not add a decode-level stop sequence until prompt-only variants have been
measured. A stop change would make it unclear whether the improvement came from
the prompt or the decoder boundary.

## 6. Comparison Method

The first matrix should compare only raw-tail variants:

| candidate | role |
| --- | --- |
| `raw_dialog_tail_variant_b` | current baseline |
| `raw_dialog_tail_variant_c` | no-echo assistant-role candidate |

Recommended prompts:

| prompt | purpose |
| --- | --- |
| `こんにちは` | known greeting baseline |
| `おはよう` | greeting variant |
| `こんばんは` | known malformed/mixed-language risk |
| `明日の天気は` | question echo probe |
| `あなたは誰ですか` | assistant-role probe |

Run these through the dev-only prompt template matrix with a raw-only filter.
Do not include `simple_ja_chat` or `gemma_it_like` in the first variant-C run;
they have previously stalled or failed in ways that obscure raw-tail behavior.

Fixed axes:

- same model artifact;
- same app build variant;
- same device/backend;
- same `max_output_tokens=32`;
- same prompt transport;
- no fallback policy changes;
- no native/decode changes;
- no production ChatScreen template switch.

## 7. Evaluation Metrics

Primary metrics:

- `natural_japanese` rate;
- `mixed_language` rate;
- `question_echo` rate;
- `empty_after_sanitize` rate.

Secondary metrics:

- native decode reach rate;
- timeout rate;
- fresh crash rate;
- fallback usage;
- template artifact rate;
- prompt length/code-point growth.

Per-row fields:

- `template_name`;
- `input_prompt_hash`, length, code points, preview;
- `request_prompt_hash`, length, code points, preview;
- `status`;
- `reason`;
- `run_decode_reached`;
- `fallback_used`;
- `timeout`;
- `fresh_crash`;
- `raw_output_hash`, length, code points, preview;
- `sanitized_output_hash`, length, code points, preview;
- `quality_classification`;
- `removed_prompt_echo`;
- `removed_template_token_count`;
- `elapsed_ms`.

Pass guidance:

- pass: native decode reached, no fallback, no timeout, non-empty sanitized
  output, `natural_japanese`, no prompt echo;
- soft fail: natural Japanese but echoes the prompt or emits a generic
  acknowledgement only;
- fail: `mixed_language`, malformed Unicode, `empty_after_sanitize`, template
  artifacts, timeout, crash, or prompt rejection.

## 8. Decision Rule

Promote `variant_c` only as a candidate, not production default, if it improves:

- `明日の天気は` no longer echoes the input;
- `こんばんは` no longer becomes malformed or `mixed_language`;
- greeting outputs remain natural Japanese;
- no new `ユーザー:` / `アシスタント:` remnants appear;
- native decode reachability remains unchanged.

If `variant_c` improves questions but regresses greetings, keep
`raw_dialog_tail_variant_b` as the production baseline and design a later
variant. If both raw variants fail, return to the broader template experiment
plan for `simple_ja_chat` / `gemma_it_like`, but keep those behind dev-only
diagnostics.

## 9. Rollback

Runtime rollback:

- keep the NPU standard route Settings mode `OFF`; or
- keep standard route S1 on the current `raw_dialog_tail_variant_b`.

Code rollback for a future implementation:

- remove `raw_dialog_tail_variant_c` from the dev-only matrix;
- keep `DEFAULT_PROMPT_TAIL_VARIANT=raw_dialog_tail_variant_b`;
- keep `RealNpuStandardRouteS1Provider` on `raw_dialog_tail_variant_b`;
- leave sanitizer, decode, native, DB, Markdown, pseudo streaming, and TTS
  unchanged.

No rollback should require DB migration, native artifact changes, or prompt
decode setting changes.
