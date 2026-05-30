# Dispatch Gate Policy

The sequential soft-reset runtime keeps the previous lifecycle gate and adds a
process boundary gate:

- `before_dispatch` must allow dispatch.
- `SUCCESS_CLEAN` is still required before the next prompt.
- `next_prompt_allowed=true` is required.
- `reuse_allowed=true` and `runtime_reuse_allowed=true` are required.
- `hidden_per_run_isolated_required=false` is required.
- `PROCESS_ABSENT_BEFORE_DISPATCH` stops before prompt dispatch.
- `PROCESS_DISAPPEARED_SUSPECT` stops sequential continuation.

When process disappearance is suspected, the runner records:

- `reuse_allowed=false`
- `runtime_reuse_allowed=false`
- `next_prompt_allowed=false`
- `runtime_reuse_policy=per_run_isolated_required`
- `hidden_per_run_isolated_required=true`
