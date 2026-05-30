# Run-Id Isolation Examples

Expected match:

```text
expected_run_id=run-a
observed_run_id=run-a
run_id_mismatch_rejected=false
```

Mismatch:

```text
expected_run_id=run-a
observed_run_id=run-b
run_id_mismatch_rejected=true
lifecycle_classification=RUN_ID_MISMATCH_REJECTED
```

Legacy missing run id:

```text
expected_run_id=unavailable
observed_run_id=unavailable
```

Legacy missing run id is not treated as success by itself. Clean
classification still requires terminal result, native completion,
cleanup elapsed time, and Engine.close evidence.
