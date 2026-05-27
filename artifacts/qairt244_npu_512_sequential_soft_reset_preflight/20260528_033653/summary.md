# QAIRT244 NPU 512 Sequential Soft-Reset Preflight

Artifact: `artifacts/qairt244_npu_512_sequential_soft_reset_preflight/20260528_033653`

Mode: preflight-only. Existing artifacts were parsed; NPU was not executed.

## Simulation Result

- baseline_256_sequence=all_prompts_can_continue
- sequential_512_codeaware_sequence=stopped_at_prompt_2
- force_stop_512_clean_reference=all_prompts_can_continue

Decision: soft-reset sequential 512 remains design/preflight only. The current real sequential artifact still stops at prompt 2. 512 remains hidden_per_run_isolated_512 only; 256 remains the hidden experimental baseline candidate; H1 remains pinned to 128; 1024/2048/4096 remain blocked.
