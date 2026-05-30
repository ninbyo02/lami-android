# QAIRT244 Edge Gallery Streaming Lifecycle Compare

Date: 2026-05-27

Artifact:
`artifacts/qairt244_edge_gallery_streaming_lifecycle_compare/20260527_223704/`

Scope: static investigation and documentation only. This pass did not change
Lami code, did not execute NPU, did not change native code, did not rebuild
QAIRT/LiteRT-LM, did not promote ChatScreen, did not connect assistant-list
insertion, DB, TTS, Markdown renderer, streaming, or selectedPath=NPU
persistence, and did not progress to 1024/2048/4096.

## Sequential Soft-Reset Runtime - 2026-05-28

Artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_041357/`

The hidden lifecycle gate was exercised once at runtime. Prompt 1 and the
Python code prompt both reached `SUCCESS_CLEAN`; prompt 2 returned
`useful_code` with code-aware sanitizer checks passing. Prompt 3 timed out and
classified as `TIMEOUT_SUSPECT`, which correctly forced
`next_prompt_allowed=false` and `hidden_per_run_isolated_required=true`.

Interpretation: the Edge Gallery-inspired lifecycle gate is useful as a
runtime safety boundary, but it does not yet make 512 sequential mode stable.
Gallery-style streaming renderer remains out of scope, 512 per-run isolated
remains the only 512 candidate mode, and 1024/2048/4096 remain blocked.

## Sequential Soft-Reset Preflight - 2026-05-28

Artifact:
`artifacts/qairt244_npu_512_sequential_soft_reset_preflight/20260528_033653/`

The Edge Gallery lifecycle lesson is still limited to hidden lifecycle gating,
not streaming UI. The new soft-reset preflight models per-turn run-id
separation, terminal lifecycle classification, cleanup/close evidence, and
immediate stop on suspect sessions before any future sequential 512 runtime
attempt.

Gallery-style streaming renderer remains out of scope. The preflight keeps
512 per-run isolated as the only candidate mode and keeps 1024/2048/4096
blocked.

## Lifecycle Summary Regeneration - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_summary_regeneration/20260528_030629/`

The hidden lifecycle parser was checked against existing artifacts only. This
confirms the Edge Gallery comparison follow-up is compatible with real Lami
artifact shapes: clean runs become `SUCCESS_CLEAN`, and the sequential and
Activity-restart-only 512 Python timeouts become `TIMEOUT_SUSPECT`.

Policy impact: suspect sessions prevent reuse and require hidden per-run
isolation. This does not authorize Gallery-style streaming UI, normal
ChatScreen integration, assistant-list insertion, DB/TTS/Markdown/streaming,
selectedPath=NPU persistence, native rebuilds, or 1024+ progression.

## Summary

Google AI Edge Gallery is useful as a lifecycle reference, but it should not be
ported wholesale into Lami. The parts worth extracting are lifecycle concepts:
per-turn session/conversation boundary, terminal callback handling, cleanup
listener, explicit reset, and error-triggered cleanup/reinitialize. Gallery's
normal chat streaming renderer and persisted chat state remain out of scope.

The recommended Lami direction is design-only for now:

1. Hidden NPU route session lifecycle wrapper.
2. Callback/state/result/native-diagnostic id separation per hidden turn.
3. Per-turn engine/session close wait and suspect-session classification when
   cleanup evidence is missing.

## Edge Gallery Findings

Gallery keeps model execution behind `runtimeHelper`. In the LLM chat helper,
`LlmModelInstance` stores one `Engine` and the current `Conversation`.
Initialization creates and initializes the engine, then creates a conversation.
Session reset closes the current conversation and creates a new conversation
from the existing engine.

Streaming is callback based. `runInference(...)` calls
`conversation.sendMessageAsync(...)`. The callback sends partial chunks through
`resultListener`, sends `done=true` from `onDone`, and treats
`CancellationException` as a completed stop path. The chat ViewModel receives
these callbacks, removes the loading message, appends partial chunks to the
last assistant text message, and clears `inProgress` on completion.

Stop is cooperative. `stopResponse(...)` calls `conversation.cancelProcess()`
and updates UI state before native cancellation is proven complete. Reset calls
stop, then retries `resetConversation(...)` with a short delay until it
succeeds. Error handling can cleanup and reinitialize the model.

## LiteRT-LM Findings

LiteRT-LM Kotlin `Conversation.sendMessageAsync(...)` wraps native callback
delivery. The Flow wrapper uses `callbackFlow`, but its `awaitClose {}` does
not call `cancelProcess()`, so collector cancellation alone does not stop
native work.

`cancelProcess()` is cooperative: Kotlin forwards to JNI, JNI forwards to
`Conversation::CancelProcess()`, and C++ forwards to the session cancel path.
Streaming generation runs prefill asynchronously, then decode asynchronously.
Decode receives a shared cancellation flag, so cancellation depends on the
decode path observing that flag or producing a terminal callback.

Session cleanup can wait for in-flight work. `SessionBasic` waits until work is
done before resetting executors. This is important for Lami because a timed-out
QAIRT244 run with no terminal callback should not be assumed clean.

## Lami Difference

Lami's current hidden route is file-contract based rather than direct callback
based. `StandardHiddenQairt244PromptReceiver` starts a guarded hidden run,
writes state/result/native-diagnostic files, and the runner waits for those
files. The completed native path records backend evidence, cleanup timing, and
`Engine.close=unique_ptr_cleanup`; the sequential 512 Python timeout records
only pre-RunDecode evidence.

512 evidence now splits the modes clearly:

- Sequential 512: Python code prompt times out after
  `SetMaxOutputTokens(512)` pre-decode evidence, with no completed result,
  cleanup, or backend evidence.
- Activity-restart-only 512: Python code prompt still times out.
- Force-stop between prompts: all three prompts pass with useful code,
  indentation/fence checks, cleanup evidence, and no retained process after 10
  seconds.

## Recommendation

Do not adopt Edge Gallery's normal streaming UI yet. First design a hidden-only
NPU lifecycle wrapper:

- Generate a unique run id for each hidden prompt.
- Bind receiver state, result, native diag, cleanup state, timeout note, and
  memory artifacts to that run id.
- Require one terminal callback/result for that run id.
- Require cleanup and `Engine.close` evidence before any sequential 512 reuse.
- On timeout after pre-RunDecode evidence, mark the session/engine/process as
  suspect unless bounded cleanup evidence arrives.
- Keep 512 in per-run isolated mode until this hidden wrapper is implemented
  and separately tested.

## Non-Adopted Elements

Do not adopt the following in this phase:

- Gallery streaming renderer.
- Assistant message-list insertion.
- Chat history persistence or DB writes.
- Markdown renderer path.
- TTS.
- selectedPath=NPU persistence.
- Release or standard behavior changes.
- Native guard changes or QAIRT rebuild.
- 1024/2048/4096 progression.

## Policy

256 remains the hidden experimental baseline candidate. 512 remains hidden
`hidden_per_run_isolated_512` only. Sequential 512 and Activity-restart-only
512 remain rollback modes. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`. 1024/2048/4096 remain blocked.

## Hidden Lifecycle Wrapper Contract - 2026-05-27

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_wrapper_design/20260527_225303/`

The first implementation step after this comparison is a hidden-only lifecycle
contract, not streaming UI. `DevOnlyNpuLifecycleWrapper` defines the run-id and
cleanup evidence rules that any future sequential 512 retest must satisfy.

Contract:

- `runId` is mandatory.
- State, result, native diag, and cleanup file names must be scoped to that
  `runId`.
- Callback, state, result, native diag, and cleanup observed run ids must match
  the current `runId`.
- Stale result files are rejected.
- Cleanup requires `cleanup_elapsed_ms` and
  `Engine.close=unique_ptr_cleanup`.
- Timeout or missing cleanup classifies the run as suspect and forbids session
  reuse.
- Side-effect flags for assistant list, selectedPath, DB, TTS, Markdown, and
  streaming must remain false.

This remains hidden-only and does not connect Gallery's streaming renderer,
ChatScreen insertion, DB persistence, TTS, Markdown, or selectedPath=NPU.

## Hidden Lifecycle Artifact Parser - 2026-05-27

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_runner_integration/20260527_231211/`

`DevOnlyNpuLifecycleArtifactParser` connects the wrapper contract to
runner/preflight artifact text. It accepts expected `runId`, state text, result
text, native diag text, cleanup text, and an artifact timestamp, then produces
the same lifecycle classifications as the wrapper.

Parser rules:

- stale result timestamps are rejected
- state/result/native_diag/cleanup run-id mismatch is rejected
- timeout becomes `TIMEOUT_SUSPECT`
- missing terminal result, missing native completed evidence, missing
  `cleanup_elapsed_ms`, or missing `Engine.close=unique_ptr_cleanup` becomes
  `CLEANUP_MISSING_SUSPECT`
- side-effect flags must remain false before a clean run is accepted

This remains hidden-only and does not introduce LiteRT-LM streaming,
ChatScreen streaming, assistant-list insertion, DB, TTS, Markdown renderer, or
selectedPath=NPU behavior.

## Hidden Lifecycle Summary Integration - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_lifecycle_summary_integration/20260528_024448/`

The hidden runner summaries now surface lifecycle classification from local
artifact text. The summary keys include `lifecycle_classification`,
`expected_run_id`, `observed_run_id`, `cleanup_elapsed_ms`,
`engine_close_evidence`, `suspect_session`, `reuse_allowed`,
`hidden_per_run_isolated_required`, `stale_result_rejected`, and
`run_id_mismatch_rejected`.

Policy mapping: timeout and missing cleanup are suspect; stale and mismatched
artifacts are rejected; suspect/rejected outcomes set `reuse_allowed=false` and
require hidden per-run isolated operation. This is artifact summary
integration only and does not adopt Gallery streaming UI.

## Runtime Reuse Enforcement - 2026-05-28

Artifact:
`artifacts/qairt244_hidden_npu_runtime_reuse_enforcement/20260528_034826/`

The hidden lifecycle classification now has an explicit runtime reuse mapping:

| Lifecycle classification | Runtime session state | `next_prompt_allowed` |
| --- | --- | --- |
| `SUCCESS_CLEAN` with side-effect flags clear | current run accepted; session reuse allowed | `true` |
| `TIMEOUT_SUSPECT` | session suspect; do not reuse | `false` |
| `CLEANUP_MISSING_SUSPECT` | session suspect; do not reuse | `false` |
| `STALE_RESULT_REJECTED` | artifact rejected; do not reuse | `false` |
| `RUN_ID_MISMATCH_REJECTED` | artifact rejected; do not reuse | `false` |

`DevOnlyNpuRuntimeReusePolicy` exposes the wrapper decision as
`next_prompt_allowed`. A later prompt can reuse the runtime only after a clean
terminal result, run-id matched artifacts, cleanup timing, and
`Engine.close=unique_ptr_cleanup` evidence. Any timeout, missing cleanup, stale
result, or run-id mismatch keeps `next_prompt_allowed=false` and
`hidden_per_run_isolated_required=true`.

Policy remains unchanged: H1 stays pinned to
`sanitizer_only + max_output_tokens=128`, 256 remains the hidden experimental
baseline candidate, 512 remains `hidden_per_run_isolated_512` only, and 1024+
remains blocked. This documentation and test pass did not execute NPU, run adb,
change native code, rebuild, or promote normal ChatScreen behavior.
