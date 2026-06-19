# LiteRT QAIRT244 Prompt Template Matrix Result

This records an already-run dev-only matrix result. It does not implement code,
run runtime checks, install APKs, change native/decode behavior, change the
production prompt template, or promote the route.

## Run Context

- execution commit: `1d13eece`
- result artifact:
  `artifacts/dev_only_npu_prompt_template_matrix_result_v3.txt`
- matrix runner: dev-only NPU prompt template matrix
- template filter: `raw_only`
- active templates:
  - `raw_dialog_tail_variant_b`
  - `raw_dialog_tail_variant_c`
- filtered templates:
  - `simple_ja_chat`
  - `gemma_it_like`
- template failure threshold: `2`
- production standard route template: unchanged,
  `raw_dialog_tail_variant_b`

The run completed with `status=completed`.

## Result Summary

| template | executed | skipped | summary |
| --- | ---: | ---: | --- |
| `raw_dialog_tail_variant_b` | 5 | 0 | completed all prompts |
| `raw_dialog_tail_variant_c` | 3 | 2 | two consecutive failures, then threshold skip |
| `simple_ja_chat` | 0 | 5 | skipped by `template_filter` |
| `gemma_it_like` | 0 | 5 | skipped by `template_filter` |

## `raw_dialog_tail_variant_b`

`variant_b` completed the full active prompt set.

| prompt | status | reason | sanitized preview | quality |
| --- | --- | --- | --- | --- |
| `こんにちは` | success | success | `こんにちは。` | natural_japanese |
| `おはよう` | success | success | `アシスタント。` | natural_japanese |
| `こんばんは` | failure | empty_after_sanitize | empty | mixed_language |
| `明日の天気は` | success | success | `明日の天気は` | natural_japanese |
| `あなたは誰ですか` | success | success | `私はGoogleによってトレーニングされた大規模言語モデルです。` | mixed_language |

Interpretation:

- `variant_b` remains the most viable baseline because it completed all cases.
- It is not clean enough to treat all `success` rows as high-quality answers.
- `おはよう -> アシスタント。` should be treated as a template-artifact or
  assistant-marker-like response, despite the current classifier marking it
  `natural_japanese`.
- `明日の天気は -> 明日の天気は` is question echo and should be classified as a
  quality failure in future sanitizer/quality work.
- `こんばんは` still exposes the known malformed/mixed-language path, followed
  by `empty_after_sanitize`.

## `raw_dialog_tail_variant_c`

`variant_c` used the no-echo assistant-role candidate:

```text
あなたは日本語だけで短く答えるアシスタントです。
ユーザーの文を繰り返さず、答えだけを1文で書いてください。
ユーザー: <prompt>
アシスタント:
```

Observed rows:

| prompt | status | reason | sanitized preview | quality |
| --- | --- | --- | --- | --- |
| `こんにちは` | success | success | `こんにちは。` | natural_japanese |
| `おはよう` | failure | empty_after_sanitize | empty | mixed_language |
| `こんばんは` | failure | adapter_failure:LiteRtLmJniException | empty | unknown |
| `明日の天気は` | skipped | template_failure_threshold | empty | skipped |
| `あなたは誰ですか` | skipped | template_failure_threshold | empty | skipped |

Interpretation:

- `variant_c` is rejected for the current route.
- The longer explicit role/no-echo instruction did not improve stability.
- It regressed `おはよう` into mixed-language output (`Wak Wak.` in raw
  preview) and then hit an adapter failure on `こんばんは`.
- The threshold behavior worked as intended: after two consecutive failures,
  remaining `variant_c` prompts were skipped rather than continuing risky
  native calls.

## Filtered Templates

`simple_ja_chat` and `gemma_it_like` were not executed in this run.

Each case was recorded as:

```text
template_skipped=true
reason=template_filter
```

This is intentional. The run was scoped to raw-tail comparison because previous
matrix runs showed non-raw templates could stall or fail in ways that obscure
raw-tail quality measurements.

## Decision

Do not adopt `raw_dialog_tail_variant_c`.

Keep `raw_dialog_tail_variant_b` as the current production/default NPU standard
route prompt tail.

Rationale:

- `variant_b` completes the prompt set and still produces the known
  `こんにちは。` baseline.
- `variant_c` fails two consecutive cases and is automatically skipped.
- `variant_c` does not solve the quality problems strongly enough to justify
  replacing a more stable baseline.

## Next Improvement Candidates

Prioritize classifier/sanitizer diagnosis before another prompt-tail change:

1. Detect `question_echo`.
   - `明日の天気は -> 明日の天気は` should not remain a clean success.
2. Classify standalone `アシスタント。` as a failure or template-artifact-like
   output.
3. Treat prompt repetition for incomplete questions as answer-quality failure.
4. Keep malformed or mixed-language output blocked by sanitizer.
5. Design `raw_dialog_tail_variant_d` only after the above failure classes are
   visible in the matrix summary.

`variant_d` should be designed cautiously. It should not mix prompt changes with
decode, native, fallback, DB, Markdown, pseudo streaming, or TTS changes.

## Rollback

No runtime rollback is required because no production template switch happened.

If future code removes `variant_c`, the safe state is:

- keep `DEFAULT_PROMPT_TAIL_VARIANT=raw_dialog_tail_variant_b`;
- keep `RealNpuStandardRouteS1Provider` on `raw_dialog_tail_variant_b`;
- keep matrix `template_filter=raw_only` available for future raw-tail
  experiments;
- do not change native/decode behavior.
