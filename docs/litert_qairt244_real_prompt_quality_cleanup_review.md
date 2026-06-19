# QAIRT244 Real Prompt Quality Cleanup Review

Date: 2026-05-31

Scope: design review only. This document does not implement code, run runtime
checks, install APKs, change native code, change prompt templates, change decode
logic, or run promote.

## Current Runtime Signals

The S1 standard route now passes the actual ChatScreen prompt through the real
NPU path:

```text
ChatScreen requestPrompt
-> NpuStandardRouteS1Bridge.run(userPrompt)
-> NpuStandardRouteS1Invoker.invoke(userPrompt)
-> NpuStandardRouteS1Provider.invoke(userPrompt)
-> RealNpuStandardRouteS1Provider.request(userPrompt)
-> DevOnlyNpuOneTurnConversationRequest.userPrompt
```

The debug trace already records prompt handoff without raw prompt disclosure:

```text
NPU_REAL_PROMPT chat_prompt_hash/length/code_points/preview
NPU_REAL_PROMPT bridge_prompt_hash/length/code_points/preview
NPU_REAL_PROMPT invoker_prompt_hash/length/code_points/preview
NPU_REAL_PROMPT provider_prompt_hash/length/code_points/preview
NPU_REAL_PROMPT request_prompt_hash/length/code_points/preview
```

Observed behavior confirms that the prompt is no longer fixed:

| Input | Observed result |
| --- | --- |
| `こんにちは` | `raw_output_preview=こんにちは。`, `sanitized_output_preview=こんにちは。`, `status=success`, `quality_classification=natural_japanese` |
| `こんばんは` | `raw_output_preview=3؟です|`, `raw_output_length=7`, `sanitized_output_length=0`, `status=failure`, `reason=empty_after_sanitize`, `quality_classification=mixed_language`, `run_decode_reached=true`, `timeout=false`, `fallback=false`, `fresh_crash=false` |
| `あ` | `raw_output_preview=何かご用でしょうか Ash`, `status=success`, `quality_classification=mixed_language` |

The output changes by input, so the remaining issue is quality and cleanup, not
fixed-prompt routing.

## 1. Prompt Handoff Confirmation

The real prompt route is considered connected when the following hashes and
lengths agree for a single send:

- `chat_prompt_hash`
- `bridge_prompt_hash`
- `invoker_prompt_hash`
- `provider_prompt_hash`
- `request_prompt_hash`

The displayed values should not expose the full prompt. A mismatch between
`chat_prompt_hash` and `request_prompt_hash` would indicate a handoff bug before
native execution. A match plus broken raw output indicates the failure is after
prompt handoff.

For `こんばんは`, the current evidence points after handoff:

- S1 block is displayed.
- `run_decode_reached=true`.
- `timeout=false`.
- `fallback=false`.
- `fresh_crash=false`.
- raw output is already malformed before sanitizer acceptance.

## 2. Output Variation By Input

The NPU response varies across short prompts:

- `こんにちは` returns natural Japanese.
- `こんばんは` returns a short malformed mixed-language fragment.
- `あ` returns a Japanese sentence with an ASCII tail.

This variation is useful: it suggests the model is responding to the prompt, but
the prompt shape and decode behavior are not stable enough for short greeting
inputs. The failure is not the old fixed `こんにちは。` provider path.

## 3. Raw Output Role-Token Remnants

`raw_dialog_tail_variant_b` currently shapes the prompt as:

```text
必ず日本語だけで短く返答してください。
ユーザー: <prompt>
アシスタント: はい、
```

The raw output can still include fragments such as:

- `ユーザー:`
- `アシスタント:`
- repeated prompt text;
- assistant prefix fragments;
- ASCII or non-Japanese tails;
- mixed punctuation or replacement-like text.

Likely causes:

- The model continues the visible chat transcript rather than returning only the
  assistant completion.
- `アシスタント: はい、` helps prevent Hindi/Korean drift, but may also make
  the model produce continuation artifacts or very short fragments.
- The lower-level NPU decode path does not expose a reliable structured stop
  boundary for chat turns.
- The sanitizer currently handles role lines and line-prefix cases, but it does
  not yet classify all inline transcript remnants.

For `こんばんは`, the malformed raw fragment is already present before
sanitization. The sanitizer is correctly refusing to promote it to a user-facing
assistant message.

## 4. Sanitized Output Cleanup Options

Recommended first cleanup is sanitizer-only, not template/decode changes.

Candidate sanitizer improvements:

1. Treat inline transcript remnants as boundaries:
   - if a line contains `ユーザー:` after natural assistant text starts, stop
     collecting;
   - if a line contains `アシスタント:` after natural assistant text starts,
     strip the prefix once and keep only the following assistant text when it is
     natural Japanese.

2. Add explicit `raw_dialog_tail_variant_b` continuation handling:
   - remove a leading `はい、` seed only when it is repeated as an artifact;
   - keep natural completions such as `はい、できます。`;
   - do not pass malformed mixed-language fragments only because they include
     Japanese kana.

3. Preserve strict failure behavior:
   - replacement chars present -> fail;
   - disallowed control chars -> fail;
   - mixed-language with ASCII/non-Japanese tail -> keep classified as quality
     failure unless a safe Japanese segment can be extracted.

4. Improve diagnostics before changing acceptance:
   - record `removed_role_prefix_count`;
   - record `transcript_boundary_detected`;
   - record `assistant_prefix_stripped`;
   - record `sanitized_empty_reason`.

Do not loosen the sanitizer to accept `3؟です|` or similar mixed fragments.

## 5. Template Change Versus Sanitizer First

Recommended order:

1. Keep `raw_dialog_tail_variant_b` unchanged.
2. Improve sanitizer classification and diagnostics.
3. Re-run a short prompt matrix.
4. Only then compare prompt-tail variants.

Reasons to avoid template changes first:

- `raw_dialog_tail_variant_b` is the first prompt shape that produced stable
  `こんにちは。` results and 5/5 dev-only success.
- Template changes can reintroduce Hindi/Korean drift or punctuation-only
  output.
- If both template and sanitizer change at once, it becomes unclear whether the
  improvement came from generation or post-processing.

Prompt-template experiments should be developer-only and explicit:

- keep `raw_dialog_tail_variant_b` as baseline;
- compare a weaker seed such as `アシスタント:` only after diagnostics show
  that the seed is causing remnants;
- keep `simple_ja_chat` and `gemma_it_like` separate from standard route
  promotion because they previously expanded the prompt and complicated
  sanitizer/validation results.

## 6. Decode And Stop Boundary Checks

The current `DevOnlyNpuOneTurnConversationDisplay` can expose parsed fields when
the lower-level result file includes them:

- `stop_reason`
- `finish_reason`
- `eos_detected`
- `output_token_count`
- `prompt_token_count`

If these fields are unavailable, the runtime should report `unknown` or
`unavailable` rather than inferring a stop reason.

For `こんばんは`, the next runtime comparison should answer:

- Did `output_token_count` differ from successful greeting prompts?
- Was `eos_detected=true` immediately after a malformed fragment?
- Is `finish_reason` exposed by the lower-level entrypoint?
- Did the raw output end because of EOS, max tokens, or an unreported stop?

If the malformed fragment is produced before EOS with valid decode timing, the
issue is likely model/decode output quality rather than UI or sanitizer.

## 7. NPU_REAL_PROMPT Logcat Visibility

`NPU_REAL_PROMPT` is currently sent through `logStreamTrace(...)` from
ChatScreen. Possible reasons `logcat grep NPU_REAL_PROMPT` may miss it:

- `logStreamTrace(...)` writes to the app trace file but not always to Android
  `Logcat`.
- The log tag may be `ChatScreen` or another stream trace tag, not
  `NPU_REAL_PROMPT`.
- The message may be filtered by log level.
- The app process may rotate or clear the in-app trace before grep.
- The trace is only emitted after the S1 gate path is entered; OFF or fallback
  to normal Local inference will not emit it.

Recommended next step is not to depend only on logcat. Keep the red UI trace as
the fastest path for device-side debugging, and optionally add a dev-only
`Log.i("NPU_REAL_PROMPT", message)` mirror later if logcat correlation is still
needed.

## 8. Red Debug Display Scope

The red S1 diagnostic is currently useful during active NPU cleanup, but it
should not remain visible to ordinary users.

Recommended gate:

```text
BuildConfig.DEBUG && developerAccessEnabled && NPU standard route mode != OFF
```

Alternative stricter gate:

```text
BuildConfig.DEBUG && selectedDisplayMode == DEVELOPER
```

Keep it out of:

- release builds;
- copy icon payloads for ordinary messages;
- TTS;
- DB saved assistant text;
- Markdown final text.

## 9. Rollback

Runtime rollback:

```text
Settings -> NPU標準ルート -> OFF
```

Code rollback for quality cleanup:

- revert sanitizer-only cleanup changes;
- keep `raw_dialog_tail_variant_b` as baseline;
- keep S1/S2/S3/S4-A/S5 gates unchanged;
- keep legacy QAIRT route hard-gated.

Code rollback for diagnostics:

- hide red debug display behind developer mode;
- remove optional `Log.i("NPU_REAL_PROMPT", ...)` mirror if added.

No DB migration, native change, or backend preference cleanup should be needed.

## 10. Runtime Check Plan

Do not change prompt template or decode settings for the next run.

Recommended checks:

1. Enable NPU standard route S1 only.
2. Send short prompts one by one:
   - `こんにちは`
   - `こんばんは`
   - `こんばんは。`
   - `こんばんわ`
   - `おはよう`
   - `ありがとう`
   - `あ`
   - `q`
   - `え`
3. Capture the red debug block for each:
   - prompt hash/length/code points/preview;
   - raw output hash/length/code points/preview;
   - sanitized output hash/length/code points/preview;
   - `status`;
   - `reason`;
   - `quality_classification`;
   - `run_decode_reached`;
   - `timeout`;
   - `fallback`;
   - `fresh_crash`.
4. If the dev-only matrix runner is used, inspect
   `files/dev_only_npu_one_turn_conversation_matrix_result.txt`.
5. Stop if:
   - timeout occurs;
   - fallback becomes true;
   - fresh crash is true;
   - `run_decode_reached=false`;
   - raw output contains replacement chars or disallowed controls.

Pass condition for the next cleanup phase:

- `こんにちは`, `おはよう`, and `ありがとう` remain natural Japanese.
- `こんばんは` produces either a natural Japanese response or a diagnosable
  malformed raw output with stable stop/token metadata.
- Sanitizer continues to reject malformed mixed-language output.

## Recommendation

Proceed in this order:

1. Keep prompt template and decode settings unchanged.
2. Use the matrix diagnostics to confirm whether `こんばんは` is uniquely
   malformed at raw output level.
3. Add sanitizer diagnostics for transcript remnants and empty reasons.
4. Only if raw output remains consistently malformed, design a separate
   prompt-tail comparison for short greetings.
