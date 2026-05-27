# QAIRT244 NPU 512 Instrumented Worker Runtime

Date: 2026-05-28

Source runner:
`scripts/run_qairt244_npu_max_output_512_sequential_soft_reset_runtime.sh`

Source runtime artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_073227/`

Scope: one approved hidden experimental runtime execution. No retry, no
second run, no force-stop between prompts, no Activity restart, no native
change, no QAIRT rebuild, no ChatScreen promotion, no assistant-list
insertion, no DB/TTS/Markdown/streaming connection, no selectedPath=NPU
persistence, and no 1024+ expansion.

Runtime:
- max_output_tokens: `512`
- timeout_seconds: `60`
- prompt mode: `gemma_it_like`
- process policy: sequential soft-reset, process maintained
- terminal trace: enabled
- process boundary snapshots: enabled

## Result

Overall status: `failure`

Result classification: `NATIVE_NON_RETURN_OR_PROCESS_DEATH` with
`PROCESS_DISAPPEARED_AFTER_CLEANUP` observed at the post-timeout boundary.

Prompt 1:
- prompt: `こんにちは`
- lifecycle: `SUCCESS_CLEAN`
- process boundary: process present at all prompt 1 boundaries
- terminal trace: `WORKER_COMPLETED_CLEAN`
- cleanup / Engine.close: present
- QNN evidence: `QNN_HTP_V79_FastRPC_native_diag`

Prompt 2:
- prompt: `Pythonで簡単な電卓コードを書いて`
- lifecycle: `TIMEOUT_SUSPECT`
- process boundary: process present before and immediately after dispatch;
  process absent at `after_result_or_timeout`, `after_cleanup`, and `after_10s`
- terminal trace final marker: `before_native_adapter_run`
- `after_native_adapter_run`: missing
- `throwable_caught`: missing
- `finally_enter`: missing
- `worker_finished`: missing
- native diag: reached `before RunDecode SetMaxOutputTokens(512)`
- cleanup / Engine.close: missing
- completed backend evidence: missing

Prompt 3:
- not dispatched
- reason: prompt 2 suspect session set `next_prompt_allowed=false`

## Decision

The prompt 2 failure is no longer attributed to terminal result writing or
cleanup writing. The worker reached the native adapter call, native diagnostics
reached pre-RunDecode, and then Kotlin did not regain control. The next
investigation should focus on native decode non-return/process death under
sequential 512 reuse.

Policy remains unchanged:
- H1 remains pinned to max_output_tokens=128.
- 256 remains the hidden experimental baseline candidate.
- 512 remains `hidden_per_run_isolated_512` candidate only.
- 512 sequential remains incomplete and non-baseline.
- 1024/2048/4096 remain blocked.
