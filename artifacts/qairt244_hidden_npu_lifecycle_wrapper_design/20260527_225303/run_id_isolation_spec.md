# Run-Id Isolation Spec

Rules:

1. `runId` is required and must be non-blank.
2. State, result, native diag, and cleanup file names must contain the current
   `runId`.
3. Callback, state, result, native diag, and cleanup observed run ids must be
   either absent or equal to the current `runId`.
4. Result timestamps older than the current run start are stale and rejected.
5. A stale result from a previous run is never accepted as current completion.
6. A mismatch in native diag run id is rejected even if the result file says
   success.

Reason:

The 512 sequential timeout evidence leaves room for callback/state/result file
collision or stale terminal output in future sequential experiments. The
wrapper prevents that class by requiring a run-id match across every evidence
channel.
