# QAIRT244 max_output_tokens=512 guard preflight

- artifact: `artifacts/qairt244_npu_max512_guard_preflight/20260527_000522`
- native_artifact: `artifacts/qairt244_editable_prompt_max512_entrypoint_build/20260526_235239`
- native_artifact_present: `true`
- staged_binary_present: `true`
- sm8750_model_evidence: `artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/20260524_175320/sm8750_model_preflight.txt`
- requested_max_output_tokens: `512`
- guard_status: `pass`
- npu_run_executed: `false`
- engine_initialize_executed: `false`
- run_decode_executed: `false`
- chat_screen_connected: `false`
- db_tts_markdown_streaming: `false,false,false,false`

## Required Static Evidence

| check | status |
| --- | --- |
| native artifact path exists | `true` |
| staged `liblitertlm_jni.so` present | `true` |
| `qairt244_editable_prompt_max512_v1` | `true` |
| `native_max_output_tokens_limit=512` | `true` |
| `SetMaxOutputTokens(512)` | `true` |
| `SM8750` selection | `true` |

## Result

512 guard-only patch built; run not executed. The next phase must be a separately approved single-prompt hidden run.
