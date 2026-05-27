# 512 Sequential Code-Aware Classification

Expectation: the Python code timeout classifies as TIMEOUT_SUSPECT or CLEANUP_MISSING_SUSPECT

| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| sequential_codeaware | konnichiwa | SUCCESS_CLEAN | false | true | false | 124 | true | false | false | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/run_512_konnichiwa` |
| sequential_codeaware | python_calculator | TIMEOUT_SUSPECT | true | false | true | missing | false | false | false | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/run_512_python_calculator` |
| sequential_codeaware | lami_npu_short | SUCCESS_CLEAN | false | true | false | 132 | true | false | false | `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/run_512_lami_npu_short` |
