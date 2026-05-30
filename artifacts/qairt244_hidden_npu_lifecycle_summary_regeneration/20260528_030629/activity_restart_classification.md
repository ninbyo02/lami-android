# 512 Activity-Restart-Only Classification

Expectation: the Python code timeout classifies as TIMEOUT_SUSPECT or CLEANUP_MISSING_SUSPECT

| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |
|---|---|---|---|---|---|---|---|---|---|---|
| activity_restart_only | konnichiwa | SUCCESS_CLEAN | false | true | false | 102 | true | false | false | `artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/run_512_konnichiwa` |
| activity_restart_only | python_calculator | TIMEOUT_SUSPECT | true | false | true | missing | false | false | false | `artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/run_512_python_calculator` |
| activity_restart_only | lami_npu_short | SUCCESS_CLEAN | false | true | false | 119 | true | false | false | `artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930/run_512_lami_npu_short` |
