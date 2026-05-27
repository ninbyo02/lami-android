# Cleanup Evidence Gate

Clean condition:

- observed run ids match the expected run id
- result is not stale
- terminal success/failure marker exists
- native diag has completed evidence
- `cleanup_elapsed_ms` is present and non-negative
- `Engine.close=unique_ptr_cleanup` is present
- side-effect flags are false

Suspect condition:

- `state=timeout` or `timeout=true` => `TIMEOUT_SUSPECT`
- missing terminal success/failure callback => `CLEANUP_MISSING_SUSPECT`
- missing native completed evidence => `CLEANUP_MISSING_SUSPECT`
- missing `cleanup_elapsed_ms` => `CLEANUP_MISSING_SUSPECT`
- missing `Engine.close=unique_ptr_cleanup` => `CLEANUP_MISSING_SUSPECT`

Suspect runs forbid session reuse and require per-run isolated/force-stop
before another 512 attempt.
