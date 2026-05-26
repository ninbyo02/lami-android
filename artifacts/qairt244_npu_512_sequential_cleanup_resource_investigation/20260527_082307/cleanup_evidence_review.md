# Cleanup evidence review

Sequential runner:

- prompt 1 cleanup is present:
  `cleanup_elapsed_ms=124`, `Engine.close=unique_ptr_cleanup`
- prompt 2 cleanup is absent because no native success/completion was written
  before timeout
- prompt 3 cleanup is present after the runner force-stops on prompt 2 timeout
  and starts the app again:
  `cleanup_elapsed_ms=132`, `Engine.close=unique_ptr_cleanup`

Per-run isolated runner:

- prompt 1 cleanup is present:
  `cleanup_elapsed_ms=126`, `Engine.close=unique_ptr_cleanup`
- prompt 2 cleanup is present:
  `cleanup_elapsed_ms=130`, `Engine.close=unique_ptr_cleanup`
- prompt 3 cleanup is present:
  `cleanup_elapsed_ms=142`, `Engine.close=unique_ptr_cleanup`

Assessment:

Cleanup evidence for completed runs is healthy in both modes. The sequential
failure is not caused by a missing prompt-1 `Engine.close` line. The stronger
hypothesis is that prompt-1 cleanup does not fully reset process/runtime state
needed by the next heavier 512 decode, or that warm-process state increases the
chance of a decode/callback stall.
