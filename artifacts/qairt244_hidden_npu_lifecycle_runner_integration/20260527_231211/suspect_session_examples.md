# Suspect Session Examples

Timeout:

```text
runId=run-a state=timeout timeout=true
```

Classification: `TIMEOUT_SUSPECT`.

Missing callback:

```text
runId=run-a state=started timeout=false
```

Classification: `CLEANUP_MISSING_SUSPECT`.

Missing native completion:

```text
runId=run-a before RunDecode SetMaxOutputTokens(512)
```

Classification: `CLEANUP_MISSING_SUSPECT`.

Run-id mismatch:

```text
expected=run-a
native_diag: runId=run-b
```

Classification: `RUN_ID_MISMATCH_REJECTED`.

Stale result:

```text
artifactTimestampMs=1000
result_written_at_ms=500
```

Classification: `STALE_RESULT_REJECTED`.
