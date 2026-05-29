# QAIRT244 Dev-Only 128 Gate Bypass Design

Date: 2026-05-29

Scope: design only. This document does not implement a validation bypass,
does not run runtime probes, does not build or install an APK, and does not
connect Backend.NPU to any standard ChatScreen route.

Related prompt-quality design note:
[`docs/litert_qairt244_prompt_tail_quality_design.md`](litert_qairt244_prompt_tail_quality_design.md).
The probe runner also has a dev-only `raw_dialog_tail` template candidate for
output-quality comparison. It appends `ユーザー: こんにちは。` /
`アシスタント:` to the long context and is not standard ChatScreen routing or a
new bypass/prefill claim by itself. The case label is `raw_dialog_tail`, while
the app-facing hidden receiver template mode remains the existing `raw`.

## Current Evidence

- Static scans of the SM8750 and regular E2B `.litertlm` files did not find
  direct readable evidence for a `512` sequence/prefill/context/input limit.
- The requested-max native artifact now honors `maxOutputTokens=16` at native
  decode time and records `SetMaxOutputTokens(16)`.
- Raw custom prompts reach NPU decode successfully for targets `1`, `8`, `16`,
  `32`, and `64`, with `fallback=false` and `fresh_crash=false`.
- The existing hidden route still enforces
  `HIDDEN_TEMPLATE_MAX_LENGTH=128`, so raw target `128+`,
  `simple_ja_chat` target `64+`, and `gemma_it_like` target `64+` are expected
  to reject before native entry.
- Therefore the current runtime evidence has not directly tested the 512
  graph/prefill boundary.

## Why A Bypass Is Needed

The 512 sequential hypothesis is about whether the native LiteRT-LM graph or
runtime fails near a final-input boundary. The current hidden route rejects
larger final inputs at app validation before JNI/native entry, so those cases
only prove app-side validation behavior.

Without a dev-only bypass:
- raw target `128+` cannot classify native graph behavior;
- `512` and `640` rows cannot reach `RunPrefill` or `RunDecode`;
- failures above the 128-codepoint gate cannot be separated from compiled
  graph shape limits, QNN runtime handoff failures, or native process death.

With a controlled bypass, a single selected case can cross the app-side gate
while still preserving all existing production and standard UI constraints.

## Non-Negotiable Constraints

- dev-only only
- non-ChatScreen only
- non-persistent
- no DB, TTS, Markdown, or streaming connection
- no standard ChatScreen route connection
- no Backend.NPU production application
- no UI settings exposure
- do not hide fallback
- execution limited to hidden receiver or test-only runner
- start with one case only
- timeout, force-stop, and diagnostic artifact collection are mandatory
- continue recording requested/effective max output tokens
- always record native prompt length, final input length, and template mode
- do not modify normal validation behavior
- bypass must require an explicit unsafe/dev-only flag

Candidate flag names:

```text
allow_dev_only_prompt_length_bypass
enable_unsafe_sequence_probe_bypass
```

The final name should include both `dev_only` or `unsafe` and `bypass` so it is
not confused with a normal route option.

## Accident Risks

Primary risks:
- accidental exposure through standard ChatScreen;
- persistent setting or UI toggle enabling a long-prompt NPU path;
- DB/TTS/Markdown/streaming side effects from probe output;
- masking fallback or crash behavior;
- confusing app-side validation rejects with native graph rejects;
- broad matrix execution before one-case safety is established;
- reusing the bypass for output-quality or product behavior tests.

Risk controls:
- receiver-only or test-runner-only input flag;
- no Settings UI, no DataStore preference, no persisted selected route;
- reject the bypass unless the hidden receiver action and developer access
  flags are both present;
- require `--only-template`, `--only-target`, and `--limit-cases 1` for the
  first bypassed run;
- force-stop before and after timeout;
- save diagnostics before/after the case;
- record every classification field needed to distinguish app, native, and
  sanitizer failures.

## Implementation Candidate

Implementation should be a narrow app-side validation branch, not a standard
validation change.

Suggested shape:
- Add an explicit boolean extra to the hidden receiver input, for example
  `allow_dev_only_prompt_length_bypass=true`.
- Gate it behind the existing developer-only receiver path and a second
  explicit unsafe flag, for example
  `enable_unsafe_sequence_probe_bypass=true`.
- Keep default behavior unchanged: if the flags are absent, continue enforcing
  `HIDDEN_TEMPLATE_MAX_LENGTH=128`.
- If the bypass is active, skip only the 128-codepoint prompt-length rejection
  for the selected hidden probe case.
- Continue validating UTF-8/control-character policy and any non-length safety
  checks unless a separate design explicitly changes them.
- Record both the normal limit and the bypass state in receiver state,
  native result metadata, and summary output.

Suggested metadata keys:

```text
prompt_length_bypass_requested=true|false
prompt_length_bypass_effective=true|false
prompt_length_bypass_flag=allow_dev_only_prompt_length_bypass
unsafe_sequence_probe_bypass=true|false
normal_prompt_input_code_point_limit=128
prompt_input_code_points=<n>
final_input_chars_approx=<n>
template_mode=<raw|simple_ja_chat|gemma_it_like>
```

## Probe Phases

### Phase 0: Existing Baseline

Already established:
- raw custom prompt targets `1`, `8`, `16`, `32`, and `64` succeed;
- requested/effective max output tokens stay at `16`;
- NPU evidence remains `QNN_HTP_V79_FastRPC_native_diag`;
- `fallback=false`;
- `fresh_crash=false`.

### Phase 1: No-Bypass Prediction Comparison

Design intent only; do not run as part of this document.

Run raw target `128` with no bypass and compare preflight prediction with
receiver/native artifacts:
- preflight may classify the row as
  `native_pre_reject_expected_by_128_gate=true`;
- the source of truth is the measured receiver/result/native artifact state:
  native entry, decode entry, NPU evidence, and receiver failure reason.

This baseline must use either:
- no `--prompt`, so raw target `128` generates the default filler input that
  exceeds the 128-codepoint gate; or
- an explicit custom prompt whose recorded `final_input_chars_approx` exceeds
  `128`.

If `--prompt` is set to a short natural prompt, `target=128` is only a case
label. It does not create a 128-target-length input, and a successful decode
from such a run is not a gate-boundary or 512 sequential result.

Observed follow-up:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_055209/summary.md
template=raw
target=128
custom_prompt=false
prompt_chars=256
final_input_chars_approx=256
native_pre_reject_expected_by_128_gate=true
status=failure
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
reason=empty_after_sanitize
```

This means Phase 1 did not confirm a native-before validation reject. Instead,
it showed a preflight prediction mismatch: the generated-filler raw target
`128` case reached native/decode and failed after output sanitization. Bypass
necessity is therefore on hold until measured gate behavior is rechecked at
larger generated-filler targets. A bypass may still be needed later, but it
should not be assumed solely from the preflight table.

Additional measured follow-up:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_055701/summary.md
template=raw
target=256
custom_prompt=false
prompt_chars=512
final_input_chars_approx=512
native_pre_reject_expected_by_128_gate=true
status=failure
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
reason=empty_after_sanitize
requested/effective=128/128
```

The raw target `256` result also reached native/decode despite the preflight
native-before-reject prediction. This further weakens the assumption that the
current hidden receiver path enforces the 128-codepoint gate as modeled by
preflight. It also surfaced a separate unresolved issue: the command requested
`--max-output-tokens 16`, but artifacts recorded requested/effective `128`.
Follow-up inspection showed the generated space-separated filler prompt likely
broke `adb shell am broadcast` argument parsing: `broadcast.txt` showed
`pkg=x`, while receiver/native artifacts recorded `prompt=x`,
`max_output_tokens_compare_enabled=false`, and `SetMaxOutputTokens(128)`.
Before implementing a bypass, first make prompt transport shell-safe for long
generated prompts and continue treating measured native/decode state as the
source of truth. The dev-only hidden receiver and sequence probe runner now use
`prompt_base64` for this transport path, while preserving the existing plain
`prompt` extra as a receiver fallback. Any future Phase 1/2 check should first
confirm `prompt_transport=base64`, `prompt_decode_success=true`, and the
expected prompt/final-input lengths in artifacts.

Phase 1 was repeated after `prompt_base64` landed:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_062513/summary.md
template=raw
target=256
custom_prompt=false
prompt_transport=base64
prompt_chars=512
final_input_chars_approx=512
native_pre_reject_expected_by_128_gate=true
status=failure
reason=gate_blocked:VALIDATOR_INVALID
native=false
decode=false
requested/effective=16/16
```

This restores the expected Phase 1 interpretation: with shell-safe prompt
transport, raw target `256` is blocked by the app-side validator before native
entry. The earlier native/decode observation was likely a false positive from
prompt transport collapse, not evidence that the 128-codepoint gate was absent.
Bypass necessity is therefore no longer on hold for transport reasons; crossing
raw target `128+` into native graph/prefill behavior requires a separate
dev-only, hidden-receiver-only gate bypass.

### Phase 2 Implementation Note

The bypass is implemented behind the explicit script flag
`--unsafe-dev-bypass-prompt-length-gate`, which sends the hidden receiver extra
`unsafe_dev_bypass_prompt_length_gate=true`. It is intentionally scoped to
`StandardHiddenQairt244PromptReceiver` and the dev-only route used by the probe:
it is not exposed in UI settings, does not persist `Backend.NPU`, and is not
connected to standard ChatScreen generation, DB, TTS, Markdown, or streaming.

The bypass only applies to the 128-codepoint hidden-template prompt-length gate
when the validator reason is `too_long`. Other validation failures, developer
access checks, route enablement checks, model resolution, max-output validation,
timeout handling, force-stop behavior, and fallback visibility are unchanged.
Artifacts must be checked for:
- `unsafe_dev_bypass_prompt_length_gate_requested=true`
- `unsafe_dev_bypass_prompt_length_gate_effective=true`
- `prompt_length_gate_would_block=true`
- `prompt_length_gate_bypassed=true`
- `prompt_transport=base64`
- requested/effective max output tokens, normally `16/16`

The first runtime use should be exactly one case, preferably raw target `128`
or raw target `256`, with `--limit-cases 1`, timeout `60`, and
`--max-output-tokens 16`.

The first raw target `128` attempt confirmed the receiver-side bypass but found
one more pre-native length gate in the debug editable-prompt wrapper:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_072125/summary.md
reason=adapter_failure:IllegalStateException
message=editable prompt rejected before native execution: reasonCode=too_long
native=false
decode=false
prompt_length_gate_bypassed=true
```

The same unsafe flag is now passed into
`Qairt244ShortMultitokenSmoke.runEditablePrompt`, where only the
hidden-template `too_long` prompt-length validation result can be bypassed.
This keeps non-length validation, max-output validation, model checks, fallback
visibility, and non-persistent hidden receiver scoping unchanged.

A follow-up raw target `128` run then reached native but failed at native C++
prompt validation:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_074542/summary.md
reason=native_result:invalid_prompt
native=true
decode=true
native_diag prompt_validation reason=too_long
native_prompt_input_code_point_limit=128
native_prompt_input_limit_mode=hidden_template_experiment
```

The native source now has a matching explicit mode,
`unsafe_dev_bypass_hidden_template_experiment`, selected only by the debug
wrapper when the hidden receiver unsafe flag is active. Native validation keeps
UTF-8, empty prompt, control character, model path, and max-output checks
unchanged, but skips the 128-codepoint `too_long` result for that mode and
records the native length-gate would-block/bypassed metadata. A new native
artifact build is required before the next APK install and runtime probe.

### Phase 2: First Bypassed Native Case

Phase 2 first succeeded with the native length-gate bypass artifact:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_200054/summary.md
template=raw
target=128
prompt_transport=base64
prompt_chars=256
final_input_chars_approx=256
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
native_pre_reject_expected_by_128_gate=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
```

The end-to-end hidden receiver bypass path is therefore validated through
native decode for raw target `128` (`final_input_chars_approx=256`). This does
not yet prove the 512 sequential boundary, but it clears the app/Kotlin/native
length-gate stack needed to test raw target `256` as the next one-case probe.

Raw target `256` then also succeeded:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_200533/summary.md
template=raw
target=256
prompt_transport=base64
prompt_chars=512
final_input_chars_approx=512
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
native_pre_reject_expected_by_128_gate=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
```

Phase 2 therefore establishes native decode through the dev-only hidden
receiver bypass at `final_input_chars_approx=512`. This weakens the 512
sequential limit hypothesis for the current raw hidden-route probe condition.
Continue with one-case increments only: raw target `384` for the safer next
step, or raw target `512` for a faster boundary check.

Raw target `384` and `512` then succeeded as well:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201022/summary.md
template=raw
target=384
final_input_chars_approx=768
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201139/summary.md
template=raw
target=512
final_input_chars_approx=1024
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language
```

Phase 2/3 now demonstrates native decode beyond the original 128 gate through
`final_input_chars_approx=1024`. The following checks continued as
single-case, max-output-16 guarded probes.

Raw target `640`, the current largest built-in script target, also succeeded:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_201630/summary.md
template=raw
target=640
prompt_transport=base64
prompt_chars=1280
final_input_chars_approx=1280
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=32
sanitized_len=31
quality=mixed_language
```

The bypassed raw path now reaches native decode through
`final_input_chars_approx=4096`. Raw-only target `1024` and raw-only target
`2048` both succeeded without adding those larger targets to `simple_ja_chat`
or `gemma_it_like`:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_202602/summary.md
template=raw
target=1024
final_input_chars_approx=2048
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=32
sanitized_len=31
quality=mixed_language

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_202732/summary.md
template=raw
target=2048
final_input_chars_approx=4096
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=64
sanitized_len=64
quality=natural_japanese
control_chars=false
```

This establishes 4096-input-prefill-equivalent native decode reachability only
for the dev-only hidden receiver, raw generated-filler, max-output-16, bypassed
gate condition. It is not standard ChatScreen enablement and does not remove
the need for standard-route safety gate redesign.

Natural-language long-prompt checks near 4096 input should use
`--prompt-file` so the prompt body is read from a UTF-8 text file and sent via
`prompt_base64`, not directly through `adb shell am broadcast --es prompt`.
When `--prompt-file` is set, `target` is only a case label; the file-derived
`prompt_chars` and `final_input_chars_approx` are the source of truth. Keep
generated-filler and natural-language evidence separate.

The first prompt-file natural-language check succeeded:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_204237/summary.md
prompt_source=prompt_file
prompt_file=/tmp/lami_npu_prompt/ja_long_4096.txt
prompt_transport=base64
template=raw
target=2048
prompt_chars=3759
final_input_chars_approx=3759
native_pre_reject_expected_by_128_gate=true
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
native_limit=512
native_file_first_max=16
raw_len=25
sanitized_len=15
quality=mixed_language
control_chars=true
```

This extends the bypass result from generated filler to a Japanese
natural-language long prompt near 4096 input. It remains a dev-only hidden
receiver result, not standard ChatScreen enablement. Output quality and
sanitizer/template behavior remain separate follow-up topics.

Subsequent natural-language prompt-file quality comparisons also reached
native/decode, but produced empty native output:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_204816/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_short_answer_4096.txt
prompt_chars=5614
final_input_chars_approx=5614
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_205044/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_short_answer_3800.txt
prompt_chars=3754
final_input_chars_approx=3754
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output

artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_205211/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4104
final_input_chars_approx=4104
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
requested/effective=16/16
raw_len=0
sanitized_len=0
quality=empty_output
```

The detailed `20260529_205211/raw_2048` native logs show prompt validation
`ok`, length gate bypass effective, prefill and decode reached, native
`result=success`, `output_candidates=1`, and `output_bytes=0`. These failures
therefore belong to output quality/prompt-shaping behavior, not to the 128 gate
bypass or 512/prefill reachability question.

#### Long Input / Natural Prompt Quality Matrix

All rows are existing dev-only hidden receiver artifacts with prompt-length
bypass enabled, `prompt_base64` transport, raw template, and
requested/effective max output tokens `16/16`.

| artifact | prompt_type | target | prompt/final input | status | native/decode | npu_evidence | output | quality | conclusion |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| `20260529_200054` | generated filler | 128 | `256/256` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | first successful bypassed native decode |
| `20260529_200533` | generated filler | 256 | `512/512` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | 512-ish input reached NPU decode |
| `20260529_201022` | generated filler | 384 | `768/768` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | larger prefill reached NPU decode |
| `20260529_201139` | generated filler | 512 | `1024/1024` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | 1024-ish input reached NPU decode |
| `20260529_201630` | generated filler | 640 | `1280/1280` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | previous script maximum reached NPU decode |
| `20260529_202602` | generated filler | 1024 | `2048/2048` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 32 / sanitized 31 | mixed_language | 2048-ish input reached NPU decode |
| `20260529_202732` | generated filler | 2048 | `4096/4096` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 64 / sanitized 64 | natural_japanese | 4096-ish generated filler reached NPU decode |
| `20260529_204237` | natural prompt-file | 2048 | `3759/3759` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 25 / sanitized 15 | mixed_language | Japanese natural prompt near 4096 reached NPU decode |
| `20260529_204816` | strict short-answer prompt-file | 2048 | `5614/5614` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_205044` | strict short-answer prompt-file | 2048 | `3754/3754` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_205211` | loose greeting prompt-file | 2048 | `4104/4104` | failure | true/true | QNN_HTP_V79_FastRPC_native_diag | output_bytes 0 / raw 0 / sanitized 0 | empty_output | decode reached; native output was empty |
| `20260529_211227` | dialog-tail prompt-file | 2048 | `4422/4422` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 31 / sanitized 30 | natural_japanese | dialog continuation tail restored output |
| `20260529_213520` | raw-dialog-tail case label | 2048 | `4126/4126` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 35 / sanitized 34 | mixed_language | dev-only case label avoids empty output, but quality/control chars still need work |
| `20260529_220023` | neutral-context raw-dialog-tail | 2048 | `5272/5272` | success | true/true | QNN_HTP_V79_FastRPC_native_diag | raw 35 / sanitized 34 | mixed_language | neutral body still avoids empty output, but quality/control chars remain |

The matrix confirms that generated filler reaches NPU decode through
`final_input_chars_approx=4096`, and natural Japanese prompt-file input also
reaches decode near that range. Empty-output cases are not bypass or prefill
reachability failures; they are prompt/output quality cases. Standard
ChatScreen promotion remains out of scope. Future runtime expansion should
change only one axis per one-case run: prompt tail instruction, template, or
max output tokens.

#### Prompt Tail / Native Output Metadata Comparison

Existing artifacts show the same native reachability path but different native
output bytes:

| artifact | case | prompt tail pattern | code points / bytes | max output | native output | timing | receiver output | conclusion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `20260529_202732/raw_2048` | generated filler success | repeated `x ` filler | 4095 / 4095 | 16 | candidates 1, bytes 192 | prefill 267 ms, decode 434 ms | raw 64, sanitized 64, natural_japanese | non-empty output after decode |
| `20260529_204237/raw_2048` | natural prompt success | repeated Japanese sentence ending with "短く返答してください。" | 3759 / 9519 | 16 | candidates 1, bytes 51 | prefill 476 ms, decode 373 ms | raw 25, sanitized 15, mixed_language | non-empty output after decode |
| `20260529_205044/raw_2048` | strict short-answer empty | final instruction asks for exactly "こんにちは" | 3754 / 9934 | 16 | candidates 1, bytes 0 | prefill 205 ms, decode 22 ms | raw 0, sanitized 0, empty_output | decode succeeds but output is empty |
| `20260529_205211/raw_2048` | loose greeting empty | final instruction asks for a short Japanese greeting | 4104 / 10544 | 16 | candidates 1, bytes 0 | prefill 322 ms, decode 22 ms | raw 0, sanitized 0, empty_output | decode succeeds but output is empty |
| `20260529_211227/raw_2048` | dialog-tail success | tail uses "ユーザー: こんにちは。" followed by "アシスタント:" | 4422 / not re-extracted | 16 | not re-extracted | not re-extracted | raw 31, sanitized 30, natural_japanese | prompt-tail change restores output |
| `20260529_213520/raw_dialog_tail_2048` | raw-dialog-tail case label success | script appends raw dialog tail to loose greeting prompt-file | 4126 / not re-extracted | 16 | not re-extracted | not re-extracted | raw 35, sanitized 34, mixed_language, control chars true | dev-only case label avoids empty output; quality is not yet clean |
| `20260529_220023/raw_dialog_tail_2048` | neutral-context raw-dialog-tail success | script appends raw dialog tail to neutral context prompt-file | 5272 / not re-extracted | 16 | candidates 1, bytes 81 | prefill 490 ms, decode 362 ms | raw 35, sanitized 34, mixed_language, control chars true | body instructions removed; empty output stays fixed but quality remains mixed |

These cases have prompt validation `ok`, native length-gate bypass
effective, and `SetMaxOutputTokens(16)`. The failures are therefore not 128
gate or 512/prefill boundary failures. They point at prompt-tail shape,
repetition structure, stop/eos behavior, or max-output cap. If runtime is
expanded, keep it one case at a time and change only one axis, such as moving
from max output 16 to 32.

The one-axis max-output comparison for the loose greeting prompt still produced
empty output:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_210719/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4104
final_input_chars_approx=4104
requested/effective=32/32
status=failure
reason=empty_after_sanitize
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
raw_len=0
sanitized_len=0
quality=empty_output
```

The loose prompt therefore remains empty at max output `32`, so max output `16`
alone is unlikely to explain this case. Next runtime work should change the
prompt tail only, with max output held fixed.

That prompt-tail-only comparison restored output:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_211227/summary.md
prompt_file=/tmp/lami_npu_prompt/ja_quality_dialog_tail_3800.txt
prompt_chars=4422
final_input_chars_approx=4422
prompt_tail=ユーザー: こんにちは。 / アシスタント:
requested/effective=16/16
status=success
native=true
decode=true
npu_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback=false
fresh_crash=false
timeout=false
raw_len=31
sanitized_len=30
quality=natural_japanese
control_chars=false
```

This confirms the empty-output behavior is sensitive to prompt tail shape:
dialog continuation (`ユーザー: ...` / `アシスタント:`) recovers output without
changing max output, bypass state, or template. Treat this as prompt shaping
and template design work, not as a prefill reachability problem.

The script-level `raw_dialog_tail` case label was also verified through the
same bypassed hidden receiver path:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_213520/summary.md
template=raw_dialog_tail
app_template_mode=raw
prompt_tail_mode=raw_dialog_tail
prompt_file=/tmp/lami_npu_prompt/ja_quality_loose_answer_3800.txt
prompt_chars=4126
final_input_chars_approx=4126
prompt_transport=base64
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
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

This confirms that the dev-only case label can pass through the bypassed
hidden receiver path and avoid empty output. It does not expand bypass scope:
the app-facing template mode remains `raw`, and standard ChatScreen, DB, TTS,
Markdown, streaming, and persisted backend selection remain disconnected. The
mixed-language/control-character output means this is not a quality baseline;
the safe next comparison is neutral context plus `raw_dialog_tail`, with the
"返答してください" instruction pattern removed from the long prompt body.

### Neutral Context Raw Dialog Tail Bypass-Scope Design

The next comparison should keep the bypass scope exactly unchanged and vary
only the prompt-file body. The purpose is output quality, not bypass
validation or prefill boundary expansion.

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
- prompt-file body only;
- remove answer instructions such as "返答してください" and "最後の指示" from
  the body;
- keep only neutral context in the file;
- let the script append the `raw_dialog_tail` conversation suffix:

```text
ユーザー: こんにちは。
アシスタント:
```

Prompt-file candidate:

```text
/tmp/lami_npu_prompt/ja_neutral_context_3800.txt
```

Candidate repeated body:

```text
これはNPU長文prefill検証用の日本語自然文です。
この文章は文脈長と安定性を確認するための中立的な説明文です。
回答指示は本文には含めません。
```

Do not put a final answer instruction in the file. The answer cue should exist
only in the script-level `raw_dialog_tail` tail.

Required outcome fields:
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
- `quality=natural_japanese`, or at least improved versus the prior
  `mixed_language/control_chars=true` result
- `control_chars=false` is preferred

This comparison is not standard route promotion and not a wider bypass. It is
a single-case prompt-shaping probe to be run only after this design is
committed.

The neutral-context follow-up stayed within that scope:

```text
artifact=artifacts/qairt244_npu_512_sequence_probe/20260529_220023/summary.md
template=raw_dialog_tail
app_template_mode=raw
prompt_tail_mode=raw_dialog_tail
prompt_file=/tmp/lami_npu_prompt/ja_neutral_context_3800.txt
prompt_chars=5272
final_input_chars_approx=5272
prompt_transport=base64
unsafe_dev_bypass_prompt_length_gate_requested=true
unsafe_dev_bypass_prompt_length_gate_effective=true
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

This confirms that removing answer instructions from the body does not expand
or change the bypass scope. The hidden receiver still uses app-facing
`raw`, remains disconnected from standard ChatScreen and DB/TTS/Markdown/
streaming, and preserves fallback/crash visibility. The comparison is useful
for output-quality classification only: empty output stayed fixed, while
`mixed_language/control_chars=true` remained unresolved.

### Raw Dialog Tail Control Character Classification

Artifact-only inspection of the raw-dialog-tail success cases shows that the
control character flag is caused by newline characters only:

| artifact | case | control chars | replacement chars | output_contains_control_chars | quality | sanitized output |
| --- | --- | --- | ---: | --- | --- | --- |
| `20260529_213520/raw_dialog_tail_2048` | loose context + script tail | `U+000A x2` | 0 | `true` | `mixed_language` | `こんにちは。\n\n私はNPU長文prefill検証用の日本語自然文です` |
| `20260529_220023/raw_dialog_tail_2048` | neutral context + script tail | `U+000A x1` | 0 | `true` | `mixed_language` | `こんにちは。\nこれはNPU長文prefill検証用の日本語自然文です` |
| `20260529_211227/raw_2048` | manual dialog-tail prompt | `none` | 0 | `false` | `natural_japanese` | `はい、どういたしまして。何かお手伝いできることはありますか？` |

This does not change the bypass interpretation. The bypassed hidden receiver
path reached native/decode, returned non-empty output, and preserved fallback,
crash, timeout, and side-effect visibility. The control-character finding is
only an output-quality classification detail.

Follow-up classification:
- newline-only `U+000A` should be considered separately from harmful control
  characters such as nulls, escapes, or replacement/invalid Unicode;
- if the quality classifier keeps a strict `control_chars=true` warning, it
  may need a subcategory such as newline-only formatting;
- `mixed_language` remains unresolved even if newline-only control chars are
  treated as acceptable, because the raw-dialog-tail output still contains
  probe/context text with ASCII terms;
- before more runtime, design the sanitizer/quality-classifier distinction and
  compare manual dialog-tail formatting against script `raw_dialog_tail`.

Earlier bypass validation guidance, retained for the original raw target 128
phase, was:
- template: `raw`
- target: `128`
- custom natural-language or echo-resistant prompt
- timeout: `60`
- max output tokens: `16`
- `--limit-cases 1`
- bypass flags enabled explicitly

Required outcome fields:
- timeout
- native reached
- prefill reached, if available
- decode reached
- NPU evidence
- fallback used
- fresh crash
- requested/effective max output tokens
- native max output limit
- prompt input code points
- final input chars
- template mode
- bypass requested/effective
- raw and sanitized output lengths
- sanitizer classification

### Phase 3: Larger Boundaries

Completed as separate guarded single-case checks:
- target `256`
- target `384`
- target `512`
- target `640`
- raw target `1024`
- raw target `2048`

Future larger targets still require separate approval or an explicit bounded
plan. Do not jump from a single-case result directly to a broad matrix.

## Classification Rules

| Observation | Classification |
| --- | --- |
| Validation rejects before native with bypass disabled | Expected 128 gate reject |
| Validation rejects before native with bypass requested but not effective | Bypass wiring failure |
| Native entry happens but prefill/decode does not | Native/runtime boundary candidate |
| `QNN_HTP_V79_FastRPC_native_diag` disappears after a target boundary | Backend handoff or graph/runtime boundary candidate |
| Decode succeeds but sanitizer empties output | Output/sanitizer issue, not sequence proof |
| Process crash or ANR with fresh crash evidence | Runtime stability issue, not app validation |
| target `512` reaches decode without graph/runtime failure | 512 sequential hypothesis weakens |

## Rollback Conditions

Stop and remove or disable the bypass path if any of the following happen:
- standard ChatScreen can activate it;
- Settings UI or persistent preferences expose it;
- DB/TTS/Markdown/streaming receives bypassed output;
- fallback is hidden or reclassified as success;
- more than one case runs when `--limit-cases 1` was intended;
- timeout does not force-stop the app;
- diagnostics are not saved;
- prompt length, bypass state, or template mode are missing from artifacts.

## Success Criteria

The bypass design is safe enough to implement only if:
- default validation behavior is unchanged;
- bypass cannot be activated outside the hidden/test receiver;
- one-case execution remains enforceable;
- timeout and force-stop are mandatory;
- artifacts distinguish app-side rejection, native entry, prefill/decode, and
  sanitizer outcomes;
- all side-effect prohibitions remain true.

## Relationship To The 512 Hypothesis

This bypass is not a product feature and not an NPU rollout step. Its only
purpose is to allow final-input lengths beyond the existing 128-codepoint app
gate to reach native so that runtime evidence can classify whether a graph,
prefill, decode, or QNN boundary exists near 512.

Until bypassed one-case probes reach the relevant final-input lengths, the 512
sequential hypothesis remains unclosed: unsupported by static strings, but not
directly disproven by runtime evidence.
