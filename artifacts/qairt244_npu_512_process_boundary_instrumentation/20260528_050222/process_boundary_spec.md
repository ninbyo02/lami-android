# Process Boundary Spec

The hidden 512 sequential soft-reset runner captures a process snapshot at
every prompt boundary without changing the runtime isolation mode.

Required boundaries per prompt:

- `before_dispatch`: taken after app-file soft reset and before broadcast.
- `after_dispatch`: taken immediately after the hidden prompt broadcast.
- `after_result_or_timeout`: taken after the state wait returns or times out.
- `after_cleanup`: taken after result/native_diag/cleanup artifacts are pulled
  and after-run meminfo is appended.
- `after_10s`: taken after the bounded 10 second post-run memory check.

The instrumentation is observational. It does not force-stop, kill, restart
the Activity, clear app data, or run additional prompts.
