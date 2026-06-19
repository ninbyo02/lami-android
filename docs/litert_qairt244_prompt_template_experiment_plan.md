# LiteRT QAIRT244 Prompt Template Experiment Plan

This is a design plan only. It does not implement code, run runtime checks,
install APKs, change native code, change decode behavior, or promote the route.

## Purpose

The NPU standard route now receives the real `ChatScreen` user prompt, but the
current response quality is uneven:

| input | current observation |
| --- | --- |
| `こんにちは` | `こんにちは。` |
| `明日の天気は` | `明日の天気は` |
| `こんばんは` | `mixed_language` |

The goal of the next experiment is to compare prompt-template behavior without
changing native code, decode settings, fallback policy, DB/TTS/Markdown/
streaming behavior, or route promotion state.

## 1. Current `raw_dialog_tail_variant_b`

`raw_dialog_tail_variant_b` currently uses app-facing template mode `raw` and
builds a visible text transcript:

```text
必ず日本語だけで短く返答してください。
ユーザー: <user prompt>
アシスタント: はい、
```

Properties:

- `RealNpuStandardRouteS1Provider` passes the real user prompt into
  `DevOnlyNpuOneTurnConversationRequest`.
- `promptTailVariant=raw_dialog_tail_variant_b`.
- `max_output_tokens=32`.
- `unsafeDevBypassPromptLengthGate=true`.
- `contextText=""`.
- The assistant role is not a structured model-native role. It is text inside a
  raw prompt.

Current interpretation:

- The tail is strong enough to produce `こんにちは。` for `こんにちは`.
- It is weak against question echo: `明日の天気は` can be continued as the same
  phrase.
- It is weak against malformed or mixed-language generation for some greetings.

This remains the baseline for any comparison.

## 2. `simple_ja_chat` Reuse Potential

`simple_ja_chat` previously used a Japanese assistant-style wrapper similar to:

```text
あなたは親切なAIアシスタントです。
ユーザー: <prompt>
アシスタント:
```

Past observations:

- Initial hidden-route comparison expanded `こんにちは` to 38 code points.
- Under the older 32-code-point prompt guard, it failed before native decode
  with `editable prompt rejected before native execution: reasonCode=too_long`.
- Under the later 128-input hidden-template experiment, it reached native decode
  with NPU evidence.
- It improved assistant-like behavior, but output quality was still not clean;
  previous docs recorded mixed non-Japanese content risk even when the
  classifier result looked better than raw.

Reuse assessment:

- Worth re-testing as a developer-only comparison.
- Not safe as the default standard route template until it proves lower
  `mixed_language`, lower prompt echo, and no new template artifacts across the
  prompt matrix.
- Must record final prompt length and validation mode because this template is
  longer than `raw_dialog_tail_variant_b`.

## 3. `gemma_it_like` Reuse Potential

`gemma_it_like` previously used Gemma-style turn markers.

Past observations:

- Initial hidden-route comparison expanded `こんにちは` to 60 code points.
- Under the older 32-code-point prompt guard, it failed before native decode
  with `reasonCode=too_long`.
- Under the later 128-input hidden-template experiment, it reached native decode
  and produced a short useful Japanese answer.
- The output was classified as `template_artifact` because turn markers leaked
  into generated text.

Reuse assessment:

- It may align better with the model than plain raw text.
- It is higher risk than `simple_ja_chat` for the standard route because leaked
  turn markers need reliable stop-boundary or sanitizer handling.
- It should remain developer-only until turn-marker leakage is measured and
  handled without hiding malformed output.

## 4. Editable Prompt Rejection Recheck

The comparison must explicitly distinguish three cases:

1. Prompt rejected before native execution.
2. Native decode reached but output is low quality.
3. Native decode reached and output is acceptable.

Required diagnostics:

- `prompt_validation_mode`
- `prompt_source`
- `prompt_tail_variant` or `template_mode`
- `request_prompt_length`
- `request_prompt_code_points`
- `final_input_code_points`
- `prompt_transport=base64`
- `run_decode_reached`
- `npu_backend_evidence`
- `fallback_used`
- `timeout`
- `fresh_crash`

If `editable prompt rejected before native execution` appears, the experiment is
not measuring generation quality for that template. It is measuring prompt
validation behavior.

## 5. `too_long` Recheck

Past template failures were tightly coupled to input length:

| template | previous `こんにちは` final input length | old result |
| --- | ---: | --- |
| `raw` | 5 | native decode reached |
| `simple_ja_chat` | 38 | `too_long` under old 32-code-point guard |
| `gemma_it_like` | 60 | `too_long` under old 32-code-point guard |

The standard route comparison must record whether current dev-only NPU
conversation validation still bypasses only the intended length gate:

- `raw_dialog_tail_variant_b` should remain the baseline and should not hit
  `too_long` for the planned short prompts.
- `simple_ja_chat` may exceed the old 32-code-point guard, so the run is valid
  only if the current route records the explicit dev-only bypass and reaches
  decode.
- `gemma_it_like` must be treated the same way and must not silently truncate
  the prompt.

Do not fix `too_long` by shortening templates in-place. If a template is too
long under a given guard, record that as the result for that condition.

## 6. Comparison Method

### Inputs

Run each template candidate against the same prompt set:

| prompt | purpose |
| --- | --- |
| `こんにちは` | known baseline greeting success |
| `おはよう` | greeting variant |
| `明日の天気は` | echo-prone incomplete question |
| `あなたは誰ですか` | role/instruction-following probe |

Optional later expansion:

- `こんばんは`
- `ありがとう`
- `q`
- `え`

### Template Candidates

Minimum comparison set:

| candidate | role |
| --- | --- |
| `raw_dialog_tail_variant_b` | current baseline |
| `simple_ja_chat` | Japanese chat wrapper candidate |
| `gemma_it_like` | model-style instruction wrapper candidate |

If implementation work is later approved, expose these only through a
developer-only diagnostic selector. Do not add a user-facing Settings selector
until the matrix is complete.

### Fixed Axes

Keep these fixed for the first matrix:

- same model artifact;
- same device/backend;
- `max_output_tokens=32`;
- `prompt_transport=base64`;
- `fallback_used=false`;
- no DB save requirement;
- no TTS;
- no Markdown-specific acceptance;
- no pseudo streaming;
- no native/decode changes.

### Recorded Fields

For each run:

- `template_mode` or `prompt_tail_variant`
- `input_hash`
- `input_length`
- `input_code_points`
- `input_preview`
- `request_prompt_hash`
- `request_prompt_length`
- `request_prompt_code_points`
- `final_input_code_points`
- `run_decode_reached`
- `npu_backend_evidence`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `status`
- `reason`
- `raw_output_hash`
- `raw_output_length`
- `raw_output_code_points`
- `raw_output_preview`
- `sanitized_output_hash`
- `sanitized_output_length`
- `sanitized_output_code_points`
- `sanitized_output_preview`
- `quality_classification`
- `replacement_char_count`
- `output_contains_control_chars`
- `removed_prompt_echo`
- `removed_template_token_count`
- `stop_reason`, if available
- `finish_reason`, if available
- `eos_detected`, if available

Do not log the full prompt or full raw output.

## 7. Evaluation

Primary metrics:

- natural Japanese success count;
- `mixed_language` rate;
- `empty_after_sanitize` rate;
- prompt echo rate;
- template artifact rate;
- native decode reach rate;
- timeout/fresh crash rate.

Per-prompt pass criteria:

- `run_decode_reached=true`;
- `npu_backend_evidence` contains QNN HTP/FastRPC evidence;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- `sanitized_output_length > 0`;
- `quality_classification=natural_japanese`;
- output is not just a user-prompt echo;
- output does not contain leaked template markers.

Failure classifications:

| condition | classification |
| --- | --- |
| prompt rejected before decode | validation failure |
| `too_long` | prompt-length failure |
| raw malformed / mixed Unicode | generation quality failure |
| sanitized empty | sanitizer-visible generation failure |
| prompt echo only | answer quality failure |
| leaked Gemma/chat markers | template artifact |

Decision guidance:

- If `simple_ja_chat` lowers echo and `mixed_language` without marker leakage,
  it becomes the leading candidate for a later dev-only standard-route trial.
- If `gemma_it_like` produces concise answers but leaks markers, do not promote
  it until stop/sanitizer handling is explicitly designed.
- If raw remains best for greetings but fails questions, add a raw no-echo
  variant before switching to structured templates.

## 8. Rollback

Runtime rollback:

- keep NPU standard route mode `OFF` for normal users;
- or return S1 to `raw_dialog_tail_variant_b` only.

Code rollback for a future implementation:

- remove the developer-only template selector;
- restore `RealNpuStandardRouteS1Provider` to fixed
  `promptTailVariant=raw_dialog_tail_variant_b`;
- remove any experimental template matrix UI or result writer;
- keep `simple_ja_chat` and `gemma_it_like` as legacy diagnostics only.

No rollback should require:

- DB migration;
- TTS cleanup;
- Markdown cleanup;
- streaming cleanup;
- native artifact change;
- decode setting change;
- `Backend.NPU` persistence change.

## Recommended Next Step

Do not change the production prompt template yet.

First implement a dev-only comparison matrix that can run:

```text
raw_dialog_tail_variant_b
simple_ja_chat
gemma_it_like
```

against:

```text
こんにちは
おはよう
明日の天気は
あなたは誰ですか
```

Then choose the next prompt template only from measured native-decode results,
not from single-prompt behavior.
