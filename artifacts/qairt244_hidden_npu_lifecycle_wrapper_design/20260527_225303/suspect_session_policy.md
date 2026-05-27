# Suspect Session Policy

Suspect classifications:

- `TIMEOUT_SUSPECT`
- `CLEANUP_MISSING_SUSPECT`
- `STALE_RESULT_REJECTED`
- `RUN_ID_MISMATCH_REJECTED`

Policy:

- A suspect run cannot authorize session reuse.
- A suspect run requires per-run isolated / force-stop operation before another
  512 attempt.
- A timeout after pre-RunDecode evidence remains suspect until a terminal
  callback/result and cleanup/close evidence are observed for the same `runId`.
- Missing cleanup evidence is enough to keep sequential 512 rollback active.

This preserves the current finding: 512 can be reviewed only as
`hidden_per_run_isolated_512`; sequential and Activity-restart-only remain
rollback modes.
