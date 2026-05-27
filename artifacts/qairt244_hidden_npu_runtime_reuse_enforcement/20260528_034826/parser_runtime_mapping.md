# Parser Runtime Mapping

`DevOnlyNpuLifecycleArtifactParser` and the shell lifecycle summary helper feed
the same runtime policy:

- clean parser evidence produces `SUCCESS_CLEAN`, `reuse_allowed=true`, and
  `runtime_reuse_allowed=true` / `next_prompt_allowed=true`.
- timeout evidence produces `TIMEOUT_SUSPECT`, `reuse_allowed=false`, and
  `next_prompt_allowed=false`.
- missing terminal/native/cleanup/Engine.close evidence produces
  `CLEANUP_MISSING_SUSPECT`.
- stale result evidence produces `STALE_RESULT_REJECTED`.
- run-id mismatch evidence produces `RUN_ID_MISMATCH_REJECTED`.

The sequential soft-reset preflight reads these fields and stops unless
`next_prompt_allowed=true`.
