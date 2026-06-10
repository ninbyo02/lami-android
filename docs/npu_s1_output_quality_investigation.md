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

Normal chat was later policy-unblocked after `Gemma recommended x20` passed and tombstone / dropbox checks were reviewed.

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
- `gemma_it_user_model_full_20_quality`
  - Uses the same prompt as `gemma_it_user_model`.
  - Runs 20 times and is the only current quality-gate pass profile for normal-chat return decisions.
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

Normal chat remained blocked until the separate output quality gate passed.

Current candidate gate preparation:

- `gemma_it_user_model` is the recommended candidate.
- `gemma_it_user_model` 3-run quality comparison has passed on device, but that is not enough for return decisions.
- Use `gemma_it_user_model_full_20_quality` / `Gemma recommended x20` before considering normal-chat return.
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

- `selected_quality_prompt_profile=gemma_it_user_model_full_20_quality`
- `run_count_completed=20`
- `success_count=20`
- `failure_count=0`
- `decode_success_count=20`
- `engine_close_reached=true`
- `engine_close_success=true`
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
- `npu_s1_quality_gate_run_count_required`
- `npu_s1_quality_gate_run_count_completed`
- `npu_s1_quality_gate_all_runs_passed`
- `npu_s1_quality_gate_20_run_status`
- `first_quality_failure_run_index`
- `first_quality_failure_reason`
- `failed_quality_run_count`

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
5. The route is promoted through an explicit policy-unblock code change.
6. `Gemma recommended x20` reports `npu_s1_quality_gate_status=pass`.

## Current Return Candidate Status

The latest device result confirmed the return-candidate quality profile:

- `selected_quality_prompt_profile=gemma_it_user_model_full_20_quality`
- `run_count_requested=20`
- `run_count_completed=20`
- `success_count=20`
- `failure_count=0`
- `decode_attempt_count=20`
- `decode_success_count=20`
- `engine_close_reached=true`
- `engine_close_success=true`
- `npu_s1_quality_gate_status=pass`
- `npu_s1_quality_gate_20_run_status=pass`
- `failed_quality_run_count=0`
- `output_quality_candidate_prepared_output=こんにちは！何かお手伝いできることはありますか？`
- `backend_evidence=QNN_HTP_V79_FastRPC_native_diag_persistent_holder`

This means `gemma_it_user_model` is the current normal-chat return candidate prompt family.
After tombstone / dropbox manual confirmation and a repeated `Gemma recommended x20` pass, the minimal policy-unblock
commit changed `NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED` to `true`.

- `ai_edge_gallery_like` remains an alias / duplicate for comparison and is not a gate-pass profile.
- `bos_eos_like_if_supported_by_existing_code` remains unsafe / not recommended.
- Normal chat native NPU route is no longer policy-blocked, but native / JNI / QAIRT / fallback / TTS / DB / streaming / markdown / sanitizer were not changed.

Final readiness diagnostics:

- `npu_s1_normal_chat_unblock_readiness_status`
- `npu_s1_normal_chat_unblock_readiness_reason`
- `npu_s1_normal_chat_unblock_required_profile`
- `npu_s1_normal_chat_unblock_required_20_run_gate`
- `npu_s1_normal_chat_unblock_policy_allowed`

With `NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED=true`, a fully passing crash + quality gate should report
`npu_s1_normal_chat_unblock_readiness_status=ready_and_policy_allowed`.

Next steps after the minimal policy unblock:

1. Send a simple normal-chat prompt such as `こんにちは`.
2. Confirm `reason=npu_s1_native_route_blocked_for_normal_chat` is gone.
3. Confirm `prompt_wrapper_used=gemma_it_user_model` or the equivalent Gemma IT final prompt:
   `<start_of_turn>user\n{user_prompt}<end_of_turn>\n<start_of_turn>model`.
4. Confirm `status`, `reason`, `run_decode_reached`, `fallback`, `fresh_crash`, `timeout`, `backend_evidence`, `raw_output`, and `sanitized_output`.
5. Manually compare the run time with tombstone / dropbox and confirm no new native crash.
6. Keep a feature flag / kill switch plan for any follow-up hardening.

The normal-chat policy unblock was allowed only after:

- `gemma_it_user_model_full_20_quality` or a successor profile repeatedly passes the 20-run quality candidate gate.
- The candidate gate rejects the known bad profiles:
  - `current_probe_quality`
  - `no_bos_no_eos`
  - `user_colon_assistant_colon`
  - `assistant_prefix_only`
  - `japanese_instruction_with_answer_prefix`
  - `bos_eos_like_if_supported_by_existing_code`
- The route retained the existing production sanitizer / stop-sequence behavior without native or fallback changes.

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

Normal chat policy unblock state:

- `NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED=true`
- `reason=npu_s1_native_route_blocked_for_normal_chat` should no longer appear for the normal S1 native route.

Crash-safety pass alone is not enough to unblock normal chat. The separate output quality gate and manual tombstone / dropbox checks were required before this policy unblock.

## Normal Chat Success Display Fix

Post-unblock device verification showed normal chat reaching the native S1 route and decoding successfully:

- `status=success`
- `reason=success`
- `raw_output=>こんにちは！何かお手伝いできることはありますか？<end_of_turn>`
- `sanitized_output=こんにちは！何かお手伝いできることはありますか？`
- `run_decode_reached=true`
- `fallback=false`
- `fresh_crash=false`
- `timeout=false`

The remaining UI issue was a false failure message:
`NPU推論の応答生成に失敗しました: success`.
The cause was that the raw output still contained safe template artifacts (`>` and `<end_of_turn>`), so
`quality_classification=template_artifact` could make `successCriteriaMet=false` even though the sanitized output was natural.

The normal-chat UI now treats this narrow case as success when all of the following are true:

- `status=success`
- `reason=success`
- `run_decode_reached=true`
- `fallback=false`
- `fresh_crash=false`
- `timeout=false`
- `sanitized_output` is non-empty
- `quality_classification=template_artifact`
- `output_quality_candidate_status=quality_candidate_pass`

The assistant text shown in normal chat uses `sanitized_output` first, then prepared output, then raw output as a final fallback.
DEV diagnostics still keep `raw_output`, `sanitized_output`, `quality_classification`,
`output_quality_candidate_status`, `output_quality_candidate_reason`, and
`output_quality_candidate_prepared_output` so raw artifacts remain inspectable.

## Normal Chat Quality Hardening After Unblock

After normal chat was restored to the native S1 route, device checks showed:

- `こんにちは` displays successfully.
- `あなたは誰ですか` can succeed, but can also return `adapter_failure:LiteRtLmJniException`.
- `１＋１は` succeeds with `１＋１は２です`.
- `１＋１は？` can leak turn markers and repeat the prompt:
  - `raw_output=１＋１は？<end_of_turn>\n<start_of_turn>user１＋１は？<end_of_turn`
  - `sanitized_output=１＋１は？\n１＋１は？<end_of_turn`
- One app crash was observed during the `１＋１は？` diagnostic pass.

Because device logcat is not reliable for this investigation, the next step is app-internal diagnostics rather than
logcat-based analysis. Normal chat S1 now records a lightweight in-app history around each native request:

- `last_npu_s1_request_started_at_elapsed_realtime_ms`
- `last_npu_s1_request_finished_at_elapsed_realtime_ms`
- `last_npu_s1_prompt`
- `last_npu_s1_final_prompt_tail`
- `last_npu_s1_stage`
- `last_npu_s1_status`
- `last_npu_s1_reason`
- `last_npu_s1_exception_class`
- `last_npu_s1_exception_message`
- `last_successful_npu_s1_prompt`
- `last_failed_npu_s1_prompt`

The quality candidate gate now fails normal-chat output when sanitized or prepared output still contains special turn
markers, when raw output contains broken turn markers, when a user turn leaks into output, when the prompt is only
repeated, or when the limited arithmetic prompts (`1+1`, `1+1は？`, `１＋１`, `１＋１は？`) do not contain `2` / `２`.

For short arithmetic prompts, normal chat keeps the Gemma IT wrapper but rewrites the user turn before native execution:

```text
<start_of_turn>user
次の計算に日本語で答えてください。答えだけ簡潔に書いてください。
問題: １＋１は？
答え:<end_of_turn>
<start_of_turn>model
```

Expected diagnostics for `１＋１は？` after the rewrite:

- `selected_prompt_profile=gemma_it_user_model`
- `prompt_wrapper_used=gemma_it_user_model`
- `arithmetic_prompt_detected=true`
- `short_prompt_rewrite_applied=true`
- `rewritten_prompt_tail=...問題: １＋１は？...答え:`
- `final_prompt_tail=...<start_of_turn>model`

If quality fails, the broken raw / sanitized output is not used as the normal chat assistant body. It remains available in
DEV diagnostics alongside the prepared output and fail reason.

Follow-up device checks showed that ASCII arithmetic prompts can still include the repeated problem before the answer:

- `raw_output=>1+1は\n答え:2</end_of_turn>`
- `sanitized_output=1+1は\n答え:2`

For arithmetic prompts only, the quality candidate extractor now treats the final `答え:` / `答え：` line as the answer
boundary and prepares the shortest `2` / `２` answer. This keeps general chat untouched while normalizing:

- `1+1は` -> final display `2`
- `1+1は?` -> final display `2`
- `１＋１は` -> final display `２`
- `１＋１は？` -> final display `２`
- `問題: 1+1は\n答え:2` -> final display `2`

The extractor is paired with the existing safe `end_of_turn` cleanup, including closing-tag variants such as
`</end_of_turn>` and `</ end_of_turn>`. It does not relax the failure rules for `<start_of_turn>` leaks, user-turn leaks,
unremovable special tokens, or arithmetic outputs that do not contain `2` / `２`.

No automatic NPU pause / guard for consecutive failures or recent crashes is implemented in this step. That remains a
separate follow-up after the app-internal history has enough evidence.

## Normal Chat S1 DEV Diagnostic Copy Layout

The normal-chat S1 diagnostic copy is split into three scopes so successful chat runs remain easy to copy while failure
cases still preserve enough evidence for investigation.

`[DEV診断: NPU S1 compact]` is always emitted. It keeps the prompt tail/profile, arithmetic rewrite flags, raw and
sanitized output, prepared output, quality candidate status/reason, status/reason, timing, decode/fallback/crash flags,
exception class/message, and native stage history. It intentionally omits fields that are usually `unknown`,
`unavailable`, or not useful for a successful one-shot chat copy, such as model unknown fields, tokenizer unavailable
fields, stop/finish/eos unavailable fields, repeated-run summaries, and persistent engine summaries.

`[DEV診断: NPU S1 failure details]` is emitted only when S1 does not meet success criteria, quality candidate fails,
adapter failure appears, timeout/fresh crash/fallback happens, special-token leak is detected, or native error fields are
available. This section carries full prompt text, rewritten prompt text/tail, native error details, failure stage, quality
reason/prepared output, native stage history, and selected app-internal history keys such as the last started/finished
elapsed time, last prompt, last final prompt tail, last native stage, last successful/failed prompt, and successful request
count.

`[DEV診断: NPU S1 full dump]` remains available as a separate formatter for the verbose legacy dump. It is the place for
fields such as selected-model unknown values, `finish_reason=unavailable`, tokenizer/model-reported unavailable values,
stop-sequence fields, and short-output telemetry. Normal chat does not append this full dump to the ordinary copy by
default.

The ordinary normal-chat `診断コピー` action uses the compact/failure copy. Repeated-run, persistent Engine, persistent
custom JNI, memory recovery, and full-dump sections remain available through their dedicated DEV UI sections or formatter
helpers, but they are not appended to the normal chat copy by default. This change is Kotlin diagnostics only; native
JNI, QAIRT overlays, fallback behavior, TTS, DB, streaming, markdown, logcat handling, and automatic NPU pause guards are
unchanged.

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
