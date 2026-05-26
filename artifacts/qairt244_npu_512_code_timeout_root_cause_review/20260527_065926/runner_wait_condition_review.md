# Runner Wait Condition Review

Reviewed scripts:

- `scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh`
- `scripts/run_qairt244_npu_max_output_512_code_bounded_retry.sh`
- `scripts/run_qairt244_npu_max_output_256_quality_compare.sh`

The requested `scripts/run_qairt244_npu_max_output_256_three_prompt_compare.sh`
does not exist in this checkout; the 256 comparison artifact was produced by
the available 256 quality comparison runner.

Both 512 runners use the same completion condition:

- broadcast the hidden prompt receiver
- wait until `files/qairt244_standard_hidden_prompt_state.txt` exists and is
  non-empty
- if not present before `TIMEOUT_SECONDS`, classify timeout and force-stop

The isolated 512 code retry used `TIMEOUT_SECONDS=60` and completed. The
code-aware sequential runner also used `TIMEOUT_SECONDS=60`, but the Python
prompt did not create the receiver state file. The pulled receiver and display
diagnostic files contain `No such file or directory`, while native diagnostics
show the lower native path reached pre-RunDecode.

Conclusion: the runner wait condition is not too strict as the primary cause.
It is doing the expected thing: no completed receiver state means no completed
result. A future review can still test alternate conditions, but only under a
separate approved runtime phase.
