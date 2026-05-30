# QAIRT244 NPU 512 soft-reset process disappearance review

- source artifact: `artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime/20260528_041357/`
- review artifact: `artifacts/qairt244_npu_512_soft_reset_process_disappearance_review/20260528_043922/`
- scope: artifact/log/runner review only
- additional NPU execution: `false`
- native change: `false`
- QAIRT rebuild: `false`

## Result

The 512 soft-reset runtime partially improved the previous sequential behavior:

- prompt 1 `こんにちは`: `SUCCESS_CLEAN`
- prompt 2 `Pythonで簡単な電卓コードを書いて`: `SUCCESS_CLEAN`, `useful_code`, indentation preserved, code fence closed, cleanup `137ms`, `Engine.close=unique_ptr_cleanup`
- prompt 3 `ラミィのNPU推論について短く説明して`: `TIMEOUT_SUSPECT`

The next blocker is process disappearance after prompt 2. Immediately after
prompt 2, `dumpsys meminfo` still found pid `4758` with total PSS `306544 KB`.
After 10 seconds, `dumpsys meminfo io.github.ninbyo02.lami` returned
`No process found`. Prompt 3 then broadcasted successfully from shell, but no
state/result/native-diagnostic/cleanup files were created.

## Classification

Primary: `process_disappearance_unexplained`

Secondary: `os_killed_cached_process_possible`

Prompt 3 classification: `prompt3_actual_timeout_after_process_disappearance`

The review did not find runner-induced `force-stop`, `am kill`, `pm clear`, an
explicit Activity restart, crash marker, tombstone marker, ANR marker, or LMK
marker in the saved artifacts. Native cleanup for prompt 2 is clean, so the
process disappearance is outside the completed native decode/cleanup evidence
currently captured by the runner.

## Decision

512 sequential soft-reset is not a baseline candidate. Keep 512 as
`hidden_per_run_isolated_512` only, keep 256 as the hidden experimental
baseline candidate, keep H1 pinned to 128, and keep 1024/2048/4096 blocked.
