# Sequential Soft-Reset Runner Spec

- max_output_tokens=512
- timeout_seconds=60
- prompt_order:
  1. こんにちは
  2. Pythonで簡単な電卓コードを書いて
  3. ラミィのNPU推論について短く説明して
- force_stop=false for the future soft-reset experiment
- activity_restart=false for the future soft-reset experiment
- unique runId required per prompt
- state/result/native_diag/cleanup paths must be runId-scoped and must not read previous results
- lifecycle summary must be regenerated after each prompt
- only lifecycle_classification=SUCCESS_CLEAN may continue
- cleanup_elapsed_ms and Engine.close=unique_ptr_cleanup are required
- stale result or runId mismatch rejects the run
- suspect_session, reuse_allowed=false, or hidden_per_run_isolated_required=true stops the sequence
- this script is preflight-only and does not execute NPU
