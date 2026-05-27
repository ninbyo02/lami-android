# Process Disappearance Boundary

Result classification: `process_disappeared_suspect`

The process disappearance was first detected at prompt 2
`after_dispatch`.

Evidence:

- prompt 1 all process-boundary snapshots: `PROCESS_PRESENT`, pid `17226`.
- prompt 2 `before_dispatch`: `PROCESS_PRESENT`, pid `17226`.
- prompt 2 `after_dispatch`: `PROCESS_DISAPPEARED_AFTER_DISPATCH`, pid `none`.
- prompt 2 `after_result_or_timeout`: `PROCESS_DISAPPEARED_AFTER_CLEANUP`, pid `none`.
- prompt 2 `after_cleanup`: `PROCESS_DISAPPEARED_AFTER_CLEANUP`, pid `none`.
- prompt 2 `after_10s`: `PROCESS_DISAPPEARED_AFTER_10S`, pid `none`.

Lifecycle result:

- prompt 1: `SUCCESS_CLEAN`.
- prompt 2: `TIMEOUT_SUSPECT`.
- prompt 3: not dispatched because prompt 2 set `next_prompt_allowed=false`,
  `reuse_allowed=false`, and `hidden_per_run_isolated_required=true`.

Native stage:

- prompt 2 reached `before RunDecode SetMaxOutputTokens(512)`.
- prompt 2 did not produce completed result, cleanup evidence, or
  `Engine.close=unique_ptr_cleanup`.

Focused process notes:

- `pidof` and `ps` found the process before prompt 2 dispatch.
- `pidof` and `ps` did not find the process immediately after prompt 2
  dispatch.
- `dumpsys activity top` at prompt 2 `after_dispatch` reported
  `io.github.ninbyo02.lami/.MainActivity ... pid=(not running)`.
- No explicit force-stop or Activity restart was used by the runner.

Decision:

512 sequential remains incomplete and non-baseline. 512 remains a
`hidden_per_run_isolated_512` candidate only. 256 remains the hidden
experimental baseline candidate. H1 remains pinned to 128. 1024/2048/4096
remain blocked.
