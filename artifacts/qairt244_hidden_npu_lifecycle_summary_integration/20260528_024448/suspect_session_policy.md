# Suspect Session Policy

Suspect session is reported when classification is:

- `TIMEOUT_SUSPECT`
- `CLEANUP_MISSING_SUSPECT`
- `STALE_RESULT_REJECTED`
- `RUN_ID_MISMATCH_REJECTED`

Policy:

- `reuse_allowed=false`
- `hidden_per_run_isolated_required=true`
- stale or mismatched artifacts are not completed results
- timeout or missing cleanup cannot be promoted to sequential 512
- 512 remains hidden per-run isolated only

Activity-restart-only 512 stays rollback even if a single case reports clean
native cleanup, because the mode gate remains failed for 512.
