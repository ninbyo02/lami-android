# Lifecycle gate results

| prompt_index | prompt | lifecycle_classification | next_prompt_allowed | reuse_allowed | runtime_reuse_allowed | runtime_reuse_policy | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | suspect_session | stop_reason | run_dir |
| ---: | --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1 | `こんにちは` | `SUCCESS_CLEAN` | `true` | `true` | `true` | `reuse_allowed` | `false` | `122` | `true` | `false` | `none` | `run_512_konnichiwa` |
| 2 | `Pythonで簡単な電卓コードを書いて` | `SUCCESS_CLEAN` | `true` | `true` | `true` | `reuse_allowed` | `false` | `137` | `true` | `false` | `none` | `run_512_python_calculator` |
| 3 | `ラミィのNPU推論について短く説明して` | `TIMEOUT_SUSPECT` | `false` | `false` | `false` | `per_run_isolated_required` | `true` | `missing` | `false` | `true` | `classification_TIMEOUT_SUSPECT` | `run_512_lami_npu_short` |
