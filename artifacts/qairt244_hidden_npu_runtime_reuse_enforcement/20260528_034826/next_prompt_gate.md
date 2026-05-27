# Next Prompt Gate

The next prompt gate is the runtime form of the lifecycle summary contract.

Required fields for continuation:

- `lifecycle_classification=SUCCESS_CLEAN`
- `reuse_allowed=true`
- `next_prompt_allowed=true`
- `runtime_reuse_policy=reuse_allowed`
- `hidden_per_run_isolated_required=false`
- `stale_result_rejected=false`
- `run_id_mismatch_rejected=false`
- side-effect flags false

Any other state stops the sequence before dispatching another prompt.
