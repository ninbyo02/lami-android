# QAIRT244 Standard Hidden Experimental Plan

## Purpose

Move the proven qairt244 SM8750 NPU route from `customBuildExperimentDebug`
toward `standardDebug` as a hidden experimental path. This is not production
`Backend.NPU` enablement. It is a controlled developer-only bridge so
`./update.sh update`, which installs `standardDebug`, can exercise the same
bounded route without exposing it to normal users.

Current evidence from `customBuildExperimentDebug`:

- 128-token UTF-8 `internal_intent` Japanese prompts passed 3/3:
  `こんにちは`, `テスト`, `ラミィ`
- `max_output_tokens=128`
- `native_max_output_tokens_limit=128`
- `prompt_validation_mode=utf8_internal_intent`
- `native_prompt_validation_mode=utf8_internal_intent`
- `utf8_allowed=true`
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- UI cleanup succeeded

## Migration Scope

Move only the guarded qairt244 SM8750 route surface needed for a hidden
`standardDebug` experiment:

- model basename resolver and SM8750-only guard
- timestamp-prefixed SM8750 basename allowance
- 128-token bounded route constants
- ChatScreen route branch behind hidden gates
- Settings row visible only after developer access is enabled
- diagnostics for model basename, canonical basename, timestamp-prefix status,
  native token limit, RunDecode reachability, NPU evidence, fallback state, and
  UI cleanup

Do not move this into the normal selected-path NPU route. Do not expose generic
NPU candidate selection. Do not add automatic fallback in this phase.

## Step 2: Shared Logic Migrated

The first foundation step is to move only pure shared logic into the main
source set so `standardDebug` can compile it while still keeping the route
hidden and OFF:

- `Qairt244ModelPathResolver`: canonical/timestamp-prefixed SM8750 basename
  detection, qcs8275/generic/E2B/E4B rejection, candidate resolution, and model
  diagnostics fields
- `NpuDiagnosticPromptValidator`: ASCII diagnostic and UTF-8 internal-intent
  prompt validation, including empty/NUL/control/invalid UTF-8/32-code-point
  rejection

The following remain `customBuildExperimentDebug`-only until the hidden
standard route is implemented:

- `Qairt244DevOnlyNpuRouteAdapter`
- `DevQairt244PromptReceiver`
- `Qairt244ShortMultitokenSmoke` and other native smoke entrypoints
- custom manifest receiver/activity declarations
- native custom artifact packaging
- ChatScreen reflection entrypoint activation
- Settings visibility for the qairt244 NPU toggle

This step creates no standard user-visible UI, no standard ChatScreen NPU
execution branch, no production `Backend.NPU` promotion, and no automatic
fallback.

## Hidden Gate Options

The hidden route should require two layers:

1. A developer access gate that reveals experimental settings.
2. A qairt244 SM8750 route toggle that is default OFF.

Acceptable developer access gates:

- Version tap sequence: repeated taps on the app version or build row unlock
  developer settings locally.
- ADB flag: an app-private debug flag written by `adb shell run-as` for
  deterministic test setup.
- Developer setting: a persisted developer-settings switch after the hidden
  area is unlocked.

Recommended first implementation: support an ADB flag for automation and a
version tap sequence for manual testing. Both should only reveal the hidden
experimental Settings row; neither should enable NPU by itself.

## Settings Display

Normal users must not see any qairt244 NPU control in `standardDebug`.

Step 3 display-only plumbing:

- Developer access is local to DEBUG builds and is enabled by tapping the About
  screen version/build text seven times.
- With developer access OFF, `standardDebug` shows no qairt244 NPU Settings row.
- In Step 3, developer access ON made `standardDebug` show a disabled/read-only
  `実験的NPU（SM8750）` row that says `standard hidden experimental`, `まだ本適用
  ではありません`, and `ChatScreen route activation は次ステップ`.
- That row was display-only and did not write the qairt244 route toggle or
  activate ChatScreen execution.
- `customBuildExperimentDebug` keeps its existing `DEV: SM8750 NPU実験` toggle
  and does not show the standard hidden placeholder row, avoiding duplicate
  controls.
- The next step is ChatScreen hidden route activation behind the standard gate
  and SM8750 model guard.

Current Step 4 plumbing:

- With developer access ON, `standardDebug` lets the `実験的NPU（SM8750）`
  toggle write the existing `dev_enable_qairt244_sm8750_npu_route` key.
- The toggle remains default OFF and is hidden whenever developer access is
  OFF.
- ChatScreen uses the hidden qairt244 route only when
  `developer_access_enabled && dev_enable_qairt244_sm8750_npu_route` is true in
  `standardDebug`; `customBuildExperimentDebug` keeps its existing route gate.
- The standard hidden route validates normal ChatScreen input with UTF-8
  bounded prompt validation (`utf8_hidden_experimental`), allowing Japanese
  while retaining empty/NUL/control/invalid UTF-8/32-code-point rejection.
- The route keeps `max_output_tokens=128`, SM8750-only model basename guards,
  and explicit failure messages with no automatic fallback.
- The hidden NPU toggle is not auto-cleared after a ChatScreen conversation.

When developer access is enabled, Settings may show a hidden experimental row:

- Label: `Experimental: SM8750 NPU`
- Default: OFF
- Enabled only when the app detects exactly one acceptable SM8750 model:
  `gemma-4-E2B-it_qualcomm_sm8750.litertlm` or
  `<digits>_gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- Disabled with an explicit reason when the model is absent, ambiguous, generic,
  qcs8275, E4B, plain E2B, or non-numeric-prefixed
- Text must say this is hidden, experimental, SM8750-only, and not production
  NPU support

## ChatScreen Prompt Policy

The `customBuildExperimentDebug` evidence uses `internal_intent` for Japanese
UTF-8 prompts because shell text input is not reliable for non-ASCII. For
`standardDebug`, ChatScreen normal input must eventually support user-entered
Japanese directly through the UI path, not through the internal intent receiver.

Initial `standardDebug` acceptance can use ASCII UI prompts for the hidden
route, but Japanese ChatScreen normal-input support is required before any
broader exposure. The prompt validator must keep rejecting empty prompts, NUL,
invalid UTF-8, and prompts above 32 UTF-8 code points unless a later design
explicitly raises that prompt bound.

## Toggle Persistence

Do not automatically turn the NPU toggle OFF after each conversation in
`standardDebug`. The custom experiment auto-clear behavior was useful for
guarded one-shot runs, but a hidden experimental standard path should let the
developer keep the route enabled across repeated manual checks.

The route still needs duplicate-run protection and must clear ChatScreen
responding/stop UI state after success, failure, timeout, and exception.

## Failure UX

On failure, do not silently fallback. Show an explicit developer-facing error in
the ChatScreen conversation or a hidden diagnostics surface:

```text
Experimental SM8750 NPU failed: <reason>
```

Diagnostics should include:

- failure stage
- resolved model basename
- canonical model basename
- timestamp prefix stripped true/false
- native token limit
- RunDecode reached true/false
- NPU evidence
- fallback used false
- timeout/fresh crash state
- UI cleanup result

Manual GPU recovery should be obvious: turn the hidden NPU toggle OFF and retry
with the existing GPU/CPU local route.

## Fallback Policy

For the hidden `standardDebug` phase:

- no automatic NPU -> GPU -> CPU fallback
- no silent retry on GPU/CPU
- no persistence that makes a failed NPU attempt look like a normal local
  inference success

Future work may design explicit NPU -> GPU -> CPU fallback, but only after the
diagnostics can report which backend produced the final answer and after the
failure UX is reviewed.

## StandardDebug Acceptance Gates

The hidden `standardDebug` route passes only if all of these are true:

- hidden developer access required before the Settings row is visible
- qairt244 SM8750 toggle defaults OFF
- canonical and numeric timestamp-prefixed SM8750 model basenames are accepted
- qcs8275, generic, plain E2B, E4B, and non-numeric-prefixed files are rejected
- `max_output_tokens=128`
- `native_max_output_tokens_limit=128`
- ChatScreen normal input run succeeds at least once with `Hello`
- Japanese normal-input plan is documented before broader exposure
- `run_decode_reached=true`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- duplicate run is blocked
- UI cleanup succeeds with no stale `Responding...`, Stop button, or `応答中`
- route remains hidden and disabled for normal users

## Conditions That Must Not Reach Standard Users

Do not show or enable the route in normal Settings when:

- developer access is disabled
- the model is missing or ambiguous
- only qcs8275, generic, E4B, or plain E2B models are present
- the native artifact does not report the 128-token guard
- NPU evidence is absent or ambiguous
- fallback behavior is automatic or silent
- UI cleanup is unreliable
- the implementation raises token limits above 128
- the route is wired into normal selected-path NPU candidate selection
- packaging or update behavior would add `.so`, `.apk`, `.aar`, `.zip`,
  `.tar`, `.gz`, or `.litertlm` artifacts to Git

The next implementation step is Settings cleanup and hidden-gate plumbing for
`standardDebug`; it should not change native artifacts, token limits, or
fallback behavior.

## NPU Output Quality Investigation

As of 2026-05-24, the standard hidden route can reach NPU and RunDecode for
normal ChatScreen Japanese input, but the ChatScreen-visible text may be only
`。` for prompt `こんにちは`.

The current evidence points to an output plumbing issue, not an early NPU stop:

- Native output is multi-line and longer than the displayed text.
- The lower-level native result file writes `output=` followed by raw multi-line
  text.
- Kotlin `parseResultFile()` treats the result as one-line `key=value` records,
  so `output=。` is read as the adapter output and later lines are ignored.
- ChatScreen inserts the adapter/assistant text directly through the
  non-streaming hidden route.
- Markdown streaming repair and Edge Gallery compatible markdown repair are not
  applied on this path.
- `max_output_tokens=128` remains fixed, `RunDecode` is reached, and
  `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag` remains present.

Diagnostics now record these fields so the native/adaptor/display boundary is
visible in artifacts:

- `raw_native_output` and `raw_native_output_length`
- `adapter_output` and `adapter_output_length`
- `displayed_assistant_text` and `displayed_assistant_text_length`
- `finish_reason`, `stop_reason`, and `output_token_count` when exposed
- `max_output_tokens`, `decode_elapsed_ms`, `route_type`, and
  `prompt_source=chat_screen`
- `markdown_mode` and `repair_applied`

Do not start Edge Gallery prompt/template alignment or output rewriting yet.
The next minimal fix should preserve the native artifact and hidden route while
making the app-side result parser handle multi-line native output explicitly.

## Tokenizer / EOS Investigation

As of 2026-05-24,担当B added output-side Unicode and stop diagnostics only.
No output quality correction, token remapping, prompt template change, or decode
behavior change was implemented.

New artifact/display diagnostics:

- `output_token_count`
- `finish_reason`
- `stop_reason`
- `eos_detected`
- `output_contains_replacement_chars`
- `output_contains_control_chars`
- `output_unicode_summary`
- `output_first_200_chars`
- `output_last_200_chars`

`output_unicode_summary` records UTF-16 length, Unicode code point count, UTF-8
byte count after Kotlin string decoding, first code points, replacement-char
count, ISO-control chars, U+3007 white-circle count, question-mark count, and a
classification. This makes the `〇〇〇〇` case distinguishable from decode failure:
real `〇` appears as repeated `U+3007` with
`output_contains_replacement_chars=false`; decode replacement appears as
`U+FFFD` with `output_contains_replacement_chars=true`.

The `？`-only case is now easier to triage by checking:

- `output_unicode_summary` classification
  `single_question_mark_output`
- `output_token_count`, if exposed by the lower-level entrypoint
- `finish_reason` / `stop_reason`
- `eos_detected`
- `output_first_200_chars` and `output_last_200_chars`

Current conclusion: Kotlin receives already-decoded `String` output, so these
diagnostics can prove whether visible glyphs are real Unicode code points or
U+FFFD replacement chars after app-side decoding. They cannot prove tokenizer
ID-to-token mapping correctness unless the lower-level native entrypoint also
exports token IDs or raw token bytes. If future runs show `eos_detected=true`
with `output_token_count=0` or `1` and only `？`, investigate native stop/EOS
handling first. If `eos_detected=false`, output token count is non-zero, and
the first code points are real punctuation or U+3007, investigate tokenizer or
model decode mapping before adding any display-side rewrite.

## Prompt Formatting Investigation

As of 2026-05-24, the standard hidden ChatScreen route does not build a chat
conversation, system prompt, or chat template before calling the lower-level
native entrypoint. The path is:

- ChatScreen prompt text
- `DevOnlyNpuChatScreenBlockedBranch.runForChatScreen`
- `NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental`
- `Qairt244DevOnlyNpuRouteAdapter.runOnce`
- `Qairt244ShortMultitokenSmoke.runEditablePrompt`
- native `nativeRunEditablePrompt(prompt = normalizedPrompt)`

The effective model input is therefore the validator-normalized user prompt.
There is no conversation history flattening, no system message, and no
Gemma/Edge Gallery chat template insertion in this hidden route.

Diagnostics now record the prompt-formatting boundary in the pulled artifacts:

- `raw_user_prompt`
- `normalized_prompt`
- `final_model_input`
- `final_model_input_length`
- `conversation_history_count=0`
- `system_prompt_used=none`
- `chat_template_used=none`
- `prompt_source=chat_screen` or `prompt_source=internal_intent`
- `prompt_formatting_mode=raw_normalized_prompt`

These fields are emitted by the shared qairt244 adapter for both the standard
hidden ChatScreen path and the `customBuildExperimentDebug` internal-intent
path, so `result.txt`, `receiver_state.txt`, and display diagnostics can be
compared directly. A meaningful difference should be limited to source and
validation mode:

- standard hidden ChatScreen: `prompt_source=chat_screen`,
  `prompt_validation_mode=utf8_hidden_experimental`,
  `route_type=standard_hidden_chat_screen`
- internal intent runner: `prompt_source=internal_intent`,
  `prompt_validation_mode=utf8_internal_intent`, `route_type=internal_intent`

This investigation is intentionally separate from Edge Gallery compatible
Markdown mode. Markdown repair/compatibility is post-generation display
processing; it does not construct the prompt passed into native on this route.

Minimal fix proposal only: if output quality requires instruction-tuned
conversation formatting, add an explicit, guarded prompt-template experiment
with diagnostics that name the exact template. Do not silently change the
current hidden route's native input shape.

## Prompt Template Experiment

As of 2026-05-24, standardDebug hidden qairt244 can compare three prompt input
shapes without changing the native artifact, token limit, fallback policy, or
production backend selection. This remains hidden experimental only and is gated
by developer access plus the SM8750 NPU toggle.

Template modes:

- `raw`: pass the validator-normalized prompt as-is.
- `simple_ja_chat`: prepend `あなたは親切なAIアシスタントです。\nユーザー: `
  and append `\nアシスタント:`.
- `gemma_it_like`: wrap the prompt as
  `<start_of_turn>user\n<prompt>\n<end_of_turn>\n<start_of_turn>model`.

Scope:

- Applied only to the standard hidden ChatScreen qairt244 route.
- `customBuildExperimentDebug` internal-intent route remains `raw`.
- The Settings selector is visible only when developer access is enabled.
- Runner comparison can pass `--template raw|simple_ja_chat|gemma_it_like`.

Diagnostics added for comparison:

- `template_mode`
- `chat_template_used`
- `final_model_input`
- `final_model_input_length`
- `template_prefix_length`
- `template_suffix_length`

Quality diagnostics now also include `quality_classification` and
`replacement_char_count`. Classifications are diagnostic labels only:
`natural_japanese`, `template_artifact`, `repetitive_circles`,
`single_question_mark`, `mixed_language`, and `empty_output`.

The next decision should be based on same-prompt artifacts across all three
template modes. Prefer the smallest hidden-only change that improves output
quality while keeping `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, and `max_output_tokens=128`.

### 2026-05-24 Initial Same-Prompt Comparison

Prompt: `こんにちは`

Artifacts:

- `raw`: `artifacts/qairt244_standard_hidden_npu_route/20260524_213504`
- `simple_ja_chat`: `artifacts/qairt244_standard_hidden_npu_route/20260524_213513`
- `gemma_it_like`: `artifacts/qairt244_standard_hidden_npu_route/20260524_213541`

Observed result:

| template_mode | final_model_input_length | result | run_decode_reached | decode_elapsed_ms | quality_classification |
| --- | ---: | --- | --- | ---: | --- |
| `raw` | 5 | `success` | `true` | 3418 | `mixed_language` |
| `simple_ja_chat` | 38 | `adapter_failure:IllegalStateException` | `false` | n/a | `empty_output` |
| `gemma_it_like` | 60 | `adapter_failure:IllegalStateException` | `false` | n/a | `empty_output` |

The templated modes did not reach native decode. The Java-side editable prompt
guard rejected the expanded final input before native execution with
`reasonCode=too_long`. This is expected with the current 32-code-point prompt
guard: even `こんにちは` expands to 38 code points in `simple_ja_chat` and 60 in
`gemma_it_like`.

Current conclusion: the selected model likely still needs chat/instruction
formatting, but the existing bounded route cannot evaluate the requested
templates until there is a new explicitly bounded prompt-length phase. Do not
work around this by truncating templates or silently passing a different native
input. The next minimal step is a separate hidden experiment that raises only
the prompt-input bound enough for the named templates while preserving the
128-token output bound, UTF-8 safety checks, SM8750 model guard, NPU evidence,
and fallback prohibition.

### 2026-05-24 128 Output / 128 Input Bounded Phase

The prompt-template blocker is resolved by a hidden-only bounded prompt-input
phase. The output cap remains `max_output_tokens=128`; only the final model
input guard used by the standard hidden template experiment is raised to 128
UTF-8 code points.

This phase is still a standardDebug hidden experimental route, not
`Backend.NPU` promotion. The gate remains developer access plus
`dev_enable_qairt244_sm8750_npu_route`, the SM8750 model guard remains exact or
timestamp-prefixed SM8750 only, and fallback remains disabled.

New diagnostics required for template comparison:

- `prompt_input_code_points`
- `prompt_input_code_point_limit=128`
- `prompt_input_limit_mode=hidden_template_experiment`
- `native_prompt_input_code_point_limit=128`
- `native_prompt_input_limit_mode=hidden_template_experiment`

The hidden template validator keeps rejecting empty input, NUL, invalid UTF-8,
carriage returns, tabs, and non-template control characters. It permits line
feeds because both `simple_ja_chat` and `gemma_it_like` intentionally include
multi-line instruction formatting.

Native artifact for this phase:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt`

`liblitertlm_jni.so` sha256:
`4065d88c4788eaf28be140e133b7141783cad0698061c942b6942fa1fa886c2e`

JNI build log:
`artifacts/litert_custom_build/20260524_215218_qairt244_128token_128input_utf8prompt/build_logs/__kotlin_java_com_google_ai_edge_litertlm_jni_litertlm_jni.log`

Patch snapshot:
`patches/qairt244_litertlm_utf8_128token_128input.patch`

The next comparison should rerun `raw`, `simple_ja_chat`, and `gemma_it_like`
with `こんにちは` and treat the experiment as valid only if all three reach
native decode with NPU evidence and no fallback.

### 2026-05-24 128 Input Template Comparison

Prompt: `こんにちは`

Artifacts:

- `raw`: `artifacts/qairt244_standard_hidden_npu_route/20260524_220541`
- `simple_ja_chat`: `artifacts/qairt244_standard_hidden_npu_route/20260524_220551`
- `gemma_it_like`: `artifacts/qairt244_standard_hidden_npu_route/20260524_220559`

All three template modes reached native decode. Each run recorded
`prompt_input_code_point_limit=128`,
`native_prompt_input_code_point_limit=128`,
`native_prompt_input_limit_mode=hidden_template_experiment`,
`max_output_tokens=128`, `native_max_output_tokens_limit=128`,
`npu_backend=NPU`, `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`, and
`ui_cleanup_wait_status=success`.

| template_mode | input code points | decode_elapsed_ms | output length | quality_classification | note |
| --- | ---: | ---: | ---: | --- | --- |
| `raw` | 5 | 2756 | 277 | `mixed_language` | Template placeholders and Korean suffixes remain. |
| `simple_ja_chat` | 38 | 3203 | 276 | `natural_japanese` | More assistant-like content, but Thai text is mixed in. |
| `gemma_it_like` | 60 | 716 | 59 | `template_artifact` | Short useful Japanese answer, but turn markers leak into output. |

Current comparison result: the 128 input guard resolves the native-entry
blocker. Output quality is still not clean enough for a user-facing route; the
next minimal fix should focus on prompt/template post-boundary behavior, likely
stopping or stripping model turn markers for the hidden experiment before any
broader route promotion.
