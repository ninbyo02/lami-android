# QAIRT244 Prompt Tail Quality Design

Date: 2026-05-29

Scope: dev-only design memo only. This does not implement standard ChatScreen
NPU routing, does not run probes, does not build or install an APK, and does
not connect DB, TTS, Markdown, streaming, or persisted backend selection.

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
conclusions. The next safe comparison is a neutral-context
`raw_dialog_tail` prompt-file run that removes "返答してください"-style
instructions from the body while keeping template, bypass state, transport,
and max output fixed.
