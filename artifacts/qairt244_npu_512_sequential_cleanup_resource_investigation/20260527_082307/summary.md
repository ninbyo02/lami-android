# QAIRT244 NPU 512 sequential cleanup/resource investigation

Date: 2026-05-27

Scope: artifact/log/runner/native-stage review only. No additional NPU
execution, 512 rerun, 1024+ expansion, native guard change, QAIRT rebuild,
ChatScreen promotion, assistant-list insertion, DB, TTS, Markdown, streaming,
selectedPath=NPU persistence, release behavior, or standard behavior change was
performed.

Reviewed inputs:

- sequential code-aware 512 failure:
  `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`
- force-stop per-run isolated 512 success:
  `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`
- previous root-cause review:
  `artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`
- per-run isolated gate:
  `artifacts/qairt244_npu_512_per_run_isolated_gate/20260527_075622/`

Finding: 512 is not rejected by the native guard and is not broadly NPU
unsupported. The Python code prompt reaches pre-RunDecode evidence in the
sequential failure, but the receiver remains at `state=started` and no native
success/cleanup/backend evidence arrives before the 60 second bounded timeout.
The same prompt succeeds when bracketed by force-stop before and after the run.

Classification:

- primary: `sequential_resource_inheritance`
- secondary: `native_callback_missing_after_decode_or_decode_never_returns`
- plausible: `cleanup_wait_insufficient`, `code_decode_slow_after_warm_run`
- possible but not proven: `thermal_or_resource_slowdown_possible`
- not primary from current evidence: `runner_wait_condition_issue`

Decision: keep 512 as a hidden `per_run_isolated` candidate only. Sequential
512 remains non-baseline. 256 remains the hidden experimental baseline
candidate. 1024, 2048, and 4096 remain blocked.
