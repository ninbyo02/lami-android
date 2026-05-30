# Runtime Policy Matrix

| lifecycle classification | runtime reuse | next prompt | hidden per-run isolated |
|---|---|---|---|
| `SUCCESS_CLEAN` | allowed | allowed | not required |
| `FAILURE_CLEAN` | not allowed for sequential continuation | blocked | required by runtime policy before another hidden attempt |
| `TIMEOUT_SUSPECT` | forbidden | blocked | required |
| `CLEANUP_MISSING_SUSPECT` | forbidden | blocked | required |
| `STALE_RESULT_REJECTED` | forbidden | blocked | required |
| `RUN_ID_MISMATCH_REJECTED` | forbidden | blocked | required |

The policy is conservative: sequential continuation requires a successful clean
turn, not merely a terminal failure with cleanup.
