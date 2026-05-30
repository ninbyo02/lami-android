# Hidden NPU Lifecycle Runner Integration

Date: 2026-05-27

Scope: artifact parser integration and unit tests only. No NPU run, native
change, QAIRT/LiteRT-LM rebuild, ChatScreen promotion, assistant-list
insertion, DB, TTS, Markdown renderer, streaming renderer, selectedPath=NPU
persistence, release/standard change, `app/src/main/jniLibs` change, or
1024/2048/4096 progression was performed.

Implementation:

- `DevOnlyNpuLifecycleArtifactParser`
- `DevOnlyNpuLifecycleArtifactParserTest`

The parser converts runner/preflight artifact text into
`DevOnlyNpuLifecycleEvidence`, then delegates to
`DevOnlyNpuLifecycleWrapper`. It handles run-id matching, stale result
rejection, cleanup/`Engine.close` gate checks, timeout classification,
cleanup-missing suspect classification, and side-effect flags.

Policy held:

- H1 remains pinned to `max_output_tokens=128`.
- 256 remains the hidden experimental baseline candidate.
- 512 remains `hidden_per_run_isolated_512` only.
- Sequential 512 and Activity-restart-only 512 remain rollback modes.
- 1024/2048/4096 remain blocked.
