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

### Phase 1: No-Bypass Rejection Check

Design intent only; do not run as part of this document.

Run raw target `128` with no bypass and confirm:
- app-side validation rejects before native;
- native/decode do not run;
- summary classifies it as `native_pre_reject_expected_by_128_gate=true`.

This proves the classification boundary before enabling bypass.

### Phase 2: First Bypassed Native Case

Requires separate approval and implementation first.

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
