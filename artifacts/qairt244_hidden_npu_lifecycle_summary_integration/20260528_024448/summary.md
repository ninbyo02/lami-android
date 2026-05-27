# Hidden NPU Lifecycle Summary Integration

Date: 2026-05-28

Scope: runner/preflight summary integration, parser summary generation, unit
tests, and docs only. No NPU run, native change, QAIRT/LiteRT-LM rebuild,
ChatScreen promotion, assistant-list insertion, DB, TTS, Markdown renderer,
streaming renderer, selectedPath=NPU persistence, release/standard change,
`app/src/main/jniLibs` change, or 1024/2048/4096 progression was performed.

Implemented:

- Shared shell summary helper:
  `scripts/qairt244_lifecycle_summary_lib.sh`
- Integrated lifecycle summary lines into:
  - `scripts/run_qairt244_npu_max_output_512_force_stop_between_prompts.sh`
  - `scripts/run_qairt244_npu_max_output_512_activity_restart_compare.sh`
  - `scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh`
- Added Kotlin summary builder:
  `DevOnlyNpuLifecycleSummary`
- Added integration unit tests:
  `DevOnlyNpuLifecycleSummaryIntegrationTest`

Summary keys now include classification, expected/observed run id,
cleanup elapsed time, Engine.close evidence, suspect session, reuse policy,
stale/mismatch rejection, and hidden per-run isolated requirement.
