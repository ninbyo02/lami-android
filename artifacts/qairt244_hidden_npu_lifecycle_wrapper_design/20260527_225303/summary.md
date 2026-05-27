# Hidden NPU Lifecycle Wrapper Design

Date: 2026-05-27

Scope: design and unit-test contract only. No NPU run, native change,
QAIRT/LiteRT-LM rebuild, ChatScreen promotion, assistant-list insertion, DB,
TTS, Markdown renderer, streaming renderer, selectedPath=NPU persistence,
release/standard change, or 1024/2048/4096 progression was performed.

Implementation:

- `app/src/debug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleWrapper.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuLifecycleWrapperTest.kt`

The wrapper is not connected to runtime execution in this phase. It fixes the
hidden route contract for future work: every hidden NPU run must be identified
by a run id, all state/result/native-diag/cleanup evidence must belong to that
run id, stale output must be rejected, cleanup and
`Engine.close=unique_ptr_cleanup` are required for clean classification, and a
timeout or missing cleanup marks the session suspect.

Policy held:

- 256 remains the hidden experimental baseline candidate.
- 512 remains `hidden_per_run_isolated_512` only.
- Sequential 512 and Activity-restart-only 512 remain rollback modes.
- H1 remains pinned to `max_output_tokens=128`.
- 1024/2048/4096 remain blocked.
