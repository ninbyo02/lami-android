# Sequential vs per-run isolated comparison

| mode | artifact | result | Python code result | cleanup | memory after 10s | decision |
| --- | --- | --- | --- | --- | --- | --- |
| 512 sequential code-aware | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/` | failure | timeout after pre-RunDecode evidence | missing for code prompt | no high retained rollback | not baseline |
| 512 per-run isolated | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/` | success | `useful_code`, indentation preserved, code fence closed | `Engine.close=unique_ptr_cleanup` for all prompts | no process after post-run force-stop | hidden per-run isolated candidate |
| 256 hidden reference | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/` | success | `useful_code` | present | no high retained rollback | hidden experimental baseline candidate |

## Interpretation

The same 512 Python calculator prompt is unstable in sequential three-prompt
execution but succeeds when each prompt is isolated by app force-stop before
and after the run. The current evidence supports a resource/cleanup or
sequential decode interaction rather than a max512 guard failure.

512 must therefore remain split into two categories:

- `sequential_512`: not accepted as a baseline
- `per_run_isolated_512`: candidate hidden mode only, gated by force-stop and
  cleanup/memory evidence
