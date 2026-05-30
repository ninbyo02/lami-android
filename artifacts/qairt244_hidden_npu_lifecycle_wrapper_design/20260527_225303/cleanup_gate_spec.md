# Cleanup Gate Spec

Required clean evidence:

- started marker for the current `runId`
- terminal success or failure marker for the current `runId`
- cleanup marker for the current `runId`
- non-negative `cleanup_elapsed_ms`
- `Engine.close=unique_ptr_cleanup`
- side-effect flags all false

If cleanup evidence is missing, the run is classified as
`CLEANUP_MISSING_SUSPECT`. If the run timed out, it is classified as
`TIMEOUT_SUSPECT` even when partial evidence exists. Both outcomes forbid
session reuse.

This gate is stricter than the current file wait. It treats cleanup and close
as evidence to prove, not as a side effect to assume.
