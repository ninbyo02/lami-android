# Sequence Gate Matrix

| condition | sequence action |
|---|---|
| lifecycle_classification=SUCCESS_CLEAN and reuse_allowed=true and hidden_per_run_isolated_required=false | continue |
| TIMEOUT_SUSPECT | stop immediately |
| CLEANUP_MISSING_SUSPECT | stop immediately |
| STALE_RESULT_REJECTED | stop immediately |
| RUN_ID_MISMATCH_REJECTED | stop immediately |
| reuse_allowed=false | stop immediately |
| hidden_per_run_isolated_required=true | stop immediately |
| cleanup_elapsed_ms=missing | stop immediately |
| engine_close_evidence=false | stop immediately |
