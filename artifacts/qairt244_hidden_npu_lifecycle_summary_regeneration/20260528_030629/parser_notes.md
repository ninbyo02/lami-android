# Parser Notes

- This regeneration is preflight-only. It reads existing artifact files and does not call adb, RunDecode, native rebuild, or app runners.
- Source run directories are treated as read-only. Regenerated lifecycle summaries are written only under this artifact directory.
- Legacy artifacts may not have every channel stored as a new run-id-scoped schema file. The regeneration uses the existing per-case `result.txt`, `receiver_state.txt`, `native_diag.txt`, and `ui_cleanup_state.txt` files with `qairt244_lifecycle_summary_lines`.
- Stale or run-id mismatch markers are rejected when present. The reviewed source artifacts did not contain stale-result or mismatch markers in the parsed cases.
- Timeout cases are passed to the parser with `wait_status=timeout`, so they classify as suspect even when the receiver state remains at `state=started`.
