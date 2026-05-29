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
promotion discussion.

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
conclusions.
