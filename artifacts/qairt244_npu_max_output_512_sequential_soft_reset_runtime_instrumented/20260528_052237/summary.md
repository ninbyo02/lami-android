# QAIRT244 NPU 512 Sequential Soft-Reset Runtime With Process Boundary Instrumentation

Source runtime artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_052237/`

Instrumented report artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime_instrumented/20260528_052237/`

Scope: one approved hidden experimental runtime execution. No second run,
max-output expansion, force-stop between prompts, Activity restart, native
change, QAIRT rebuild, ChatScreen promotion, assistant-list insertion, DB,
TTS, Markdown renderer, streaming, selectedPath=NPU persistence, release/
standard change, or 1024+ progression was performed.

## Result

- result_classification: `process_disappeared_suspect`
- stopped_at_prompt: `2`
- stop_reason:
  `PROCESS_DISAPPEARED_SUSPECT_PROCESS_DISAPPEARED_AFTER_DISPATCH`
- process disappearance boundary: prompt 2 `after_dispatch`
- prompt 3 dispatch: `false`

## Prompt Results

| prompt | lifecycle_classification | process boundary summary | result |
| --- | --- | --- | --- |
| `こんにちは` | `SUCCESS_CLEAN` | all snapshots `PROCESS_PRESENT`, pid `17226` | success, natural Japanese |
| `Pythonで簡単な電卓コードを書いて` | `TIMEOUT_SUSPECT` | `before_dispatch=PROCESS_PRESENT`, `after_dispatch=PROCESS_DISAPPEARED_AFTER_DISPATCH` | timeout, no completed callback/cleanup |
| `ラミィのNPU推論について短く説明して` | not run | not dispatched | blocked by process disappearance gate |

## Native / Safety Evidence

- prompt 1: QNN evidence present, fallback=false, timeout=false,
  fresh_crash=false, cleanup elapsed `133ms`, `Engine.close` evidence true.
- prompt 2: reached `before RunDecode SetMaxOutputTokens(512)` but did not
  produce completed backend evidence, cleanup evidence, or output.
- side-effect flags remained false: assistant list, selectedPathSaved, DB,
  TTS, Markdown, and streaming.

## Decision

The instrumentation worked: process disappearance was detected immediately
after prompt 2 dispatch and sequential continuation was stopped before prompt
3. 512 sequential is still not ready for baseline use. Keep 512 as
`hidden_per_run_isolated_512` candidate only, keep 256 as hidden experimental
baseline candidate, keep H1 pinned to 128, and keep 1024/2048/4096 blocked.
