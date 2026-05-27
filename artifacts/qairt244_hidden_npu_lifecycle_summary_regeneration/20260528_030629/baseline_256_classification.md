# 256 Three-Prompt Baseline Candidate Classification

Expectation: completed 256 hidden runs classify as SUCCESS_CLEAN

| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| baseline_256_three_prompt | konnichiwa | SUCCESS_CLEAN | false | true | false | 146 | true | false | false | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_konnichiwa` |
| baseline_256_three_prompt | python_calculator | SUCCESS_CLEAN | false | true | false | 146 | true | false | false | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_python_calculator` |
| baseline_256_three_prompt | lami_npu_short | SUCCESS_CLEAN | false | true | false | 160 | true | false | false | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_lami_npu_short` |
