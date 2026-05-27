# Runner action review

Reviewed script:
`scripts/run_qairt244_npu_max_output_512_sequential_soft_reset_runtime.sh`

## Relevant runner behavior

- Starts `MainActivity` once before the prompt sequence.
- Does not call Activity start before each prompt.
- Does not force-stop between prompts.
- Does not Activity-restart between prompts.
- Before each prompt, deletes only app-private hidden runner files using
  `run-as io.github.ninbyo02.lami rm -f files/...`.
- On timeout, writes `timeout_no_force_stop.txt` and does not call
  `am force-stop`.

## Prompt 2 / prompt 3 artifacts

`run_512_python_calculator/activity_lifecycle.txt`:

- `activity_restart_before_prompt=false`
- `process_force_stop_before_prompt=false`
- `soft_reset_app_files=true`
- `activity_start_invoked_for_prompt=false`

`run_512_lami_npu_short/activity_lifecycle.txt` has the same values.

`run_512_lami_npu_short/timeout_no_force_stop.txt`:

- `timeout=true`
- `force_stop_invoked=false`
- `activity_restart_invoked=false`
- `sequence_policy=stop_without_process_kill`

## Conclusion

No runner action explains the process disappearance. The runner did not invoke
force-stop, Activity restart, `am kill`, `pm clear`, or an equivalent explicit
process-stop command between prompt 2 and prompt 3.
