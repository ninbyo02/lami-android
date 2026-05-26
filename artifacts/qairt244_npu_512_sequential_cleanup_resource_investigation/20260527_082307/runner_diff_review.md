# Runner diff review

## Sequential runner

Script:
`scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh`

Observed behavior:

- waits for `files/qairt244_standard_hidden_prompt_state.txt`
- removes prior state/result/native diagnostic files before each prompt
- starts `.MainActivity` before each prompt
- broadcasts the hidden receiver with `max_output_tokens=512`
- records meminfo after each prompt
- force-stops only on timeout
- does not force-stop after successful prompt 1
- does not wait for no-process between prompts

Relevant runner lines:

- `wait_for_state`: lines 131-138
- `cleanup_app_files`: lines 293-302
- prompt execution: lines 367-423
- timeout force-stop only: lines 407-410
- final after-10s meminfo only: lines 942-944

## Per-run isolated runner

Script:
`scripts/run_qairt244_npu_max_output_512_force_stop_between_prompts.sh`

Observed behavior:

- uses the same state-file wait condition and the same 60 second upper bound
- force-stops before each prompt
- sleeps 2 seconds after pre-run force-stop
- records before-run meminfo, which reports no process
- force-stops after each prompt, even on success
- sleeps 10 seconds and records after-10s meminfo per prompt
- after-10s meminfo reports no process for all three prompts

Relevant runner lines:

- `wait_for_state`: lines 134-141
- `force_stop_app`: lines 342-351
- pre-run force-stop/meminfo: lines 422-425
- post-run force-stop/after-10s meminfo: lines 456-459

## Runner interpretation

The state-file wait condition is shared and is therefore unlikely to be the
primary root cause. The primary runner difference is warm-process reuse versus
force-stop bracketing. A state-file collision remains possible, but less likely
because the sequential runner deletes the state file before each prompt and the
isolated success uses the same state file path.
