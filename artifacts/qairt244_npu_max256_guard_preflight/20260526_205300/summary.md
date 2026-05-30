# QAIRT244 max_output_tokens=256 guard preflight

- artifact: `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300`
- native_artifact: `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155`
- requested_max_output_tokens: `256`
- guard_status: `pass`
- npu_run_executed: `false`
- run_decode_executed: `false`
- chat_screen_connected: `false`
- db_tts_markdown_streaming: `false,false,false,false`

## Required Static Evidence

| check | status |
| --- | --- |
| `qairt244_editable_prompt_max256_v1` | `true` |
| `native_max_output_tokens_limit=256` | `true` |
| `SetMaxOutputTokens(256)` | `true` |
| `SM8750` selection | `true` |

## Result

256 guard-only patch built; run not executed. The 256 runner may proceed only outside `--preflight-only` and only with this guard evidence still present.
