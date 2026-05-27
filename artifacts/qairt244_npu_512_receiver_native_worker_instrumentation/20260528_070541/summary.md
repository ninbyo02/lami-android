# QAIRT244 NPU 512 Receiver/Native Worker Instrumentation

Date: 2026-05-28

Scope: instrumentation, tests, runner artifact schema, and docs only.

No NPU run, 512 rerun, native change, QAIRT rebuild, ChatScreen promotion,
assistant list insertion, DB, TTS, Markdown renderer, streaming, selectedPath
persistence, release/standard change, or 1024+ expansion was performed.

Changes:
- Added runId-scoped terminal trace file support:
  `terminal_trace_<runId>.txt`.
- Instrumented the hidden broadcast receiver / `goAsync` worker boundary.
- Instrumented `runForChatScreen` terminal result and cleanup boundaries.
- Instrumented the native adapter call boundary and pre-RunDecode marker
  detection.
- Added classifier tests for clean, throwable, native non-return/process
  death, terminal result missing, cleanup missing, stale, and runId mismatch.
- Updated the 512 sequential soft-reset runner to pull terminal traces in the
  next approved runtime.

Policy remains unchanged:
- H1 remains pinned to max_output_tokens=128.
- 256 remains the hidden experimental baseline candidate.
- 512 remains `hidden_per_run_isolated_512` candidate only.
- 512 sequential remains incomplete and non-baseline.
- 1024/2048/4096 remain blocked.
