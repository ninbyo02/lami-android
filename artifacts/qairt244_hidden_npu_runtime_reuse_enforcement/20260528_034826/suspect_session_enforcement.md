# Suspect Session Enforcement

`TIMEOUT_SUSPECT` and `CLEANUP_MISSING_SUSPECT` mark the session as suspect.

Suspect policy:

- `reuse_allowed=false`
- `runtime_reuse_allowed=false`
- `next_prompt_allowed=false`
- `hidden_per_run_isolated_required=true`
- sequential continuation forbidden

After suspect classification, a future hidden runtime attempt must use the
already-formalized per-run isolated path. This artifact does not authorize that
runtime attempt.
