# Parser Contract

Input:

- expected `runId`
- state file text
- result file text
- native diag text
- cleanup text
- artifact timestamp
- optional run-id scoped file names

Output:

- `SUCCESS_CLEAN`
- `FAILURE_CLEAN`
- `TIMEOUT_SUSPECT`
- `CLEANUP_MISSING_SUSPECT`
- `STALE_RESULT_REJECTED`
- `RUN_ID_MISMATCH_REJECTED`

The parser extracts observed run ids and key-value fields from each artifact
text. It creates wrapper evidence and applies the lifecycle decision:

- state/result/native_diag/cleanup run ids must match the expected run id when
  present.
- result timestamps older than the artifact timestamp are stale.
- terminal result evidence must be present.
- native completed evidence must be present.
- cleanup elapsed time and `Engine.close=unique_ptr_cleanup` must be present.
- side-effect flags must remain false for the run to be accepted.
