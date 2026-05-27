# Lifecycle Wrapper Spec

The hidden NPU lifecycle wrapper is a contract layer for diagnostic execution.
It does not run native code and does not connect output to ChatScreen. Its
purpose is to decide whether one hidden turn is clean, suspect, stale, or
run-id invalid before any future sequential reuse.

Required inputs:

- `runId`
- run-id scoped state file name
- run-id scoped result file name
- run-id scoped native diag file name
- run-id scoped cleanup file name
- callback/state/result/native-diag/cleanup observed run ids
- started marker
- terminal success/failure/timeout marker
- cleanup marker
- `cleanup_elapsed_ms`
- `Engine.close=unique_ptr_cleanup`
- side-effect flags

Classifications:

- `SUCCESS_CLEAN`
- `FAILURE_CLEAN`
- `TIMEOUT_SUSPECT`
- `CLEANUP_MISSING_SUSPECT`
- `STALE_RESULT_REJECTED`
- `RUN_ID_MISMATCH_REJECTED`

Clean classifications require all side-effect flags false before the run can be
accepted for future reuse. Suspect or rejected classifications require
per-run isolation before another 512 attempt.
