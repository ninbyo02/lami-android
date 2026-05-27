# Lifecycle Classification Matrix

| Classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | completed_result_accepted |
| --- | --- | --- | --- | --- |
| `SUCCESS_CLEAN` | false | true | false | true |
| `FAILURE_CLEAN` | false | true | false | true |
| `TIMEOUT_SUSPECT` | true | false | true | false |
| `CLEANUP_MISSING_SUSPECT` | true | false | true | false |
| `STALE_RESULT_REJECTED` | true | false | true | false |
| `RUN_ID_MISMATCH_REJECTED` | true | false | true | false |

Clean classifications still require side-effect flags false before the current
run can be accepted.
