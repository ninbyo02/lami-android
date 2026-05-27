# Process Death Classification

Primary classification:

`broadcast_receiver_native_worker_process_exit`

Rejected or not supported:

- `runner_induced_process_stop`: no runner force-stop/kill/clear/restart.
- `background_execution_limit`: broadcast accepted with `result=0`; receiver
  state/native diag were written.
- `process_snapshot_side_effect`: snapshot helper only reads process state and
  logcat/dumpsys data.
- `android_cached_process_kill`: possible in theory, but no explicit LMK or
  framework kill line is present.
- `native_abort_without_tombstone`: possible, but not proven without tombstone
  or fatal line.

Evidence gap:

The artifacts do not include a terminal marker immediately before/after the
receiver worker call into `runForChatScreen`, nor a catch-all worker exception
marker. That is the next narrow point to instrument.
