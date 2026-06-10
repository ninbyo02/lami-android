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

## Current Prompt Comparison Result

Observed so far:

- `gemma_it_user_model`
  - `final_prompt_text=<start_of_turn>user\nこんにちは<end_of_turn>\n<start_of_turn>model`
  - `raw_output=>こんにちは！何かお手伝いできることはありますか？<end_of_turn>`
  - `sanitized_output=こんにちは！何かお手伝いできることはありますか？`
  - 3/3 success, no crash
  - current recommended candidate profile
- `ai_edge_gallery_like`
  - currently the same prompt shape as `gemma_it_user_model`
  - treat as an alias / duplicate until the Gallery prompt path diverges
- `current_probe_quality`, `raw_prompt_quality`, `no_hidden_template_quality`
  - `prompt=こんにちは`
  - repeated template-like output
  - starts with punctuation
  - contains `いつもお世話になっております`
  - contains `[あなたの名前]`
- `simple_ja_chat_quality`
  - `prompt=こんにちは。あなたは誰ですか？`
  - empty output
- `simple_ja_arithmetic_quality`
  - `prompt=1+1は？`
  - newline-only output
- `short_ja_self_intro_quality`
  - `prompt=日本語で短く自己紹介してください。`
  - Japanese text appears, but still looks close to repeated template output

This means the next comparison should focus on prompt wrappers and decode start anchoring rather than crash safety.

## Prompt Wrapper Profiles

New DEV-only wrapper profiles are Kotlin-side prompt strings. They do not require a native rebuild.

- `gemma_it_user_model`
  - Uses `<start_of_turn>user ... <end_of_turn>` and `<start_of_turn>model`.
  - Checks whether Gemma instruction-turn boundaries align generation.
- `gemma_it_start_turn`
  - Uses start-turn markers without the user end-turn boundary.
  - Checks whether a shorter turn prefix changes decode start behavior.
- `ai_edge_gallery_like`
  - Uses a Gallery-like Gemma turn wrapper.
  - Checks whether Gallery-style prompt shape improves quality.
- `user_colon_assistant_colon`
  - Uses `User:` / `Assistant:` text roles.
  - Checks whether plain text role anchors reduce template leakage.
- `assistant_prefix_only`
  - Uses the raw prompt followed by `Assistant:`.
  - Checks whether only the answer prefix is enough.
- `japanese_instruction_with_answer_prefix`
  - Uses Japanese instruction, question, and `回答:`.
  - Checks whether a Japanese answer anchor improves output.
- `no_bos_no_eos`
  - Keeps the raw prompt with no special markers.
  - Baseline for explicit no-special-token behavior.
- `bos_eos_like_if_supported_by_existing_code`
  - Uses textual `<bos>` / `<eos>` markers only.
  - This is not real token-id insertion; native support would require a separate patch.
  - Observed `engine_create_failed`; treat as unsafe / not recommended.

Each wrapper profile runs `run_count=3` for quick comparison. `current_probe_quality` keeps `run_count=20` for stability confirmation.

## Success Criteria

At least one profile must satisfy:

- output is not empty
- output is not newline-only
- no placeholder leak
- no business template leak
- output broadly responds to the prompt
- `output_equals_across_runs` is not always fixed, or fixed output is a natural short sentence

Normal chat must remain blocked until a separate output quality gate passes.

Current candidate gate preparation:

- `gemma_it_user_model` is the recommended candidate.
- Leading `>` before a natural answer is considered safely removable in DEV gate preparation.
- `<end_of_turn>` is considered safely removable in DEV gate preparation.
- The prepared output must still be non-empty natural Japanese after those two removals.
- `ai_edge_gallery_like` is currently a duplicate of `gemma_it_user_model`.
- `bos_eos_like_if_supported_by_existing_code` is not recommended because it failed at engine create.

## Quality Gate Status

The DEV diagnostics now separate the quality candidate result from the crash-safety promotion gate.
This is still diagnostics-only; it does not unblock normal chat.

- `quality_candidate_pass`
  - `raw_output` and `sanitized_output` are non-empty.
  - The prepared output remains non-empty after safe leading `>` and `<end_of_turn>` cleanup.
  - No square-bracket placeholder leak is detected.
  - No business-template phrase is detected.
  - No `Assistant:` repetition is detected.
  - No Q/A continuation pattern is detected.
- `quality_candidate_fail`
  - At least one required condition fails.
  - This includes empty output, newline-only output, placeholder leak, business-template leak, `Assistant:` repetition, or Q/A continuation.
- `quality_candidate_unknown`
  - The candidate output has not been collected yet.

The Gemma candidate quality gate passes only when:

- `selected_quality_prompt_profile=gemma_it_user_model`
- `output_quality_candidate_status=quality_candidate_pass`
- `output_empty=false`
- `output_only_newline=false`
- `output_contains_placeholder=false`
- `output_looks_business_template=false`
- `output_quality_candidate_assistant_repetition=false`
- `output_quality_candidate_qa_continuation=false`

`ai_edge_gallery_like` remains an alias / duplicate for comparison, but it is not a gate-pass profile.
Even if its output is currently equivalent to `gemma_it_user_model`, `npu_s1_quality_gate_status` must remain fail
until a separate production prompt path decision promotes it explicitly.

The DEV copy keys are:

- `npu_s1_quality_gate_status`
- `npu_s1_quality_gate_reason`
- `npu_s1_quality_gate_prompt_profile`

Known bad profile handling:

- `current_probe_quality`: legacy / failing; repeated template output, punctuation start, placeholder/business template leak.
- `raw_prompt_quality`: legacy / failing until proven otherwise.
- `simple_ja_arithmetic_quality`: failing if output is empty, newline-only, or literal `\n`.
- `short_ja_self_intro_quality`: failing if output contains self-introduction template markers such as `〇〇`, `---`, or `**自己紹介`.
- `no_bos_no_eos`: legacy / failing; same no-wrapper failure class as the raw prompt path.
- `user_colon_assistant_colon`: legacy / failing; `Assistant:` repetition.
- `assistant_prefix_only`: legacy / failing; `Assistant:` repetition.
- `japanese_instruction_with_answer_prefix`: legacy / failing so far; Q/A continuation risk.
- `bos_eos_like_if_supported_by_existing_code`: unsafe / not recommended; observed engine create failure with `LiteRtLmJniException`.

The candidate helper rejects these patterns across `raw_output`, `sanitized_output`, and prepared output:

- empty, whitespace-only, newline-only, or literal `\n` only output
- `〇〇`
- `---`
- `**自己紹介`
- `質問:`
- `回答:`
- `Assistant: Assistant:`

Normal chat native NPU route restoration remains prohibited in this phase.

## Normal Chat Return Conditions

Before native NPU can return to normal chat:

1. Crash-safety gate remains pass.
2. A prompt wrapper profile produces natural Japanese output.
3. The output does not contain placeholders or business template leakage.
4. The output is not empty or newline-only for simple prompts.
5. The route is promoted through an explicit code change; `NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED=false` must not be changed by diagnostics work.

Do not restore normal chat native NPU until:

- `gemma_it_user_model` or a successor profile repeatedly passes the quality candidate gate.
- The candidate gate rejects the known bad profiles:
  - `current_probe_quality`
  - `no_bos_no_eos`
  - `user_colon_assistant_colon`
  - `assistant_prefix_only`
  - `japanese_instruction_with_answer_prefix`
  - `bos_eos_like_if_supported_by_existing_code`
- The route has a dedicated production sanitizer / stop-sequence plan reviewed separately.

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
