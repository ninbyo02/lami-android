# Reuse Enforcement Rules

- `next_prompt_allowed=true` only when `lifecycle_classification=SUCCESS_CLEAN`.
- `runtime_reuse_allowed=true` only when `next_prompt_allowed=true`.
- `reuse_allowed=false` stops the sequence.
- `hidden_per_run_isolated_required=true` stops the sequence.
- Missing `cleanup_elapsed_ms` or missing `Engine.close=unique_ptr_cleanup`
  classifies the run as cleanup suspect before reuse.
- Stale result or run-id mismatch rejects the artifact and forbids reuse.
- Side-effect ingress keeps the current run from being accepted.
