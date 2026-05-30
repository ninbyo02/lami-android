# 512 Bounded Code Retry Classification

Expectation: the isolated bounded code retry classifies as SUCCESS_CLEAN when cleanup and Engine.close are present

| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| bounded_code_retry | python_calculator | SUCCESS_CLEAN | false | true | false | 142 | true | false | false | `artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/run_512_python_calculator` |
