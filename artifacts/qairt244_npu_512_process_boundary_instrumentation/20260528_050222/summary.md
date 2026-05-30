# QAIRT244 NPU 512 Process Boundary Instrumentation

Scope: instrumentation, preflight policy, docs, and unit tests only. No NPU
execution, 512 rerun, native change, QAIRT rebuild, ChatScreen promotion,
assistant-list insertion, DB, TTS, Markdown renderer, streaming, selectedPath
persistence, release/standard change, or 1024+ expansion was performed.

The sequential soft-reset runner now records process boundary snapshots around
each prompt dispatch:

- `before_dispatch`
- `after_dispatch`
- `after_result_or_timeout`
- `after_cleanup`
- `after_10s`

Each snapshot records `pidof`, `ps`, `dumpsys activity processes`,
`dumpsys activity top`, visible-window state, timestamps, and a filtered logcat
slice. The runner writes a `process_boundary_results.md` table and folds
process disappearance into the same sequential stop policy used by lifecycle
classification.

Policy remains unchanged: H1 is pinned to `max_output_tokens=128`; 256 remains
the hidden experimental baseline candidate; 512 remains a
`hidden_per_run_isolated_512` candidate while sequential support is incomplete;
1024/2048/4096 remain blocked.
