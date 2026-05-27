# QAIRT244 NPU Max Output Token Limit Investigation

Date: 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

Scope: static investigation only. This pass did not change native code, did
not rebuild QAIRT/LiteRT-LM, did not execute NPU generation, did not call
`Engine.initialize`, and did not call `RunDecode`.

## Max512 Per-Run Isolated Formalization - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_per_run_isolated_formalization/20260527_215325/`

Scope: runner/gate/docs/tests formalization only. No additional NPU execution,
sequential 512 rerun, Activity-restart rerun, native change, QAIRT rebuild,
ChatScreen promotion, assistant-list insertion, DB, TTS, Markdown renderer,
streaming, selectedPath=NPU persistence, release behavior, or standard
behavior change was performed.

Formal mode matrix:

- `hidden_experimental_256`: maintained as the hidden experimental baseline
  candidate.
- `hidden_per_run_isolated_512`: the only accepted 512 hidden candidate mode.
  It requires force-stop before and after each prompt, `max_output_tokens=512`,
  `RunDecode` and `SetMaxOutputTokens(512)` evidence, `timeout=false`,
  `fresh_crash=false`, `fallback_used=false`,
  `QNN_HTP_V79_FastRPC_native_diag`, `Engine.close=unique_ptr_cleanup`,
  cleanup evidence, no retained process after 10 seconds, code-aware sanitizer,
  preserved indentation, completed/closed code fence, `selectedPathSaved=false`,
  assistant-list insertion false, and DB/TTS/Markdown/streaming false.

Rollback remains mandatory for sequential 512, Activity-restart-only 512,
timeout, missing cleanup, retained memory/process, broken indentation,
incomplete fence, fresh crash, fallback, selectedPath persistence,
assistant-list insertion, or DB/TTS/Markdown/streaming ingress.

Decision: 512 is formalized as hidden-only `mode=hidden_per_run_isolated_512`.
It is not a sequential baseline, not Activity-restart-only baseline, not H1,
not normal ChatScreen, not standard/release behavior, and not a path to
1024/2048/4096. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`.

## Max512 Activity Restart Only Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/`

The three approved hidden prompts ran once each at `max_output_tokens=512` with
code-aware sanitizer enabled and a bounded 60 second timeout. The runner used
Activity finish/relaunch between prompts and explicitly did not issue app
process force-stop.

Results:

- `こんにちは`: success, `natural_japanese`, `decode_ms=834`,
  `elapsed_ms=2000`
- `Pythonで簡単な電卓コードを書いて`: timeout, `elapsed_ms=70000`,
  pre-RunDecode `SetMaxOutputTokens(512)` evidence present, no completed
  backend result, raw output, sanitized output, cleanup, or `Engine.close`
  evidence
- `ラミィのNPU推論について短く説明して`: success, `natural_japanese`,
  `decode_ms=4393`, `elapsed_ms=6000`

The Activity restart sequence did not create a reliable fresh Activity/process
boundary. After the first prompt, the relaunch was delivered to the current
top-most Activity and the PID stayed `17789`. The Python prompt then timed out;
after that timeout, meminfo reported no process before the runner relaunched
the Activity into a new PID `18538`. No explicit force-stop was used, and no
fresh crash is asserted from the completed receiver result because that result
was unavailable for the timed-out prompt.

Classification: primary `activity_restart_insufficient`; secondary
`cleanup_or_process_resource_issue`; still possible
`native_callback_missing_or_decode_never_returns`. Activity/lifecycle restart
alone does not solve the 512 sequential code prompt timeout. The successful
force-stop comparison remains the evidence for a hidden per-run isolated 512
candidate.

Decision: 512 remains non-baseline for sequential and Activity-restart-only
modes. 512 may be reviewed only as hidden `mode=per_run_isolated` with
force-stop before and after each prompt. 256 remains the hidden experimental
baseline candidate. H1 remains pinned to sanitizer-only
`max_output_tokens=128`. 1024, 2048, and 4096 remain blocked.

## Max512 Sequential Cleanup/Resource Investigation - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_sequential_cleanup_resource_investigation/20260527_082307/`

Scope: artifact/log/runner/native-stage review only. No additional NPU
execution, 512 rerun, 1024+ expansion, native change, QAIRT rebuild, ChatScreen
promotion, assistant-list insertion, DB, TTS, Markdown, streaming,
selectedPath=NPU persistence, release behavior, or standard behavior change
was performed.

Classification: primary `sequential_resource_inheritance`; secondary
`native_callback_missing_after_decode_or_decode_never_returns`; plausible
`cleanup_wait_insufficient` and `code_decode_slow_after_warm_run`. The
state-file wait condition is not primary because the sequential and isolated
runners use the same state-file wait pattern and 60 second bound, while only
the force-stop bracketed run succeeds.

The key runner difference is process isolation. The sequential runner cleans
state files and starts Activity for each prompt, but it does not force-stop
after the successful first prompt. The force-stop runner kills the app before
and after every prompt, waits, and records no process after 10 seconds. Native
stage confirms the sequential Python prompt reaches
`before RunDecode SetMaxOutputTokens(512)` but never writes native success,
cleanup, `Engine.close`, backend evidence, raw output, or sanitized output
before timeout.

Decision: 512 per-run isolated remains a hidden candidate. Sequential 512
remains non-baseline. 256 remains the hidden experimental baseline candidate.
1024, 2048, and 4096 remain blocked. If another runtime experiment is approved,
the next single axis should be prompt-to-prompt Activity restart only, not
1024 expansion.

## Max512 Per-Run Isolated Mode Gate - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_per_run_isolated_gate/20260527_075622/`

Scope: documentation and gate design only. No additional NPU execution, native
change, QAIRT rebuild, ChatScreen promotion, assistant-list insertion, DB, TTS,
Markdown, streaming, selectedPath=NPU persistence, release behavior, or
standard behavior change was performed.

The gate splits 512 into two explicit categories. `sequential_512` remains
non-baseline because the code-aware three-prompt sequential run reproduced the
Python code prompt timeout. `per_run_isolated_512` may be reviewed as a hidden
mode only when every prompt is bracketed by app force-stop before and after the
run.

Required gate signals for `mode=per_run_isolated`: `max_output_tokens=512`,
force-stop before/after each prompt, `RunDecode` reached, pre-decode
`SetMaxOutputTokens(512)` evidence, `timeout=false`, `fresh_crash=false`,
`fallback_used=false`, `QNN_HTP_V79_FastRPC_native_diag`,
`Engine.close=unique_ptr_cleanup`, no after-10s process or no high retained
memory, code-aware sanitizer enabled, code indentation preserved, code fence
closed/completed, `selectedPathSaved=false`, and DB/TTS/Markdown/streaming
false.

Decision: 512 can be treated only as `extended experimental / per-run isolated
candidate`. It is not a general sequential 512 baseline, not H1, and not normal
ChatScreen. 256 remains the hidden experimental baseline candidate. 1024,
2048, and 4096 remain blocked.

## Max512 Force-Stop Between Prompts Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`

The three approved hidden prompts ran once each at `max_output_tokens=512` with
`timeout_seconds=60`, code-aware sanitizer enabled, and app force-stop before
and after every prompt. This tests a per-run isolated/fresh-process equivalent
mode, not the earlier sequential runner.

Results:

- `こんにちは`: success, `natural_japanese`, `decode_ms=835`,
  `elapsed_ms=3000`
- `Pythonで簡単な電卓コードを書いて`: success, `useful_code`,
  `decode_ms=12448`, `elapsed_ms=14000`, indentation preserved, code fence
  closed/completed
- `ラミィのNPU推論について短く説明して`: success, `natural_japanese`,
  `decode_ms=4359`, `elapsed_ms=6000`

All three runs reached `before RunDecode SetMaxOutputTokens(512)` with
`native_max_output_tokens_limit=512` and
`qairt244_editable_prompt_max512_v1`. Completed diagnostics recorded
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`Engine.close=unique_ptr_cleanup`, and cleanup timings of 126 ms, 130 ms, and
142 ms. `timeout=false`, `fresh_crash=false`, `fallback_used=false`,
`selected_path_npu_saved=false`, and DB/TTS/Markdown/streaming remained false.
After each post-run force-stop, after-10s meminfo reported no process for the
package, so no retained-memory rollback was observed.

Decision: 512 is viable as a per-run isolated hidden experimental candidate
when every prompt is bracketed by app force-stop. It is still not a general
sequential 512 baseline and is not promoted to H1 or normal ChatScreen. 256
remains the hidden experimental baseline candidate. 1024, 2048, and 4096 remain
blocked until a separate gate decides whether per-run isolated 512 is an
acceptable operating mode or whether sequential 512 must pass first.

## Max512 Code Prompt Repeated Timeout Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`

Scope: artifact/log/runner/docs review only. No additional NPU execution, 512
rerun, 1024+ expansion, native guard change, QAIRT rebuild, or UI promotion was
performed.

Classification: primary `sequential_decode_timeout`, secondary
`code_prompt_decode_too_long_under_three_prompt_runner`. Possible contributing
factors are `cleanup_dependency_between_runs` and
`thermal_or_resource_slowdown_possible`; direct thermal evidence is not present.
`runner_wait_condition_too_strict` is not supported as the primary cause
because the isolated 512 code retry and code-aware three-prompt runner both use
the same 60 second state-file wait, and the isolated retry completed.

The key difference is run context. The 512 isolated Python code retry completed
as `useful_code` with `decode_ms=11600`, `elapsed_ms=14000`, QNN evidence, and
cleanup evidence. The code-aware three-prompt run placed the same prompt second
after `こんにちは`; it reached `before RunDecode SetMaxOutputTokens(512)`, then
timed out with no native success, no cleanup/`Engine.close`, no completed
backend evidence, and no completed raw/sanitized code output.

Decision: 512 remains extended experimental and code-prompt unstable. 512 is
not a hidden baseline candidate. 256 remains the hidden experimental baseline
candidate. 1024, 2048, and 4096 remain blocked. A future runtime phase, if
approved separately, should choose one of: 512 code-only isolated retry with
artifact, 512 three-prompt order-swapped comparison, or 512 per-run force-stop
between prompts.

## Max512 Code-Aware Three-Prompt Rerun - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`

The code-aware sanitizer was applied and the three approved hidden prompts were
run once each at `max_output_tokens=512` with a bounded 60 second timeout. The
rerun still fails the 512 baseline gate because the Python calculator prompt
timed out after reaching `before RunDecode SetMaxOutputTokens(512)`. No
completed raw/sanitized code output was returned for that prompt, so
indentation and fence completion could not be evaluated from a completed
response.

Results:

- `こんにちは`: success, `natural_japanese`, `decode_ms=848`,
  `elapsed_ms=2000`
- `Pythonで簡単な電卓コードを書いて`: timeout, `elapsed_ms=70000`,
  pre-RunDecode evidence present, no completed sanitizer result
- `ラミィのNPU推論について短く説明して`: success, `natural_japanese`,
  `decode_ms=3989`, `elapsed_ms=5000`

Decision: 512 remains extended experimental and is not a hidden baseline
candidate. 256 remains the hidden experimental baseline candidate. 1024 remains
blocked until a 512 code prompt returns under a bounded gate with `useful_code`,
cleanup evidence, code indentation preserved, closed/completed fence, QNN
evidence, side-effect flags false, and memory recovery.

## Code-Aware Sanitizer Minimal Implementation - 2026-05-27

Artifact:
`artifacts/qairt244_code_aware_sanitizer_review/20260527_012650/`

Scope: sanitizer implementation and tests only. No additional NPU execution,
512 retry, 1024+ expansion, native guard change, QAIRT rebuild, or UI promotion
was performed.

The 512 Python code prompt issue is now treated as display sanitizer quality,
not NPU decode failure. The sanitizer detects Markdown code fences, preserves
leading spaces/tabs/blank lines inside fenced code blocks, keeps the existing
non-code template/prompt/drift sanitizer behavior, and appends a derived
closing fence when truncation leaves an opened code block unclosed. Diagnostics
now record `code_block_detected` and `code_fence_completed`.

Decision: 512 remains extended experimental and is not a baseline candidate
from this implementation alone. 256 remains the hidden experimental baseline
candidate. The next candidate step is a separately approved bounded 512
three-prompt comparison using the code-aware sanitizer.

## Max512 Code Output Quality Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_output_quality_review/20260527_011217/`

Review scope: artifact-only. No additional NPU execution, 512 retry, 1024+
expansion, native guard change, QAIRT rebuild, or UI promotion was performed.

The bounded retry output is NPU-safety successful: `result=success`,
`quality_classification=useful_code`, `timeout=false`, `fresh_crash=false`,
`fallback_used=false`, `RunDecode reached=true`, QNN/HTP/FastRPC evidence
present, and cleanup/`Engine.close` evidence present. The display quality gate
does not pass. Raw output preserves Python indentation, but sanitized output
strips leading spaces inside the code block. Both raw and sanitized output keep
the opening `python` code fence and miss the closing fence because the response
ends mid-statement at `elif choice == '`.

Classification: `indentation_broken_by_sanitizer`,
`code_fence_unclosed_due_to_truncation`, `output_truncated_by_token_limit`, and
`markdown_display_risk`.

Decision: 512 remains extended experimental and is not a hidden baseline
candidate. 256 remains the hidden experimental baseline candidate. 1024 remains
blocked. The next approved 512 step should be code-fence/indentation display
sanitizer design, followed by a bounded 512 three-prompt comparison before any
baseline decision.

## Max512 Three-Prompt Hidden Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_compare/20260527_003429/`

The approved three hidden prompts ran once each at `max_output_tokens=512`.
The run is not a 512 baseline candidate because the Python calculator prompt
timed out under the bounded 30 second runner timeout.

Results:

- `こんにちは`: success, `natural_japanese`, `decode_ms=727`,
  `elapsed_ms=2000`
- `Pythonで簡単な電卓コードを書いて`: `timeout`, no completed sanitized output,
  `elapsed_ms=40000`; native diag reached
  `before RunDecode SetMaxOutputTokens(512)`
- `ラミィのNPU推論について短く説明して`: success, `natural_japanese`,
  `decode_ms=4250`, `elapsed_ms=5000`

QNN/HTP/FastRPC evidence was present where diagnostics were captured, and no
normal UI, assistant-list, DB, TTS, Markdown, streaming, or selected-path
persistence ingress was recorded. Memory decreased after 10 seconds from
`TOTAL PSS=292816 KB / Native Heap=82828 KB` to
`TOTAL PSS=272689 KB / Native Heap=54604 KB`, so no retained-memory rollback
was recorded. The rollback reason is the 512 code-generation timeout and empty
sanitized output for that prompt.

Decision: keep 128 as the H1/display baseline and keep 256 as the hidden
experimental baseline candidate. Do not promote 512 and do not proceed to 1024
until a separately approved 512 three-prompt run passes all prompts, including
`useful_code` for the Python prompt, without timeout.

## Max512 Code Prompt Timeout Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_code_timeout_review/20260527_005112/`

Review scope: artifact/log/runner/docs only. No additional NPU execution,
512 retry, 1024+ expansion, native guard change, QAIRT rebuild, or UI
promotion was performed.

Classification: `C. native_hang_or_no_callback`, with
`D. cleanup_unknown`. The Python calculator prompt reached the native
editable-prompt path and recorded `before RunDecode SetMaxOutputTokens(512)`,
but it did not produce native `success`, `cleanup_elapsed_ms`, or
`Engine.close` evidence before the bounded runner timeout. The runner waited
30 seconds for `files/qairt244_standard_hidden_prompt_state.txt`, then
force-stopped the app. `result_2.txt` stayed at receiver `state=started`, and
raw/sanitized output files for prompt 2 were empty.

The 256 reference completed the same Python prompt as `useful_code` with
`decode_ms=7351` and `elapsed_ms=9000`. The 512 run reached
`elapsed_ms=40000` including timeout/force-stop overhead and produced no
completed output. This does not prove a crash or memory high-retention issue;
after-10s memory decreased from the final after-run sample.

Decision: 512 remains non-promotable and cannot be a hidden baseline
candidate. 1024 remains blocked. A later 512 code-prompt retry, if approved,
must be one run only, same prompt, `max_output_tokens=512`, bounded timeout
only, and must pass `useful_code`, `timeout=false`, `fresh_crash=false`,
`fallback_used=false`, QNN evidence, cleanup/`Engine.close` evidence, memory
recovery, and side-effect flags false.

## Max512 Code Prompt Bounded Retry - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`

The same Python calculator prompt was retried once at `max_output_tokens=512`
with a bounded `timeout_seconds=60`. No additional retry was run.

Result classification: `A. success_but_slow`. The retry returned
`result=success`, `quality_classification=useful_code`, `timeout=false`,
`fresh_crash=false`, `fallback_used=false`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`. Native diagnostics
recorded `before RunDecode SetMaxOutputTokens(512)`,
`decode_elapsed_ms=11600`, `cleanup_elapsed_ms=142`, and
`Engine.close=unique_ptr_cleanup`. Runner elapsed time was `14000 ms`.

The output is useful as code-generation evidence but not display-baseline
evidence: the sanitized code block is long, truncated at the tail, and the
sanitized display form loses indentation. Memory was not a rollback reason:
`after TOTAL PSS=251268 KB / Native Heap=33172 KB`; `after_10s TOTAL PSS=258999
KB / Native Heap=33172 KB`. Side-effect flags remained false.

Decision: the 30 second timeout was too short for this 512 code prompt, but
512 still is not promoted to a hidden baseline, H1 baseline, or normal
ChatScreen. 1024 remains blocked until a separately approved full 512
three-prompt comparison passes all prompts under explicit bounded gates and the
code display/indentation issue is reviewed.

## Max512 Single Prompt Verification - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_single_prompt/20260527_002303/`

The approved single hidden prompt `こんにちは` ran once at
`max_output_tokens=512` with the staged max512 native artifact. `RunDecode` was
reached with `before RunDecode SetMaxOutputTokens(512)`,
`native_max_output_tokens_limit=512`, and
`max_output_tokens_limit_marker=qairt244_editable_prompt_max512_v1` in native
diagnostics.

Result: success. The raw native output still contained prompt echo and
`<end_of_turn>` markers, but sanitizer removed both and produced
`こんにちは！何かお手伝いできることはありますか？`, classified as
`natural_japanese` in the case summary. `fallback_used=false`,
`timeout=false`, `fresh_crash=false`, `npu_backend=NPU`, and
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.

Memory did not show a retained high-water rollback condition:
`after TOTAL PSS=299933 KB / Native Heap=82768 KB`, then
`after_10s TOTAL PSS=253806 KB / Native Heap=28632 KB`. Side-effect flags
remained false for selected-path persistence, standard route, normal UI route,
assistant-list insertion, DB, TTS, Markdown, and streaming.

Decision: 512 may proceed to a separately approved three-prompt hidden
comparison. Do not promote 512 to the hidden baseline, H1 display baseline, or
normal ChatScreen.

## Max512 Guard Preflight Update - 2026-05-26

Status: 512 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max512_entrypoint_build/20260526_235239/`
- preflight artifact:
  `artifacts/qairt244_npu_max512_guard_preflight/20260527_000522/`

The max512 quality entrypoint is preflight-only for this phase. It exits before
device selection, app launch, NPU generation, `Engine.initialize`, or
`RunDecode`. The preflight refuses progression unless static evidence shows:

- `qairt244_editable_prompt_max512_v1`
- `native_max_output_tokens_limit=512`
- `SetMaxOutputTokens(512)`
- SM8750 model-selection evidence

The preflight summary records `guard_status=pass`, `npu_run_executed=false`,
`engine_initialize_executed=false`, and `run_decode_executed=false`. The staged
binary check records the rebuilt `liblitertlm_jni.so` with:

```text
build_id=82cf5b24f5b2897edf3b4b8a6970cf8e
sha256=7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105
```

No `.so` was copied into `app/src/main/jniLibs`, and no runtime generation was
attempted.

## Max256 Guard Preflight Update - 2026-05-26

Status: 256 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

The 256 runner now has a preflight-only guard path. `--preflight-only` exits
before device selection, app launch, NPU generation, `Engine.initialize`, or
`RunDecode`. Non-preflight execution is refused unless supplied static native
artifact evidence shows all of:

- `qairt244_editable_prompt_max256_v1`
- `native_max_output_tokens_limit=256`
- `SetMaxOutputTokens(256)`
- SM8750-only model/selection evidence

No ChatScreen, DB, TTS, Markdown, streaming, selected-path persistence, or
normal route surface is connected by the preflight.

Limited rebuild metadata:

```text
liblitertlm_jni.so build_id=c42e4438f1b39e384ab075b9392831ca
liblitertlm_jni.so sha256=3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

The preflight passed against the rebuilt artifact. This is not runtime proof:
the next phase still needs separate approval for a single hidden experimental
256 prompt run with timeout, fresh-crash, fallback, QNN evidence, memory, and
sanitizer quality gates.

## Max256 Single Prompt Verification - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

Result: the first hidden experimental `max_output_tokens=256` runtime
verification passed for exactly one prompt, `こんにちは`.

Key evidence:

- `result=success`
- `max_output_tokens=256`
- `run_decode_reached=true`
- native diag reached
  `before RunDecode SetMaxOutputTokens(256) native_max_output_tokens_limit=256`
- `max_output_tokens_limit_marker=qairt244_editable_prompt_max256_v1`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- sanitized output quality: `natural_japanese`
- sanitized output:
  `こんにちは！何かお手伝いできることはありますか？`

Raw output still contains Gemma turn artifacts and prompt echo, but sanitizer
removed them for display (`removed_template_token_count=2`,
`removed_prompt_echo=true`). This keeps the existing gate interpretation:
raw artifacts are tolerated only when sanitized output is safe.

Memory evidence:

```text
after run TOTAL PSS=298148 KB
after 10s TOTAL PSS=288007 KB
```

Decision: 256 may proceed to a separately approved three-prompt hidden
comparison. Do not promote 256 to the H1 display baseline yet; 128 remains the
adopted baseline.

## Max256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The approved hidden experimental three-prompt comparison ran exactly once per
prompt with `sanitizer_only + max_output_tokens=256`.

Results:

- `こんにちは`: `success`, `quality_classification=natural_japanese`,
  `decode_ms=884`, `elapsed_ms=3000`.
- `Pythonで簡単な電卓コードを書いて`: `success`,
  `quality_classification=useful_code`, `decode_ms=7351`,
  `elapsed_ms=9000`.
- `ラミィのNPU推論について短く説明して`: `success`,
  `quality_classification=natural_japanese`, `decode_ms=4110`,
  `elapsed_ms=6000`.

All rows recorded `RunDecode` reached, `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, and DB/TTS/Markdown/streaming false.

Memory did not remain high after the 10-second cool-down:
`TOTAL PSS=224993 KB` and `Native Heap=34500 KB`.

Decision: 256 is now a hidden experimental baseline candidate, but it is not
adopted for H1 or normal UI. The Python/code prompt is useful, though display
formatting and indentation should be reviewed before any UI-facing baseline
change. Proceed to 512 only through a separate guard/build/preflight and a
single-prompt run first; do not jump to 1024, 2048, or 4096.

Result commit decision: keep 128 as the adopted H1/normal-UI-safe baseline and
record 256 as a hidden experimental baseline candidate only. Any 512 work must
start with native guard/build/preflight evidence, then one single prompt with
`RunDecode` reached, QNN evidence, no timeout, no fresh crash, no fallback,
memory-after-10s recovery, and sanitizer quality review.

## Finding

The observed `max_output_tokens=256` rollback is caused by the custom qairt244
editable-prompt native entrypoint in:

`/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`

The native entrypoint rejects values outside `1..128` before decode setup:

```text
invalid_max_output_tokens value=256 native_max_output_tokens_limit=128
```

`DecodeConfig::CreateDefault()`, `SetMaxOutputTokens(max_output_tokens)`, and
`RunDecode` are downstream of that guard, so the 256 request did not reach the
LiteRT-LM decode API or the QNN runtime.

## Classification

Classification: `A. custom_safety_guard_only`

Static rationale:

- the qairt244 JNI editable-prompt path has an explicit `>128` rejection
  before `DecodeConfig`;
- `WriteQairt244EditablePromptResult` also emits
  `native_max_output_tokens_limit=128`;
- `DecodeConfig::SetMaxOutputTokens` is exposed as an integer setter in
  LiteRT-LM runtime code and no static 128 cap was found in the inspected
  setter path;
- the 256 artifact failed with `empty_after_sanitize` because native returned
  an invalid-token request before decode, not because of fallback, timeout, or
  fresh crash.

This does not prove that the compiled SM8750 model, tokenizer/context window,
KV/cache behavior, QNN/HTP runtime, or device memory are safe above 128. It
only identifies the source of the current rejection.

## Current Decision

Keep the hidden experimental baseline fixed at:

```text
sanitizer_only + max_output_tokens=128
```

`max_output_tokens=256` remains rollback-only until a deliberate native guard
patch is reviewed, rebuilt, installed, and validated in a separate phase.

## Minimal Future Patch Shape

Do not implement this in the current task. The next patch should be small and
reviewable:

- replace the literal 128 in the qairt244 editable-prompt guard with a named
  qairt244 constant;
- raise that constant only to 256 in the first experimental patch;
- ensure the result writer records the requested/actual max output token value
  and the native limit consistently;
- keep the change limited to `customBuildExperimentDebug`/hidden diagnostic
  behavior;
- do not touch release, standard, normal ChatScreen, DB, TTS, Markdown,
  streaming, selected-path persistence, or `app/src/main/jniLibs`.

## Stage Plan Toward 4096

- Phase T0: keep the 128 baseline.
- Phase T1: prepare and review a guard-only 256 patch; no generation required.
- Phase T2: run one 256 prompt once with memory and cleanup evidence.
- Phase T3: run the 256 three-prompt comparison once.
- Phase T4: run one 512 prompt once.
- Phase T5: run one 1024 prompt once.
- Phase T6: run one 2048 prompt once.
- Phase T7: run one 4096 prompt once.

4096 is the final target, not the next runnable step. Do not jump directly from
128 to 4096.

## Gates For Every Expansion Phase

Required:

- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- sanitized output is non-empty
- no template artifact after sanitize
- no repeated completion after sanitize
- no multilingual drift after sanitize
- memory before/after/after-10s captured
- cleanup evidence captured
- process-alive and fresh tombstone checks captured
- artifact size remains bounded

Long decode phases also need a separate timeout policy, UI-freeze guard,
sanitizer memory-pressure check, and repetition/drift review.

## Rollback Conditions

Rollback if any phase records timeout, fresh crash, fallback, missing QNN
evidence, empty sanitized output, template artifact after sanitize, worse
repetition, worse multilingual drift, retained memory growth after 10 seconds,
selected-path persistence, DB/TTS/Markdown/streaming flow, normal route
connection, generic/qcs8275 model selection, or stale artifact evidence.
