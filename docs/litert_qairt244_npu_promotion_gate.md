# QAIRT244 Hidden NPU Promotion Gate

## Max Output Tokens 512 Sequential Soft-Reset Runtime - 2026-05-28

Artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_041357/`

Gate status: hidden experimental runtime-policy validation only. This was one
512 sequential soft-reset runtime attempt with no force-stop between prompts,
no Activity restart between prompts, no native change, and no QAIRT rebuild.

Runtime result: prompt 1 (`こんにちは`) classified `SUCCESS_CLEAN`, prompt 2
(`Pythonで簡単な電卓コードを書いて`) classified `SUCCESS_CLEAN` with
`useful_code`, preserved indentation, closed code fence, QNN evidence, and
`Engine.close=unique_ptr_cleanup`. Prompt 3
(`ラミィのNPU推論について短く説明して`) hit `TIMEOUT_SUSPECT`, with
`next_prompt_allowed=false`, `reuse_allowed=false`,
`runtime_reuse_policy=per_run_isolated_required`, missing cleanup, and no
completed backend evidence.

Promotion decision: unchanged. The lifecycle gate worked and stopped on the
suspect session, but 512 sequential is still not a baseline candidate. 512
remains hidden `hidden_per_run_isolated_512` only; 256 remains the hidden
experimental baseline candidate; H1 remains pinned to 128; 1024/2048/4096
remain blocked.

## Hidden NPU Runtime Reuse Enforcement - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_runtime_reuse_enforcement/20260528_034826/`

Gate status: runtime policy, preflight enforcement, tests, and docs only. No
NPU runtime, RunDecode, native change, or QAIRT rebuild was performed.

Lifecycle classification now drives runtime reuse policy. `SUCCESS_CLEAN` is
the only state that opens `next_prompt_allowed=true`. `TIMEOUT_SUSPECT`,
`CLEANUP_MISSING_SUSPECT`, stale result rejection, run-id mismatch rejection,
and other non-success classifications forbid sequential continuation. Suspect
sessions set `reuse_allowed=false` and require hidden per-run isolation before
any later hidden attempt.

Promotion decision: unchanged. 512 remains hidden
`hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only
512 remain rollback. 256 remains the hidden experimental baseline candidate,
H1 remains pinned to 128, and 1024/2048/4096 remain blocked.

## Max Output Tokens 512 Sequential Soft-Reset Preflight - 2026-05-28

Artifact:
`artifacts/qairt244_npu_512_sequential_soft_reset_preflight/20260528_033653/`

Gate status: runner design, preflight simulation, and tests only. No NPU
runtime, RunDecode, native change, or QAIRT rebuild was performed.

The future soft-reset sequential runner is gated so each prompt must have a
unique runId and isolated state/result/native-diag/cleanup evidence. After
each prompt, the lifecycle summary must be `SUCCESS_CLEAN` with
`reuse_allowed=true`, `hidden_per_run_isolated_required=false`,
`cleanup_elapsed_ms`, and `Engine.close=unique_ptr_cleanup`. Any
`TIMEOUT_SUSPECT`, `CLEANUP_MISSING_SUSPECT`, stale result, run-id mismatch,
or reuse denial stops the sequence immediately.

Preflight simulation keeps the policy unchanged: 256 clean artifacts can
continue, the existing 512 sequential code-aware artifact stops at prompt 2
with `TIMEOUT_SUSPECT`, and the 512 force-stop artifact is only a clean
reference. 512 remains hidden `hidden_per_run_isolated_512` only; sequential
512 is not a baseline.

## Hidden NPU Lifecycle Summary Regeneration - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_summary_regeneration/20260528_030629/`

Gate status: preflight-only regeneration from existing artifacts. No NPU
execution, no RunDecode invocation, no native change, and no QAIRT rebuild
were performed.

The lifecycle parser was run against real 512/256 artifacts. Force-stop
between prompts, bounded 512 code retry, and the 256 three-prompt baseline
candidate classify as `SUCCESS_CLEAN`. The Python code prompt in both
Activity-restart-only 512 and sequential code-aware 512 classifies as
`TIMEOUT_SUSPECT` with `reuse_allowed=false` and
`hidden_per_run_isolated_required=true`. No stale-result or run-id mismatch
marker was present in the reviewed source artifacts.

Promotion decision: unchanged. 512 remains hidden
`hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only
512 remain rollback. 256 remains the hidden experimental baseline candidate,
H1 remains pinned to 128, and 1024/2048/4096 remain blocked.

## Hidden NPU Lifecycle Summary Integration - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_summary_integration/20260528_024448/`

Gate status: runner/preflight summary integration and unit tests only. No NPU
runtime evidence is added.

Hidden summaries now report lifecycle classifications:
`SUCCESS_CLEAN`, `FAILURE_CLEAN`, `TIMEOUT_SUSPECT`,
`CLEANUP_MISSING_SUSPECT`, `STALE_RESULT_REJECTED`, and
`RUN_ID_MISMATCH_REJECTED`. Timeout, cleanup-missing, stale, and mismatch
outcomes prevent reuse and require hidden per-run isolation. Clean
classifications still require side-effect flags false before acceptance.

Promotion decision: unchanged. 512 remains hidden
`hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only
512 remain rollback. 256 remains the hidden experimental baseline candidate,
H1 remains pinned to 128, and 1024/2048/4096 remain blocked.

## Hidden NPU Lifecycle Artifact Parser - 2026-05-27

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_runner_integration/20260527_231211/`

Gate status: parser integration and unit tests only. No NPU runtime evidence is
added.

`DevOnlyNpuLifecycleArtifactParser` now converts runner/preflight artifact text
into the lifecycle wrapper decision. It rejects stale results and run-id
mismatches across state, result, native diag, and cleanup channels. It requires
terminal result evidence, native completed evidence, `cleanup_elapsed_ms`, and
`Engine.close=unique_ptr_cleanup` for a clean run. Timeout or missing cleanup
is classified as suspect and forbids session reuse.

Promotion decision: unchanged. 512 remains hidden
`hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only
512 remain rollback modes. 256 remains the hidden experimental baseline
candidate, H1 remains pinned to 128, and 1024/2048/4096 remain blocked.

## Hidden NPU Lifecycle Wrapper Contract - 2026-05-27

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_wrapper_design/20260527_225303/`

Gate status: contract and unit tests only. No NPU runtime evidence is added.

The hidden lifecycle wrapper fixes the evidence contract for any future
sequential 512 retest. A current run is accepted only when callback, state,
result, native diag, and cleanup evidence all match the same `runId`, stale
results are absent, cleanup has `cleanup_elapsed_ms`, and
`Engine.close=unique_ptr_cleanup` is present. Timeout or missing cleanup
classifies the run as suspect and forbids session reuse.

Promotion decision: unchanged. Sequential 512 and Activity-restart-only 512
remain rollback modes. 512 remains hidden `hidden_per_run_isolated_512` only,
256 remains the hidden experimental baseline candidate, H1 remains pinned to
128, and 1024/2048/4096 remain blocked.

## Edge Gallery Streaming Lifecycle Compare - 2026-05-27

Artifact:
`artifacts/qairt244_edge_gallery_streaming_lifecycle_compare/20260527_223704/`

Gate status: docs/static review only. No runtime evidence is added and no
promotion boundary changes.

Edge Gallery shows a callback-driven chat lifecycle with engine reuse,
conversation reset, cooperative cancel, cleanup listener, and streaming chunks
delivered into ViewModel state. LiteRT-LM shows that cancellation and close are
cooperative, and Flow cancellation does not automatically call native cancel.
Therefore Gallery's streaming UI model is not a promotion path for Lami 512.

Promotion decision: unchanged. Before any sequential 512 retest, design a
hidden-only lifecycle wrapper that separates run ids across state/result/native
diag files and requires terminal callback plus cleanup/`Engine.close` evidence.
Do not adopt normal streaming renderer, assistant-list insertion, DB, TTS,
Markdown renderer, selectedPath=NPU persistence, or ChatScreen promotion.
512 remains hidden `hidden_per_run_isolated_512` only; sequential 512 and
Activity-restart-only 512 remain rollback. 256 remains the hidden experimental
baseline candidate, H1 remains pinned to 128, and 1024/2048/4096 remain
blocked.

## Max Output Tokens 512 Per-Run Isolated Formalization - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_per_run_isolated_formalization/20260527_215325/`

Gate status: formalized for hidden-only operation. The gate now distinguishes
`hidden_experimental_256` from `hidden_per_run_isolated_512`. It does not
promote 512 to H1, normal ChatScreen, release behavior, or standard behavior.

`hidden_per_run_isolated_512` is accepted only when all required evidence is
present: force-stop before/after each prompt, `max_output_tokens=512`,
`RunDecode` reached, `SetMaxOutputTokens(512)` evidence, `timeout=false`,
`fresh_crash=false`, `fallback_used=false`, QNN/HTP/FastRPC evidence,
`Engine.close=unique_ptr_cleanup`, cleanup evidence, no retained process or
high retained memory after 10 seconds, code-aware sanitizer, preserved code
indentation, completed/closed code fence, selectedPath not saved, no assistant
message-list insertion, and DB/TTS/Markdown/streaming false.

Explicit rollback modes: sequential 512 and Activity-restart-only 512. Both
are rejected even though the native max512 guard exists. The passing force-stop
artifact is scoped to per-run isolated hidden operation only.

Promotion decision: 256 remains the hidden experimental baseline candidate,
H1 remains pinned to 128, and 1024/2048/4096 remain blocked.

## Max Output Tokens 512 Activity Restart Only Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/`

Gate result: failure. Activity finish/relaunch between prompts did not solve
the 512 Python code prompt timeout. The two Japanese prompts completed with
QNN/HTP/FastRPC evidence and `fallback_used=false`, but the Python prompt
timed out after pre-RunDecode `SetMaxOutputTokens(512)` evidence and before a
completed result, cleanup, `Engine.close`, backend evidence, raw output, or
sanitized output was available.

Prompt results:

- `こんにちは`: `natural_japanese`, `decode_ms=834`, `elapsed_ms=2000`
- `Pythonで簡単な電卓コードを書いて`: timeout, `elapsed_ms=70000`, no
  `useful_code`, no indentation/fence result
- `ラミィのNPU推論について短く説明して`: `natural_japanese`,
  `decode_ms=4393`, `elapsed_ms=6000`

The runner records `process_force_stop_used=false`. The first prompt relaunch
was delivered to the already-running top-most Activity with the same PID, so
Activity restart only is not equivalent to the force-stop isolated gate. After
the Python timeout, no process was found before the runner relaunched the
Activity into a new PID, without explicit force-stop.

Promotion decision: do not promote 512 as a sequential or Activity-restart-only
hidden baseline. Keep 512 only as the hidden `per_run_isolated` candidate that
requires force-stop before and after each prompt. Keep 256 as the hidden
experimental baseline candidate, keep H1 pinned to 128, and keep
1024/2048/4096 blocked.

## Max Output Tokens 512 Sequential Cleanup/Resource Investigation - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_sequential_cleanup_resource_investigation/20260527_082307/`

Gate status: docs/review only. The investigation does not add runtime evidence
and does not alter the promotion boundary.

The sequential 512 failure is now classified primarily as
`sequential_resource_inheritance`. The Python prompt reaches native pre-decode
evidence but remains at receiver `state=started` with no native success,
cleanup, `Engine.close`, completed backend evidence, raw output, or sanitized
output. Per-run isolated 512 succeeds because every prompt is bracketed by app
force-stop and no-process after 10 seconds.

Promotion decision: unchanged. 512 is not a sequential hidden baseline. 512 may
be considered only as a hidden per-run isolated candidate. 256 remains the
hidden experimental baseline candidate, H1 remains pinned to 128, and
1024/2048/4096 remain blocked. The next approved runtime axis, if any, should
be prompt-to-prompt Activity restart only.

## Max Output Tokens 512 Per-Run Isolated Gate - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_per_run_isolated_gate/20260527_075622/`

Gate status: defined, docs-only. This does not run NPU again and does not
promote 512 to H1, normal ChatScreen, release behavior, or standard behavior.

512 is accepted for review only as `mode=per_run_isolated`. The gate requires
force-stop before and after each prompt, `max_output_tokens=512`, `RunDecode`
pre-call evidence, `timeout=false`, `fresh_crash=false`,
`fallback_used=false`, QNN/HTP/FastRPC evidence, `Engine.close`, cleanup
evidence, no after-10s retained-memory condition, code-aware sanitizer,
preserved code indentation, closed/completed code fence, and side-effect flags
false.

Rollback conditions include sequential execution used as a baseline, timeout,
missing cleanup, memory high retained, broken indentation, unclosed fence,
fresh crash, fallback, selectedPath=NPU persistence, assistant-list insertion,
or DB/TTS/Markdown/streaming ingress.

Promotion decision: 512 is not a sequential hidden baseline. 512 may be
considered only as a hidden per-run isolated candidate. 256 remains the hidden
experimental baseline candidate, H1 remains pinned to 128, and 1024/2048/4096
remain blocked.

## Max Output Tokens 512 Force-Stop Between Prompts - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`

Gate result: per-run isolated comparison passed. The runner force-stopped the
app before and after each approved prompt, then executed exactly one
`max_output_tokens=512` hidden run per prompt with a bounded 60 second timeout.
All three prompts succeeded with QNN/HTP/FastRPC evidence, no fallback, no
timeout, no fresh crash, cleanup/`Engine.close` evidence, and side-effect flags
false.

Prompt results:

- `こんにちは`: `natural_japanese`, `decode_ms=835`, `elapsed_ms=3000`
- `Pythonで簡単な電卓コードを書いて`: `useful_code`, `decode_ms=12448`,
  `elapsed_ms=14000`, indentation preserved, code fence closed
- `ラミィのNPU推論について短く説明して`: `natural_japanese`,
  `decode_ms=4359`, `elapsed_ms=6000`

Promotion decision: 512 can be considered a hidden per-run isolated mode
candidate, but it is not promoted as the general 512 baseline because the
sequential three-prompt runner still has a reproduced code-prompt timeout.
Keep 256 as the hidden experimental baseline candidate, keep H1 pinned to 128,
and keep 1024/2048/4096 blocked until a separate gate accepts per-run
force-stop as the intended 512 operating mode or sequential 512 passes.

## Max Output Tokens 512 Repeated Code Timeout Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`

The 512 code prompt remains non-promotable. It can complete once in an isolated
bounded run, but it timed out when run second in the code-aware three-prompt
comparison. The timeout happens after native pre-RunDecode evidence and before
native success, cleanup, receiver completion, or sanitized code output.

Gate decision: 512 is not a hidden baseline candidate. Keep 256 as the hidden
experimental candidate, keep H1 pinned to 128, and keep 1024/2048/4096 blocked.
Any future 512 runtime attempt requires separate approval and must explain
whether it is code-only isolated, order-swapped, or per-run force-stop.

## Max Output Tokens 512 Code-Aware Rerun - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`

Gate result: failure. The two Japanese prompts completed with sanitized
`natural_japanese` output and QNN/HTP/FastRPC evidence, but the Python
calculator prompt timed out under the bounded 60 second runner after native
pre-decode evidence. Because the code prompt returned no completed sanitizer
result, `useful_code`, indentation preservation, and fence closure are not
proven by this artifact.

Promotion decision: do not classify 512 as a hidden baseline candidate. Keep
256 as the hidden experimental candidate, keep H1 pinned to 128, and keep 1024
blocked. A future 512 attempt needs separate approval and must resolve the code
prompt timeout before any promotion review.

## Code-Aware Sanitizer Gate Update - 2026-05-27

Artifact:
`artifacts/qairt244_code_aware_sanitizer_review/20260527_012650/`

The NPU code prompt blocker is addressed at the sanitizer/display layer. Fenced
code blocks now preserve indentation and tabs, while non-code text keeps the
existing template-token, prompt-echo, repeated-completion, and leading drift
sanitizer behavior. If a response truncates after an opening fence, sanitized
display text receives a derived closing fence and records
`code_fence_completed=true`.

Gate status: implementation is necessary but not sufficient for baseline
promotion. 512 still requires a separately approved bounded three-prompt
comparison with `useful_code`, no timeout, no fresh crash, no fallback, QNN
evidence, cleanup evidence, memory recovery, and side-effect flags false. 256
remains the hidden experimental candidate; H1 remains 128; 1024 remains
blocked.

## Max Output Tokens 512 Code Display Quality Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_output_quality_review/20260527_011217/`

The 512 Python code prompt is safety-successful under the 60 second bounded
retry, but it fails the baseline display-quality gate. Raw output contains
valid-looking Python indentation; sanitized output loses indentation inside the
fenced block. The opening `python` code fence is retained, but the closing fence
is absent because the output is truncated at `elif choice == '`.

New gate requirement: 512 cannot become a hidden baseline candidate unless code
display quality passes for the code prompt. The gate must preserve indentation
inside fenced code blocks, detect or repair an unclosed code fence in derived
display text, report truncation, and still satisfy timeout/crash/fallback/QNN,
cleanup, memory, and side-effect checks.

Promotion decision: 512 remains extended experimental, 256 remains the hidden
experimental candidate, H1 remains pinned to 128, and 1024 remains blocked.

## Max Output Tokens 512 Three-Prompt Hidden Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_compare/20260527_003429/`

The 512 hidden comparison ran the three approved prompts once each. Overall
status is failure because the Python calculator prompt timed out before a
completed result/sanitized output was available.

Prompt results:

- `こんにちは`: success, `natural_japanese`, `decode_ms=727`,
  `elapsed_ms=2000`
- `Pythonで簡単な電卓コードを書いて`: `timeout`, `quality_classification=timeout`,
  no useful code output; `RunDecode` pre-call evidence was present
- `ラミィのNPU推論について短く説明して`: success, `natural_japanese`,
  `decode_ms=4250`, `elapsed_ms=5000`

Gate status: 512 fails promotion because timeout and empty sanitized output are
rollback conditions. No selected-path persistence, standard route, normal UI
route, assistant-list insertion, DB, TTS, Markdown, or streaming ingress was
recorded. Memory after 10 seconds was lower than immediately after the run.

Promotion decision: 512 is not a hidden baseline candidate. Keep 256 as the
hidden experimental candidate and keep H1 pinned to 128. 1024 remains blocked
until a later explicitly approved 512 three-prompt comparison passes all gates.

### Code Prompt Timeout Review

Review artifact:
`artifacts/qairt244_npu_max_output_512_code_timeout_review/20260527_005112/`

The Python calculator timeout is classified as `native_hang_or_no_callback`
with cleanup unknown. Native diagnostics reached pre-decode evidence
`before RunDecode SetMaxOutputTokens(512)`, but no native success, cleanup
timing, or `Engine.close` line was captured before the runner's bounded
30 second wait expired and force-stop was issued.

Gate decision is unchanged: 512 fails promotion, 256 remains the hidden
experimental candidate, H1 remains 128-only, and 1024+ expansion remains
blocked. A retry proposal must be separately approved and bounded; an
unlimited timeout is not acceptable.

### Code Prompt Bounded Retry

Retry artifact:
`artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`

The approved one-run retry used the same Python calculator prompt with
`max_output_tokens=512` and `timeout_seconds=60`. It returned within the bound:
`result=success`, `quality_classification=useful_code`,
`decode_ms=11600`, `elapsed_ms=14000`, `timeout=false`,
`fresh_crash=false`, `fallback_used=false`, QNN/HTP/FastRPC evidence present,
and cleanup/`Engine.close` evidence present.

Promotion decision: still no baseline promotion. This proves the code prompt
can complete under a 60 second bounded gate, but it does not replace the failed
512 three-prompt artifact. The output is long/truncated and the sanitized
display form loses indentation, so H1 and normal UI remain blocked. 1024
remains blocked until a full 512 three-prompt comparison is separately
approved and passes.

## Max Output Tokens 512 Single Prompt - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_single_prompt/20260527_002303/`

The single approved hidden experimental prompt `こんにちは` passed with
`max_output_tokens=512`. `RunDecode` was reached and native diagnostics record
`before RunDecode SetMaxOutputTokens(512)`,
`native_max_output_tokens_limit=512`, and
`qairt244_editable_prompt_max512_v1`. QNN/HTP/FastRPC evidence was present.

The sanitized output was natural Japanese:
`こんにちは！何かお手伝いできることはありますか？`. Raw native output retained
prompt echo and `<end_of_turn>` markers, so the sanitizer remains required for
512 just as it was for 128/256.

Gate status:

- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `assistant_message_list_inserted=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`

Memory after 10 seconds dropped from `TOTAL PSS=299933 KB` and
`Native Heap=82768 KB` to `TOTAL PSS=253806 KB` and `Native Heap=28632 KB`;
no retained-memory rollback was recorded.

Promotion decision: 512 may proceed only to a separately approved three-prompt
hidden comparison. Do not adopt 512 as baseline, do not use it for H1, and do
not feed it into normal UI/ChatScreen.

## Max Output Tokens 512 Guard Preflight - 2026-05-26

Status: 512 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max512_entrypoint_build/20260526_235239/`
- preflight artifact:
  `artifacts/qairt244_npu_max512_guard_preflight/20260527_000522/`

The max512 runner is guard/preflight-only in this phase. It records summary,
native marker scan, `SetMaxOutputTokens(512)` evidence, build artifact path,
grep-safety, and staged-binary check, then exits before device selection, app
launch, NPU generation, `Engine.initialize`, or `RunDecode`.

Promotion remains blocked from any 512 runtime use unless supplied evidence
continues to prove `qairt244_editable_prompt_max512_v1`,
`native_max_output_tokens_limit=512`, `SetMaxOutputTokens(512)`, and SM8750
model selection. ChatScreen, DB, TTS, Markdown, streaming, selected-path
persistence, release behavior, and standard behavior remain disconnected.

The staged-binary check records the rebuilt `liblitertlm_jni.so`:

```text
build_id=82cf5b24f5b2897edf3b4b8a6970cf8e
sha256=7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105
```

Promotion decision: no normal UI or H1 baseline change; the next 512 step
still requires separately approved single-prompt hidden safety execution.

## Max Output Tokens 256 Guard Preflight - 2026-05-26

Status: 256 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

The 256 quality runner is now guarded before execution. `--preflight-only`
records summary, marker, evidence, grep-safety, and staged-binary checks, then
exits before device selection, app launch, NPU generation, `Engine.initialize`,
or `RunDecode`.

Promotion remains blocked from 256 runtime use unless the supplied native
artifact/static metadata proves `qairt244_editable_prompt_max256_v1`,
`native_max_output_tokens_limit=256`, `SetMaxOutputTokens(256)`, and SM8750
selection evidence. ChatScreen, DB, TTS, Markdown, streaming, and selected-path
persistence remain disconnected from this preflight.

The rebuilt JNI artifact records:

```text
build_id=c42e4438f1b39e384ab075b9392831ca
sha256=3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

Promotion decision: still no normal UI or H1 baseline change. 256 may proceed
only to a separately approved hidden experimental single-run phase.

## Max Output Tokens 256 Single Prompt - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

The single approved hidden experimental prompt `こんにちは` passed with
`max_output_tokens=256`. `RunDecode` was reached, QNN/HTP/FastRPC evidence was
present, and the sanitized output was natural Japanese with no template
artifact remaining after sanitize.

Gate status:

- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`

Promotion decision: allow a next-phase 256 three-prompt hidden comparison.
Do not adopt 256 as baseline and do not feed 256 output into normal UI/H1.

## Max Output Tokens 256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The three approved hidden prompts passed once each at
`max_output_tokens=256`:

- `こんにちは`: `natural_japanese`
- `Pythonで簡単な電卓コードを書いて`: `useful_code`
- `ラミィのNPU推論について短く説明して`: `natural_japanese`

Gate status for all rows: `RunDecode` reached, QNN/HTP/FastRPC evidence
present, `fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, `assistant_message_list_inserted=false`,
and `db=false`, `tts=false`, `markdown=false`, `streaming=false`.

Memory after 10 seconds returned to `TOTAL PSS=224993 KB`,
`Native Heap=34500 KB`, so no retained-memory rollback was recorded.

Promotion decision: 256 may be treated as a hidden experimental candidate for
further token expansion. It is not adopted into the H1 display baseline or
normal UI gate. H1 remains pinned to `sanitizer_only + max_output_tokens=128`
until a separate UI-facing decision is made.

Result commit decision: 256 is fixed as a hidden experimental baseline
candidate only. The promotion gate still rejects H1 or normal UI use of 256
until a later explicit gate revision. The next expansion target is 512 and
requires a separate native guard/build/preflight and single-prompt safety run
before any three-prompt comparison.

## Max Output Token Limit Investigation - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

Static finding: the 128-token ceiling is currently enforced by the custom
qairt244 editable-prompt JNI guard in external LiteRT-LM
`kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`. The guard rejects
`max_output_tokens > 128` and reports
`invalid_max_output_tokens ... native_max_output_tokens_limit=128` before
`DecodeConfig::SetMaxOutputTokens(max_output_tokens)` and before `RunDecode`.

Classification: `A. custom_safety_guard_only` for the observed 256 rejection.
No static evidence in the inspected `DecodeConfig` setter path shows a 128 API
limit, but model/runtime/memory safety above 128 remains unproven.

Promotion decision: keep `sanitizer_only + max_output_tokens=128` as the only
accepted hidden display baseline. 256/512/1024/2048/4096 require staged native
guard review, rebuild, one-shot runs, memory-after-10s evidence, cleanup
evidence, fresh crash checks, and sanitizer quality gates before they can be
considered.

## Max Output Tokens 256 Compare - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129/`

Result: keep `sanitizer_only + max_output_tokens=128` as the hidden
experimental display baseline.

The compare-only Java gate accepted the explicit
`allow_max_output_tokens_compare=true` request, but the lower native editable
prompt entrypoint rejected `max_output_tokens=256` with
`invalid_max_output_tokens value=256 native_max_output_tokens_limit=128`.
All three requested prompts therefore returned empty sanitized output and
`quality_classification=empty_after_sanitize`.

Safety invariants remained intact: `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, `db=false`, `tts=false`, `markdown=false`,
and `streaming=false`.

Promotion blocker: `max_output_tokens=256` is rollback-only until native
`native_max_output_tokens_limit` is deliberately raised and separately gated.
H1 UI promotion remains blocked from using 256; H1 metadata/card contracts
continue to require `max_output_tokens=128`.

Date: 2026-05-25

Scope: documentation-only planning for the hidden experimental qairt244 SM8750
NPU route. This gate does not promote `Backend.NPU` into normal selected-path
inference, does not run NPU, and does not change native code.

## Adopted Hidden Baseline

The adopted hidden experimental display-quality baseline is:

```text
case=sanitizer_only
max_output_tokens=128
baseline=enhanced_sanitizer_only_128
```

Lower token caps are not adopted. The recorded rollback-only rows remain:

- `lower_max_tokens_64_sanitizer`: rejected after `empty_after_sanitize`
  evidence.
- `lower_max_tokens_32_sanitizer`: rejected after adapter failure / timeout
  evidence.

`stop_sequence_end_of_turn` remains `not_run/native_stop_not_exposed`. Native
stop sequence or native turn-stop behavior is not required for this hidden
baseline unless a real API surface is exposed and separately gated.

## Promotion Gate

Before any broader hidden experimental promotion, every accepted run must
record all of the following:

- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `fresh_crash=false`
- `timeout=false`
- `quality_classification=natural_japanese` after sanitize
- sanitized display output is artifact-free: no `<start_of_turn>`,
  `<end_of_turn>`, prompt echo, template residue, repeated completion
  classification, or multilingual drift classification
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `selected_path_npu_normal_route=no`
- `conversation_created=no`
- `generate_response=no`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- standard route disconnection regression test passes
- staged binary check passes
- artifact metadata timestamp is fresh:
  `artifact_timestamp_ms`, `artifact_timestamp`, `synced_at`, or `created_at`
  within 24 hours
- missing, future, or stale artifact metadata is rejected before display
- artifact metadata boundary validation passes before mapper/freshness handoff:
  all minimum fields are present, boolean and numeric fields parse cleanly,
  unknown keys are ignored, duplicate keys use the last value, and `raw_output`
  is not propagated into UI input
- `dev_enable_npu_chatscreen_route=false` blocks metadata read and parse
- metadata-to-presenter integration passes: valid fresh metadata reaches
  `visible=true` sanitized output only, while fallback, timeout,
  non-natural quality, standard route connection, or DB ingress reaches
  `visible=false` rollback
- presenter side-effect flags remain false:
  `shouldPersistToDb=false`, `shouldSpeakTts=false`,
  `shouldRenderMarkdown=false`, `shouldStream=false`
- card view model contract passes before UI wiring:
  success displays sanitized output only, rollback/hidden display no body,
  detail lines include token/decode/backend/artifact and side-effect false
  metadata, and all raw/retry/persist/TTS/Markdown/streaming controls are false
- preview renderer contract passes before UI wiring:
  success lines render in stable order, rollback/hidden render no lines, raw
  output and template tokens are absent, and action labels are absent
- Diagnostic/DEV minimal wiring passes before any ChatScreen promotion:
  `dev_enable_npu_chatscreen_route=false` defaults to no read/parse, true reads
  metadata only, fresh gate-passing sanitized output is rendered, and
  stale/rollback/hidden output is not rendered

Raw native output may be classified as `template_artifact` only as diagnostic
evidence. That raw artifact is acceptable only when the sanitized display output
is meaningful natural Japanese and is artifact-free after sanitize.

## Promotion Blockers

Promotion remains blocked by any of the following:

- raw artifact remains visible after sanitize
- sanitized output is empty
- repetition or multilingual drift remains after sanitize
- `fresh_crash=true`
- `timeout=true`
- `fallback_used=true`
- missing `QNN_HTP_V79_FastRPC_native_diag` evidence
- `selectedPath=npu` or equivalent normal-route NPU selection is saved
- DB, TTS, Markdown, or streaming ingress appears
- standard or normal UI route is connected
- generic or QCS8275 model is selected for NPU
- stale artifact is used as the basis for promotion
- `stale_or_unknown` or `stale_or_invalid` artifact timestamp is used as the
  basis for promotion

## Standard Route Boundary

The hidden qairt244 route remains separate from normal local inference:

- no normal Settings exposure without developer access
- no production `Backend.NPU` candidate selection
- no automatic NPU to GPU or CPU fallback
- no persisted `selectedPath=npu`
- no DB persistence, TTS, Markdown repair, or streaming integration

Regression coverage should continue to prove the standard route is disconnected,
including the blocked-branch coverage referenced by
`DevOnlyNpuChatScreenBlockedBranchTest`.

## Staged Binary Check

Promotion review requires an explicit staged-binary check before any merge or
release-facing handoff:

- `git status --short` must not show newly staged `.so`, `.litertlm`, `.apk`,
  `.aar`, `.zip`, `.tar`, or `.gz` artifacts.
- Native artifact provenance must be recorded by path and SHA-256 in the
  relevant run document or in `docs/qairt244_native_artifact_reproducibility.md`.
- Any native artifact used by the hidden run must remain reproducible from
  documented source/build steps or a pinned external checkout; do not rely on an
  untracked local binary as promotion evidence.

## Existing Coverage

The current supporting docs already cover the main pieces:

- `docs/litert_qairt244_npu_hidden_to_ui_handoff_plan.md` defines the staged
  hidden-to-UI handoff. The first allowed UI candidate is Phase H1 transient
  preview only: sanitized output display, no DB, no TTS, no Markdown, no
  streaming, no selected-path NPU persistence, and no standard route connection.
- `docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md` defines the Phase
  H1 surface: DEV-only transient card/banner/snackbar, `sanitized_output` only,
  raw output artifact-only, clear on input/navigation/toggle-off/failure/app
  restart/stale artifact, and no retry or auto fallback on failure.
  The first code step is state/display-model/presenter tests only; ChatScreen
  remains disconnected until those tests pin raw-output exclusion and
  side-effect flags false.
  The second code step maps artifact key-value metadata into H1 input while
  discarding `raw_output`; gate mismatches become rollback/failure input before
  any ChatScreen connection.
  The third code step fixes artifact freshness and clear/refresh state
  transitions in pure Kotlin. Refresh is metadata-only and records no NPU,
  engine, or decode execution flags.
  The fourth code step fixes the metadata input boundary: key-value text,
  `Map<String, String>`, and file-content text converge into validated metadata
  while raw/native-only fields are dropped before UI input.
  The fifth code step fixes metadata-to-presenter integration from key-value
  text through `UiState` without adding any ChatScreen call site.
  The sixth code step fixes the read-only transient-card view model contract
  and snapshot text without adding any UI component.
  The seventh code step fixes the preview renderer/formatter contract without
  adding Compose UI or ChatScreen wiring.
  The eighth code step wires a read-only metadata-to-renderer preview into
  `NpuDiagnosticChatActivity` only, still outside the normal ChatScreen route.
- `docs/litert_qairt244_npu_turn_stop_quality_compare.md` records the
  `sanitizer_only + max_output_tokens=128` adoption, raw/sanitized output
  policy, and native stop API limitation.
- `artifacts/qairt244_npu_stop_api_investigation/20260525_214513/` records the
  read-only native stop API investigation. The result is
  `native_stop_not_exposed`; no native stop comparison is implemented.
- `docs/litert_qairt244_chat_screen_npu_integration_plan.md` records the same
  sanitizer baseline and no DB/TTS/Markdown/streaming boundary.
- `docs/qairt244_standard_hidden_experimental_plan.md` records the standard
  hidden route gate, default-OFF developer access boundary, and standard-user
  exclusion rules.
- `docs/qairt244_native_artifact_reproducibility.md` records native artifact
  provenance and the no-large-binary Git policy.

## Phase H1 UI Capture Evidence - 2026-05-26

The promotion gate evidence set now includes
`artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`,
which supplements the earlier logic-only wiring artifact
`artifacts/qairt244_phase_h1_transient_preview_wiring/20260526_062814`.

The UI capture passes the pre-promotion H1 display checks:

- Diagnostic-only screen: `NpuDiagnosticChatActivity`
- H1 section visible in `screenshot.png` and `window.xml`
- `DEV ONLY - DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized output rendered as natural Japanese
- no `raw_output`, `<end_of_turn>`, or `<start_of_turn>` in UI
- `selectedPathNpuSaved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- `retry=false`, `auto_fallback=false`
- `npu_generation=false`, `engine_initialize=false`, `run_decode=false`

This evidence is not a normal UI promotion and is not a replacement for a fresh
future promotion run. It only closes the missing representative screenshot/window
gap for the already implemented Diagnostic-only H1 wiring.

## Phase H1 Read-Only Card Evidence - 2026-05-26

`NpuDiagnosticChatActivity` now uses the existing H1 metadata gate to show a
dedicated read-only card instead of relying only on a plain diagnostics text
section.

The card is eligible for display only when:

- metadata is fresh
- sanitizer baseline metadata passes
- renderer returns visible lines
- sanitized output is natural Japanese
- raw output and template tokens are excluded
- standard and normal UI route flags remain false
- DB/TTS/Markdown/streaming flags remain false
- `npu_generation=false`
- `engine_initialize=false`
- `run_decode=false`

This still does not satisfy normal UI promotion by itself. It is a
pre-promotion Diagnostic-only surface check.

## Phase H1 Hidden-State Gate Evidence - 2026-05-26

Artifact:
`artifacts/qairt244_phase_h1_readonly_card_hidden_state_regression/20260526_074740/`

The card display gate now has connected-device regression evidence:

- `success`: `metadata_read=true`, `preview_visible=true`,
  `readonly_card_visible=true`
- `stale`: `metadata_read=true`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=stale_artifact`
- `rollback`: `metadata_read=true`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=fallback_used`
- `toggle_false`: `metadata_read=false`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=initial`

The evidence confirms raw output and template tokens are not displayed in the
hidden cases, and all side-effect flags remain false.

## Compose Adapter Gate Contract - 2026-05-26

The H1 Compose adapter contract adds another pre-promotion guard between the
Diagnostic card model and any future Compose surface.

Required adapter outputs:

- `shouldShowSurface=true` only for visible success card models
- `shouldShowSurface=false` for hidden and rollback card models
- `insertIntoAssistantList=false`
- `persistToDb=false`
- `speakTts=false`
- `renderMarkdown=false`
- `stream=false`
- retry/fallback buttons false

This contract is unit-tested and does not connect to ChatScreen or normal UI.

## Diagnostic Host Gate Contract - 2026-05-26

`DevOnlyNpuPhaseH1PreviewHostState` adds a host-level gate after the Compose
adapter contract.

Promotion-relevant guarantees:

- success only: `visible=true`, `showCard=true`
- stale/rollback/hidden/toggle false: `visible=false`, `showCard=false`
- assistant insertion remains false
- DB/TTS/Markdown/streaming remain false
- retry/fallback remain false
- metadata read remains false at host level
- NPU run, `Engine.initialize`, and `RunDecode` remain false at host level

This is still Diagnostic-only and is not a normal UI promotion.

## XML Card / Host Consistency Gate - 2026-05-26

The H1 promotion gate now includes unit coverage that the current Diagnostic
XML/read-only card and the preview host expose the same text contract.

Gate checks added:

- success XML card text equals preview host render text
- hidden, stale, rollback, and toggle-false states produce no card text
- raw output and turn template tokens are absent
- assistant insertion and side-effect routes remain false
- metadata read, NPU run, `Engine.initialize`, and `RunDecode` remain false at
  host level

This prevents a future host/Compose surface from drifting away from the existing
Diagnostic card behavior.

## Preview Consistency Contract Gate - 2026-05-26

The gate now includes `DevOnlyNpuPhaseH1PreviewConsistencyTest`, which compares
the full read-only preview chain:

```text
XML card helper == PreviewRenderer == PreviewHost == Compose render text
```

For success metadata, the snapshot fixes badge/title/status/output/detail order
and confirms sanitized output only. For hidden, rollback, stale, and toggle-false
states, every render layer is empty/hidden. The same test verifies raw output and
turn template artifacts are absent and that assistant insertion, DB, TTS,
Markdown, streaming, retry/fallback, metadata read, NPU run, engine initialize,
and decode stay false.
