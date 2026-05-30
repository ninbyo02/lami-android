# Preflight Simulation

| suite | prompt_index | prompt | lifecycle_classification | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | sequence_can_continue_after_prompt | stop_reason | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| baseline_256_clean | 1 | こんにちは | SUCCESS_CLEAN | true | false | 146 | true | true | ok | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_konnichiwa` |
| baseline_256_clean | 2 | Pythonで簡単な電卓コードを書いて | SUCCESS_CLEAN | true | false | 146 | true | true | ok | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_python_calculator` |
| baseline_256_clean | 3 | ラミィのNPU推論について短く説明して | SUCCESS_CLEAN | true | false | 160 | true | true | ok | `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/run_256_lami_npu_short` |
| sequential_512_codeaware | 1 | こんにちは | SUCCESS_CLEAN | true | false | 124 | true | true | ok | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/run_512_konnichiwa` |
| sequential_512_codeaware | 2 | Pythonで簡単な電卓コードを書いて | TIMEOUT_SUSPECT | false | true | missing | false | false | timeout_suspect | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/run_512_python_calculator` |
| force_stop_512_clean_reference | 1 | こんにちは | SUCCESS_CLEAN | true | false | 126 | true | true | ok | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_konnichiwa` |
| force_stop_512_clean_reference | 2 | Pythonで簡単な電卓コードを書いて | SUCCESS_CLEAN | true | false | 130 | true | true | ok | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_python_calculator` |
| force_stop_512_clean_reference | 3 | ラミィのNPU推論について短く説明して | SUCCESS_CLEAN | true | false | 142 | true | true | ok | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_lami_npu_short` |
