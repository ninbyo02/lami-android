# QAIRT244 Dev-Only 128 Gate Bypass Design

Date: 2026-05-29

Scope: design only. This document does not implement a validation bypass,
does not run runtime probes, does not build or install an APK, and does not
connect Backend.NPU to any standard ChatScreen route.

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

Requires separate approval before runtime execution.

Run exactly one case:
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

Only after Phase 2 succeeds safely:
- target `256`
- target `384`
- target `512`

Each target requires separate approval or an explicit bounded plan. Do not jump
from target `128` directly to a broad matrix.

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
