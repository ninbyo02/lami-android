# Timeout Classification

Primary classification: `C. native_hang_or_no_callback`.

Secondary classification: `D. cleanup_unknown`.

Rejected classifications:

- `A. runner_timeout_too_short`: possible but not proven. The runner timeout is
  bounded at 30 seconds, but the native path produced no completion, no partial
  output, and no cleanup evidence before force-stop.
- `B. decode_too_long_but_alive`: possible but not proven. Native reached
  `SetMaxOutputTokens(512)`, so it likely entered or was about to enter decode,
  but the artifact does not prove the process was alive and decoding at the
  timeout instant.
- `E. crash_or_process_death`: no fresh crash, tombstone, fatal log, or crash
  classification was captured. The process was intentionally force-stopped by
  the runner after timeout.
- `F. unknown`: not needed as the artifact is specific enough to classify the
  observed failure as native no-return/no-callback before the runner deadline.

Evidence:

- `timeout_seconds=30`
- Python prompt case summary: `status=timeout`, `wait_status=timeout`,
  `elapsed_ms=40000`, `quality_classification=timeout`
- `result_2.txt` stops at receiver `state=started`
- `native_diag_2.txt` stops after
  `before RunDecode SetMaxOutputTokens(512) native_max_output_tokens_limit=512`
- `raw_output_2.txt` and `sanitized_output_2.txt` are empty
- `meminfo_after_each_run.txt` records
  `after_python_calculator: No process found`, consistent with runner
  force-stop after timeout
