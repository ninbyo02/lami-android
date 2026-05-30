# Hidden NPU Lifecycle Summary Regeneration

Artifact: `artifacts/qairt244_hidden_npu_lifecycle_summary_regeneration/20260528_030629`

Mode: preflight-only regeneration from existing artifacts.

No NPU execution, no RunDecode invocation, no native change, and no QAIRT rebuild were performed.

## Targets

- `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002`
- `artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930`
- `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523`
- `artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116`
- `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`

## Result

- force_stop_all_success_clean=true
- bounded_retry_success_clean=true
- baseline_256_success_clean=true
- activity_restart_python_suspect=true
- sequential_codeaware_python_suspect=true
- stale_or_mismatch_rejected=not_present

Policy status: 256 remains the hidden experimental baseline candidate; 512 remains `hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only 512 remain rollback; H1 remains pinned to `max_output_tokens=128`; 1024/2048/4096 remain blocked.
