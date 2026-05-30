# Parser Summary Examples

Clean success:

```text
lifecycle_classification=SUCCESS_CLEAN
suspect_session=false
reuse_allowed=true
hidden_per_run_isolated_required=false
cleanup_elapsed_ms=42
engine_close_evidence=true
```

Timeout:

```text
lifecycle_classification=TIMEOUT_SUSPECT
suspect_session=true
reuse_allowed=false
hidden_per_run_isolated_required=true
result_rejected=true
```

Cleanup missing:

```text
lifecycle_classification=CLEANUP_MISSING_SUSPECT
suspect_session=true
reuse_allowed=false
hidden_per_run_isolated_required=true
cleanup_elapsed_ms=missing
```

Stale result:

```text
lifecycle_classification=STALE_RESULT_REJECTED
stale_result_rejected=true
reuse_allowed=false
completed_result_accepted=false
```

Run-id mismatch:

```text
lifecycle_classification=RUN_ID_MISMATCH_REJECTED
run_id_mismatch_rejected=true
reuse_allowed=false
completed_result_accepted=false
```
