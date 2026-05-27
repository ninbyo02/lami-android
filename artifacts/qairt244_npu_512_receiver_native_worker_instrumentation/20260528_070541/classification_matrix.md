# Classification Matrix

| Classification | Evidence | Runtime meaning |
| --- | --- | --- |
| `WORKER_COMPLETED_CLEAN` | Ordered clean markers through native return, terminal result write, cleanup, finally, and worker finish | Reuse may remain allowed by lifecycle policy |
| `WORKER_THROWABLE_CAUGHT` | `throwable_caught` marker present | Worker exception path; suspect |
| `NATIVE_RETURNED_WITHOUT_RESULT` | Reserved for future native-return/no-result refinement | Suspect |
| `NATIVE_NON_RETURN_OR_PROCESS_DEATH` | `before_native_adapter_run` present, `after_native_adapter_run` absent, and `finally_enter` absent | Native non-return or process death window |
| `FINALLY_NOT_REACHED` | Worker markers exist but `finally_enter` absent | Process death or worker termination before finally |
| `TERMINAL_RESULT_WRITE_MISSING` | Native returned but terminal result markers are missing/incomplete | Result write path suspect |
| `CLEANUP_MISSING` | Terminal result written but `after_cleanup` missing | Cleanup path suspect |
| `RUN_ID_MISMATCH_REJECTED` | Any trace event runId differs from expected runId | Reject stale/colliding artifact |
| `STALE_TRACE_REJECTED` | Trace timestamp outside freshness window | Reject old artifact |
| `UNKNOWN` | Insufficient marker evidence | Suspect |

All non-clean classifications are suspect and require hidden per-run isolation
before another hidden attempt.
