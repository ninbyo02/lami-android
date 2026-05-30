# 512 Force-Stop Between Prompts Classification

Expectation: all completed prompt runs classify as SUCCESS_CLEAN and satisfy per-run isolation

| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| force_stop_between_prompts | konnichiwa | SUCCESS_CLEAN | false | true | false | 126 | true | false | false | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_konnichiwa` |
| force_stop_between_prompts | python_calculator | SUCCESS_CLEAN | false | true | false | 130 | true | false | false | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_python_calculator` |
| force_stop_between_prompts | lami_npu_short | SUCCESS_CLEAN | false | true | false | 142 | true | false | false | `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/run_512_lami_npu_short` |
