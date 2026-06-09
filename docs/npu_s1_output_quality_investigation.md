# NPU S1 Output Quality Investigation

## Purpose

NPU S1 persistent custom JNI `full_20` now validates the crash-safety hypothesis:

- `engine_create_count=1`
- `run_count_completed=20`
- `success_count=20`
- `failure_count=0`
- `decode_success_count=20`
- `engine_close_success=true`
- `backend_evidence=QNN_HTP_V79_FastRPC_native_diag_persistent_holder`

This means the current blocker is not the repeated `EngineFactory::CreateDefault` crash. The current blocker is output quality.

## Current Observation

For `prompt=こんにちは`, persistent custom JNI output has looked like:

```text
。お元気ですか。いつもお世話になっております。[あなたの名前]です...
```

This is suspicious because it starts with punctuation and looks like a business template or placeholder leak rather than a natural greeting response.

The latest diagnostics make prompt/template contamination unlikely:

- `final_prompt_text=こんにちは`
- `final_prompt_length_chars=5`
- `system_template_used=false`
- `hidden_template_used=false`
- `prompt_wrapper_used=none`

Despite that, the output can still repeat the same template-like text across all runs:

- `output_repeats_same_across_runs=true`
- `output_quality_reason=starts_with_punctuation+business_template_phrase+placeholder_leak+same_output_repeated`

The primary suspect is now decode/tokenizer/token-boundary behavior rather than prompt wrapping.

## Prompt Path

The current DEV persistent custom JNI probe uses:

1. `NPU_S1_REPEATED_RUN_DEFAULT_PROMPT`
2. `NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(...)`
3. `validation.normalizedPrompt`
4. `Qairt244ShortMultitokenSmoke.runPersistentProbe(...)`
5. native `RunPrefill` receives the final prompt text

The Kotlin validator does not add a system prompt or assistant/user wrapper. The diagnostics now record:

- `prompt_input_limit_mode`
- `final_prompt_text`
- `final_prompt_length_chars`
- `final_prompt_tail_preview`
- `system_template_used`
- `hidden_template_used`
- `prompt_wrapper_used`
- `prefill_text_or_token_note`

Normal chat is still intentionally blocked and is not using this native route.

## Quality Diagnostics

The DEV copy now records summary quality keys:

- `first_output_chars`
- `output_prefix_classification`
- `output_quality_reason`
- `output_repeats_same_across_runs`
- `output_looks_business_template`
- `output_starts_with_punctuation`
- `output_contains_placeholder`

Each `full_20` run detail also records:

- `output_prefix_20_chars`
- `prefill_input_text`
- `prefill_input_chars`
- `decode_first_chunk_text`
- `decode_first_non_empty_chunk_text`
- `output_first_1_char`
- `output_first_5_chars`
- `output_first_20_chars`
- `output_last_20_chars`
- `output_length_chars`
- `output_newline_count`
- `output_leading_punctuation_count`
- `output_trimmed_first_chars`
- `output_after_lstrip_first_chars`
- `output_equals_across_runs`
- `starts_with_punctuation`
- `contains_business_phrase`
- `contains_placeholder`
- `quality_classification`

Token ids and token text are reported as `unavailable` for now:

- `prefill_token_count`
- `decode_token_count`
- `first_output_token_id`
- `first_output_token_text`
- `first_5_output_token_ids`
- `first_5_output_token_texts`
- `eos_seen`
- `bos_seen_in_output`
- `special_token_seen_in_output`

Reason:

```text
token_ids_not_exposed_by_current_custom_jni_probe_without_native_rebuild
```

The DEV UI can also run shorter prompt comparison profiles without a native rebuild:

- `current_probe_quality`: `こんにちは`, 20 runs
- `raw_prompt_quality`: `こんにちは`, 3 runs
- `simple_ja_chat_quality`: `こんにちは。あなたは誰ですか？`, 3 runs
- `simple_ja_arithmetic_quality`: `1+1は？`, 3 runs
- `short_ja_self_intro_quality`: `日本語で短く自己紹介してください。`, 3 runs
- `no_hidden_template_quality`: `こんにちは`, 3 runs

## Suspected Causes

Primary suspects:

- decode start offset / first-token boundary mismatch
- tokenizer output boundary handling
- special token handling around BOS/EOS or generated prefix
- input limit / max token budget mismatch
- prefill text handling
- model-specific behavior with very short Japanese greeting prompts

The Dispatch / CompilerPlugin / QAIRT overlay mismatch is considered the likely cause of the earlier `SIGABRT`, but not of the current template-like output.

## Release Policy

Normal chat remains blocked by policy:

- `NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED=false`
- `reason=npu_s1_native_route_blocked_for_normal_chat`

Crash-safety pass is not enough to unblock normal chat. A separate output quality gate is required before any native NPU route is restored to normal conversation.

## Next Device Check

Run:

1. `DEV診断 -> NPU S1 persistent custom JNI`
2. `full_20`
3. Copy diagnostics
4. Check:
   - `final_prompt_text`
   - `prompt_wrapper_used`
   - `prefill_input_text`
   - `decode_first_chunk_text`
   - `first_output_chars`
   - `output_prefix_classification`
   - `output_quality_reason`
   - `output_repeats_same_across_runs`
   - `output_leading_punctuation_count`
   - `token_diagnostics_note`
   - run detail `output_prefix_20_chars`
   - run detail `quality_classification`

If `output_repeats_same_across_runs=true` and the same business template appears in all 20 runs, investigate prompt/prefill/decode-start handling before any normal chat unblock.
