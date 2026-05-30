# QAIRT244 Prompt Tail Quality Design

Date: 2026-05-29

Scope: dev-only design memo only. This does not implement standard ChatScreen
NPU routing, does not run probes, does not build or install an APK, and does
not connect DB, TTS, Markdown, streaming, or persisted backend selection.

Related dev-only conversation plan:
[`docs/litert_qairt244_dev_only_npu_conversation_plan.md`](litert_qairt244_dev_only_npu_conversation_plan.md).
That plan defines the five-step path toward a debug-only one-turn NPU
conversation entry and keeps standard ChatScreen promotion out of scope.

## Current Evidence

The dev-only hidden receiver path uses `prompt_base64`, explicit unsafe prompt
length bypass, raw template mode, and bounded one-case probes. Under that
condition:

| case | input shape | result |
| --- | --- | --- |
| raw generated filler | `final_input_chars_approx=4096` | success, NPU decode reached |
| natural prompt-file | `prompt_chars=3759` | success, NPU decode reached |
| strict short-answer prompt-file | `prompt_chars=3754` and `5614` | decode reached, `output_bytes=0` |
| loose greeting prompt-file | `prompt_chars=4104`, max output `16` and `32` | decode reached, `output_bytes=0` |
| dialog-tail prompt-file | `prompt_chars=4422`, tail `ユーザー: こんにちは。\nアシスタント:` | success, `natural_japanese` |
| raw-dialog-tail case label | `prompt_chars=4126`, app template mode `raw` | success, `mixed_language`, control chars observed |
| neutral-context raw-dialog-tail | `prompt_chars=5272`, app template mode `raw` | success, `mixed_language`, control chars observed |

The empty-output cases are therefore not NPU reachability failures. They are
prompt/output quality behavior after successful native decode.

## Separation Of Concerns

Keep these tracks separate:

- **Prefill / sequence reachability:** whether long input reaches native
  prompt validation, prefill, decode, and QNN/HTP/FastRPC evidence.
- **Output quality:** whether the decode result contains usable bytes and
  survives sanitizer/echo handling.
- **Standard route promotion:** whether a route is safe enough for
  ChatScreen, persistence, DB, TTS, Markdown, and streaming integration.

The current evidence supports dev-only hidden-route reachability near 4096
input. It does not justify standard ChatScreen NPU enablement.

## Tail Guidance

Avoid raw string injection as the promotion shape. Prefer an explicit
conversation-continuation tail:

```text
<long context>

ユーザー: {user_prompt}
アシスタント:
```

The closed instruction style below is risky in this probe family:

```text
最後の指示: 日本語で「こんにちは」と一言だけ返答してください。
```

That style can reach native decode but still produce `output_candidates=1` and
`output_bytes=0`. Raising max output from `16` to `32` did not recover output
for the loose greeting prompt, while changing only the tail to the dialog
continuation form did recover `natural_japanese` output.

## Candidate Templates

### raw_dialog_tail

```text
<long context>

ユーザー: {user_prompt}
アシスタント:
```

Use this as the next dev-only raw template candidate before any standard route
promotion discussion. `scripts/run_npu_512_sequence_probe.sh` now exposes this
as `--only-template raw_dialog_tail` for dry-run and guarded hidden-receiver
probes. This is still a dev-only probe template and is not standard ChatScreen
routing.

Generation rules:

- with `--prompt` or `--prompt-file`, treat that content as the long context
  and append `\n\nユーザー: こんにちは。\nアシスタント:`;
- without `--prompt` or `--prompt-file`, generate the same long `x ` context
  used by raw and append the same tail;
- with prompt overrides, `target` remains a case label and
  `final_input_chars_approx` comes from context plus the appended tail.
- the script records `template=raw_dialog_tail`, but sends the existing
  hidden receiver `raw` template mode to the app so no Kotlin route or
  standard ChatScreen template support is required.

This template is for output-quality comparison after long-input reachability
has already been demonstrated. It is not new evidence by itself for 4096
prefill reachability.

Runtime result recorded after this template was added:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_213520/summary.md
template=raw_dialog_tail
app_template_mode=raw
prompt_tail_mode=raw_dialog_tail
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4126
final_input_chars_approx=4126
prompt_transport=base64
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=35
sanitized_len=34
quality=mixed_language
control_chars=true
```

Interpretation:
- the script-level `raw_dialog_tail` dev-only template candidate can reach NPU
  decode and avoid empty output;
- the app-facing template mode remains `raw`, so this does not add a
  standard ChatScreen template or route;
- DB, TTS, Markdown, streaming, and persisted backend selection remain
  disconnected;
- because the result is `quality=mixed_language` with control characters
  observed, it is reachability and non-empty-output evidence, but not yet a
  clean quality baseline.

## Neutral Context Raw Dialog Tail Probe Design

Purpose:
- improve output quality by removing duplicated answer instructions from the
  prompt body and tail;
- compare prompt shaping and output quality only, not prefill reachability or
  standard route promotion;
- keep answer induction centralized in the script-provided `raw_dialog_tail`
  suffix:

```text
ユーザー: こんにちは。
アシスタント:
```

Fixed axes:
- `template=raw_dialog_tail`
- `app_template_mode=raw`
- `prompt_transport=base64`
- `unsafe_dev_bypass_prompt_length_gate=true`
- `max_output_tokens=16`
- `--only-target 2048`
- `--limit-cases 1`
- standard ChatScreen route remains disconnected
- DB, TTS, Markdown, and streaming remain disconnected

Changed axis:
- only the `prompt_file` body changes;
- replace the current loose-answer body with neutral long context that removes
  answer instructions such as "返答してください" and "最後の指示";
- keep the conversation tail delegated to the `raw_dialog_tail` template.

Prompt-file candidate:

```text
/tmp/lami_npu_prompt/ja_neutral_context_3800.txt
```

Body pattern:

```text
これはNPU長文prefill検証用の日本語自然文です。
この文章は文脈長と安定性を確認するための中立的な説明文です。
回答指示は本文には含めません。
```

Repeat the neutral sentences to the desired length. Do not add a final answer
instruction at the end of the file.

Required result fields:
- `status`
- `native` / `decode`
- `npu_evidence`
- `fallback` / `fresh_crash` / `timeout`
- requested/effective max output tokens
- `raw_len` / `sanitized_len`
- `quality`
- `control_chars`
- `output_first_200_chars`
- native diag `output_bytes`

Success criteria:
- `native=true`
- `decode=true`
- `npu_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback=false`
- `fresh_crash=false`
- `timeout=false`
- `raw_len > 0`
- `sanitized_len > 0`
- `quality=natural_japanese`, or at least an improvement over the prior
  `mixed_language` result
- `control_chars=false` is preferred

This comparison remains a dev-only hidden receiver quality probe. It is not
standard route enablement, not a prefill boundary test, and not permission to
connect DB, TTS, Markdown, streaming, or persisted backend selection. Execute
at most one case after this design is committed.

Runtime result:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_220023/summary.md
template=raw_dialog_tail
app_template_mode=raw
prompt_tail_mode=raw_dialog_tail
prompt_file=/tmp/lami_npu_prompt/ja_neutral_context_3800.txt
prompt_chars=5272
final_input_chars_approx=5272
prompt_transport=base64
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=35
sanitized_len=34
quality=mixed_language
control_chars=true
native_diag output_bytes=81
output_first_200_chars=こんにちは。\nこれはNPU長文prefill検証用の日本語自然文です
```

Interpretation:
- neutral context plus `raw_dialog_tail` still avoids empty output;
- removing "返答してください"-style body instructions does not prevent output;
- the result supports the hypothesis that the dialog tail is the key
  empty-output recovery axis in this family;
- quality is still not clean because `mixed_language` and control characters
  remain;
- this is prompt shaping / output quality evidence, not prefill reachability
  evidence or standard route promotion.

## Raw Dialog Tail Control Character Classification

Existing artifact inspection shows that the `control_chars=true` flag in the
two script-level `raw_dialog_tail` success cases is caused only by newline
characters:

| artifact | context | raw/sanitized length | quality | output_contains_control_chars | control chars | replacement chars | output preview |
| --- | --- | --- | --- | --- | --- | ---: | --- |
| `20260529_213520/raw_dialog_tail_2048` | loose context | `35/34` | `mixed_language` | `true` | `U+000A x2` | 0 | `こんにちは。\n\n私はNPU長文prefill検証用の日本語自然文です` |
| `20260529_220023/raw_dialog_tail_2048` | neutral context | `35/34` | `mixed_language` | `true` | `U+000A x1` | 0 | `こんにちは。\nこれはNPU長文prefill検証用の日本語自然文です` |
| `20260529_211227/raw_2048` | manual dialog-tail prompt | `31/30` | `natural_japanese` | `false` | `none` | 0 | `はい、どういたしまして。何かお手伝いできることはありますか？` |

Interpretation:
- the observed control character is newline only, not an unexpected binary,
  escape, null, replacement, or invalid Unicode artifact;
- newline-only output can be considered a potentially acceptable formatting
  control character for quality classification;
- `mixed_language` remains separate from `control_chars=true`: both
  `raw_dialog_tail` outputs echo ASCII-heavy probe terms such as `NPU`,
  `prefill`, and the natural prompt text, while the manual dialog-tail output
  is a direct Japanese assistant response;
- the next safe work should be a sanitizer/quality-classifier design review
  before adding runtime. The question is whether newline-only control
  characters should be downgraded from a quality warning, and whether
  `mixed_language` should track ASCII probe-term echo separately.

### Proposed Quality Policy

Do not treat `control_chars=true` as an automatic quality failure. Split it
into explicit classes:

| class | condition | proposed outcome |
| --- | --- | --- |
| `control_chars_newline_only` | only `U+000A` line breaks are present, `replacement_char_count=0`, and `raw_len > 0` | soft pass as acceptable formatting |
| `control_chars_disallowed` | control characters other than allowed whitespace/newline are present | fail |
| `replacement_chars_present` | `replacement_char_count > 0` or invalid Unicode replacement is observed | fail |

Mixed-language classification should be independent from control-character
classification. Candidate causes for `mixed_language` in these probes are:
- ASCII probe terms in the prompt or echo, such as `NPU`, `prefill`, and
  `raw_dialog_tail`;
- Japanese plus ASCII/katakana mixtures from diagnostic wording;
- prompt echo or tail echo, including `ユーザー:` and `アシスタント:`.

Next classifier design:
- newline-only + `replacement_char_count=0` + non-empty raw/sanitized output
  is a soft pass for control-character policy;
- replacement characters are a failure regardless of language mix;
- disallowed control characters are a failure regardless of language mix;
- `mixed_language` should be judged using `output_first_200_chars` and
  prompt/tail echo detection, not by the newline-only flag.

This is a runtime-result reclassification design only. It is not standard
route promotion. If implemented later, use the sequence: docs, classifier
design, tests, then dev-only display/reporting updates.

### simple_ja_chat / gemma_it_like

These remain separate comparisons. Earlier template probes showed echo and
sanitizer interactions, so do not mix template validation with prefill boundary
claims. Compare one template at a time and keep sanitizer changes out of the
same run.

## Pre-Promotion Gates

Before considering standard ChatScreen integration, the dev-only path should
show at least one raw-dialog-tail case with:

- `fallback=false`
- `fresh_crash=false`
- `timeout=false`
- `npu_evidence=QNN_HTP_V79_FastRPC_native_diag`
- requested/effective max output tokens recorded
- `raw_len > 0`
- `sanitized_len > 0`
- no DB, TTS, Markdown, streaming, standard ChatScreen, or persisted backend
  connection

This gate is necessary but not sufficient for promotion. Standard route safety
still needs a separate design review.

## Runtime Discipline

Future runtime probes should change one axis at a time:

- tail only
- template only
- max output only
- sanitizer only

Do not bundle prompt-tail changes with sanitizer changes or standard route
wiring. Continue using one-case probes, explicit timeout, force-stop, and
diagnostic artifact collection.

## Current Recommendation

Treat `raw_dialog_tail` as the preferred dev-only quality baseline. Keep
closed short-answer tails as known-risk cases for empty native output. Keep
512 sequential/prefill boundary conclusions separate from output quality
conclusions. The neutral-context `raw_dialog_tail` run confirms non-empty
output without body-level answer instructions, but quality still needs a
separate prompt-shaping or sanitizer-classification follow-up.
