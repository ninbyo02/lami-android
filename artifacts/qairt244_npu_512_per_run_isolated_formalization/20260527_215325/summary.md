# QAIRT244 NPU 512 per-run isolated formalization

- artifact: `artifacts/qairt244_npu_512_per_run_isolated_formalization/20260527_215325`
- scope: runner/gate/docs/tests only
- additional_npu_execution: `false`
- native_change: `false`
- qairt_rebuild: `false`
- formal_512_mode: `hidden_per_run_isolated_512`
- accepted_execution_isolation: `per_run_force_stop`
- sequential_512_baseline: `false`
- activity_restart_only_512_baseline: `false`
- h1_pinned_max_output_tokens: `128`
- hidden_256_baseline_candidate: `true`
- blocked_expansions: `1024,2048,4096`

This phase formalizes the already-passing force-stop comparison as the only
allowed 512 hidden candidate mode. It does not authorize normal ChatScreen,
assistant-list insertion, DB, TTS, Markdown renderer, streaming, selectedPath
persistence, release behavior, or standard behavior.
